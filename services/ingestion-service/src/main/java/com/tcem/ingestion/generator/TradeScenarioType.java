package com.tcem.ingestion.generator;

/**
 * Enumeration of synthetic trade test scenarios for generation and testing.
 */
public enum TradeScenarioType {

    /** Valid, standard FX Option trade eligible for full Straight-Through Processing (STP). */
    VALID_STANDARD_STP,

    /** Missing mandatory strike value (triggers MISSING_ECONOMIC_DATA / MISSING_STRIKE). */
    MISSING_ECONOMIC_STRIKE,

    /** Missing mandatory notional amount (triggers MISSING_ECONOMIC_DATA / MISSING_NOTIONAL). */
    MISSING_ECONOMIC_NOTIONAL,

    /** Missing mandatory premium amount (triggers MISSING_ECONOMIC_DATA / MISSING_PREMIUM). */
    MISSING_ECONOMIC_PREMIUM,

    /** Unregistered counterparty ID (passes schema validation, fails downstream in enrichment). */
    MISSING_REFDATA_COUNTERPARTY,

    /** Unknown or unsupported confirmation template hint (fails in template matching). */
    TEMPLATE_ISSUE_MISSING,

    /** Sanctioned or restricted jurisdiction / counterparty (fails STP business rules). */
    BUSINESS_RULE_NON_STP,

    /** Reused messageId and tradeId to test idempotency duplicate suppression. */
    DUPLICATE_TRADE,

    /** Syntactically invalid JSON payload to test DLQ / parse error routing. */
    MALFORMED_JSON,

    /** Amendment trade event (AMEND_TRADE) with incremented version. */
    AMEND_TRADE
}
