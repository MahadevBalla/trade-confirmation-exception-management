package com.tcem.ingestion.producer;

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

    @Test
    @DisplayName("Successfully dispatches ExceptionEvent to 'exception.created' topic")
    void testSendExceptionCreatedSuccess() {
        MessageHeader header = new MessageHeader();
        header.setMessageId("MSG-EXC-001");
        header.setCorrelationId("CORR-001");

        ExceptionRecord record = new ExceptionRecord();
        record.setExceptionId("EXC-90001");
        record.setTradeId("TRD-90001");
        record.setCategory(ExceptionCategory.TECHNICAL_PROCESSING_FAILURE);
        record.setSubTheme(ExceptionSubTheme.DUPLICATE_MESSAGE);

        ExceptionEvent event = new ExceptionEvent(header, record);

        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition(TopicNames.EXCEPTION_CREATED, 0),
                0, 0, 0, 0, 0
        );
        SendResult<String, String> sendResult = new SendResult<>(null, metadata);
        when(kafkaOperations.send(eq(TopicNames.EXCEPTION_CREATED), eq("TRD-90001"), any()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        CompletableFuture<SendResult<String, String>> future = producer.sendExceptionCreated("TRD-90001", event);

        assertThat(future).isCompleted();
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaOperations).send(eq(TopicNames.EXCEPTION_CREATED), eq("TRD-90001"), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue())
                .contains("EXC-90001")
                .contains("DUPLICATE_MESSAGE")
                .contains("TECHNICAL_PROCESSING_FAILURE");
    }
}
