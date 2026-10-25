package com.tcem.exception.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.common.constants.TopicNames;
import com.tcem.common.event.ExceptionEvent;
import com.tcem.common.model.exception.ExceptionRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ManualReviewProducer}.
 */
@ExtendWith(MockitoExtension.class)
class ManualReviewProducerTest {

    @Mock
    private KafkaOperations<String, String> kafkaOperations;

    private ObjectMapper objectMapper;
    private ManualReviewProducer producer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        producer = new ManualReviewProducer(kafkaOperations, objectMapper);
    }

    @Test
    void send_withValidTradeId_publishesToManualReviewTopic() {
        ExceptionEvent event = buildEvent("TRD-002");
        when(kafkaOperations.send(eq(TopicNames.MANUAL_REVIEW_REQUIRED), eq("TRD-002"), anyString()))
                .thenReturn(completedFuture("TRD-002"));

        CompletableFuture<SendResult<String, String>> result =
                producer.sendManualReviewRequired("TRD-002", event);

        assertThat(result).isNotNull();
        verify(kafkaOperations).send(
                eq(TopicNames.MANUAL_REVIEW_REQUIRED), eq("TRD-002"), anyString());
    }

    @Test
    void send_withNullTradeId_usesUnknownExceptionKey() {
        ExceptionEvent event = buildEvent(null);
        when(kafkaOperations.send(eq(TopicNames.MANUAL_REVIEW_REQUIRED), eq("UNKNOWN_EXCEPTION"), anyString()))
                .thenReturn(completedFuture("UNKNOWN_EXCEPTION"));

        producer.sendManualReviewRequired(null, event);

        verify(kafkaOperations).send(
                eq(TopicNames.MANUAL_REVIEW_REQUIRED), eq("UNKNOWN_EXCEPTION"), anyString());
    }

    @Test
    void send_withBlankTradeId_usesUnknownExceptionKey() {
        ExceptionEvent event = buildEvent(null);
        when(kafkaOperations.send(eq(TopicNames.MANUAL_REVIEW_REQUIRED), eq("UNKNOWN_EXCEPTION"), anyString()))
                .thenReturn(completedFuture("UNKNOWN_EXCEPTION"));

        producer.sendManualReviewRequired("", event);

        verify(kafkaOperations).send(
                eq(TopicNames.MANUAL_REVIEW_REQUIRED), eq("UNKNOWN_EXCEPTION"), anyString());
    }

    @Test
    void send_serializationFailure_returnsFailedFuture() throws Exception {
        ObjectMapper brokenMapper = new ObjectMapper() {
            @Override
            public String writeValueAsString(Object value) throws com.fasterxml.jackson.core.JsonProcessingException {
                throw new com.fasterxml.jackson.core.JsonProcessingException("ser-error") {};
            }
        };
        ManualReviewProducer brokenProducer = new ManualReviewProducer(kafkaOperations, brokenMapper);

        CompletableFuture<SendResult<String, String>> result =
                brokenProducer.sendManualReviewRequired("TRD-002", buildEvent("TRD-002"));

        assertThat(result.isCompletedExceptionally()).isTrue();
        verifyNoInteractions(kafkaOperations);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private ExceptionEvent buildEvent(String tradeId) {
        ExceptionRecord rec = new ExceptionRecord();
        rec.setTradeId(tradeId);
        rec.setExceptionId("EXC-30002");
        return ExceptionEvent.builder().exception(rec).build();
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<SendResult<String, String>> completedFuture(String key) {
        ProducerRecord<String, String> pr =
                new ProducerRecord<>(TopicNames.MANUAL_REVIEW_REQUIRED, key, "{}");
        RecordMetadata meta = new RecordMetadata(
                new TopicPartition(TopicNames.MANUAL_REVIEW_REQUIRED, 0), 0, 0, 0, 0, 0);
        return CompletableFuture.completedFuture(new SendResult<>(pr, meta));
    }
}

