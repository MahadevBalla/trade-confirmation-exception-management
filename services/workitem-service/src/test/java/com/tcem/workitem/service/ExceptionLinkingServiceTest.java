package com.tcem.workitem.service;

import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.constants.ExceptionSubTheme;
import com.tcem.workitem.DlqTestSupport;
import com.tcem.workitem.TestWiring;
import com.tcem.workitem.model.document.WorkItemDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaOperations;

import java.util.List;

import static com.tcem.common.constants.ExceptionCategory.COUNTERPARTY_MATCHING_DISPUTE;
import static com.tcem.common.constants.ExceptionCategory.MISSING_ECONOMIC_DATA;
import static com.tcem.common.constants.ExceptionCategory.TEMPLATE_ISSUE;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ExceptionLinkingServiceTest {

    @Mock
    private KafkaOperations<String, String> kafka;

    private TestWiring wiring;
    private ExceptionLinkingService service;

    @BeforeEach
    void setUp() {
        wiring = new TestWiring(kafka);
        service = wiring.exceptionLinkingService;
    }

    private String classified(String exceptionId, String tradeId, ExceptionCategory category,
                              ExceptionSubTheme subTheme, double confidence, String queue, String impact) throws Exception {
        return DlqTestSupport.json(DlqTestSupport.exceptionEvent(exceptionId, tradeId, category, subTheme,
                confidence, queue, impact));
    }

    @Test
    @DisplayName("a classified template issue becomes a TEMPLATE_FIX item using the recommendation")
    void classifiedCreatesTypedItem() throws Exception {
        WorkItemDocument item = service.onClassified(classified("EXC-1", "TRD-1", TEMPLATE_ISSUE,
                ExceptionSubTheme.NO_MATCHING_TEMPLATE, 0.95, "TEMPLATE_MAINTENANCE", "HIGH"));

        assertEquals("TEMPLATE_FIX", item.getType());
        assertEquals("TEMPLATE_MAINTENANCE", item.getOwnerQueue());
        assertEquals("HIGH", item.getPriority());
        assertEquals("EXC-1", item.getLinkedExceptionId());
        assertEquals("TEMPLATE_ISSUE", item.getCategory());
        assertEquals("NO_MATCHING_TEMPLATE", item.getSubTheme());
        assertEquals("NO_MATCHING_TEMPLATE for TRD-1", item.getTitle());
        assertTrue(item.getDescription().contains("Recommended action: Fix it"));
    }

    @Test
    @DisplayName("a second exception of the same type on the same trade is linked, and redelivery is a no-op")
    void secondExceptionLinkedAndRedeliveryIgnored() throws Exception {
        String first = classified("EXC-1", "TRD-1", MISSING_ECONOMIC_DATA, ExceptionSubTheme.MISSING_STRIKE, 0.9, "DATA_REMEDIATION", "MEDIUM");
        WorkItemDocument a = service.onClassified(first);
        WorkItemDocument b = service.onClassified(classified("EXC-2", "TRD-1", MISSING_ECONOMIC_DATA,
                ExceptionSubTheme.MISSING_NOTIONAL, 0.9, "DATA_REMEDIATION", "MEDIUM"));
        WorkItemDocument redelivered = service.onClassified(first);

        assertEquals(a.getWorkItemId(), b.getWorkItemId());
        assertEquals(a.getWorkItemId(), redelivered.getWorkItemId());
        WorkItemDocument stored = wiring.workItemRepository.findById(a.getWorkItemId()).orElseThrow();
        assertEquals(List.of("EXC-1", "EXC-2"), stored.getLinkedExceptionIds());
        assertEquals(1, wiring.workItemRepository.findAll().size());
    }

    @Test
    @DisplayName("a low-confidence classification goes to a MANUAL_REVIEW item in OPERATIONS_REVIEW")
    void lowConfidenceGoesToReview() throws Exception {
        WorkItemDocument item = service.onClassified(classified("EXC-1", "TRD-1", TEMPLATE_ISSUE,
                ExceptionSubTheme.NO_MATCHING_TEMPLATE, 0.4, "TEMPLATE_MAINTENANCE", "LOW"));

        assertEquals("MANUAL_REVIEW", item.getType());
        assertEquals("OPERATIONS_REVIEW", item.getOwnerQueue());
        assertTrue(item.getDescription().startsWith("Low classification confidence 0.4"));
        assertEquals("Review NO_MATCHING_TEMPLATE for TRD-1", item.getTitle());
    }

    @Test
    @DisplayName("an unknown recommended queue falls back to the category default")
    void unknownQueueFallsBack() throws Exception {
        WorkItemDocument item = service.onClassified(classified("EXC-1", "TRD-1", COUNTERPARTY_MATCHING_DISPUTE,
                ExceptionSubTheme.ECONOMIC_TERM_MISMATCH, 0.9, "SOMEWHERE", null));

        assertEquals("MANUAL_REVIEW", item.getType());
        assertEquals("OPERATIONS_REVIEW", item.getOwnerQueue());
        assertEquals("MEDIUM", item.getPriority(), "no impact given; MEDIUM difficulty -> MEDIUM");
    }

    @Test
    @DisplayName("manual.review.required creates a MANUAL_REVIEW item even when the exception has a typed item")
    void manualReviewIsSeparateFromTypedItem() throws Exception {
        String event = classified("EXC-1", "TRD-1", TEMPLATE_ISSUE, ExceptionSubTheme.NO_MATCHING_TEMPLATE,
                0.95, "TEMPLATE_MAINTENANCE", "HIGH");
        WorkItemDocument typed = service.onClassified(event);
        WorkItemDocument review = service.onManualReview(event);
        WorkItemDocument reviewAgain = service.onManualReview(event);

        assertEquals("TEMPLATE_FIX", typed.getType());
        assertEquals("MANUAL_REVIEW", review.getType());
        assertEquals("TEMPLATE_MAINTENANCE", review.getOwnerQueue());
        assertNotEquals(typed.getWorkItemId(), review.getWorkItemId());
        assertEquals(review.getWorkItemId(), reviewAgain.getWorkItemId());
        assertEquals(2, wiring.workItemRepository.findAll().size());
    }

    @Test
    @DisplayName("records without an exception payload are discarded")
    void badPayloadsDiscarded() {
        assertNull(service.onClassified("{not json"));
        assertNull(service.onClassified("{\"messageHeader\":{}}"));
        assertNull(service.onManualReview("[]"));
        assertTrue(wiring.workItemRepository.findAll().isEmpty());
    }
}
