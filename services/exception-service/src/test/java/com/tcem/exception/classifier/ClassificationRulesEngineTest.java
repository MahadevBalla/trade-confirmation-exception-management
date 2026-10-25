package com.tcem.exception.classifier;

import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.constants.ExceptionSubTheme;
import com.tcem.common.model.exception.ExceptionRecord;
import com.tcem.exception.classifier.rules.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ClassificationRulesEngine — verifying rule dispatch, confidence scoring,
 * fallback behaviour, and exception safety across all 6 MVP categories.
 */
class ClassificationRulesEngineTest {

    private ClassificationRulesEngine engine;

    @BeforeEach
    void setUp() {
        List<ClassificationRule> rules = List.of(
                new MissingEconomicDataRule(),
                new MissingReferenceDataRule(),
                new TemplateIssueRule(),
                new BusinessRuleFailureRule(),
                new TechnicalFailureRule(),
                new CounterpartyDisputeRule()
        );
        engine = new ClassificationRulesEngine(rules);
    }

    // -----------------------------------------------------------------------
    // Happy path — full confidence (1.0) for each category
    // -----------------------------------------------------------------------

    @Test
    void missingEconomicData_fullConfidence_whenSubThemeAndFieldsMatch() {
        ExceptionRecord record = ExceptionRecord.builder()
                .tradeId("TRD-10001")
                .category(ExceptionCategory.MISSING_ECONOMIC_DATA)
                .subTheme(ExceptionSubTheme.MISSING_STRIKE)
                .affectedFields("fxOptionEconomics.strike")
                .build();

        ClassificationResult result = engine.classify(record);

        assertThat(result.getConfirmedCategory()).isEqualTo(ExceptionCategory.MISSING_ECONOMIC_DATA);
        assertThat(result.getConfirmedSubTheme()).isEqualTo(ExceptionSubTheme.MISSING_STRIKE);
        assertThat(result.getClassificationConfidence()).isEqualTo(1.0);
        assertThat(result.requiresManualReview()).isFalse();
    }

    @Test
    void missingReferenceData_fullConfidence_forUnknownCounterparty() {
        ExceptionRecord record = ExceptionRecord.builder()
                .tradeId("TRD-10001")
                .category(ExceptionCategory.MISSING_STATIC_REFERENCE_DATA)
                .subTheme(ExceptionSubTheme.UNKNOWN_COUNTERPARTY)
                .affectedFields("counterparty.id")
                .build();

        ClassificationResult result = engine.classify(record);

        assertThat(result.getConfirmedCategory()).isEqualTo(ExceptionCategory.MISSING_STATIC_REFERENCE_DATA);
        assertThat(result.getConfirmedSubTheme()).isEqualTo(ExceptionSubTheme.UNKNOWN_COUNTERPARTY);
        assertThat(result.getClassificationConfidence()).isEqualTo(1.0);
    }

    @Test
    void templateIssue_fullConfidence_forNoMatchingTemplate() {
        ExceptionRecord record = ExceptionRecord.builder()
                .tradeId("TRD-10001")
                .category(ExceptionCategory.TEMPLATE_ISSUE)
                .subTheme(ExceptionSubTheme.NO_MATCHING_TEMPLATE)
                .build();

        ClassificationResult result = engine.classify(record);

        assertThat(result.getConfirmedCategory()).isEqualTo(ExceptionCategory.TEMPLATE_ISSUE);
        assertThat(result.getConfirmedSubTheme()).isEqualTo(ExceptionSubTheme.NO_MATCHING_TEMPLATE);
        assertThat(result.getClassificationConfidence()).isEqualTo(1.0);
    }

    @Test
    void businessRuleFailure_fullConfidence_forCounterpartyNotSTPEligible() {
        ExceptionRecord record = ExceptionRecord.builder()
                .tradeId("TRD-10001")
                .category(ExceptionCategory.BUSINESS_RULE_FAILURE)
                .subTheme(ExceptionSubTheme.COUNTERPARTY_NOT_STP_ELIGIBLE)
                .build();

        ClassificationResult result = engine.classify(record);

        assertThat(result.getConfirmedCategory()).isEqualTo(ExceptionCategory.BUSINESS_RULE_FAILURE);
        assertThat(result.getConfirmedSubTheme()).isEqualTo(ExceptionSubTheme.COUNTERPARTY_NOT_STP_ELIGIBLE);
        assertThat(result.getClassificationConfidence()).isEqualTo(1.0);
    }

    @Test
    void technicalFailure_fullConfidence_forParseFailure() {
        ExceptionRecord record = ExceptionRecord.builder()
                .tradeId("TRD-10001")
                .category(ExceptionCategory.TECHNICAL_PROCESSING_FAILURE)
                .subTheme(ExceptionSubTheme.PARSE_FAILURE)
                .build();

        ClassificationResult result = engine.classify(record);

        assertThat(result.getConfirmedCategory()).isEqualTo(ExceptionCategory.TECHNICAL_PROCESSING_FAILURE);
        assertThat(result.getConfirmedSubTheme()).isEqualTo(ExceptionSubTheme.PARSE_FAILURE);
        assertThat(result.getClassificationConfidence()).isEqualTo(1.0);
    }

