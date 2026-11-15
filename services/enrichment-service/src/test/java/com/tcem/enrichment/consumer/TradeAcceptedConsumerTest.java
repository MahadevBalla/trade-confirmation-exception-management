package com.tcem.enrichment.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.common.constants.EventType;
import com.tcem.common.constants.ProductType;
import com.tcem.common.model.message.MessageHeader;
import com.tcem.common.model.message.TradeEvent;
import com.tcem.common.model.trade.Trade;
import com.tcem.enrichment.idempotency.EnrichmentIdempotencyRepository;
import com.tcem.enrichment.producer.DeadLetterProducer;
import com.tcem.enrichment.producer.TradeEnrichedProducer;
import com.tcem.enrichment.service.TradeEnricher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeAcceptedConsumerTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final TradeEnricher enricher = mock(TradeEnricher.class);
    private final TradeEnrichedProducer enrichedProducer = mock(TradeEnrichedProducer.class);
    private final DeadLetterProducer deadLetterProducer = mock(DeadLetterProducer.class);
    private final EnrichmentIdempotencyRepository idempotency = mock(EnrichmentIdempotencyRepository.class);
    private final TradeAcceptedConsumer consumer = new TradeAcceptedConsumer(mapper, enricher, enrichedProducer, deadLetterProducer, idempotency);

    @Test
    void publishesAndRecordsFirstDelivery() throws Exception {
        TradeEvent event = event();
        when(idempotency.isProcessed(any())).thenReturn(false);
        when(enricher.enrich(any(TradeEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        consumer.onMessage(new ConsumerRecord<>("trade.accepted", 0, 0L, "TRD-1", mapper.writeValueAsString(event)));

        verify(enrichedProducer).publish(eq("TRD-1"), any(TradeEvent.class));
        verify(idempotency).record(any());
    }

    @Test
    void suppressesDuplicateDelivery() throws Exception {
        TradeEvent event = event();
        when(idempotency.isProcessed(any())).thenReturn(true);

        consumer.onMessage(new ConsumerRecord<>("trade.accepted", 0, 0L, "TRD-1", mapper.writeValueAsString(event)));

        verify(enrichedProducer, never()).publish(any(), any());
        verify(enricher, never()).enrich(any());
    }

    @Test
    void deadLettersMalformedPayloadWithoutRetryingBusinessFlow() {
        consumer.onMessage(new ConsumerRecord<>("trade.accepted", 0, 0L, "TRD-1", "not-json"));
        verify(deadLetterProducer).publish(eq("TRD-1"), eq("not-json"), isNull(), any(), eq(false));
        verify(enrichedProducer, never()).publish(any(), any());
    }

    private TradeEvent event() {
        MessageHeader header = new MessageHeader("MSG-1", "CORR-1", EventType.NEW_TRADE,
                "capstone-fpml-json-1.0", Instant.parse("2026-01-01T00:00:00Z"), "ingestion-service", 0);
        Trade trade = new Trade(); trade.setTradeId("TRD-1"); trade.setProductType(ProductType.FX_OPTION);
        return new TradeEvent(header, trade);
    }

    @Test
    void replayIsReprocessedEvenWhenAnEarlierReplayRecordedTheKey() throws Exception {
        TradeEvent event = event();
        event.getMessageHeader().setEventType(EventType.REPLAY);
        event.getMessageHeader().setReplayCount(2);
        when(idempotency.isProcessed(any())).thenReturn(true);
        when(enricher.enrich(any(TradeEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        consumer.onMessage(new ConsumerRecord<>("trade.accepted", 0, 0L, "TRD-1", mapper.writeValueAsString(event)));

        verify(enrichedProducer).publish(eq("TRD-1"), any(TradeEvent.class));
        // The marker document already exists and the repository PUTs without a _rev, so re-recording would 409.
        verify(idempotency, never()).record(any());
    }

    @Test
    void aPublishFailureLeavesTheKeyUnrecordedSoTheMessageIsRetried() throws Exception {
        TradeEvent event = event();
        when(idempotency.isProcessed(any())).thenReturn(false);
        when(enricher.enrich(any(TradeEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.doThrow(new IllegalStateException("timed out")).when(enrichedProducer).publish(any(), any());

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
                consumer.onMessage(new ConsumerRecord<>("trade.accepted", 0, 0L, "TRD-1", mapper.writeValueAsString(event))));

        verify(idempotency, never()).record(any());
    }
}
