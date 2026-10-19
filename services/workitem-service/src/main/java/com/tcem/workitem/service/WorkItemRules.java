package com.tcem.workitem.service;

import com.tcem.common.constants.ExceptionCategory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Explainable rules for turning classified exceptions into work items (FR10).
 * Owner-queue fallbacks mirror frontend/src/services/recommendationEngine.ts and spec section 12.
 */
public final class WorkItemRules {

    private WorkItemRules() {}

    // Sorted so validation messages list allowed values in a stable order.
    public static final Set<String> TYPES = sorted("DATA_FIX", "TEMPLATE_FIX", "RULE_UPDATE", "MANUAL_REVIEW", "ENHANCEMENT");
    public static final Set<String> STATUSES = sorted("OPEN", "IN_PROGRESS", "DONE", "CANCELLED");
    public static final Set<String> TERMINAL_STATUSES = Set.of("DONE", "CANCELLED");
    public static final List<String> PRIORITIES = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    public static final Set<String> OWNER_QUEUES = sorted("DATA_REMEDIATION", "OPERATIONS_REVIEW",
            "TEMPLATE_MAINTENANCE", "TECHNOLOGY_SUPPORT", "REFERENCE_DATA", "SPECIALIST_REVIEW", "RULES_OWNER");

    private static Set<String> sorted(String... values) {
        return Collections.unmodifiableSortedSet(new TreeSet<>(List.of(values)));
    }

    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            "OPEN", Set.of("IN_PROGRESS", "DONE", "CANCELLED"),
            "IN_PROGRESS", Set.of("OPEN", "DONE", "CANCELLED"));

    public static String typeFor(ExceptionCategory category) {
        if (category == null) {
            return "MANUAL_REVIEW";
        }
        return switch (category) {
            case MISSING_ECONOMIC_DATA, MISSING_STATIC_REFERENCE_DATA -> "DATA_FIX";
            case TEMPLATE_ISSUE -> "TEMPLATE_FIX";
            case BUSINESS_RULE_FAILURE, FALSE_POSITIVE_CONFIRMABLE_EVENT -> "RULE_UPDATE";
            case TECHNICAL_PROCESSING_FAILURE -> "ENHANCEMENT";
            case COUNTERPARTY_MATCHING_DISPUTE, LEGAL_DOCUMENTATION_GAP -> "MANUAL_REVIEW";
        };
    }

    public static String defaultQueueFor(ExceptionCategory category) {
        if (category == null) {
            return "OPERATIONS_REVIEW";
        }
        return switch (category) {
            case MISSING_ECONOMIC_DATA -> "DATA_REMEDIATION";
            case MISSING_STATIC_REFERENCE_DATA -> "REFERENCE_DATA";
            case TEMPLATE_ISSUE -> "TEMPLATE_MAINTENANCE";
            case BUSINESS_RULE_FAILURE, COUNTERPARTY_MATCHING_DISPUTE -> "OPERATIONS_REVIEW";
            case TECHNICAL_PROCESSING_FAILURE -> "TECHNOLOGY_SUPPORT";
            case LEGAL_DOCUMENTATION_GAP -> "SPECIALIST_REVIEW";
            case FALSE_POSITIVE_CONFIRMABLE_EVENT -> "RULES_OWNER";
        };
    }

    /** Uses the recommendation's queue when it is a known queue, otherwise the category default. */
    public static String queueFor(String recommendedQueue, ExceptionCategory category) {
        return recommendedQueue != null && OWNER_QUEUES.contains(recommendedQueue)
                ? recommendedQueue : defaultQueueFor(category);
    }

    /** Priority = operational impact when it is a valid priority, else derived from difficulty, else MEDIUM. */
    public static String priorityFor(String operationalImpact, String resolutionDifficulty) {
        if (operationalImpact != null && PRIORITIES.contains(operationalImpact)) {
            return operationalImpact;
        }
        if ("HIGH".equals(resolutionDifficulty)) {
            return "HIGH";
        }
        if ("LOW".equals(resolutionDifficulty)) {
            return "LOW";
        }
        return "MEDIUM";
    }

    public static String higherPriority(String a, String b) {
        return rank(a) >= rank(b) ? a : b;
    }

    public static int rank(String priority) {
        return priority == null ? -1 : PRIORITIES.indexOf(priority);
    }

    public static boolean canTransition(String from, String to) {
        return ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }
}
