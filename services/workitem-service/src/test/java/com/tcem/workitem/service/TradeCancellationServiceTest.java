package com.tcem.workitem.service;

import com.tcem.workitem.TestWiring;
import com.tcem.workitem.model.document.WorkItemDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class TradeCancellationServiceTest {

    private static final String CANCELLED =
            "{\"messageHeader\":{\"correlationId\":\"CORR-1\",\"eventType\":\"CANCEL_TRADE\"},\"tradeId\":\"TRD-1\",\"cancelled\":true}";

    @Mock
    private KafkaOperations<String, String> kafka;

    private TestWiring wiring;

    @BeforeEach
    void setUp() {
        wiring = new TestWiring(kafka);
    }

    private WorkItemDocument open(String tradeId) {
        return wiring.workItemService.createManual(tradeId, "DATA_FIX", "fix", null, null, null, null);
    }

    private String status(WorkItemDocument item) {
        return wiring.workItemRepository.findById(item.getWorkItemId()).orElseThrow().getStatus();
    }

    @Test
    @DisplayName("a cancelled outcome cancels the trade's open items only, and leaves finished items alone")
    void cancelsOpenItemsOfTheTrade() {
        WorkItemDocument item = open("TRD-1");
        WorkItemDocument finished = open("TRD-1");
        wiring.workItemService.update(finished.getWorkItemId(), "DONE", null, null, null, null);
        WorkItemDocument otherTrade = open("TRD-2");

        assertEquals(1, wiring.tradeCancellationService.onStpValidated("TRD-1", CANCELLED).size());

        assertEquals("CANCELLED", status(item));
        assertNotNull(wiring.workItemRepository.findById(item.getWorkItemId()).orElseThrow().getResolvedAt());
        assertEquals("DONE", status(finished));
        assertEquals("OPEN", status(otherTrade));
    }

    @Test
    @DisplayName("ordinary STP outcomes and unparseable records change nothing")
    void ignoresOtherRecords() {
        WorkItemDocument item = open("TRD-1");

        assertTrue(wiring.tradeCancellationService.onStpValidated("TRD-1", "{\"tradeId\":\"TRD-1\",\"stpPassed\":false,\"cancelled\":false}").isEmpty());
        assertTrue(wiring.tradeCancellationService.onStpValidated("TRD-1", "{\"tradeId\":\"TRD-1\",\"stpPassed\":true}").isEmpty());
        assertTrue(wiring.tradeCancellationService.onStpValidated("TRD-1", "{broken").isEmpty());

        assertEquals("OPEN", status(item));
    }

    @Test
    @DisplayName("the trade id falls back to the Kafka key, and a cancel with no open items is harmless")
    void fallbackAndNoItems() {
        WorkItemDocument item = open("TRD-9");

        assertEquals(1, wiring.tradeCancellationService.onStpValidated("TRD-9", "{\"cancelled\":true}").size());
        assertEquals("CANCELLED", status(item));
        assertTrue(wiring.tradeCancellationService.onStpValidated("TRD-9", "{\"cancelled\":true}").isEmpty());
    }
}
