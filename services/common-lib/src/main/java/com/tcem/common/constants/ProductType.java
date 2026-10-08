package com.tcem.common.constants;

/**
 * Supported product types.
 * <p>
 * MVP: {@code FX_OPTION} only. Additional product families are added here
 * and in the {@link com.tcem.common.registry.ConfirmableFieldRegistry} as the
 * schema evolves.
 * </p>
 */
public enum ProductType {

    /** Foreign-exchange vanilla option (MVP product). */
    FX_OPTION,

    /** Foreign-exchange forward – placeholder for stretch goal. */
    FX_FORWARD,

    /** Equity swap – placeholder for stretch goal. */
    EQUITY_SWAP,

    /** Interest-rate swap – placeholder for stretch goal. */
    IRS,

    /** Used when a raw message arrives with an unrecognised or missing product type. */
    UNKNOWN
}
