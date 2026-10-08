package com.tcem.common.model.trade;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * FX Option economics block.
 * <p>
 * All confirmable fields are declared in
 * {@link com.tcem.common.registry.ConfirmableFieldRegistry} for {@code FX_OPTION}.
 * A {@code null} value on a required confirmable field triggers a
 * {@code MISSING_ECONOMIC_DATA} exception.
 * </p>
 *
 * <h3>Confirmable vs. non-confirmable fields</h3>
 * Confirmable (both parties must agree):
 * notionalAmount, notionalCurrency, buySell, optionType, strike, underlier,
 * premiumAmount, premiumCurrency, premiumPaymentDate.
 * <br>
 * Non-confirmable (processing metadata; not in the confirmation document):
 * internalPricingRef, hedgeId.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FxOptionEconomics {

    // -----------------------------------------------------------------------
    // Confirmable fields – both sides must agree
    // -----------------------------------------------------------------------

    /**
     * Notional amount of the option.
     * Example: {@code 1000000} (one million USD)
     */
    private BigDecimal notionalAmount;

    /**
     * ISO 4217 currency code of the notional.
     * Example: {@code USD}
     */
    private String notionalCurrency;

    /**
     * Whether the house side is buying or selling the option.
     * Values: {@code BUY}, {@code SELL}
     */
    private String buySell;

    /**
     * Option type.
     * Values: {@code CALL}, {@code PUT}
     */
    private String optionType;

    /**
     * Strike rate / price agreed in the contract.
     * Example: {@code 1.085} for EUR/USD.
     */
    private BigDecimal strike;

    /**
     * Underlying currency pair or asset.
     * Example: {@code EUR/USD}
     */
    private String underlier;

    /**
     * Premium amount payable by the option buyer.
     */
    private BigDecimal premiumAmount;

    /**
     * ISO 4217 currency code of the premium.
     * Example: {@code USD}
     */
    private String premiumCurrency;

    /**
     * ISO-8601 date on which the premium is due.
     * Example: {@code 2026-01-17}
     */
    private String premiumPaymentDate;

    // -----------------------------------------------------------------------
    // Non-confirmable fields – internal processing metadata
    // -----------------------------------------------------------------------

    /**
     * Internal pricing model reference. NOT included in the confirmation document.
     * Populated by the enrichment-service from the instrument catalogue.
     */
    private String internalPricingRef;

    /**
     * Optional hedge/book identifier for risk systems. NOT confirmable.
     */
    private String hedgeId;
}
