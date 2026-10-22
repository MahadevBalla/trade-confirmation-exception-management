package com.tcem.validation.rules;

import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.constants.ExceptionSubTheme;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link StpValidationResult}.
 * <p>
 * Validates the accumulator contract: all failures are collected without
 * short-circuiting, pass/fail decision is correct, and the returned list is
 * immutable.
 * </p>
 */
@DisplayName("StpValidationResult")
class StpValidationResultTest {

    private StpValidationResult result;

    @BeforeEach
    void setUp() {
        result = new StpValidationResult();
    }

    // -----------------------------------------------------------------------
    // isStpPassed
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("passes when no failures have been recorded")
    void isStpPassed_noFailures_returnsTrue() {
        assertThat(result.isStpPassed()).isTrue();
    }

    @Test
    @DisplayName("fails after a single failure is added")
    void isStpPassed_oneFailure_returnsFalse() {
        result.addFailure(
                ExceptionCategory.MISSING_ECONOMIC_DATA,
                ExceptionSubTheme.MISSING_NOTIONAL,
                "notionalAmount is null",
                "fxOptionEconomics.notionalAmount");

        assertThat(result.isStpPassed()).isFalse();
    }

    // -----------------------------------------------------------------------
    // Failure accumulation — non-short-circuiting
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("accumulates multiple independent failures in order")
    void addFailure_multipleViolations_allRecorded() {
        result.addFailure(ExceptionCategory.MISSING_ECONOMIC_DATA,
                ExceptionSubTheme.MISSING_NOTIONAL,
                "notionalAmount missing", "fxOptionEconomics.notionalAmount");
        result.addFailure(ExceptionCategory.BUSINESS_RULE_FAILURE,
                ExceptionSubTheme.COUNTERPARTY_NOT_STP_ELIGIBLE,
                "Counterparty ineligible", "enrichmentMetadata.counterpartySTPEligible");
        result.addFailure(ExceptionCategory.TEMPLATE_ISSUE,
                ExceptionSubTheme.NO_MATCHING_TEMPLATE,
                "No template resolved", "enrichmentMetadata.resolvedTemplateId");

        List<StpValidationResult.Failure> failures = result.getFailures();
        assertThat(failures).hasSize(3);
        assertThat(failures.get(0).getCategory()).isEqualTo(ExceptionCategory.MISSING_ECONOMIC_DATA);
        assertThat(failures.get(1).getCategory()).isEqualTo(ExceptionCategory.BUSINESS_RULE_FAILURE);
        assertThat(failures.get(2).getCategory()).isEqualTo(ExceptionCategory.TEMPLATE_ISSUE);
    }

    // -----------------------------------------------------------------------
    // Failure fields
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Failure carries category, subTheme, reasonCode, affectedFields")
    void failure_fieldsPopulatedCorrectly() {
        result.addFailure(
                ExceptionCategory.TEMPLATE_ISSUE,
                ExceptionSubTheme.TEMPLATE_VERSION_MISMATCH,
                "Template version missing",
                "enrichmentMetadata.resolvedTemplateVersion");

        StpValidationResult.Failure f = result.getFailures().get(0);
        assertThat(f.getCategory()).isEqualTo(ExceptionCategory.TEMPLATE_ISSUE);
        assertThat(f.getSubTheme()).isEqualTo(ExceptionSubTheme.TEMPLATE_VERSION_MISMATCH);
        assertThat(f.getReasonCode()).isEqualTo("Template version missing");
        assertThat(f.getAffectedFields()).isEqualTo("enrichmentMetadata.resolvedTemplateVersion");
    }

    @Test
    @DisplayName("Failure.toString() includes category and subTheme")
    void failure_toString_containsCategoryAndSubTheme() {
        result.addFailure(ExceptionCategory.BUSINESS_RULE_FAILURE,
                ExceptionSubTheme.PRODUCT_NOT_STP_ELIGIBLE,
                "product not eligible", "enrichmentMetadata.productSTPEligible");

        String str = result.getFailures().get(0).toString();
        assertThat(str).contains("BUSINESS_RULE_FAILURE");
        assertThat(str).contains("PRODUCT_NOT_STP_ELIGIBLE");
    }

    // -----------------------------------------------------------------------
    // summarize()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("summarize() returns 'STP_PASSED' when there are no failures")
    void summarize_noFailures_returnsSTPPASSED() {
        assertThat(result.summarize()).isEqualTo("STP_PASSED");
    }

    @Test
    @DisplayName("summarize() starts with 'STP_FAILED' when there are failures")
    void summarize_withFailures_startsSTPFAILED() {
        result.addFailure(ExceptionCategory.MISSING_ECONOMIC_DATA,
                ExceptionSubTheme.MISSING_STRIKE, "strike missing", "fxOptionEconomics.strike");

        assertThat(result.summarize()).startsWith("STP_FAILED");
        assertThat(result.summarize()).contains("MISSING_ECONOMIC_DATA");
    }

    @Test
    @DisplayName("summarize() includes all failure codes when multiple failures exist")
    void summarize_multipleFailures_containsAll() {
        result.addFailure(ExceptionCategory.MISSING_ECONOMIC_DATA,
                ExceptionSubTheme.MISSING_NOTIONAL, "notional missing", "notionalAmount");
        result.addFailure(ExceptionCategory.TEMPLATE_ISSUE,
                ExceptionSubTheme.NO_MATCHING_TEMPLATE, "no template", "resolvedTemplateId");

        String summary = result.summarize();
        assertThat(summary).contains("MISSING_ECONOMIC_DATA");
        assertThat(summary).contains("TEMPLATE_ISSUE");
    }

    // -----------------------------------------------------------------------
    // Immutability of returned list
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("getFailures() returns an immutable view — add throws UnsupportedOperationException")
    void getFailures_immutableList_throwsOnAdd() {
        result.addFailure(ExceptionCategory.MISSING_ECONOMIC_DATA,
                ExceptionSubTheme.MISSING_PREMIUM, "premium missing", "premiumAmount");

        List<StpValidationResult.Failure> immutable = result.getFailures();
        assertThatThrownBy(() -> immutable.add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

