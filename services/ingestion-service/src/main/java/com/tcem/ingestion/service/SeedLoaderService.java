package com.tcem.ingestion.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.ingestion.config.IngestionProperties;
import com.tcem.ingestion.generator.SyntheticTradeGenerator;
import com.tcem.ingestion.generator.TradeScenarioType;
import com.tcem.ingestion.producer.TradeRawProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.tcem.ingestion.repository.TradeCouchDbRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service orchestrating synthetic trade generation and seed loading into trade.raw (FR1).
 */
@Service
public class SeedLoaderService implements TradeSeedService {

    private static final Logger log = LoggerFactory.getLogger(SeedLoaderService.class);

    private final SyntheticTradeGenerator generator;
    private final TradeRawProducer producer;
    private final IngestionProperties properties;
    private final ObjectMapper objectMapper;
    private final TradeCouchDbRepository tradeRepository;

    /** Closes the gap between a first seed starting and its trades being stored, e.g. a double click. */
    private final AtomicBoolean seedStarted = new AtomicBoolean(false);
    private long nextBatchSequence = 0;

    public SeedLoaderService(SyntheticTradeGenerator generator,
                             TradeRawProducer producer,
                             TradeCouchDbRepository tradeRepository,
                             IngestionProperties properties,
                             ObjectMapper objectMapper) {
        this.generator = generator;
        this.producer = producer;
        this.tradeRepository = tradeRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> generateAndPublish(int count, boolean publishToKafka) {
        int targetCount = count > 0 ? count : properties.getDefaultGenerateCount();
        long startTime = System.currentTimeMillis();

        // Published batches get fresh ids. Sequence numbers from 1 would repeat the seed's messages, and each one
        // would be suppressed as a duplicate rather than creating a trade.
        List<String> payloads = publishToKafka
                ? generator.generateBatch(targetCount, nextBatchStart(targetCount))
                : generator.generateBatch(targetCount);
        int published = 0;
        if (publishToKafka) {
            published = producer.sendBatch(payloads);
        }
        long elapsed = System.currentTimeMillis() - startTime;

        Map<String, Object> result = new HashMap<>();
        result.put("status", "SUCCESS");
        result.put("requestedCount", targetCount);
        result.put("publishedToKafka", publishToKafka);
        result.put("publishedCount", published);
        result.put("durationMs", elapsed);
        result.put("targetTopic", "trade.raw");

        // Include preview of first generated trade
        if (!payloads.isEmpty()) {
            try {
                result.put("sampleTrade", objectMapper.readTree(payloads.get(0)));
            } catch (Exception ignored) {
                result.put("sampleTrade", payloads.get(0));
            }
        }

        log.info("Completed generation of {} trades (publishedToKafka={}) in {}ms",
                targetCount, publishToKafka, elapsed);
        return result;
    }

    @Override
    public Map<String, Object> generateSingleScenario(TradeScenarioType scenario, int sequence, boolean publishToKafka) {
        String payload = generator.generateTradeJson(scenario, sequence > 0 ? sequence : 1);
        int published = 0;
        if (publishToKafka) {
            published = producer.sendBatch(List.of(payload));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("status", "SUCCESS");
        result.put("scenario", scenario.name());
        result.put("sequence", sequence);
        result.put("publishedToKafka", publishToKafka);
        result.put("publishedCount", published);
        result.put("targetTopic", "trade.raw");

        try {
            result.put("tradePayload", objectMapper.readTree(payload));
        } catch (Exception ignored) {
            result.put("tradePayload", payload);
        }

        return result;
    }

    @Override
    public Map<String, Object> loadSeedData(boolean publishToKafka, boolean force) {
        String resourcePath = properties.getSeedFilePath();
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            if (resource.exists()) {
                try (InputStream inputStream = resource.getInputStream()) {
                    List<Map<String, Object>> rawList = objectMapper.readValue(inputStream, new TypeReference<>() {});
                    List<String> payloads = rawList.stream()
                            .map(item -> {
                                try {
                                    Map<String, Object> trade = (Map<String, Object>) item.get("trade");
                                    if (trade != null) {
                                        Map<String, Object> eco = (Map<String, Object>) trade.get("fxOptionEconomics");
                                        if (eco != null && !eco.containsKey("premiumPaymentDate") && trade.containsKey("effectiveDate")) {
                                            eco.put("premiumPaymentDate", trade.get("effectiveDate"));
                                        }
                                    }
                                    return objectMapper.writeValueAsString(item);
                                } catch (Exception e) {
                                    return item.toString();
                                }
                            })
                            .toList();

                    if (publishToKafka && !force) {
                        claimSeed(rawList);
                    }
                    int published = 0;
                    if (publishToKafka) {
                        try {
                            published = producer.sendBatch(payloads);
                            seedStarted.set(true);
                        } catch (RuntimeException ex) {
                            seedStarted.set(false);
                            throw ex;
                        }
                    }

                    Map<String, Object> result = new HashMap<>();
                    result.put("status", "SUCCESS");
                    result.put("source", "file:" + resourcePath);
                    result.put("totalSeedTrades", payloads.size());
                    result.put("publishedToKafka", publishToKafka);
                    result.put("publishedCount", published);
                    if (!rawList.isEmpty()) {
                        result.put("sampleTrade", rawList.get(0));
                    }
                    return result;
                }
            }
        } catch (SeedAlreadyLoadedException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Could not read seed file '{}': {}. Falling back to programmatic generation",
                    resourcePath, ex.getMessage());
        }

        log.info("Generating default batch of {} trades as seed data", properties.getDefaultGenerateCount());
        return generateAndPublish(properties.getDefaultGenerateCount(), publishToKafka);
    }

    /** Refuses a repeat seed. Checking the database as well as the flag means this also holds across restarts. */
    @SuppressWarnings("unchecked")
    private void claimSeed(List<Map<String, Object>> seedMessages) {
        if (!seedMessages.isEmpty() && seedMessages.get(0).get("trade") instanceof Map<?, ?> trade
                && ((Map<String, Object>) trade).get("tradeId") instanceof String firstTradeId
                && tradeRepository.findById(firstTradeId).isPresent()) {
            throw new SeedAlreadyLoadedException();
        }
        if (!seedStarted.compareAndSet(false, true)) {
            throw new SeedAlreadyLoadedException();
        }
    }

    /** A sequence range no earlier batch used, including before a restart, and above the seed's 1-500. */
    private synchronized int nextBatchStart(int count) {
        long fromClock = (System.currentTimeMillis() / 1000 % 1_000_000) * 1000;
        long start = Math.max(nextBatchSequence, fromClock);
        nextBatchSequence = start + count;
        return (int) start;
    }
}
