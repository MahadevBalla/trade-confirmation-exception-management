package com.tcem.workitem.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.tcem.common.constants.HeaderKeys;
import com.tcem.common.constants.TopicNames;
import com.tcem.common.event.DeadLetterEvent;
import com.tcem.workitem.DlqTestSupport;
import com.tcem.workitem.TestWiring;
import com.tcem.workitem.model.document.DlqEntryDocument;
import com.tcem.workitem.repository.DlqEntryRepository;
import com.tcem.workitem.repository.WorkItemRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReplayServiceTest {

    @Mock
    private KafkaOperations<String, String> kafka;

    private DlqEntryRepository dlqRepository;
    private WorkItemRepository workItemRepository;
    private DlqService dlqService;
    private ReplayService replayService;

    @BeforeEach
    void setUp() {
        TestWiring wiring = new TestWiring(kafka);
        dlqRepository = wiring.dlqRepository;
        workItemRepository = wiring.workItemRepository;
        dlqService = wiring.dlqService;
        replayService = wiring.replayService;
    }

    private DlqEntryDocument ingest(String messageId, boolean replayable, int replayCount) throws Exception {
        return dlqService.handleDeadLetter("TRD-000001",
                DlqTestSupport.json(DlqTestSupport.deadLetter(messageId, replayable, replayCount)));
    }

    @Test
    @DisplayName("replay republishes to the failed topic as a REPLAY message and marks the entry REPLAYING")
    void replayRepublishesWithReplayHeader() throws Exception {
        DlqEntryDocument entry = ingest("DLQ-1", true, 0);

        DlqEntryDocument result = replayService.replay(entry.getId());

        assertEquals("REPLAYING", result.getDlqStatus());
        assertEquals(1, result.getReplayCount());
        assertNotNull(result.getLastReplayAt());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(captor.capture());
        ProducerRecord<String, String> sent = captor.getValue();
        assertEquals(TopicNames.TRADE_RAW, sent.topic());
        assertEquals("TRD-000001", sent.key());
        assertEquals("1", new String(sent.headers().lastHeader(HeaderKeys.REPLAY_COUNT).value(), StandardCharsets.UTF_8));
        assertEquals("REPLAY", new String(sent.headers().lastHeader(HeaderKeys.EVENT_TYPE).value(), StandardCharsets.UTF_8));

        JsonNode body = DlqTestSupport.objectMapper().readTree(sent.value());
        assertEquals("REPLAY", body.at("/messageHeader/eventType").asText());
        assertEquals(1, body.at("/messageHeader/replayCount").asInt());
        assertEquals("MSG-1", body.at("/messageHeader/messageId").asText(), "original message id is preserved");
        assertEquals("TRD-000001", body.at("/trade/tradeId").asText(), "business payload is untouched");
    }

    @Test
    @DisplayName("non-JSON payloads are republished verbatim")
    void nonJsonPayloadIsRepublishedVerbatim() {
        assertEquals("not-json", replayService.buildReplayPayload("not-json", 1));
        assertEquals("{\"a\":1}", replayService.buildReplayPayload("{\"a\":1}", 1));
        assertNull(replayService.buildReplayPayload(null, 1));
    }

    @Test
    @DisplayName("replaying an unknown entry fails with NOT_FOUND")
    void unknownEntryNotFound() {
        ReplayException ex = assertThrows(ReplayException.class, () -> replayService.replay("dlq:missing"));
        assertEquals(ReplayException.Reason.NOT_FOUND, ex.getReason());
    }

    @Test
    @DisplayName("non-replayable entries are rejected without publishing")
    void nonReplayableRejected() throws Exception {
        DlqEntryDocument entry = ingest("DLQ-2", false, 0);

        ReplayException ex = assertThrows(ReplayException.class, () -> replayService.replay(entry.getId()));

        assertEquals(ReplayException.Reason.NOT_REPLAYABLE, ex.getReason());
        verify(kafka, never()).send(any(ProducerRecord.class));
    }

    @Test
    @DisplayName("an entry that is already REPLAYING cannot be replayed again")
    void alreadyReplayingRejected() throws Exception {
        DlqEntryDocument entry = ingest("DLQ-3", true, 0);
        replayService.replay(entry.getId());

        ReplayException ex = assertThrows(ReplayException.class, () -> replayService.replay(entry.getId()));

        assertEquals(ReplayException.Reason.ALREADY_REPLAYING, ex.getReason());
    }

    @Test
    @DisplayName("at the replay limit the entry becomes PERMANENTLY_FAILED and its existing work item is escalated")
    void replayLimitEscalates() throws Exception {
        DlqEntryDocument entry = ingest("DLQ-4", true, 2);
        String originalWorkItem = entry.getLinkedWorkItemId();
        replayService.replay(entry.getId());                 // attempt 3 -> REPLAYING, count 3
        DlqEntryDocument replaying = dlqRepository.findById(entry.getId()).orElseThrow();
        assertEquals(3, replaying.getReplayCount());
        replaying.setDlqStatus("PENDING_REPLAY");            // simulate the replay failing again
        dlqRepository.save(replaying);

        ReplayException ex = assertThrows(ReplayException.class, () -> replayService.replay(entry.getId()));

        assertEquals(ReplayException.Reason.MAX_ATTEMPTS_REACHED, ex.getReason());
        DlqEntryDocument failed = dlqRepository.findById(entry.getId()).orElseThrow();
        assertEquals("PERMANENTLY_FAILED", failed.getDlqStatus());
        assertEquals(originalWorkItem, failed.getLinkedWorkItemId(), "the existing work item is reused");
        assertEquals("HIGH", workItemRepository.findById(originalWorkItem).orElseThrow().getPriority());
        assertEquals(1, workItemRepository.findAll().size(), "no second work item is created");
        assertEquals(3, failed.getReplayCount(), "no extra replay was published");
    }

    @Test
    @DisplayName("a downstream failure that arrives while the replay is still being published is merged, not duplicated")
    void failureDuringPublishIsMerged() throws Exception {
        DlqEntryDocument entry = ingest("DLQ-7", true, 0);
        DeadLetterEvent fastFailure = DlqTestSupport.deadLetter("DLQ-7-AGAIN", false, 0);
        when(kafka.send(any(ProducerRecord.class))).thenAnswer(invocation -> {
            // ingestion rejects the replayed message before the producer acknowledges the send
            dlqService.handleDeadLetter("TRD-000001", DlqTestSupport.json(fastFailure));
            return CompletableFuture.completedFuture(new SendResult<String, String>(null, null));
        });

        replayService.replay(entry.getId());

        assertEquals(1, dlqRepository.findPage(null, 0, 50).size(), "the failure was merged into the replayed entry");
        DlqEntryDocument stored = dlqRepository.findById(entry.getId()).orElseThrow();
        assertEquals("PERMANENTLY_FAILED", stored.getDlqStatus());
        assertEquals(1, stored.getReplayCount());
    }

    @Test
    @DisplayName("an entry whose replay already succeeded cannot be replayed again")
    void succeededEntryRejected() throws Exception {
        DlqEntryDocument entry = ingest("DLQ-6", true, 0);
        entry.setDlqStatus("REPLAY_SUCCEEDED");
        dlqRepository.save(entry);

        ReplayException ex = assertThrows(ReplayException.class, () -> replayService.replay(entry.getId()));

        assertEquals(ReplayException.Reason.NOT_REPLAYABLE, ex.getReason());
        verify(kafka, never()).send(any(ProducerRecord.class));
    }

    @Test
    @DisplayName("a failed publish leaves the entry untouched and reports PUBLISH_FAILED")
    void publishFailureKeepsState() throws Exception {
        DlqEntryDocument entry = ingest("DLQ-5", true, 0);
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("broker down"));
        when(kafka.send(any(ProducerRecord.class))).thenReturn(failed);

        ReplayException ex = assertThrows(ReplayException.class, () -> replayService.replay(entry.getId()));

        assertEquals(ReplayException.Reason.PUBLISH_FAILED, ex.getReason());
        DlqEntryDocument unchanged = dlqRepository.findById(entry.getId()).orElseThrow();
        assertEquals("PENDING_REPLAY", unchanged.getDlqStatus());
        assertEquals(0, unchanged.getReplayCount());
    }
}
