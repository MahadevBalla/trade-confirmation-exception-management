package com.tcem.ingestion.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tcem.common.constants.TopicNames;
import com.tcem.common.model.message.MessageHeader;
import com.tcem.common.model.message.TradeEvent;
import com.tcem.common.model.trade.Trade;
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
class TradeAcceptedProducerTest {

    @Mock
    private KafkaOperations<String, String> kafkaOperations;

    private ObjectMapper objectMapper;
    private TradeAcceptedProducer producer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        producer = new TradeAcceptedProducer(kafkaOperations, objectMapper);
    }

    @Test
    @DisplayName("Successfully dispatches canonical TradeEvent to 'trade.accepted' topic")
    void testSendTradeAcceptedSuccess() {
        TradeEvent event = new TradeEvent();
        MessageHeader header = new MessageHeader();
        header.setMessageId("MSG-001");
        header.setCorrelationId("CORR-001");
        event.setMessageHeader(header);

        Trade trade = new Trade();
        trade.setTradeId("TRD-90001");
        event.setTrade(trade);

        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition(TopicNames.TRADE_ACCEPTED, 0),
                0, 0, 0, 0, 0
        );
        SendResult<String, String> sendResult = new SendResult<>(null, metadata);
        when(kafkaOperations.send(eq(TopicNames.TRADE_ACCEPTED), eq("TRD-90001"), any()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        CompletableFuture<SendResult<String, String>> future = producer.sendTradeAccepted("TRD-90001", event);

        assertThat(future).isCompleted();
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaOperations).send(eq(TopicNames.TRADE_ACCEPTED), eq("TRD-90001"), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).contains("TRD-90001").contains("MSG-001");
    }
}
