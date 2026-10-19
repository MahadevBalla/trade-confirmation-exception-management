package com.tcem.workitem.service;

import com.tcem.workitem.TestWiring;
import com.tcem.workitem.couchdb.FindQueryRequest;
import com.tcem.workitem.model.document.AuditDocument;
import com.tcem.workitem.model.document.WorkItemDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaOperations;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ConfirmationServiceTest {

    private static final String CONFIRMATION = "{\"messageHeader\":{\"messageId\":\"MSG-C1\",\"correlationId\":\"CORR-1\","
            + "\"eventType\":\"NEW_TRADE\"},\"confirmation\":{\"confirmationId\":\"CONF-1\",\"tradeId\":\"TRD-1\","
            + "\"templateId\":\"TMPL-FX-OPT-001\",\"status\":\"MATCHED\"}}";

    @Mock
    private KafkaOperations<String, String> kafka;

    private TestWiring wiring;

    @BeforeEach
    void setUp() {
        wiring = new TestWiring(kafka);
    }

    @Test
    @DisplayName("a confirmation closes the trade's open work items and is audited as CONFIRMED")
    void closesOpenItemsAndAudits() {
        WorkItemDocument open = wiring.workItemService.createManual("TRD-1", "DATA_FIX", "fix", null, null, null, null);
        WorkItemDocument cancelled = wiring.workItemService.createManual("TRD-1", "TEMPLATE_FIX", "tmpl", null, null, null, null);
        wiring.workItemService.update(cancelled.getWorkItemId(), "CANCELLED", null, null, null, null);
        WorkItemDocument otherTrade = wiring.workItemService.createManual("TRD-2", "DATA_FIX", "fix", null, null, null, null);

        List<WorkItemDocument> closed = wiring.confirmationService.onConfirmation("TRD-1", CONFIRMATION);

        assertEquals(List.of(open.getWorkItemId()), closed.stream().map(WorkItemDocument::getWorkItemId).toList());
        WorkItemDocument stored = wiring.workItemRepository.findById(open.getWorkItemId()).orElseThrow();
        assertEquals("DONE", stored.getStatus());
        assertEquals("Trade confirmed by CONF-1", stored.getResolutionNotes());
        assertEquals("CANCELLED", wiring.workItemRepository.findById(cancelled.getWorkItemId()).orElseThrow().getStatus());
        assertEquals("OPEN", wiring.workItemRepository.findById(otherTrade.getWorkItemId()).orElseThrow().getStatus());

        List<AuditDocument> audits = wiring.couchDb.find("audit-events",
                new FindQueryRequest(Map.of("tradeId", "TRD-1", "stage", "CONFIRMATION_RECORDED"), 10, 0), AuditDocument.class);
        assertEquals(1, audits.size());
        assertEquals("CONFIRMED", audits.get(0).getStatus().name());
        assertEquals("CORR-1", audits.get(0).getCorrelationId());
    }

    @Test
    @DisplayName("the trade id falls back to the Kafka key; unparseable records are ignored")
    void fallbacks() {
        WorkItemDocument open = wiring.workItemService.createManual("TRD-9", "DATA_FIX", "fix", null, null, null, null);

        wiring.confirmationService.onConfirmation("TRD-9", "{\"confirmation\":{\"confirmationId\":\"CONF-9\"}}");
        assertTrue(wiring.confirmationService.onConfirmation("TRD-9", "{broken").isEmpty());
        assertTrue(wiring.confirmationService.onConfirmation(null, "{}").isEmpty());

        assertEquals("DONE", wiring.workItemRepository.findById(open.getWorkItemId()).orElseThrow().getStatus());
    }
}
