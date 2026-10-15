package com.tcem.workitem.consumer;

import com.tcem.common.constants.HeaderKeys;
import com.tcem.common.constants.TopicNames;
import com.tcem.workitem.service.ConfirmationService;
import com.tcem.workitem.service.ExceptionLinkingService;
import com.tcem.workitem.service.ReplayOutcomeService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PipelineConsumersTest {

    @Mock
    private ReplayOutcomeService replayOutcomeService;
    @Mock
    private ConfirmationService confirmationService;
    @Mock
    private ExceptionLinkingService linkingService;

    @Test
    @DisplayName("progress consumer: confirmations are recorded and every keyed record is checked for replay outcome")
    void progressRouting() {
        PipelineProgressConsumer consumer = new PipelineProgressConsumer(replayOutcomeService, confirmationService);

        consumer.onMessage(new ConsumerRecord<>(TopicNames.CONFIRMATION_GENERATED, 0, 0L, "TRD-1", "{}"));
        consumer.onMessage(new ConsumerRecord<>(TopicNames.TRADE_ACCEPTED, 0, 1L, "TRD-2", "{}"));
        consumer.onMessage(new ConsumerRecord<>(TopicNames.TRADE_ACCEPTED, 0, 2L, null, "{}"));

        verify(confirmationService).onConfirmation("TRD-1", "{}");
        verify(replayOutcomeService).onPipelineMessage(TopicNames.CONFIRMATION_GENERATED, "TRD-1", "{}", false);
        verify(replayOutcomeService).onPipelineMessage(TopicNames.TRADE_ACCEPTED, "TRD-2", "{}", false);
        verify(confirmationService, never()).onConfirmation("TRD-2", "{}");
    }

    @Test
    @DisplayName("exception and manual-review consumers delegate and swallow failures")
    void exceptionConsumers() {
        when(linkingService.onClassified(any())).thenThrow(new IllegalStateException("couchdb down"));
        ExceptionClassifiedConsumer classified = new ExceptionClassifiedConsumer(linkingService, replayOutcomeService);
        ManualReviewConsumer manualReview = new ManualReviewConsumer(linkingService);

        assertDoesNotThrow(() -> classified.onMessage(
                new ConsumerRecord<>(TopicNames.EXCEPTION_CLASSIFIED, 0, 0L, "TRD-1", "{}")));
        manualReview.onMessage(new ConsumerRecord<>(TopicNames.MANUAL_REVIEW_REQUIRED, 0, 0L, "TRD-1", "{\"x\":1}"));

        verify(linkingService).onManualReview("{\"x\":1}");
        verify(replayOutcomeService, never()).onPipelineMessage(any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("a positive tcem.replayCount header marks a record as a replay")
    void replayHeader() {
        ConsumerRecord<String, String> plain = new ConsumerRecord<>(TopicNames.TRADE_ACCEPTED, 0, 0L, "TRD-1", "{}");
        ConsumerRecord<String, String> replayed = new ConsumerRecord<>(TopicNames.TRADE_ACCEPTED, 0, 1L, "TRD-1", "{}");
        replayed.headers().add(HeaderKeys.REPLAY_COUNT, "2".getBytes(StandardCharsets.UTF_8));
        ConsumerRecord<String, String> zero = new ConsumerRecord<>(TopicNames.TRADE_ACCEPTED, 0, 2L, "TRD-1", "{}");
        zero.headers().add(HeaderKeys.REPLAY_COUNT, "0".getBytes(StandardCharsets.UTF_8));

        assertFalse(ConsumerRecords.hasReplayHeader(plain));
        assertTrue(ConsumerRecords.hasReplayHeader(replayed));
        assertFalse(ConsumerRecords.hasReplayHeader(zero));
    }
}
