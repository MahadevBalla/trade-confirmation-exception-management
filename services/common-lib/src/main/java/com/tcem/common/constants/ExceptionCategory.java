package com.tcem.common.constants;

/**
 * Exception category taxonomy.
 * <p>
 * At least 6 MVP categories are required (spec §12). Each category drives the
 * recommendation logic in the exception-service. Sub-themes are defined in
 * {@link ExceptionSubTheme}.
 * </p>
 */
public enum ExceptionCategory {

    // MVP categories (spec §12 table)

    /** Strike, notional, premium, rate, or coupon missing. */
    MISSING_ECONOMIC_DATA,

    /** Counterparty, instrument, template, or jurisdiction missing from reference data. */
    MISSING_STATIC_REFERENCE_DATA,

    /** No valid confirmation template found, or product/template mismatch. */
    TEMPLATE_ISSUE,

    /** Client/product not eligible for STP; manual review required. */
    BUSINESS_RULE_FAILURE,

    /** Enrichment timeout, parse failure, duplicate event, or infrastructure error. */
    TECHNICAL_PROCESSING_FAILURE,

    /** Economic or non-economic discrepancy between parties (matching failure). */
    COUNTERPARTY_MATCHING_DISPUTE,

    // Stretch categories

    /** Missing legal clause or bespoke wording requiring specialist review. */
    LEGAL_DOCUMENTATION_GAP,

    /**
     * An event was incorrectly flagged as confirmable (false positive).
     * Drives classification-rule tuning.
     */
    FALSE_POSITIVE_CONFIRMABLE_EVENT
}
