package com.tcem.common.model.reference;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Records how a single enriched field was derived.
 * <p>
 * Stored alongside the enriched trade in CouchDB to satisfy the
 * "record provenance" principle (spec §11): for each enriched field,
 * we know the source, timestamp, and whether the value was supplied,
 * derived, or defaulted.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EnrichmentSource {

    /** Name of the reference-data source used. Example: {@code COUNTERPARTY_MASTER} */
    private String sourceName;

    /** ISO-8601 UTC timestamp when the lookup was performed. */
    private Instant lookedUpAt;

    /**
     * How the value was determined.
     * Values: {@code SUPPLIED} (came from the original message),
     * {@code DERIVED} (computed from other fields),
     * {@code DEFAULTED} (fallback value applied).
     */
    private String derivationMethod;

    /**
     * Optional reference-data record version / ETag for cache invalidation.
     */
    private String referenceDataVersion;
}
