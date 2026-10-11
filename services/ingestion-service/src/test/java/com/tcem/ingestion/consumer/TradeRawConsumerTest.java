package com.tcem.ingestion.consumer;

import com.tcem.common.constants.TopicNames;
import com.tcem.ingestion.model.IngestionResult;
import com.tcem.ingestion.service.TradeIngestionService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeRawConsumerTest {

    @Mock
    private TradeIngestionService tradeIngestionService;

    @InjectMocks
    private TradeRawConsumer consumer;

    @Test
    @DisplayName("Successfully consumes record from trade.raw and delegates to TradeIngestionService")
    void testOnMessageSuccess() {
        String key = "TRD-10001";
        String payload = "{\"trade\":{\"tradeId\":\"TRD-10001\"}}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>(TopicNames.TRADE_RAW, 0, 10L, key, payload);

        when(tradeIngestionService.processRawTrade(eq(key), eq(payload)))
                .thenReturn(IngestionResult.accepted(key, "MSG-001", "idemp-key"));

        consumer.onMessage(record);

        verify(tradeIngestionService).processRawTrade(eq(key), eq(payload));
    }

    @Test
    @DisplayName("Handles unhandled exception during processing without throwing out of listener")
    void testOnMessageExceptionHandledGracefully() {
        String key = "TRD-99999";
        String payload = "{}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>(TopicNames.TRADE_RAW, 0, 11L, key, payload);

        when(tradeIngestionService.processRawTrade(eq(key), eq(payload)))
                .thenThrow(new RuntimeException("Simulated unexpected failure"));

        // Must not throw exception out of onMessage
        consumer.onMessage(record);

        verify(tradeIngestionService).processRawTrade(eq(key), eq(payload));
    }
}
