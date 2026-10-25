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
import org.mockito.ArgumentCaptor;
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
 * Unit tests for {@link ExceptionClassifiedProducer}.
 */
@ExtendWith(MockitoExtension.class)
class ExceptionClassifiedProducerTest {

    @Mock
    private KafkaOperations<String, String> kafkaOperations;

    private ObjectMapper objectMapper;
    private ExceptionClassifiedProducer producer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules(); // for Instant serialisation
        producer = new ExceptionClassifiedProducer(kafkaOperations, objectMapper);
    }

    @Test
    void send_withValidTradeId_publishesToCorrectTopic() {
        ExceptionEvent event = buildEvent("TRD-001");
        CompletableFuture<SendResult<String, String>> future = completedFuture("TRD-001");
        when(kafkaOperations.send(eq(TopicNames.EXCEPTION_CLASSIFIED), eq("TRD-001"), anyString()))
                .thenReturn(future);

        CompletableFuture<SendResult<String, String>> result =
                producer.sendExceptionClassified("TRD-001", event);

        assertThat(result).isNotNull();
        verify(kafkaOperations).send(
                eq(TopicNames.EXCEPTION_CLASSIFIED), eq("TRD-001"), anyString());
    }

    @Test
    void send_withNullTradeId_usesUnknownExceptionKey() {
        ExceptionEvent event = buildEvent(null);
        CompletableFuture<SendResult<String, String>> future = completedFuture("UNKNOWN_EXCEPTION");
        when(kafkaOperations.send(eq(TopicNames.EXCEPTION_CLASSIFIED), eq("UNKNOWN_EXCEPTION"), anyString()))
                .thenReturn(future);

        producer.sendExceptionClassified(null, event);

        verify(kafkaOperations).send(
                eq(TopicNames.EXCEPTION_CLASSIFIED), eq("UNKNOWN_EXCEPTION"), anyString());
    }

    @Test
    void send_withBlankTradeId_usesUnknownExceptionKey() {
        ExceptionEvent event = buildEvent(null);
        CompletableFuture<SendResult<String, String>> future = completedFuture("UNKNOWN_EXCEPTION");
        when(kafkaOperations.send(eq(TopicNames.EXCEPTION_CLASSIFIED), eq("UNKNOWN_EXCEPTION"), anyString()))
                .thenReturn(future);

        producer.sendExceptionClassified("   ", event);

        verify(kafkaOperations).send(
                eq(TopicNames.EXCEPTION_CLASSIFIED), eq("UNKNOWN_EXCEPTION"), anyString());
    }

    @Test
    void send_serializationFailure_returnsFailedFuture() throws Exception {
        // Use a broken ObjectMapper subclass (avoids Mockito mocking issues on JDK 26+)
        ObjectMapper brokenMapper = new ObjectMapper() {
            @Override
            public String writeValueAsString(Object value) throws com.fasterxml.jackson.core.JsonProcessingException {
                throw new com.fasterxml.jackson.core.JsonProcessingException("ser-error") {};
            }
        };
        ExceptionClassifiedProducer brokenProducer =
                new ExceptionClassifiedProducer(kafkaOperations, brokenMapper);

        CompletableFuture<SendResult<String, String>> result =
                brokenProducer.sendExceptionClassified("TRD-001", buildEvent("TRD-001"));

        assertThat(result.isCompletedExceptionally()).isTrue();
        verifyNoInteractions(kafkaOperations);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private ExceptionEvent buildEvent(String tradeId) {
        ExceptionRecord rec = new ExceptionRecord();
        rec.setTradeId(tradeId);
        rec.setExceptionId("EXC-30001");
        return ExceptionEvent.builder().exception(rec).build();
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<SendResult<String, String>> completedFuture(String key) {
        ProducerRecord<String, String> pr = new ProducerRecord<>(TopicNames.EXCEPTION_CLASSIFIED, key, "{}");
        RecordMetadata meta = new RecordMetadata(
                new TopicPartition(TopicNames.EXCEPTION_CLASSIFIED, 0), 0, 0, 0, 0, 0);
        SendResult<String, String> sr = new SendResult<>(pr, meta);
        return CompletableFuture.completedFuture(sr);
    }
}

