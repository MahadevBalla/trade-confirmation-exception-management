package com.tcem.ingestion.repository;

import com.tcem.ingestion.config.CouchDbProperties;
import com.tcem.ingestion.couchdb.CouchDbException;
import com.tcem.ingestion.couchdb.CouchDbOperations;
import com.tcem.ingestion.couchdb.CouchDbResponse;
import com.tcem.ingestion.couchdb.FindQueryRequest;
import com.tcem.ingestion.model.document.AuditDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Comparator;

/**
 * CouchDB repository for immutable audit event records in 'audit-events'.
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
     * Appends an audit document to CouchDB.
     */
    public AuditDocument save(AuditDocument document) {
        if (document.getTimestamp() == null) {
            document.setTimestamp(Instant.now());
        }
        if (document.getId() == null) {
            String generatedId = "audit:" + (document.getTradeId() != null ? document.getTradeId() : "global")
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
        log.debug("Recorded audit event '{}' for trade '{}'", document.getId(), document.getTradeId());
        return document;
    }

    /**
     * Finds audit records associated with a specific trade.
     */
    public List<AuditDocument> findByTradeId(String tradeId) {
        FindQueryRequest query = FindQueryRequest.builder()
                .selector(Map.of("docType", "AUDIT_EVENT", "tradeId", tradeId))
                .limit(100)
                .build();
        return couchDb.find(properties.getAuditEventsDatabase(), query, AuditDocument.class);
    }

    /**
     * Retrieves the most recent audit records across all trades.
     */
    public List<AuditDocument> findRecent(int limit, int skip) {
        FindQueryRequest query = FindQueryRequest.builder()
                .selector(Map.of("docType", "AUDIT_EVENT", "timestamp", Map.of("$gt", "")))
                .limit(limit)
                .skip(skip)
                .sort(List.of(Map.of("timestamp", "desc")))
                .build();
        try {
            return couchDb.find(properties.getAuditEventsDatabase(), query, AuditDocument.class);
        } catch (CouchDbException ex) {
            log.warn("Sorted audit query failed; returning an unsorted page: {}", ex.getMessage());
            List<AuditDocument> records = couchDb.find(
                    properties.getAuditEventsDatabase(),
                    FindQueryRequest.builder()
                            .selector(Map.of("docType", "AUDIT_EVENT"))
                            .limit(limit)
                            .skip(skip)
                            .build(),
                    AuditDocument.class);
            return records.stream()
                    .sorted(Comparator.comparing(AuditDocument::getTimestamp,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
        }
    }
}
