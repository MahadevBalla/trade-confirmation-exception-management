package com.tcem.common.constants;

/**
 * Lifecycle event types that can appear in a trade message.
 * <p>
 * These correspond to FpML's lifecycle event concepts. The validation service
 * uses this enum to dispatch STP rules that are event-type-specific.
 * </p>
 */
public enum EventType {

    /** First booking of a trade. */
    NEW_TRADE,

    /** Economic or non-economic amendment to an existing trade. */
    AMEND_TRADE,

    /**
     * Cancellation of a trade.
     * No confirmation should be generated; any open exceptions should be closed.
     */
    CANCEL_TRADE,

    /**
     * Partial or full novation (counterparty transfer).
     * Requires re-validation of counterparty eligibility.
     */
    NOVATION,

    /**
     * Compression event (multiple trades merged into one).
     * Stretch goal only.
     */
    COMPRESSION,

    /** Indicates a resubmission triggered by the replay mechanism (FR12). */
    REPLAY
}
