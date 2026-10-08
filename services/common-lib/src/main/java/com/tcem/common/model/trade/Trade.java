package com.tcem.common.model.trade;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tcem.common.constants.ProductType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Core trade record — the central entity of the platform.
 * <p>
 * Inspired by FpML's trade header, product view, and party identifiers.
 * All versions of a trade share the same {@code tradeId}; the
 * {@code tradeVersion} increments on each amendment. The
 * {@link com.tcem.common.registry.ConfirmationFingerprint} utility hashes
 * only the confirmable fields to decide whether a new confirmation is required
 * when a new version arrives (addresses the "nothing changed = don't resend"
 * requirement).
 * </p>
 *
 * <h3>Schema evolution</h3>
 * Adding a new optional field is backward-compatible with {@code schemaVersion}
 * {@code 1.0} (Jackson ignores unknown fields by default). A breaking change
 * requires incrementing the version in {@link com.tcem.common.model.message.SchemaVersions}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Trade {

    // -----------------------------------------------------------------------
    // Identifiers (spec §11: "Separate identifiers from economics")
    // -----------------------------------------------------------------------

    /**
     * Platform-assigned trade identifier – stable across all versions.
     * This is the Kafka partition key to guarantee ordering per trade.
     * Example: {@code TRD-10001}
     */
    private String tradeId;

    /**
     * Trade identifier in the originating source system.
     * Preserved for traceability without overwriting the platform ID.
     * Example: {@code SRC-77881}
     */
    private String sourceSystemTradeId;

    /**
     * Monotonically increasing version number.
     * {@code 1} on NEW_TRADE; increments on each AMEND_TRADE.
     * Used by the fingerprint utility to detect amendment-without-confirmable-change.
     */
    @Builder.Default
    private int tradeVersion = 1;

    // -----------------------------------------------------------------------
    // Product classification
    // -----------------------------------------------------------------------

    /**
     * Product family. Drives which economics block is populated and which
     * validation/confirmation rules apply.
     */
    private ProductType productType;

    // -----------------------------------------------------------------------
    // Dates (ISO-8601 strings; stored as strings to avoid TZ ambiguity in JSON)
    // -----------------------------------------------------------------------

    /** Date on which the trade was agreed. Example: {@code 2026-01-15} */
    private String tradeDate;

    /** Date on which the trade becomes effective. Example: {@code 2026-01-17} */
    private String effectiveDate;

    /**
     * Date on which the trade matures / expires.
     * For FX Option, this is the expiry date.
     * Example: {@code 2026-04-17}
     */
    private String maturityDate;

    // -----------------------------------------------------------------------
    // Parties (FpML: PartyTradeIdentifier pattern)
    // -----------------------------------------------------------------------

    /**
     * All parties to the trade. MVP: exactly two (BANK + COUNTERPARTY).
     * Stretch: may include BROKER or CLEARING_HOUSE.
     */
    private List<Party> parties;

    // -----------------------------------------------------------------------
    // Economics – product-specific block
    // -----------------------------------------------------------------------

    /**
     * FX Option economics. Populated when {@code productType == FX_OPTION}.
     * Null for other product families.
     */
    private FxOptionEconomics fxOptionEconomics;

    // -----------------------------------------------------------------------
    // Non-economic terms
    // -----------------------------------------------------------------------

    /** Terms that appear in the confirmation but are not product economics. */
    private NonEconomicTerms nonEconomicTerms;

    // -----------------------------------------------------------------------
    // Enrichment metadata (populated by enrichment-service, not the producer)
    // -----------------------------------------------------------------------

    /**
     * Reference data enrichment summary.
     * Null until the enrichment-service processes the trade.
     */
    private com.tcem.common.model.reference.ReferenceDataEnrichment enrichmentMetadata;

    // -----------------------------------------------------------------------
    // Explicit getters for compilation without Lombok
    // -----------------------------------------------------------------------

    public String getTradeId() {
        return tradeId;
    }

    public ProductType getProductType() {
        return productType;
    }
}
