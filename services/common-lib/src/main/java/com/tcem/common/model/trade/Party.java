package com.tcem.common.model.trade;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a party to a trade.
 * <p>
 * Inspired by FpML's {@code PartyTradeIdentifier} concept (spec source note [7]).
 * The enrichment-service resolves {@code partyId} against the mock reference-data
 * catalogue and populates the enriched fields.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Party {

    /**
     * Functional role this party plays on the trade.
     * <p>
     * MVP values: {@code BANK} (house side), {@code COUNTERPARTY} (client side).
     * FpML also defines {@code BROKER}, {@code CLEARING_HOUSE}, etc.
     * </p>
     */
    private String partyRole;

    /**
     * Platform-internal party identifier.
     * Example: {@code PTY-BANK-001}, {@code PTY-CP-101}
     */
    private String partyId;

    // -----------------------------------------------------------------------
    // Enriched fields – populated by enrichment-service (FR4)
    // Provenance tracked in EnrichmentSource within ReferenceDataEnrichment.
    // -----------------------------------------------------------------------

    /** Legal entity name resolved from reference data. Null until enriched. */
    private String legalEntityName;

    /** LEI (Legal Entity Identifier) – 20-char ISO 17442 code. Null until enriched. */
    private String lei;

    /** Two-letter ISO country code of incorporation. Null until enriched. */
    private String country;

    /** Account or book identifier on the house side. Optional. */
    private String accountId;
}
