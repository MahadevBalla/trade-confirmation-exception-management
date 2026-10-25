package com.tcem.exception.recommendation;

import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.model.exception.ExceptionRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RecommendationEngine}.
 * Verifies that each ExceptionCategory maps to the correct
 * ownerQueue, replayMayResolve flag, resolutionDifficulty, and operationalImpact.
 */
class RecommendationEngineTest {

    private RecommendationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new RecommendationEngine();
    }

    // -----------------------------------------------------------------------
    // Happy-path: one test per ExceptionCategory
    // -----------------------------------------------------------------------

    @Test
    void enrich_missingEconomicData_setsDataRemediationQueue() {
        ExceptionRecord record = recordWithCategory(ExceptionCategory.MISSING_ECONOMIC_DATA);
        engine.enrich(record);

        assertThat(record.getOwnerQueue()).isEqualTo("DATA_REMEDIATION");
        assertThat(record.isReplayMayResolve()).isTrue();
        assertThat(record.getResolutionDifficulty()).isEqualTo("LOW");
        assertThat(record.getOperationalImpact()).isEqualTo("HIGH");
        assertThat(record.getRecommendedAction())
                .contains("Locate missing field");
    }

    @Test
    void enrich_missingStaticReferenceData_setsReferenceDataQueue() {
        ExceptionRecord record = recordWithCategory(ExceptionCategory.MISSING_STATIC_REFERENCE_DATA);
        engine.enrich(record);

        assertThat(record.getOwnerQueue()).isEqualTo("REFERENCE_DATA");
        assertThat(record.isReplayMayResolve()).isTrue();
        assertThat(record.getResolutionDifficulty()).isEqualTo("MEDIUM");
        assertThat(record.getOperationalImpact()).isEqualTo("MEDIUM");
        assertThat(record.getRecommendedAction())
                .contains("Update reference data catalogue");
    }

    @Test
    void enrich_templateIssue_setsTemplateMaintenanceQueue() {
        ExceptionRecord record = recordWithCategory(ExceptionCategory.TEMPLATE_ISSUE);
        engine.enrich(record);

        assertThat(record.getOwnerQueue()).isEqualTo("TEMPLATE_MAINTENANCE");
        assertThat(record.isReplayMayResolve()).isFalse();
        assertThat(record.getResolutionDifficulty()).isEqualTo("MEDIUM");
        assertThat(record.getOperationalImpact()).isEqualTo("MEDIUM");
        assertThat(record.getRecommendedAction())
                .contains("Review and update confirmation template");
    }

    @Test
    void enrich_businessRuleFailure_setsOperationsReviewQueueAndHighDifficulty() {
        ExceptionRecord record = recordWithCategory(ExceptionCategory.BUSINESS_RULE_FAILURE);
        engine.enrich(record);

        assertThat(record.getOwnerQueue()).isEqualTo("OPERATIONS_REVIEW");
        assertThat(record.isReplayMayResolve()).isFalse();
        assertThat(record.getResolutionDifficulty()).isEqualTo("HIGH");
        assertThat(record.getOperationalImpact()).isEqualTo("HIGH");
        assertThat(record.getRecommendedAction())
                .contains("Manual review required");
    }

    @Test
    void enrich_technicalProcessingFailure_setsTechnologySupportAndReplay() {
        ExceptionRecord record = recordWithCategory(ExceptionCategory.TECHNICAL_PROCESSING_FAILURE);
        engine.enrich(record);

        assertThat(record.getOwnerQueue()).isEqualTo("TECHNOLOGY_SUPPORT");
        assertThat(record.isReplayMayResolve()).isTrue();
        assertThat(record.getResolutionDifficulty()).isEqualTo("LOW");
        assertThat(record.getOperationalImpact()).isEqualTo("LOW");
        assertThat(record.getRecommendedAction())
                .contains("Investigate infrastructure");
    }

    @Test
    void enrich_counterpartyMatchingDispute_setCriticalImpactAndNoReplay() {
        ExceptionRecord record = recordWithCategory(ExceptionCategory.COUNTERPARTY_MATCHING_DISPUTE);
        engine.enrich(record);

        assertThat(record.getOwnerQueue()).isEqualTo("OPERATIONS_REVIEW");
        assertThat(record.isReplayMayResolve()).isFalse();
        assertThat(record.getResolutionDifficulty()).isEqualTo("HIGH");
        assertThat(record.getOperationalImpact()).isEqualTo("CRITICAL");
        assertThat(record.getRecommendedAction())
                .contains("Escalate to operations");
    }

    @Test
    void enrich_legalDocumentationGap_setsSpecialistReviewQueue() {
        ExceptionRecord record = recordWithCategory(ExceptionCategory.LEGAL_DOCUMENTATION_GAP);
        engine.enrich(record);

        assertThat(record.getOwnerQueue()).isEqualTo("SPECIALIST_REVIEW");
        assertThat(record.isReplayMayResolve()).isFalse();
        assertThat(record.getResolutionDifficulty()).isEqualTo("HIGH");
        assertThat(record.getOperationalImpact()).isEqualTo("HIGH");
        assertThat(record.getRecommendedAction())
                .contains("Escalate to legal");
    }

    @Test
    void enrich_falsePositive_setsRulesOwnerQueue() {
        ExceptionRecord record = recordWithCategory(ExceptionCategory.FALSE_POSITIVE_CONFIRMABLE_EVENT);
        engine.enrich(record);

        assertThat(record.getOwnerQueue()).isEqualTo("RULES_OWNER");
        assertThat(record.isReplayMayResolve()).isFalse();
        assertThat(record.getResolutionDifficulty()).isEqualTo("LOW");
        assertThat(record.getOperationalImpact()).isEqualTo("LOW");
        assertThat(record.getRecommendedAction())
                .contains("classification rule review");
    }

    // -----------------------------------------------------------------------
    // Edge cases
    // -----------------------------------------------------------------------

    @Test
    void enrich_nullCategory_appliesDefaultFallback() {
        ExceptionRecord record = recordWithCategory(null);
        engine.enrich(record);

        assertThat(record.getOwnerQueue()).isEqualTo("OPERATIONS_REVIEW");
        assertThat(record.isReplayMayResolve()).isFalse();
        assertThat(record.getResolutionDifficulty()).isEqualTo("MEDIUM");
        assertThat(record.getOperationalImpact()).isEqualTo("MEDIUM");
    }

    @Test
    void enrich_nullRecord_doesNotThrow() {
        // Should log a warning and return silently — no NPE
        engine.enrich(null);
        // If we reach here the test passes
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private ExceptionRecord recordWithCategory(ExceptionCategory category) {
        ExceptionRecord record = new ExceptionRecord();
        record.setExceptionId("EXC-TEST-001");
        record.setTradeId("TRD-TEST-001");
        record.setCategory(category);
        return record;
    }
}

