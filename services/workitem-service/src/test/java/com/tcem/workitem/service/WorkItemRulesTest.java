package com.tcem.workitem.service;

import com.tcem.common.constants.ExceptionCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class WorkItemRulesTest {

    @ParameterizedTest(name = "{0} -> {1} in {2}")
    @CsvSource({
            "MISSING_ECONOMIC_DATA, DATA_FIX, DATA_REMEDIATION",
            "MISSING_STATIC_REFERENCE_DATA, DATA_FIX, REFERENCE_DATA",
            "TEMPLATE_ISSUE, TEMPLATE_FIX, TEMPLATE_MAINTENANCE",
            "BUSINESS_RULE_FAILURE, RULE_UPDATE, OPERATIONS_REVIEW",
            "TECHNICAL_PROCESSING_FAILURE, ENHANCEMENT, TECHNOLOGY_SUPPORT",
            "COUNTERPARTY_MATCHING_DISPUTE, MANUAL_REVIEW, OPERATIONS_REVIEW",
            "LEGAL_DOCUMENTATION_GAP, MANUAL_REVIEW, SPECIALIST_REVIEW",
            "FALSE_POSITIVE_CONFIRMABLE_EVENT, RULE_UPDATE, RULES_OWNER"
    })
    @DisplayName("every exception category maps to a work-item type and default owner queue")
    void categoryMapping(ExceptionCategory category, String type, String queue) {
        assertEquals(type, WorkItemRules.typeFor(category));
        assertEquals(queue, WorkItemRules.defaultQueueFor(category));
    }

    @Test
    @DisplayName("unclassified exceptions go to manual review")
    void nullCategory() {
        assertEquals("MANUAL_REVIEW", WorkItemRules.typeFor(null));
        assertEquals("OPERATIONS_REVIEW", WorkItemRules.defaultQueueFor(null));
    }

    @Test
    @DisplayName("the recommended queue wins only when it is a known queue")
    void queueFor() {
        assertEquals("SPECIALIST_REVIEW", WorkItemRules.queueFor("SPECIALIST_REVIEW", ExceptionCategory.TEMPLATE_ISSUE));
        assertEquals("TEMPLATE_MAINTENANCE", WorkItemRules.queueFor("NOT_A_QUEUE", ExceptionCategory.TEMPLATE_ISSUE));
        assertEquals("TEMPLATE_MAINTENANCE", WorkItemRules.queueFor(null, ExceptionCategory.TEMPLATE_ISSUE));
    }

    @Test
    @DisplayName("priority comes from impact, then difficulty, then defaults to MEDIUM")
    void priority() {
        assertEquals("CRITICAL", WorkItemRules.priorityFor("CRITICAL", "LOW"));
        assertEquals("HIGH", WorkItemRules.priorityFor(null, "HIGH"));
        assertEquals("LOW", WorkItemRules.priorityFor("bogus", "LOW"));
        assertEquals("MEDIUM", WorkItemRules.priorityFor(null, null));
        assertEquals("HIGH", WorkItemRules.higherPriority("MEDIUM", "HIGH"));
        assertEquals("CRITICAL", WorkItemRules.higherPriority("CRITICAL", "HIGH"));
    }

    @Test
    @DisplayName("status transitions: OPEN and IN_PROGRESS move freely, DONE and CANCELLED are terminal")
    void transitions() {
        assertTrue(WorkItemRules.canTransition("OPEN", "IN_PROGRESS"));
        assertTrue(WorkItemRules.canTransition("IN_PROGRESS", "OPEN"));
        assertTrue(WorkItemRules.canTransition("IN_PROGRESS", "DONE"));
        assertTrue(WorkItemRules.canTransition("OPEN", "CANCELLED"));
        assertFalse(WorkItemRules.canTransition("DONE", "OPEN"));
        assertFalse(WorkItemRules.canTransition("CANCELLED", "IN_PROGRESS"));
    }
}
