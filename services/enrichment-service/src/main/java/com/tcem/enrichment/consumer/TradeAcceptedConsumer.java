package com.tcem.enrichment.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.common.constants.EventType;
import com.tcem.common.constants.TopicNames;
import com.tcem.common.idempotency.IdempotencyKey;
import com.tcem.common.model.message.MessageHeader;
import com.tcem.common.model.message.TradeEvent;
import com.tcem.common.routing.SchemaVersionRouter;
import com.tcem.enrichment.idempotency.EnrichmentIdempotencyRepository;
import com.tcem.enrichment.producer.DeadLetterProducer;
import com.tcem.enrichment.producer.TradeEnrichedProducer;
import com.tcem.enrichment.service.TradeEnricher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TradeAcceptedConsumer {
    private static final Logger log = LoggerFactory.getLogger(TradeAcceptedConsumer.class);
    private final ObjectMapper objectMapper;
    private final TradeEnricher enricher;
    private final TradeEnrichedProducer enrichedProducer;
    private final DeadLetterProducer deadLetterProducer;
    private final EnrichmentIdempotencyRepository idempotencyRepository;

    public TradeAcceptedConsumer(ObjectMapper objectMapper, TradeEnricher enricher, TradeEnrichedProducer enrichedProducer,
                                 DeadLetterProducer deadLetterProducer, EnrichmentIdempotencyRepository idempotencyRepository) {
        this.objectMapper = objectMapper; this.enricher = enricher; this.enrichedProducer = enrichedProducer;
        this.deadLetterProducer = deadLetterProducer; this.idempotencyRepository = idempotencyRepository;
    }

    @KafkaListener(topics = TopicNames.TRADE_ACCEPTED, groupId = "${spring.kafka.consumer.group-id}",
            autoStartup = "${spring.kafka.listener.auto-startup:true}")
    public void onMessage(ConsumerRecord<String, String> record) {
        log.info("Received accepted trade [key={}, topic={}, partition={}, offset={}]",
                record.key(), record.topic(), record.partition(), record.offset());
        TradeEvent event;
        try {
            event = new SchemaVersionRouter(objectMapper).route(record.value());
            requireIdentity(event);
        } catch (java.io.IOException | SchemaVersionRouter.UnsupportedSchemaVersionException | IllegalArgumentException ex) {
            log.warn("Dead-lettering invalid accepted trade [key={}, topic={}, partition={}, offset={}]: {}",
                    record.key(), record.topic(), record.partition(), record.offset(), ex.getMessage());
            deadLetterProducer.publish(record.key(), record.value(), extractHeader(record.value()), ex, false);
            return;
        } catch (Exception ex) {
            log.warn("Dead-lettering unreadable accepted trade [key={}, topic={}, partition={}, offset={}]: {}",
                    record.key(), record.topic(), record.partition(), record.offset(), ex.getMessage());
            deadLetterProducer.publish(record.key(), record.value(), extractHeader(record.value()), ex, true);
            return;
        }

        IdempotencyKey key = IdempotencyKey.of(event.getTrade().getTradeId(), event.getMessageHeader().getMessageId(),
                event.getMessageHeader().getSchemaVersion(), event.getMessageHeader().getEventType());
        // Every replay attempt of a message shares one key, so replays bypass the guard (as in validation and
        // exception-service); otherwise attempt 2 would be dropped and its DLQ entry would stay REPLAYING.
        boolean replay = event.getMessageHeader().getEventType() == EventType.REPLAY;
        if (!replay && idempotencyRepository.isProcessed(key)) {
            log.info("Skipping duplicate accepted trade [tradeId={}, messageId={}, topic={}, partition={}, offset={}]",
                    event.getTrade().getTradeId(), event.getMessageHeader().getMessageId(), record.topic(), record.partition(), record.offset());
            return;
        }

        TradeEvent enriched = enricher.enrich(event);
        enrichedProducer.publish(enriched.getTrade().getTradeId(), enriched);
        // At-least-once boundary: a crash after Kafka acknowledges but before this write can re-publish on redelivery.
        if (!replay) {
            idempotencyRepository.record(key); // PUT without _rev: re-recording an existing marker would 409.
        }
        log.info("Published enriched trade [tradeId={}, messageId={}, outputTopic={}, inputPartition={}, inputOffset={}]",
                enriched.getTrade().getTradeId(), enriched.getMessageHeader().getMessageId(), "trade.enriched",
                record.partition(), record.offset());
    }

    private void requireIdentity(TradeEvent event) {
        if (event == null || event.getTrade() == null || event.getMessageHeader() == null
                || blank(event.getTrade().getTradeId()) || blank(event.getMessageHeader().getMessageId())
                || blank(event.getMessageHeader().getSchemaVersion()) || event.getMessageHeader().getEventType() == null) {
            throw new IllegalArgumentException("trade.accepted event is missing a required identity field");
        }
    }

    private MessageHeader extractHeader(String payload) {
        try { return objectMapper.readValue(payload, TradeEvent.class).getMessageHeader(); }
        catch (Exception ignored) { return null; }
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
}
