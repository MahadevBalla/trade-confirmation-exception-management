package com.tcem.common.constants;

/**
 * Confirmation status aligned with FpML {@code confirmationStatus} concepts.
 * <p>
 * Reference: FpML confirmationStatus – matched, mismatched, unmatched, alleged.
 * Spec source note [6].
 * </p>
 */
public enum ConfirmationStatus {

    /** Both parties' terms agree; confirmation is legally complete. */
    MATCHED,

    /** Terms received from both parties but one or more fields disagree. */
    MISMATCHED,

    /** Confirmation sent; counterparty has not yet responded. */
    ALLEGED,

    /** No confirmation attempt has been made yet. */
    UNMATCHED,

    /** Confirmation process could not be initiated due to an exception. */
    FAILED,

    /** Superseded by a later version of the same trade. */
    SUPERSEDED
}
