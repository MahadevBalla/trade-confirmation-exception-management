package com.tcem.ingestion.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.common.constants.TopicNames;
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

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeRawProducerTest {

    @Mock
    private KafkaOperations<String, String> kafkaOperations;

    private TradeRawProducer producer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        producer = new TradeRawProducer(kafkaOperations, objectMapper);
    }

    @Test
    void sendTradeRaw_sendsToKafkaTopic() {
        ProducerRecord<String, String> record = new ProducerRecord<>(TopicNames.TRADE_RAW, "TRD-001", "{}");
        RecordMetadata metadata = new RecordMetadata(new TopicPartition(TopicNames.TRADE_RAW, 0), 0, 0, 0, 0, 0);
        SendResult<String, String> sendResult = new SendResult<>(record, metadata);

        when(kafkaOperations.send(eq(TopicNames.TRADE_RAW), eq("TRD-001"), eq("{}")))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        CompletableFuture<SendResult<String, String>> future = producer.sendTradeRaw("TRD-001", "{}");

        assertThat(future).isCompletedWithValue(sendResult);
        verify(kafkaOperations).send(eq(TopicNames.TRADE_RAW), eq("TRD-001"), eq("{}"));
    }

    @Test
    void sendBatch_extractsKeysAndPublishesAll() {
        String payload1 = "{\"trade\":{\"tradeId\":\"TRD-101\"}}";
        String payload2 = "{\"trade\":{\"tradeId\":\"TRD-102\"}}";

        when(kafkaOperations.send(eq(TopicNames.TRADE_RAW), anyString(), anyString()))
                .thenReturn(new CompletableFuture<>());

        int count = producer.sendBatch(List.of(payload1, payload2));

        assertThat(count).isEqualTo(2);
        verify(kafkaOperations).send(eq(TopicNames.TRADE_RAW), eq("TRD-101"), eq(payload1));
        verify(kafkaOperations).send(eq(TopicNames.TRADE_RAW), eq("TRD-102"), eq(payload2));
    }
}
