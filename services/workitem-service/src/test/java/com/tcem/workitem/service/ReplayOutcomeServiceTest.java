package com.tcem.workitem.service;

import com.tcem.common.constants.TopicNames;
import com.tcem.common.event.DeadLetterEvent;
import com.tcem.workitem.DlqTestSupport;
import com.tcem.workitem.TestWiring;
import com.tcem.workitem.model.document.DlqEntryDocument;
import com.tcem.workitem.model.document.WorkItemDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaOperations;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ReplayOutcomeServiceTest {

    private static final String TRADE = "TRD-000001";
    private static final String REPLAYED_ACCEPTED =
            "{\"messageHeader\":{\"messageId\":\"MSG-1\",\"eventType\":\"REPLAY\",\"replayCount\":1},\"trade\":{\"tradeId\":\"TRD-000001\"}}";
    private static final String ORDINARY_ACCEPTED =
            "{\"messageHeader\":{\"messageId\":\"MSG-2\",\"eventType\":\"NEW_TRADE\",\"replayCount\":0},\"trade\":{\"tradeId\":\"TRD-000001\"}}";

    @Mock
    private KafkaOperations<String, String> kafka;

    private TestWiring wiring;

    @BeforeEach
    void setUp() {
        wiring = new TestWiring(kafka);
    }

    /** Stores a replayable dead letter for TRADE and replays it once. */
    private DlqEntryDocument replayingEntry(String messageId) throws Exception {
        DlqEntryDocument entry = wiring.dlqService.handleDeadLetter(TRADE,
                DlqTestSupport.json(DlqTestSupport.deadLetter(messageId, true, 0)));
        return wiring.replayService.replay(entry.getId());
    }

    private DlqEntryDocument fail(String messageId, boolean replayable) throws Exception {
        DeadLetterEvent failure = DlqTestSupport.deadLetter(messageId, replayable, 0);
        failure.setFailureReason("still failing: " + messageId);
        return wiring.dlqService.handleDeadLetter(TRADE, DlqTestSupport.json(failure));
    }

    @Test
    @DisplayName("a replay-marked message on a downstream topic marks the entry REPLAY_SUCCEEDED and closes its work item")
    void successWithMarker() throws Exception {
        DlqEntryDocument entry = replayingEntry("DLQ-1");

        assertEquals(1, wiring.replayOutcomeService.onPipelineMessage(TopicNames.TRADE_ACCEPTED, TRADE, REPLAYED_ACCEPTED, false).size());

        assertEquals("REPLAY_SUCCEEDED", wiring.dlqRepository.findById(entry.getId()).orElseThrow().getDlqStatus());
        WorkItemDocument item = wiring.workItemRepository.findById(entry.getLinkedWorkItemId()).orElseThrow();
        assertEquals("DONE", item.getStatus());
        assertTrue(item.getResolutionNotes().contains("Replay #1 succeeded"));
    }

    @Test
    @DisplayName("the Kafka replay header alone is accepted as a marker")
    void successWithHeaderOnly() throws Exception {
        DlqEntryDocument entry = replayingEntry("DLQ-1");

        wiring.replayOutcomeService.onPipelineMessage(TopicNames.EXCEPTION_CREATED, TRADE, "not json", true);

        assertEquals("REPLAY_SUCCEEDED", wiring.dlqRepository.findById(entry.getId()).orElseThrow().getDlqStatus());
    }

    @Test
    @DisplayName("an unmarked message or an unrelated topic does not count as replay success")
    void noFalseSuccess() throws Exception {
        DlqEntryDocument entry = replayingEntry("DLQ-1");

        assertTrue(wiring.replayOutcomeService.onPipelineMessage(TopicNames.TRADE_ACCEPTED, TRADE, ORDINARY_ACCEPTED, false).isEmpty());
        assertTrue(wiring.replayOutcomeService.onPipelineMessage(TopicNames.TRADE_ENRICHED, TRADE, REPLAYED_ACCEPTED, false).isEmpty(),
                "trade.enriched does not follow trade.raw directly");
        assertTrue(wiring.replayOutcomeService.onPipelineMessage(TopicNames.TRADE_ACCEPTED, "TRD-OTHER", REPLAYED_ACCEPTED, false).isEmpty());

        assertEquals("REPLAYING", wiring.dlqRepository.findById(entry.getId()).orElseThrow().getDlqStatus());
    }

    @Test
    @DisplayName("a new dead letter for a REPLAYING trade is merged back into the entry, and its redelivery is a no-op")
    void failedReplayMerged() throws Exception {
        DlqEntryDocument entry = replayingEntry("DLQ-1");

        DlqEntryDocument merged = fail("DLQ-2", true);
        DlqEntryDocument redelivered = fail("DLQ-2", true);

        assertEquals(entry.getId(), merged.getId());
        assertEquals(entry.getId(), redelivered.getId());
        DlqEntryDocument stored = wiring.dlqRepository.findById(entry.getId()).orElseThrow();
        assertEquals("PENDING_REPLAY", stored.getDlqStatus());
        assertEquals(1, stored.getReplayCount());
        assertEquals("still failing: DLQ-2", stored.getFailureReason());
        assertEquals(2, stored.getFailureHistory().size());
        assertTrue(stored.getFailureHistory().get(1).startsWith("Replay #1 failed"));
        assertEquals(1, wiring.dlqRepository.findPage(null, 0, 50).size(), "no second entry");
        assertEquals(1, wiring.workItemRepository.findAll().size(), "no second work item");
    }

    @Test
    @DisplayName("three failed replays end in PERMANENTLY_FAILED with the one work item escalated to HIGH")
    void replayChainExhausts() throws Exception {
        DlqEntryDocument entry = replayingEntry("DLQ-1");
        fail("DLQ-2", true);
        wiring.replayService.replay(entry.getId());
        fail("DLQ-3", true);
        wiring.replayService.replay(entry.getId());
        fail("DLQ-4", true);

        DlqEntryDocument stored = wiring.dlqRepository.findById(entry.getId()).orElseThrow();
        assertEquals("PERMANENTLY_FAILED", stored.getDlqStatus());
        assertEquals(3, stored.getReplayCount());
        assertEquals(4, stored.getFailureHistory().size());
        assertEquals(1, wiring.workItemRepository.findAll().size());
        WorkItemDocument item = wiring.workItemRepository.findById(entry.getLinkedWorkItemId()).orElseThrow();
        assertEquals("HIGH", item.getPriority());
        assertTrue(item.getDescription().contains("replay limit of 3 reached"));
    }

    @Test
    @DisplayName("a non-replayable failure of a replay ends the entry immediately")
    void nonReplayableFailureEndsEntry() throws Exception {
        DlqEntryDocument entry = replayingEntry("DLQ-1");

        fail("DLQ-2", false);

        DlqEntryDocument stored = wiring.dlqRepository.findById(entry.getId()).orElseThrow();
        assertEquals("PERMANENTLY_FAILED", stored.getDlqStatus());
        assertFalse(stored.isReplayable());
        assertEquals("HIGH", wiring.workItemRepository.findById(entry.getLinkedWorkItemId()).orElseThrow().getPriority());
    }

    @Test
    @DisplayName("dead letters for trades without a pending replay create new entries as before")
    void unrelatedDeadLetterNotMerged() throws Exception {
        fail("DLQ-1", true);
        fail("DLQ-2", true);

        assertEquals(2, wiring.dlqRepository.findPage(null, 0, 50).size());
    }

    @Test
    @DisplayName("a replayed exception.created that is routed to manual review counts as processed")
    void manualReviewRoutingIsSuccess() throws Exception {
        DeadLetterEvent failure = DlqTestSupport.deadLetter("DLQ-MR", true, 0);
        failure.setFailedOnTopic(TopicNames.EXCEPTION_CREATED);
        DlqEntryDocument entry = wiring.replayService.replay(
                wiring.dlqService.handleDeadLetter(TRADE, DlqTestSupport.json(failure)).getId());

        wiring.replayOutcomeService.onPipelineMessage(TopicNames.MANUAL_REVIEW_REQUIRED, TRADE, REPLAYED_ACCEPTED, false);

        assertEquals("REPLAY_SUCCEEDED", wiring.dlqRepository.findById(entry.getId()).orElseThrow().getDlqStatus());
    }
}
