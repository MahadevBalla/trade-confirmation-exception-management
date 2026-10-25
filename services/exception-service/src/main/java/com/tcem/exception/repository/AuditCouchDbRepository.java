package com.tcem.exception.repository;

import com.tcem.exception.config.CouchDbProperties;
import com.tcem.exception.couchdb.CouchDbOperations;
import com.tcem.exception.couchdb.CouchDbResponse;
import com.tcem.exception.couchdb.FindQueryRequest;
import com.tcem.exception.model.document.AuditDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CouchDB repository for immutable audit event records in the 'audit-events' database.
 */
@Repository
public class AuditCouchDbRepository {

    private static final Logger log = LoggerFactory.getLogger(AuditCouchDbRepository.class);

    private final CouchDbOperations couchDb;
    private final CouchDbProperties properties;

    public AuditCouchDbRepository(CouchDbOperations couchDb, CouchDbProperties properties) {
        this.couchDb = couchDb;
        this.properties = properties;
    }

    /**
     * Appends an audit document to the 'audit-events' database.
     * Audit records are immutable — each save generates a unique ID.
     */
    public AuditDocument save(AuditDocument document) {
        if (document.getTimestamp() == null) {
            document.setTimestamp(Instant.now());
        }
        if (document.getId() == null) {
            String generatedId = "audit:"
                    + (document.getTradeId() != null ? document.getTradeId() : "global")
                    + ":" + document.getTimestamp().toEpochMilli()
                    + ":" + UUID.randomUUID().toString().substring(0, 8);
            document.setId(generatedId);
        }

        CouchDbResponse response = couchDb.putDocument(
                properties.getAuditEventsDatabase(),
                document.getId(),
                document
        );
        if (response != null && response.getRev() != null) {
            document.setRev(response.getRev());
        }
        log.debug("Recorded audit event '{}' for trade '{}' / exception '{}'",
                document.getId(), document.getTradeId(), document.getExceptionId());
        return document;
    }

    /**
     * Finds audit records for a given tradeId (chronological view).
     */
    public List<AuditDocument> findByTradeId(String tradeId) {
        FindQueryRequest query = FindQueryRequest.builder()
                .selector(Map.of("docType", "AUDIT_EVENT", "tradeId", tradeId))
                .limit(200)
                .build();
        return couchDb.find(properties.getAuditEventsDatabase(), query, AuditDocument.class);
    }

    /**
     * Finds audit records for a given exceptionId.
     */
    public List<AuditDocument> findByExceptionId(String exceptionId) {
        FindQueryRequest query = FindQueryRequest.builder()
                .selector(Map.of("docType", "AUDIT_EVENT", "exceptionId", exceptionId))
                .limit(100)
                .build();
        return couchDb.find(properties.getAuditEventsDatabase(), query, AuditDocument.class);
    }
}

