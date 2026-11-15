package com.tcem.enrichment.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.common.model.message.MessageHeader;
import com.tcem.common.model.message.TradeEvent;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProducerSendTimeoutTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @SuppressWarnings("unchecked")
    private final KafkaOperations<String, String> kafka = mock(KafkaOperations.class);

    private final TradeEnrichedProducer enriched = new TradeEnrichedProducer(kafka, mapper, 50);
    private final DeadLetterProducer deadLetter = new DeadLetterProducer(kafka, mapper, 50);

    private void sendReturns(CompletableFuture<SendResult<String, String>> future) {
        when(kafka.send(any(String.class), any(String.class), any(String.class))).thenReturn(future);
    }

    @Test
    void aSendThatNeverCompletesFailsInsteadOfBlockingTheConsumerThread() {
        sendReturns(new CompletableFuture<>());

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            assertThrows(IllegalStateException.class,
                    () -> enriched.publish("TRD-1", new TradeEvent(new MessageHeader(), null)));
            assertThrows(IllegalStateException.class,
                    () -> deadLetter.publish("TRD-1", "{}", null, new RuntimeException("boom"), true));
        });
    }

    @Test
    void aFailedSendIsReportedSoTheIdempotencyKeyIsNotRecorded() {
        sendReturns(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        assertThrows(IllegalStateException.class,
                () -> enriched.publish("TRD-1", new TradeEvent(new MessageHeader(), null)));
    }

    @Test
    void aCompletedSendSucceeds() {
        sendReturns(CompletableFuture.completedFuture(null));

        assertDoesNotThrow(() -> enriched.publish("TRD-1", new TradeEvent(new MessageHeader(), null)));
    }
}
