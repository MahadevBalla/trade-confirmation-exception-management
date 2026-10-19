package com.tcem.workitem.service;

import com.tcem.common.constants.TopicNames;
import com.tcem.workitem.DlqTestSupport;
import com.tcem.workitem.TestWiring;
import com.tcem.workitem.model.document.DlqEntryDocument;
import com.tcem.workitem.model.document.WorkItemDocument;
import com.tcem.workitem.repository.DlqEntryRepository;
import com.tcem.workitem.repository.WorkItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaOperations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DlqServiceTest {

    @Mock
    private KafkaOperations<String, String> kafka;

    private DlqEntryRepository dlqRepository;
    private WorkItemRepository workItemRepository;
    private DlqService service;

    @BeforeEach
    void setUp() {
        TestWiring wiring = new TestWiring(kafka);
        dlqRepository = wiring.dlqRepository;
        workItemRepository = wiring.workItemRepository;
        service = wiring.dlqService;
    }

    @Test
    @DisplayName("replayable dead letter is stored as PENDING_REPLAY with a MEDIUM DATA_FIX work item")
    void replayableDeadLetterCreatesMediumWorkItem() throws Exception {
        DlqEntryDocument entry = service.handleDeadLetter("TRD-000001",
                DlqTestSupport.json(DlqTestSupport.deadLetter("DLQ-1", true, 0)));

        assertNotNull(entry);
        assertEquals("dlq:DLQ-1", entry.getId());
        assertEquals("TRD-000001", entry.getTradeId());
        assertEquals("PENDING_REPLAY", entry.getDlqStatus());
        assertEquals(TopicNames.TRADE_RAW, entry.getFailedOnTopic());
        assertNotNull(entry.getLinkedWorkItemId());

        WorkItemDocument item = workItemRepository.findById(entry.getLinkedWorkItemId()).orElseThrow();
        assertEquals("DATA_FIX", item.getType());
        assertEquals("MEDIUM", item.getPriority());
        assertEquals("OPEN", item.getStatus());
        assertEquals("TECHNOLOGY_SUPPORT", item.getOwnerQueue());
        assertEquals("dlq:DLQ-1", item.getLinkedDlqEntryId());
        verify(kafka).send(eq(TopicNames.WORKITEM_LINKED), eq("TRD-000001"), any());
    }

    @Test
    @DisplayName("non-replayable dead letter is PERMANENTLY_FAILED with a HIGH priority work item")
    void nonReplayableIsPermanentlyFailed() throws Exception {
        DlqEntryDocument entry = service.handleDeadLetter("DLQ_1699999999",
                DlqTestSupport.json(DlqTestSupport.deadLetter("DLQ-2", false, 0)));

        assertEquals("PERMANENTLY_FAILED", entry.getDlqStatus());
        assertNull(entry.getTradeId(), "synthetic DLQ_ keys must not be treated as trade ids");
        WorkItemDocument item = workItemRepository.findById(entry.getLinkedWorkItemId()).orElseThrow();
        assertEquals("HIGH", item.getPriority());
    }

    @Test
    @DisplayName("dead letter already at the replay limit is PERMANENTLY_FAILED")
    void replayLimitReachedIsPermanentlyFailed() throws Exception {
        DlqEntryDocument entry = service.handleDeadLetter("TRD-000001",
                DlqTestSupport.json(DlqTestSupport.deadLetter("DLQ-3", true, 3)));

        assertEquals("PERMANENTLY_FAILED", entry.getDlqStatus());
    }

    @Test
    @DisplayName("redelivered dead letter does not create a second entry or work item")
    void redeliveryIsIdempotent() throws Exception {
        String payload = DlqTestSupport.json(DlqTestSupport.deadLetter("DLQ-4", true, 0));

        DlqEntryDocument first = service.handleDeadLetter("TRD-000001", payload);
        DlqEntryDocument second = service.handleDeadLetter("TRD-000001", payload);

        assertEquals(first.getId(), second.getId());
        assertEquals(first.getLinkedWorkItemId(), second.getLinkedWorkItemId());
        assertEquals(1, workItemRepository.findPage(null, 0, 50).size());
        verify(kafka, times(1)).send(eq(TopicNames.WORKITEM_LINKED), any(), any());
    }

    @Test
    @DisplayName("ingestion placeholder keys are not stored as trade ids")
    void placeholderKeysAreNotTradeIds() throws Exception {
        assertNull(service.resolveTradeId("UNKNOWN_KEY_1792042677595", null));
        assertNull(service.resolveTradeId("DLQ_1699999999", null));
        assertNull(service.resolveTradeId(null, null));
        assertNull(service.resolveTradeId("  ", null));
        assertEquals("TRD-000001", service.resolveTradeId("TRD-000001", null));

        DlqEntryDocument entry = service.handleDeadLetter("UNKNOWN_KEY_1792042677595",
                DlqTestSupport.json(DlqTestSupport.deadLetter("DLQ-7", false, 0)));

        assertNull(entry.getTradeId());
        WorkItemDocument item = workItemRepository.findById(entry.getLinkedWorkItemId()).orElseThrow();
        assertNull(item.getTradeId());
        verify(kafka).send(eq(TopicNames.WORKITEM_LINKED), eq(item.getWorkItemId()), any());
    }

    @Test
    @DisplayName("unparseable record is discarded without side effects")
    void unparseableRecordIsDiscarded() {
        assertNull(service.handleDeadLetter("TRD-000001", "{not json"));

        assertTrue(dlqRepository.findPage(null, 0, 50).isEmpty());
        assertTrue(workItemRepository.findPage(null, 0, 50).isEmpty());
        verify(kafka, never()).send(any(String.class), any(), any());
    }

    @Test
    @DisplayName("status filter only returns matching entries")
    void listFiltersByStatus() throws Exception {
        service.handleDeadLetter("TRD-000001", DlqTestSupport.json(DlqTestSupport.deadLetter("DLQ-5", true, 0)));
        service.handleDeadLetter("DLQ_1", DlqTestSupport.json(DlqTestSupport.deadLetter("DLQ-6", false, 0)));

        List<DlqEntryDocument> pending = dlqRepository.findPage("PENDING_REPLAY", 0, 10);
        List<DlqEntryDocument> failed = dlqRepository.findPage("PERMANENTLY_FAILED", 0, 10);

        assertEquals(1, pending.size());
        assertEquals("dlq:DLQ-5", pending.get(0).getId());
        assertEquals(1, failed.size());
        assertEquals("dlq:DLQ-6", failed.get(0).getId());
    }
}
