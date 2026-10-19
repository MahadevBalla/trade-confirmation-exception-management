package com.tcem.workitem.consumer;

import com.tcem.common.constants.TopicNames;
import com.tcem.workitem.service.DlqService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeadLetterConsumerTest {

    @Mock
    private DlqService dlqService;

    @Test
    @DisplayName("delegates key and payload to DlqService")
    void delegatesToService() {
        DeadLetterConsumer consumer = new DeadLetterConsumer(dlqService);

        consumer.onMessage(new ConsumerRecord<>(TopicNames.DEADLETTER_TRADE, 0, 7L, "TRD-1", "{}"));

        verify(dlqService).handleDeadLetter("TRD-1", "{}");
    }

    @Test
    @DisplayName("a failing service call is swallowed so the listener keeps consuming")
    void serviceFailureDoesNotPropagate() {
        DeadLetterConsumer consumer = new DeadLetterConsumer(dlqService);
        when(dlqService.handleDeadLetter("TRD-1", "{}")).thenThrow(new IllegalStateException("couchdb down"));

        assertDoesNotThrow(() ->
                consumer.onMessage(new ConsumerRecord<>(TopicNames.DEADLETTER_TRADE, 0, 8L, "TRD-1", "{}")));
    }
}
