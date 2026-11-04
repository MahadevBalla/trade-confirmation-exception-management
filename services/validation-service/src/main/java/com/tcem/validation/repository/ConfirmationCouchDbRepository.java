package com.tcem.validation.repository;

import com.tcem.validation.config.CouchDbProperties;
import com.tcem.validation.couchdb.CouchDbOperations;
import com.tcem.validation.couchdb.CouchDbResponse;
import com.tcem.validation.couchdb.FindQueryRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * CouchDB repository for confirmation documents in the 'confirmations' database (FR6).
 * <p>
 * Operates purely on {@link ConfirmationDocument}, aligning with the document
 * repository pattern used for CouchDB persistence across platform services.
 * </p>
 */
@Repository
public class ConfirmationCouchDbRepository {

    private static final Logger log = LoggerFactory.getLogger(ConfirmationCouchDbRepository.class);

    private final CouchDbOperations couchDb;
    private final String database;

    public ConfirmationCouchDbRepository(CouchDbOperations couchDb, CouchDbProperties properties) {
        this.couchDb = couchDb;
        this.database = properties.getConfirmationsDatabase();
    }

    /**
     * Saves or updates a confirmation document in CouchDB.
     */
    public ConfirmationDocument save(ConfirmationDocument document) {
        if (document.getId() == null) {
            document.setId(document.getConfirmationId());
        }
        if (document.getGeneratedAt() == null) {
            document.setGeneratedAt(Instant.now());
        }

        CouchDbResponse response = couchDb.putDocument(
                database,
                document.getId(),
                document
        );
        if (response != null && response.getRev() != null) {
            document.setRev(response.getRev());
        }
        log.info("Persisted confirmation document [id={}, tradeId={}, fingerprint={}, rev={}]",
                document.getConfirmationId(), document.getTradeId(),
                document.getConfirmationFingerprint(), document.getRev());
        return document;
    }

    /**
     * Fetches a confirmation document by its confirmationId (CouchDB _id).
     */
    public Optional<ConfirmationDocument> findById(String confirmationId) {
        return couchDb.getDocument(database, confirmationId, ConfirmationDocument.class);
    }

    /**
     * Finds all confirmations for a given tradeId, ordered by tradeVersion descending
     * so that the first result is the most recent.
     */
    public List<ConfirmationDocument> findByTradeId(String tradeId) {
        Map<String, Object> selector = new HashMap<>();
        selector.put("docType", "CONFIRMATION");
        selector.put("tradeId", tradeId);

        FindQueryRequest query = new FindQueryRequest(selector, 100, 0);
        List<ConfirmationDocument> docs = couchDb.find(database, query, ConfirmationDocument.class);
        return docs.stream()
                .sorted((a, b) -> Integer.compare(b.getTradeVersion(), a.getTradeVersion()))
                .toList();
    }

    /**
     * Returns the most recent (highest tradeVersion) confirmation document for a trade.
     * Used by the fingerprint duplicate-suppression check on AMEND_TRADE events.
     */
    public Optional<ConfirmationDocument> findLatestForTrade(String tradeId) {
        List<ConfirmationDocument> all = findByTradeId(tradeId);
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    /**
     * Returns a page of confirmations across all trades, newest first within the page.
     */
    public List<ConfirmationDocument> findPage(int limit, int skip) {
        Map<String, Object> selector = new HashMap<>();
        selector.put("docType", "CONFIRMATION");
        FindQueryRequest query = new FindQueryRequest(selector, limit, skip);
        return couchDb.find(database, query, ConfirmationDocument.class).stream()
                .sorted(java.util.Comparator.comparing(ConfirmationDocument::getGeneratedAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .toList();
    }

    /**
     * Counts total confirmations — used by metrics endpoint.
     */
    public long countAll() {
        Map<String, Object> selector = new HashMap<>();
        selector.put("docType", "CONFIRMATION");
        FindQueryRequest query = new FindQueryRequest(selector, 10000, null);
        return couchDb.find(database, query, ConfirmationDocument.class).size();
    }
}