    @Test
    void counterpartyDispute_fullConfidence_forEconomicTermMismatch() {
        ExceptionRecord record = ExceptionRecord.builder()
                .tradeId("TRD-10001")
                .category(ExceptionCategory.COUNTERPARTY_MATCHING_DISPUTE)
                .subTheme(ExceptionSubTheme.ECONOMIC_TERM_MISMATCH)
                .build();

        ClassificationResult result = engine.classify(record);

        assertThat(result.getConfirmedCategory()).isEqualTo(ExceptionCategory.COUNTERPARTY_MATCHING_DISPUTE);
        assertThat(result.getConfirmedSubTheme()).isEqualTo(ExceptionSubTheme.ECONOMIC_TERM_MISMATCH);
        assertThat(result.getClassificationConfidence()).isEqualTo(1.0);
    }

    // -----------------------------------------------------------------------
    // Reduced confidence cases
    // -----------------------------------------------------------------------

    @Test
    void missingEconomicData_reducedConfidence_whenAffectedFieldsBlank() {
        ExceptionRecord record = ExceptionRecord.builder()
                .tradeId("TRD-10001")
                .category(ExceptionCategory.MISSING_ECONOMIC_DATA)
                .subTheme(ExceptionSubTheme.MISSING_NOTIONAL)
                .affectedFields("")   // blank — deducts confidence
                .build();

        ClassificationResult result = engine.classify(record);

        assertThat(result.getClassificationConfidence()).isEqualTo(0.8);
        assertThat(result.getDiagnosticNote()).contains("affectedFields is blank");
    }

    @Test
    void unknownSubTheme_yieldsMidConfidence() {
        ExceptionRecord record = ExceptionRecord.builder()
                .tradeId("TRD-10001")
                .category(ExceptionCategory.MISSING_ECONOMIC_DATA)
                .subTheme(ExceptionSubTheme.UNKNOWN)
                .build();

        ClassificationResult result = engine.classify(record);

        assertThat(result.getClassificationConfidence()).isEqualTo(0.5);
        assertThat(result.requiresManualReview()).isTrue();
    }

    @Test
    void nullSubTheme_yieldsMidConfidence() {
        ExceptionRecord record = ExceptionRecord.builder()
                .tradeId("TRD-10001")
                .category(ExceptionCategory.TEMPLATE_ISSUE)
                .subTheme(null)
                .build();

        ClassificationResult result = engine.classify(record);

        assertThat(result.getClassificationConfidence()).isLessThanOrEqualTo(0.8);
    }

    // -----------------------------------------------------------------------
    // Null / edge-case guards
    // -----------------------------------------------------------------------

    @Test
    void nullRecord_returnsLowConfidenceResult() {
        ClassificationResult result = engine.classify(null);

        assertThat(result.getClassificationConfidence()).isEqualTo(0.3);
        assertThat(result.requiresManualReview()).isTrue();
    }

    @Test
    void nullCategory_returnsLowConfidenceResult() {
        ExceptionRecord record = ExceptionRecord.builder()
                .tradeId("TRD-10001")
                .category(null)
                .build();

        ClassificationResult result = engine.classify(record);

        assertThat(result.getClassificationConfidence()).isEqualTo(0.3);
        assertThat(result.requiresManualReview()).isTrue();
    }

    @Test
    void unregisteredCategory_returnsMidConfidenceWithNote() {
        // LEGAL_DOCUMENTATION_GAP has no rule registered in this engine setup
        ExceptionRecord record = ExceptionRecord.builder()
                .tradeId("TRD-10001")
                .category(ExceptionCategory.LEGAL_DOCUMENTATION_GAP)
                .subTheme(ExceptionSubTheme.BESPOKE_CLAUSE_REQUIRED)
                .build();

        ClassificationResult result = engine.classify(record);

        assertThat(result.getConfirmedCategory()).isEqualTo(ExceptionCategory.LEGAL_DOCUMENTATION_GAP);
        assertThat(result.getClassificationConfidence()).isEqualTo(0.5);
        assertThat(result.getDiagnosticNote()).contains("No rule registered");
    }

    // -----------------------------------------------------------------------
    // ClassificationResult helpers
    // -----------------------------------------------------------------------

    @Test
    void requiresManualReview_trueWhenConfidenceBelow06() {
        ClassificationResult result = new ClassificationResult().withConfidence(0.59);
        assertThat(result.requiresManualReview()).isTrue();
    }

    @Test
    void requiresManualReview_falseWhenConfidenceAtThreshold() {
        ClassificationResult result = new ClassificationResult().withConfidence(0.6);
        assertThat(result.requiresManualReview()).isFalse();
    }

    @Test
    void withConfidence_clampsBelowZero() {
        ClassificationResult result = new ClassificationResult().withConfidence(-0.5);
        assertThat(result.getClassificationConfidence()).isEqualTo(0.0);
    }

    @Test
    void withConfidence_clampsAboveOne() {
        ClassificationResult result = new ClassificationResult().withConfidence(1.5);
        assertThat(result.getClassificationConfidence()).isEqualTo(1.0);
    }

    @Test
    void technicalFailure_duplicateMessage_fullConfidence() {
        ExceptionRecord record = ExceptionRecord.builder()
                .tradeId("TRD-10001")
                .category(ExceptionCategory.TECHNICAL_PROCESSING_FAILURE)
                .subTheme(ExceptionSubTheme.DUPLICATE_MESSAGE)
                .build();

        ClassificationResult result = engine.classify(record);

        assertThat(result.getClassificationConfidence()).isEqualTo(1.0);
        assertThat(result.getConfirmedSubTheme()).isEqualTo(ExceptionSubTheme.DUPLICATE_MESSAGE);
    }
}

