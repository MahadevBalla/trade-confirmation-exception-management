package com.tcem.ingestion.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.ingestion.config.IngestionProperties;
import com.tcem.ingestion.generator.SyntheticTradeGenerator;
import com.tcem.ingestion.generator.TradeScenarioType;
import com.tcem.ingestion.producer.TradeRawProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public SeedLoaderService(SyntheticTradeGenerator generator,
                             TradeRawProducer producer,
                             IngestionProperties properties,
                             ObjectMapper objectMapper) {
        this.generator = generator;
        this.producer = producer;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> generateAndPublish(int count) {
        int targetCount = count > 0 ? count : properties.getDefaultGenerateCount();
        long startTime = System.currentTimeMillis();

        List<String> payloads = generator.generateBatch(targetCount);
        int published = producer.sendBatch(payloads);
        long elapsed = System.currentTimeMillis() - startTime;

        Map<String, Object> result = new HashMap<>();
        result.put("status", "SUCCESS");
        result.put("requestedCount", targetCount);
        result.put("publishedCount", published);
        result.put("durationMs", elapsed);
        result.put("targetTopic", "trade.raw");

        log.info("Completed generation and publishing of {} trades in {}ms", published, elapsed);
        return result;
    }

    @Override
    public Map<String, Object> generateSingleScenario(TradeScenarioType scenario, int sequence) {
        String payload = generator.generateTradeJson(scenario, sequence > 0 ? sequence : 1);
        producer.sendBatch(List.of(payload));

        Map<String, Object> result = new HashMap<>();
        result.put("status", "SUCCESS");
        result.put("scenario", scenario.name());
        result.put("sequence", sequence);
        result.put("targetTopic", "trade.raw");
        return result;
    }

    @Override
    public Map<String, Object> loadSeedData() {
        String resourcePath = properties.getSeedFilePath();
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            if (resource.exists()) {
                try (InputStream inputStream = resource.getInputStream()) {
                    List<Object> rawList = objectMapper.readValue(inputStream, new TypeReference<>() {});
                    List<String> payloads = rawList.stream()
                            .map(item -> {
                                try {
                                    return objectMapper.writeValueAsString(item);
                                } catch (Exception e) {
                                    return item.toString();
                                }
                            })
                            .toList();

                    int published = producer.sendBatch(payloads);
                    return Map.of(
                            "status", "SUCCESS",
                            "source", "file:" + resourcePath,
                            "publishedCount", published
                    );
                }
            }
        } catch (Exception ex) {
            log.warn("Could not read seed file '{}': {}. Falling back to programmatic generation",
                    resourcePath, ex.getMessage());
        }

        log.info("Generating default batch of {} trades as seed data", properties.getDefaultGenerateCount());
        return generateAndPublish(properties.getDefaultGenerateCount());
    }
}
