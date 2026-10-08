package com.tcem.common.registry;

import com.tcem.common.constants.ProductType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Product-aware registry of confirmable field names.
 * <p>
 * A <em>confirmable field</em> is one that must appear in the confirmation
 * document and on which both parties must agree. Non-confirmable fields are
 * processing metadata (internal pricing refs, routing codes, etc.) that are
 * never sent to the counterparty.
 * </p>
 *
 * <h3>Why this matters</h3>
 * <ul>
 *   <li><strong>Duplicate suppression</strong>: when trade version N+1 arrives,
 *       the {@link ConfirmationFingerprint} hashes only the confirmable fields.
 *       If the hash equals the stored fingerprint, no new confirmation is emitted
 *       (the "nothing changed = don't resend" rule).</li>
 *   <li><strong>Exception classification</strong>: a missing value on a confirmable
 *       field triggers {@code MISSING_ECONOMIC_DATA} or similar; a missing value on
 *       a non-confirmable field is not an exception (addresses the "not every
 *       attribute is confirmable" requirement).</li>
 *   <li><strong>False-positive prevention</strong>: if an AMEND_TRADE only changes
 *       non-confirmable fields (e.g. internal routing code), no
 *       {@code confirmation.generated} event is produced.</li>
 * </ul>
 *
 * <h3>Adding a new product</h3>
 * Add a new entry to {@link #REGISTRY} mapping the new {@link ProductType} to the
 * set of its confirmable field names (dot-notation paths into the economics block).
 *
 * <h3>Field name convention</h3>
 * Names follow the dot-notation path from the root {@code Trade} object.
 * Examples:
 * <ul>
 *   <li>{@code fxOptionEconomics.notionalAmount}</li>
 *   <li>{@code nonEconomicTerms.jurisdiction}</li>
 *   <li>{@code parties[COUNTERPARTY].partyId}</li>
 * </ul>
 */
public final class ConfirmableFieldRegistry {

    private ConfirmableFieldRegistry() {}

    /**
     * The registry. Keys are product types; values are immutable sets of
     * confirmable field name paths.
     */
    private static final Map<ProductType, Set<String>> REGISTRY;

    static {
        Map<ProductType, Set<String>> map = new EnumMap<>(ProductType.class);

        // -----------------------------------------------------------------------
        // FX Option – MVP product family
        // Economics fields (confirmable):
        // -----------------------------------------------------------------------
        map.put(ProductType.FX_OPTION, Set.of(
                // Trade-level identifiers that both parties reference
                "tradeId",
                "tradeDate",
                "effectiveDate",
                "maturityDate",

                // Party identification (counterparty must agree on who they are)
                "parties[COUNTERPARTY].partyId",
                "parties[BANK].partyId",

                // FX Option economics
                "fxOptionEconomics.notionalAmount",
                "fxOptionEconomics.notionalCurrency",
                "fxOptionEconomics.buySell",
                "fxOptionEconomics.optionType",
                "fxOptionEconomics.strike",
                "fxOptionEconomics.underlier",
                "fxOptionEconomics.premiumAmount",
                "fxOptionEconomics.premiumCurrency",
                "fxOptionEconomics.premiumPaymentDate",

                // Non-economic confirmable terms
                "nonEconomicTerms.jurisdiction",
                "nonEconomicTerms.confirmationMethod",
                "nonEconomicTerms.governingLaw",
                "nonEconomicTerms.documentationVersion"
        ));

        // -----------------------------------------------------------------------
        // FX Forward – stretch placeholder
        // -----------------------------------------------------------------------
        map.put(ProductType.FX_FORWARD, Set.of(
                "tradeId", "tradeDate", "effectiveDate", "maturityDate",
                "parties[COUNTERPARTY].partyId", "parties[BANK].partyId",
                "nonEconomicTerms.jurisdiction",
                "nonEconomicTerms.confirmationMethod"
                // Economics fields to be added when FX_FORWARD model is built
        ));

        REGISTRY = Collections.unmodifiableMap(map);
    }

    /**
     * Returns the set of confirmable field paths for the given product type.
     *
     * @param productType the product type
     * @return immutable set of confirmable field name paths
     * @throws IllegalArgumentException if the product type has no registry entry
     */
    public static Set<String> getConfirmableFields(ProductType productType) {
        Set<String> fields = REGISTRY.get(productType);
        if (fields == null) {
            throw new IllegalArgumentException(
                    "No confirmable-field registry entry for product type: " + productType
                    + ". Add it to ConfirmableFieldRegistry before implementing this product.");
        }
        return fields;
    }

    /**
     * Returns {@code true} if the given field path is confirmable for the
     * specified product type.
     *
     * @param productType the product type
     * @param fieldPath   dot-notation field path
     * @return whether the field is confirmable
     */
    public static boolean isConfirmable(ProductType productType, String fieldPath) {
        Set<String> fields = REGISTRY.get(productType);
        return fields != null && fields.contains(fieldPath);
    }
}
