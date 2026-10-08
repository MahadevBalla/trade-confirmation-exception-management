package com.tcem.common.constants;

/**
 * Sub-themes for exception classification (child of {@link ExceptionCategory}).
 * <p>
 * The exception-service maps each raw validation/enrichment failure to one
 * {@code ExceptionCategory} and one {@code ExceptionSubTheme}. Adding a new
 * sub-theme here does not break existing deserialization (Jackson maps unknown
 * enum values to {@code UNKNOWN} when {@code @JsonEnumDefaultValue} is used).
 * </p>
 */
public enum ExceptionSubTheme {

    // -----------------------------------------------------------------------
    // MISSING_ECONOMIC_DATA
    // -----------------------------------------------------------------------
    MISSING_STRIKE,
    MISSING_NOTIONAL,
    MISSING_PREMIUM,
    MISSING_PREMIUM_CURRENCY,
    MISSING_UNDERLIER,
    MISSING_EXPIRY_DATE,
    MISSING_OPTION_TYPE,

    // -----------------------------------------------------------------------
    // MISSING_STATIC_REFERENCE_DATA
    // -----------------------------------------------------------------------
    UNKNOWN_COUNTERPARTY,
    UNKNOWN_INSTRUMENT,
    MISSING_JURISDICTION,
    MISSING_TEMPLATE_CATALOGUE_ENTRY,

    // -----------------------------------------------------------------------
    // TEMPLATE_ISSUE
    // -----------------------------------------------------------------------
    NO_MATCHING_TEMPLATE,
    PRODUCT_TEMPLATE_MISMATCH,
    TEMPLATE_VERSION_MISMATCH,

    // -----------------------------------------------------------------------
    // BUSINESS_RULE_FAILURE
    // -----------------------------------------------------------------------
    COUNTERPARTY_NOT_STP_ELIGIBLE,
    PRODUCT_NOT_STP_ELIGIBLE,
    TRADE_DATE_MISMATCH,
    VALUE_DATE_HOLIDAY,

    // -----------------------------------------------------------------------
    // TECHNICAL_PROCESSING_FAILURE
    // -----------------------------------------------------------------------
    DUPLICATE_MESSAGE,
    PARSE_FAILURE,
    ENRICHMENT_TIMEOUT,
    SCHEMA_VERSION_UNSUPPORTED,
    DLQ_REPROCESSING_FAILURE,

    // -----------------------------------------------------------------------
    // COUNTERPARTY_MATCHING_DISPUTE
    // -----------------------------------------------------------------------
    ECONOMIC_TERM_MISMATCH,
    NON_ECONOMIC_TERM_MISMATCH,
    PARTY_ROLE_MISMATCH,

    // -----------------------------------------------------------------------
    // LEGAL_DOCUMENTATION_GAP (stretch)
    // -----------------------------------------------------------------------
    MISSING_NETTING_AGREEMENT,
    BESPOKE_CLAUSE_REQUIRED,

    // -----------------------------------------------------------------------
    // FALSE_POSITIVE_CONFIRMABLE_EVENT (stretch)
    // -----------------------------------------------------------------------
    NON_CONFIRMABLE_ATTRIBUTE_CHANGED,

    /** Fallback when deserialization encounters an unrecognised sub-theme value. */
    UNKNOWN
}
