package com.tcem.workitem.service;

import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.constants.ExceptionSubTheme;
import com.tcem.common.constants.TopicNames;
import com.tcem.workitem.DlqTestSupport;
import com.tcem.workitem.TestWiring;
import com.tcem.workitem.model.document.DlqEntryDocument;
import com.tcem.workitem.model.document.WorkItemDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ManualReviewServiceTest {

    @Mock
    private KafkaOperations<String, String> kafka;

    private TestWiring wiring;
    private ManualReviewService service;

    @BeforeEach
    void setUp() {
        wiring = new TestWiring(kafka);
        service = wiring.manualReviewService;
    }

    private WorkItemDocument reviewItem(String exceptionId, String tradeId) throws Exception {
        return wiring.exceptionLinkingService.onManualReview(DlqTestSupport.json(DlqTestSupport.exceptionEvent(
                exceptionId, tradeId, ExceptionCategory.BUSINESS_RULE_FAILURE,
                ExceptionSubTheme.COUNTERPARTY_NOT_STP_ELIGIBLE, 0.9, "OPERATIONS_REVIEW", "MEDIUM")));
    }

    @Test
    @DisplayName("APPROVE closes the review item as DONE and records the decision")
    void approve() throws Exception {
        WorkItemDocument item = reviewItem("EXC-1", "TRD-1");

        WorkItemDocument decided = service.decide(item.getWorkItemId(), "APPROVE", "eligible after onboarding", "supervisor-1");

        assertEquals("DONE", decided.getStatus());
        assertEquals("APPROVED", decided.getReviewDecision());
        assertEquals("supervisor-1", decided.getReviewedBy());
        assertNotNull(decided.getResolvedAt());
        verify(kafka, never()).send(eq(TopicNames.DEADLETTER_TRADE), anyString(), any());
        // validation-service generates the confirmation from this event.
        verify(kafka).send(eq(TopicNames.WORKITEM_LINKED), eq("TRD-1"),
                org.mockito.ArgumentMatchers.argThat(json -> json != null && json.contains("\"reviewDecision\":\"APPROVED\"")));
    }

    @Test
    @DisplayName("REJECT closes the item and dead-letters the trade as non-replayable")
    void rejectDeadLetters() throws Exception {
        WorkItemDocument item = reviewItem("EXC-1", "TRD-1");

        WorkItemDocument decided = service.decide(item.getWorkItemId(), "REJECT", "client not onboarded", "supervisor-1");

        assertEquals("DONE", decided.getStatus());
        assertEquals("REJECTED", decided.getReviewDecision());
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(eq(TopicNames.DEADLETTER_TRADE), eq("TRD-1"), payload.capture());

        // the DLQ consumer then records it like any other dead letter
        DlqEntryDocument entry = wiring.dlqService.handleDeadLetter("TRD-1", payload.getValue());
        assertEquals("PERMANENTLY_FAILED", entry.getDlqStatus());
        assertFalse(entry.isReplayable());
        assertEquals(TopicNames.MANUAL_REVIEW_REQUIRED, entry.getFailedOnTopic());
        assertEquals("Rejected in manual review: client not onboarded", entry.getFailureReason());
        assertTrue(entry.getOriginalPayload().contains(item.getWorkItemId()));
    }

    @Test
    @DisplayName("invalid decisions are rejected: bad value, reject without notes, wrong type, already decided")
    void invalidDecisions() throws Exception {
        WorkItemDocument item = reviewItem("EXC-1", "TRD-1");
        WorkItemDocument dataFix = wiring.workItemService.createManual("TRD-2", "DATA_FIX", "fix", null, null, null, null);

        assertEquals(WorkItemException.Reason.INVALID_REQUEST, assertThrows(WorkItemException.class,
                () -> service.decide(item.getWorkItemId(), "MAYBE", null, null)).getReason());
        assertEquals(WorkItemException.Reason.INVALID_REQUEST, assertThrows(WorkItemException.class,
                () -> service.decide(item.getWorkItemId(), "REJECT", " ", null)).getReason());
        assertEquals(WorkItemException.Reason.CONFLICT, assertThrows(WorkItemException.class,
                () -> service.decide(dataFix.getWorkItemId(), "APPROVE", null, null)).getReason());
        assertEquals(WorkItemException.Reason.NOT_FOUND, assertThrows(WorkItemException.class,
                () -> service.decide("WI-NOPE", "APPROVE", null, null)).getReason());

        service.decide(item.getWorkItemId(), "APPROVE", null, null);
        assertEquals(WorkItemException.Reason.CONFLICT, assertThrows(WorkItemException.class,
                () -> service.decide(item.getWorkItemId(), "REJECT", "too late", null)).getReason());
    }
}
