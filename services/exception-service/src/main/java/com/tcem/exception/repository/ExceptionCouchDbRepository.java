package com.tcem.exception.repository;

import com.tcem.common.constants.ExceptionCategory;
import com.tcem.exception.config.CouchDbProperties;
import com.tcem.exception.couchdb.CouchDbOperations;
import com.tcem.exception.couchdb.CouchDbResponse;
import com.tcem.exception.couchdb.FindQueryRequest;
import com.tcem.exception.model.document.ExceptionDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * CouchDB repository for exception documents in the 'exceptions' database (FR7-FR9).
 */
@Repository
public class ExceptionCouchDbRepository {

    private static final Logger log = LoggerFactory.getLogger(ExceptionCouchDbRepository.class);

    private final CouchDbOperations couchDb;
    private final CouchDbProperties properties;

    public ExceptionCouchDbRepository(CouchDbOperations couchDb, CouchDbProperties properties) {
        this.couchDb = couchDb;
        this.properties = properties;
    }

    /**
     * Saves or updates an exception document. Auto-sets the document ID to the exceptionId.
     */
    public ExceptionDocument save(ExceptionDocument document) {
        if (document.getId() == null) {
            document.setId(document.getExceptionId());
        }
        if (document.getClassifiedAt() == null) {
            document.setClassifiedAt(Instant.now());
        }

        CouchDbResponse response = couchDb.putDocument(
                properties.getExceptionsDatabase(),
                document.getId(),
                document
        );
        if (response != null && response.getRev() != null) {
            document.setRev(response.getRev());
        }
        log.debug("Saved exception document '{}' for trade '{}'",
                document.getExceptionId(), document.getTradeId());
        return document;
    }

    /**
     * Finds an exception document by its exceptionId.
     */
    public Optional<ExceptionDocument> findById(String exceptionId) {
        return couchDb.getDocument(properties.getExceptionsDatabase(), exceptionId, ExceptionDocument.class);
    }

    /**
     * Finds all exceptions for a given tradeId.
     */
    public List<ExceptionDocument> findByTradeId(String tradeId) {
        FindQueryRequest query = FindQueryRequest.builder()
                .selector(Map.of("docType", "EXCEPTION", "tradeId", tradeId))
                .limit(100)
                .build();
        return couchDb.find(properties.getExceptionsDatabase(), query, ExceptionDocument.class);
    }

    /**
     * Finds exceptions matching a specific status with pagination.
     */
    public List<ExceptionDocument> findByStatus(String status, int limit, int skip) {
        Map<String, Object> selector = new HashMap<>();
        selector.put("docType", "EXCEPTION");
        if (status != null && !status.isBlank()) {
            selector.put("status", status);
        }

        FindQueryRequest query = FindQueryRequest.builder()
                .selector(selector)
                .limit(limit > 0 ? limit : 50)
                .skip(skip >= 0 ? skip : 0)
                .build();

        return couchDb.find(properties.getExceptionsDatabase(), query, ExceptionDocument.class);
    }

    /**
     * Finds exceptions for a given ExceptionCategory with pagination.
     */
    public List<ExceptionDocument> findByCategory(ExceptionCategory category, int limit, int skip) {
        Map<String, Object> selector = new HashMap<>();
        selector.put("docType", "EXCEPTION");
        if (category != null) {
            selector.put("category", category.name());
        }

        FindQueryRequest query = FindQueryRequest.builder()
                .selector(selector)
                .limit(limit > 0 ? limit : 50)
                .skip(skip >= 0 ? skip : 0)
                .build();

        return couchDb.find(properties.getExceptionsDatabase(), query, ExceptionDocument.class);
    }

        /**
     * Returns total count of exception documents (used for metrics).
     */
    public int countAll() {
        return Math.toIntExact(couchDb.countByView(
                properties.getExceptionsDatabase(),
                "exception_counts",
                "total"
        ));
    }

    /**
     * Returns count of exceptions with a given status.
     */
    public int countByStatus(String status) {
        return findByStatus(status, 1000, 0).size();
    }
}

