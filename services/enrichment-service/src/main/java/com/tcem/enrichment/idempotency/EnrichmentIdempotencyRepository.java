package com.tcem.enrichment.idempotency;

import com.tcem.common.idempotency.IdempotencyKey;
import com.tcem.enrichment.config.CouchDbProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.Map;

/** Service-scoped durable duplicate marker; it deliberately cannot collide with ingestion markers. */
@Repository
public class EnrichmentIdempotencyRepository {
    private final RestClient restClient;
    private final CouchDbProperties properties;

    public EnrichmentIdempotencyRepository(RestClient enrichmentCouchDbRestClient, CouchDbProperties properties) {
        this.restClient = enrichmentCouchDbRestClient;
        this.properties = properties;
    }

    public boolean isProcessed(IdempotencyKey key) {
        try {
            restClient.get().uri("/{db}/{id}", properties.getIdempotencyDatabase(), documentId(key))
                    .retrieve().toBodilessEntity();
            return true;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) return false;
            throw ex;
        }
    }

    public void record(IdempotencyKey key) {
        Map<String, Object> document = Map.of(
                "_id", documentId(key), "docType", "ENRICHMENT_IDEMPOTENCY",
                "idempotencyKey", key.toStorageKey(), "processedAt", Instant.now().toString());
        restClient.put().uri("/{db}/{id}", properties.getIdempotencyDatabase(), documentId(key))
                .body(document).retrieve().toBodilessEntity();
    }

    public void ensureDatabase() {
        if (!properties.isAutoCreateDatabases()) return;
        try {
            restClient.put().uri("/{db}", properties.getIdempotencyDatabase())
                    .exchange((request, response) -> {
                        HttpStatusCode status = response.getStatusCode();
                        if (status.value() != 201 && status.value() != 412) {
                            throw new IllegalStateException("Unable to initialize enrichment idempotency database: HTTP " + status.value());
                        }
                        return null;
                    });
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException("Unable to initialize enrichment idempotency database", ex);
        }
    }

    private String documentId(IdempotencyKey key) { return "enrichment-" + key.toStorageKey(); }
}
