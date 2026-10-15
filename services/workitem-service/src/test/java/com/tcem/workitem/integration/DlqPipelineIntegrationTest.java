package com.tcem.workitem.integration;

import com.tcem.common.constants.TopicNames;
import com.tcem.workitem.DlqTestSupport;
import com.tcem.workitem.TestWiring;
import com.tcem.workitem.consumer.DeadLetterConsumer;
import com.tcem.workitem.consumer.PipelineProgressConsumer;
import com.tcem.workitem.couchdb.FindQueryRequest;
import com.tcem.workitem.model.document.AuditDocument;
import com.tcem.workitem.model.document.DlqEntryDocument;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaOperations;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/** Consumer -> service -> persistence -> replay -> replay outcome, with an in-memory CouchDB and a mocked Kafka. */
@ExtendWith(MockitoExtension.class)
class DlqPipelineIntegrationTest {

    @Mock
    private KafkaOperations<String, String> kafka;

    @Test
    @DisplayName("dead letter is consumed, linked to a work item, replayed, and closed when the replay succeeds")
    void deadLetterToReplaySuccess() throws Exception {
        TestWiring wiring = new TestWiring(kafka);
        DeadLetterConsumer dlqConsumer = new DeadLetterConsumer(wiring.dlqService);
        PipelineProgressConsumer progressConsumer =
                new PipelineProgressConsumer(wiring.replayOutcomeService, wiring.confirmationService);

        dlqConsumer.onMessage(new ConsumerRecord<>(TopicNames.DEADLETTER_TRADE, 0, 0L, "TRD-000001",
                DlqTestSupport.json(DlqTestSupport.deadLetter("DLQ-INT-1", true, 0))));

        List<DlqEntryDocument> entries = wiring.dlqRepository.findPage("PENDING_REPLAY", 0, 10);
        assertEquals(1, entries.size());
        DlqEntryDocument stored = entries.get(0);
        assertEquals("OPEN", wiring.workItemRepository.findById(stored.getLinkedWorkItemId()).orElseThrow().getStatus());
        verify(kafka, atLeastOnce()).send(eq(TopicNames.WORKITEM_LINKED), eq("TRD-000001"), any());

        wiring.replayService.replay(stored.getId());
        assertEquals("REPLAYING", wiring.dlqRepository.findById(stored.getId()).orElseThrow().getDlqStatus());

        // ingestion accepts the replayed trade and republishes it, header still marked REPLAY
        String accepted = "{\"messageHeader\":{\"messageId\":\"MSG-1\",\"eventType\":\"REPLAY\",\"replayCount\":1},"
                + "\"trade\":{\"tradeId\":\"TRD-000001\"}}";
        progressConsumer.onMessage(new ConsumerRecord<>(TopicNames.TRADE_ACCEPTED, 0, 5L, "TRD-000001", accepted));

        assertEquals("REPLAY_SUCCEEDED", wiring.dlqRepository.findById(stored.getId()).orElseThrow().getDlqStatus());
        assertEquals("DONE", wiring.workItemRepository.findById(stored.getLinkedWorkItemId()).orElseThrow().getStatus());

        List<String> stages = wiring.couchDb.find("audit-events",
                        new FindQueryRequest(Map.of("docType", "AUDIT_EVENT", "tradeId", "TRD-000001"), 50, 0),
                        AuditDocument.class).stream()
                .map(AuditDocument::getStage).toList();
        assertTrue(stages.containsAll(List.of("DLQ_RECEIVED", "WORKITEM_CREATED", "DLQ_REPLAY",
                "DLQ_REPLAY_SUCCEEDED", "WORKITEM_CLOSED")), "audit trail was " + stages);
    }
}
