package com.tcem.validation.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.constants.ExceptionSubTheme;
import com.tcem.common.constants.TopicNames;
import com.tcem.common.event.ExceptionEvent;
import com.tcem.common.model.exception.ExceptionRecord;
import com.tcem.common.model.message.MessageHeader;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExceptionCreatedProducerTest {

    @Mock
    private KafkaOperations<String, String> kafkaOperations;

    private ObjectMapper objectMapper;
    private ExceptionCreatedProducer producer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        producer = new ExceptionCreatedProducer(kafkaOperations, objectMapper);
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private SendResult<String, String> buildSendResult(String topic) {
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition(topic, 0), 0, 0, 0, 0, 0);
        return new SendResult<>(null, metadata);
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Dispatches ExceptionEvent to 'exception.created' with correct tradeId key")
    void testSendExceptionCreatedSuccess() {
        MessageHeader header = new MessageHeader();
        header.setMessageId("MSG-EXC-001");
        header.setCorrelationId("CORR-EXC-001");

        ExceptionRecord record = new ExceptionRecord();
        record.setExceptionId("EXC-30001");
        record.setTradeId("TRD-30001");
        record.setCategory(ExceptionCategory.MISSING_ECONOMIC_DATA);
        record.setSubTheme(ExceptionSubTheme.MISSING_NOTIONAL);

        ExceptionEvent event = new ExceptionEvent(header, record);

        when(kafkaOperations.send(eq(TopicNames.EXCEPTION_CREATED), eq("TRD-30001"), any()))
                .thenReturn(CompletableFuture.completedFuture(buildSendResult(TopicNames.EXCEPTION_CREATED)));

        CompletableFuture<SendResult<String, String>> future = producer.sendExceptionCreated("TRD-30001", event);

        assertThat(future).isCompleted();
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaOperations).send(eq(TopicNames.EXCEPTION_CREATED), eq("TRD-30001"), payloadCaptor.capture());
        String payload = payloadCaptor.getValue();
        assertThat(payload)
                .contains("EXC-30001")
                .contains("TRD-30001")
                .contains("MISSING_ECONOMIC_DATA")
                .contains("MISSING_NOTIONAL")
                .contains("MSG-EXC-001");
    }

    @Test
    @DisplayName("Dispatches business eligibility exception with correct category and subtheme")
    void testSendExceptionCreatedBusinessEligibility() {
        ExceptionRecord record = new ExceptionRecord();
        record.setExceptionId("EXC-30002");
        record.setTradeId("TRD-30002");
        record.setCategory(ExceptionCategory.BUSINESS_RULE_FAILURE);
        record.setSubTheme(ExceptionSubTheme.COUNTERPARTY_NOT_STP_ELIGIBLE);

        ExceptionEvent event = new ExceptionEvent(new MessageHeader(), record);

        when(kafkaOperations.send(eq(TopicNames.EXCEPTION_CREATED), eq("TRD-30002"), any()))
                .thenReturn(CompletableFuture.completedFuture(buildSendResult(TopicNames.EXCEPTION_CREATED)));

        producer.sendExceptionCreated("TRD-30002", event);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaOperations).send(eq(TopicNames.EXCEPTION_CREATED), eq("TRD-30002"), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue())
                .contains("BUSINESS_RULE_FAILURE")
                .contains("COUNTERPARTY_NOT_STP_ELIGIBLE");
    }

    @Test
    @DisplayName("Dispatches template issue exception with correct category and subtheme")
    void testSendExceptionCreatedTemplateIssue() {
        ExceptionRecord record = new ExceptionRecord();
        record.setExceptionId("EXC-30003");
        record.setTradeId("TRD-30003");
        record.setCategory(ExceptionCategory.TEMPLATE_ISSUE);
        record.setSubTheme(ExceptionSubTheme.NO_MATCHING_TEMPLATE);

        ExceptionEvent event = new ExceptionEvent(new MessageHeader(), record);

        when(kafkaOperations.send(eq(TopicNames.EXCEPTION_CREATED), eq("TRD-30003"), any()))
                .thenReturn(CompletableFuture.completedFuture(buildSendResult(TopicNames.EXCEPTION_CREATED)));

        producer.sendExceptionCreated("TRD-30003", event);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaOperations).send(eq(TopicNames.EXCEPTION_CREATED), eq("TRD-30003"), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue())
                .contains("TEMPLATE_ISSUE")
                .contains("NO_MATCHING_TEMPLATE");
    }

    @Test
    @DisplayName("Falls back to 'UNKNOWN_EXCEPTION' key when tradeId is null")
    void testSendExceptionCreatedNullTradeIdFallback() {
        ExceptionEvent event = new ExceptionEvent();

        when(kafkaOperations.send(eq(TopicNames.EXCEPTION_CREATED), eq("UNKNOWN_EXCEPTION"), any()))
                .thenReturn(CompletableFuture.completedFuture(buildSendResult(TopicNames.EXCEPTION_CREATED)));

        CompletableFuture<SendResult<String, String>> future = producer.sendExceptionCreated(null, event);

        assertThat(future).isCompleted();
        verify(kafkaOperations).send(eq(TopicNames.EXCEPTION_CREATED), eq("UNKNOWN_EXCEPTION"), any());
    }

    @Test
    @DisplayName("Returns failed future when Kafka send fails")
    void testSendExceptionCreatedKafkaFailure() {
        ExceptionEvent event = new ExceptionEvent();
        RuntimeException kafkaError = new RuntimeException("Broker unavailable");

        when(kafkaOperations.send(eq(TopicNames.EXCEPTION_CREATED), eq("TRD-30004"), any()))
                .thenReturn(CompletableFuture.failedFuture(kafkaError));

        CompletableFuture<SendResult<String, String>> future = producer.sendExceptionCreated("TRD-30004", event);

        assertThat(future).isCompletedExceptionally();
    }
}

