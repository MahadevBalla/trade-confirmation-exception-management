package com.tcem.common.model.reference;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Enrichment metadata attached to a {@link com.tcem.common.model.trade.Trade}
 * after the enrichment-service processes it (FR4).
 * <p>
 * This object is nested inside {@code Trade.enrichmentMetadata} so the full
 * enrichment trail is co-located with the trade document in CouchDB.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReferenceDataEnrichment {

    /**
     * ID of the confirmation template selected from the mock catalogue.
     * Null if no matching template was found (triggers a {@code TEMPLATE_ISSUE} exception).
     * Example: {@code TMPL-FX-OPT-001}
     */
    private String resolvedTemplateId;

    /**
     * Version of the resolved template. Compared against the trade's product
     * configuration to detect {@code TEMPLATE_VERSION_MISMATCH}.
     */
    private String resolvedTemplateVersion;

    /**
     * Whether the counterparty is eligible for straight-through processing.
     * Determined from the mock STP-eligibility reference data.
     */
    private Boolean counterpartySTPEligible;

    /**
     * Whether the product type is configured for STP on this counterparty.
     */
    private Boolean productSTPEligible;

    /**
     * Provenance map: field name → {@link EnrichmentSource}.
     * Tracks how each enriched value was derived.
     * Key examples: {@code "legalEntityName"}, {@code "resolvedTemplateId"}.
     */
    private Map<String, EnrichmentSource> fieldProvenance;

    /**
     * Free-text enrichment notes (e.g. "defaulted jurisdiction to GENERIC").
     * Stored for audit; not used in validation logic.
     */
    private String enrichmentNotes;
}
