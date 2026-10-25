package com.tcem.exception.consumer;

import com.tcem.exception.model.ExceptionProcessingResult;
import com.tcem.exception.service.ExceptionProcessingService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ExceptionCreatedConsumer}.
 * Verifies delegation to {@link ExceptionProcessingService} and error-swallowing behaviour.
 */
@ExtendWith(MockitoExtension.class)
class ExceptionCreatedConsumerTest {

    @Mock
    private ExceptionProcessingService processingService;

    private ExceptionCreatedConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ExceptionCreatedConsumer(processingService);
    }

    @Test
    @DisplayName("Valid message is delegated to ExceptionProcessingService")
    void validMessage_delegatesToService() {
        ConsumerRecord<String, String> record = record("TRD-001", "{\"exception\":{}}");
        when(processingService.process("TRD-001", "{\"exception\":{}}"))
                .thenReturn(ExceptionProcessingResult.CLASSIFIED);

        consumer.onMessage(record);

        verify(processingService).process("TRD-001", "{\"exception\":{}}");
    }

    @Test
    @DisplayName("DUPLICATE result from service does not throw")
    void duplicateResult_doesNotThrow() {
        ConsumerRecord<String, String> record = record("TRD-DUP", "{}");
        when(processingService.process(anyString(), anyString()))
                .thenReturn(ExceptionProcessingResult.DUPLICATE);

        consumer.onMessage(record);

        verify(processingService).process("TRD-DUP", "{}");
    }

    @Test
    @DisplayName("DEAD_LETTER result from service does not throw")
    void deadLetterResult_doesNotThrow() {
        ConsumerRecord<String, String> record = record("TRD-DLQ", "bad-json");
        when(processingService.process(anyString(), anyString()))
                .thenReturn(ExceptionProcessingResult.DEAD_LETTER);

        consumer.onMessage(record);

        verify(processingService).process("TRD-DLQ", "bad-json");
    }

    @Test
    @DisplayName("Unexpected exception from service is swallowed — Kafka offset is committed")
    void unexpectedException_isSwallowed() {
        ConsumerRecord<String, String> record = record("TRD-ERR", "{}");
        when(processingService.process(anyString(), anyString()))
                .thenThrow(new RuntimeException("Unexpected failure"));

        // Should not propagate — consumer must swallow to prevent infinite re-delivery
        consumer.onMessage(record);

        verify(processingService).process("TRD-ERR", "{}");
    }

    @Test
    @DisplayName("MANUAL_REVIEW result from service does not throw")
    void manualReviewResult_doesNotThrow() {
        ConsumerRecord<String, String> record = record("TRD-MR", "{}");
        when(processingService.process(anyString(), anyString()))
                .thenReturn(ExceptionProcessingResult.MANUAL_REVIEW);

        consumer.onMessage(record);

        verify(processingService).process("TRD-MR", "{}");
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private ConsumerRecord<String, String> record(String key, String value) {
        return new ConsumerRecord<>("exception.created", 0, 0L, key, value);
    }
}

