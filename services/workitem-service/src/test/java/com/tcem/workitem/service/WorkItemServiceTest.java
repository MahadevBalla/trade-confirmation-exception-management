package com.tcem.workitem.service;

import com.tcem.common.constants.TopicNames;
import com.tcem.workitem.TestWiring;
import com.tcem.workitem.model.document.WorkItemDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WorkItemServiceTest {

    @Mock
    private KafkaOperations<String, String> kafka;

    private TestWiring wiring;
    private WorkItemService service;

    @BeforeEach
    void setUp() {
        wiring = new TestWiring(kafka);
        service = wiring.workItemService;
    }

    private WorkItemDocument draft(String tradeId, String type, String priority) {
        WorkItemDocument item = new WorkItemDocument();
        item.setTradeId(tradeId);
        item.setType(type);
        item.setTitle(type + " for " + tradeId);
        item.setOwnerQueue("DATA_REMEDIATION");
        item.setPriority(priority);
        return item;
    }

    @Test
    @DisplayName("create assigns a WI- id, OPEN status and publishes to workitem.linked")
    void create() {
        WorkItemDocument item = service.create(draft("TRD-1", "DATA_FIX", "MEDIUM"), "CORR-1");

        assertTrue(item.getWorkItemId().startsWith("WI-"));
        assertEquals("OPEN", item.getStatus());
        assertNotNull(item.getCreatedAt());
        assertTrue(wiring.workItemRepository.findById(item.getWorkItemId()).isPresent());
        verify(kafka).send(eq(TopicNames.WORKITEM_LINKED), eq("TRD-1"), any());
    }

    @Test
    @DisplayName("a second exception of the same type for the same trade is linked, keeping the higher priority")
    void linkSameType() {
        WorkItemDocument first = service.linkOrCreate(draft("TRD-1", "DATA_FIX", "MEDIUM"), "EXC-1", null);
        WorkItemDocument second = service.linkOrCreate(draft("TRD-1", "DATA_FIX", "CRITICAL"), "EXC-2", null);

        assertEquals(first.getWorkItemId(), second.getWorkItemId());
        assertEquals(List.of("EXC-1", "EXC-2"), second.getLinkedExceptionIds());
        assertEquals("EXC-1", second.getLinkedExceptionId());
        assertEquals("CRITICAL", second.getPriority());
        assertEquals(1, wiring.workItemRepository.findAll().size());
    }

    @Test
    @DisplayName("a different type, a different trade or a closed item all get a new work item")
    void createWhenNothingToLinkTo() {
        WorkItemDocument dataFix = service.linkOrCreate(draft("TRD-1", "DATA_FIX", "MEDIUM"), "EXC-1", null);
        WorkItemDocument template = service.linkOrCreate(draft("TRD-1", "TEMPLATE_FIX", "MEDIUM"), "EXC-2", null);
        WorkItemDocument otherTrade = service.linkOrCreate(draft("TRD-2", "DATA_FIX", "MEDIUM"), "EXC-3", null);
        service.update(dataFix.getWorkItemId(), "DONE", null, null, "fixed", "ops");
        WorkItemDocument afterClose = service.linkOrCreate(draft("TRD-1", "DATA_FIX", "MEDIUM"), "EXC-4", null);

        assertNotEquals(dataFix.getWorkItemId(), template.getWorkItemId());
        assertNotEquals(dataFix.getWorkItemId(), otherTrade.getWorkItemId());
        assertNotEquals(dataFix.getWorkItemId(), afterClose.getWorkItemId());
        assertEquals(4, wiring.workItemRepository.findAll().size());
    }

    @Test
    @DisplayName("update walks OPEN -> IN_PROGRESS -> DONE and stamps resolvedAt")
    void updateLifecycle() {
        WorkItemDocument item = service.create(draft("TRD-1", "DATA_FIX", "MEDIUM"), null);

        WorkItemDocument inProgress = service.update(item.getWorkItemId(), "IN_PROGRESS", "HIGH", "REFERENCE_DATA", null, "ops");
        assertEquals("IN_PROGRESS", inProgress.getStatus());
        assertEquals("HIGH", inProgress.getPriority());
        assertEquals("REFERENCE_DATA", inProgress.getOwnerQueue());
        assertNull(inProgress.getResolvedAt());

        WorkItemDocument done = service.update(item.getWorkItemId(), "DONE", null, null, "source fixed", "ops");
        assertEquals("DONE", done.getStatus());
        assertNotNull(done.getResolvedAt());
        assertEquals("source fixed", done.getResolutionNotes());
        verify(kafka, times(3)).send(eq(TopicNames.WORKITEM_LINKED), eq("TRD-1"), any());
    }

    @Test
    @DisplayName("terminal items reject changes with CONFLICT; bad values are INVALID_REQUEST")
    void updateErrors() {
        WorkItemDocument item = service.create(draft("TRD-1", "DATA_FIX", "MEDIUM"), null);
        String id = item.getWorkItemId();

        assertEquals(WorkItemException.Reason.INVALID_REQUEST,
                assertThrows(WorkItemException.class, () -> service.update(id, "FINISHED", null, null, null, null)).getReason());
        assertEquals(WorkItemException.Reason.INVALID_REQUEST,
                assertThrows(WorkItemException.class, () -> service.update(id, null, "URGENT", null, null, null)).getReason());
        assertEquals(WorkItemException.Reason.INVALID_REQUEST,
                assertThrows(WorkItemException.class, () -> service.update(id, null, null, null, null, null)).getReason());
        assertEquals(WorkItemException.Reason.NOT_FOUND,
                assertThrows(WorkItemException.class, () -> service.update("WI-NOPE", "DONE", null, null, null, null)).getReason());

        service.update(id, "CANCELLED", null, null, "duplicate", "ops");
        assertEquals(WorkItemException.Reason.CONFLICT,
                assertThrows(WorkItemException.class, () -> service.update(id, "OPEN", null, null, null, null)).getReason());
    }

    @Test
    @DisplayName("createManual validates type, title, queue and priority")
    void createManualValidation() {
        assertThrows(WorkItemException.class, () -> service.createManual("TRD-1", "BUG", "t", null, null, null, null));
        assertThrows(WorkItemException.class, () -> service.createManual("TRD-1", "DATA_FIX", " ", null, null, null, null));
        assertThrows(WorkItemException.class, () -> service.createManual("TRD-1", "DATA_FIX", "t", null, "NOPE", null, null));
        assertThrows(WorkItemException.class, () -> service.createManual("TRD-1", "DATA_FIX", "t", null, null, "NOPE", null));

        WorkItemDocument item = service.createManual("TRD-1", "ENHANCEMENT", "Add retry", null, null, null, "EXC-9");
        assertEquals("OPERATIONS_REVIEW", item.getOwnerQueue());
        assertEquals("MEDIUM", item.getPriority());
        assertEquals(List.of("EXC-9"), item.getLinkedExceptionIds());
    }

    @Test
    @DisplayName("escalate raises to HIGH but never lowers CRITICAL")
    void escalate() {
        WorkItemDocument low = service.create(draft("TRD-1", "DATA_FIX", "LOW"), null);
        WorkItemDocument critical = service.create(draft("TRD-2", "DATA_FIX", "CRITICAL"), null);

        assertEquals("HIGH", service.escalate(low.getWorkItemId(), "limit").orElseThrow().getPriority());
        assertEquals("CRITICAL", service.escalate(critical.getWorkItemId(), "limit").orElseThrow().getPriority());
        assertTrue(service.escalate("WI-NOPE", "limit").isEmpty());
    }

    @Test
    @DisplayName("closeOpenForTrade closes only that trade's open items")
    void closeOpenForTrade() {
        WorkItemDocument a = service.create(draft("TRD-1", "DATA_FIX", "LOW"), null);
        WorkItemDocument b = service.create(draft("TRD-1", "TEMPLATE_FIX", "LOW"), null);
        WorkItemDocument other = service.create(draft("TRD-2", "DATA_FIX", "LOW"), null);
        service.update(b.getWorkItemId(), "CANCELLED", null, null, null, null);

        List<WorkItemDocument> closed = service.closeOpenForTrade("TRD-1", "confirmed");

        assertEquals(List.of(a.getWorkItemId()), closed.stream().map(WorkItemDocument::getWorkItemId).toList());
        assertEquals("CANCELLED", wiring.workItemRepository.findById(b.getWorkItemId()).orElseThrow().getStatus());
        assertEquals("OPEN", wiring.workItemRepository.findById(other.getWorkItemId()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("summary counts by status, type, open queue and open priority, and flags aged items")
    @SuppressWarnings("unchecked")
    void summary() {
        WorkItemDocument aged = service.create(draft("TRD-1", "DATA_FIX", "HIGH"), null);
        aged.setCreatedAt(Instant.now().minus(Duration.ofHours(30)));
        wiring.workItemRepository.save(aged);
        service.create(draft("TRD-2", "TEMPLATE_FIX", "LOW"), null);
        WorkItemDocument done = service.create(draft("TRD-3", "DATA_FIX", "LOW"), null);
        service.update(done.getWorkItemId(), "DONE", null, null, null, null);

        Map<String, Object> summary = service.summary();

        assertEquals(3, summary.get("total"));
        assertEquals(2, summary.get("open"));
        assertEquals(1L, summary.get("agedOpen"));
        assertEquals(Map.of("OPEN", 2L, "DONE", 1L), summary.get("byStatus"));
        assertEquals(Map.of("DATA_FIX", 2L, "TEMPLATE_FIX", 1L), summary.get("byType"));
        assertEquals(Map.of("HIGH", 1L, "LOW", 1L), summary.get("openByPriority"));
    }
}
