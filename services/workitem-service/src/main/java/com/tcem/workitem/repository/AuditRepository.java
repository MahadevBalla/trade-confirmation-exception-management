package com.tcem.workitem.repository;

import com.tcem.workitem.config.CouchDbProperties;
import com.tcem.workitem.couchdb.CouchDbOperations;
import com.tcem.workitem.model.document.AuditDocument;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only writer for audit entries in 'audit-events'.
 */
@Repository
public class AuditRepository {

    private final CouchDbOperations couchDb;
    private final CouchDbProperties properties;

    public AuditRepository(CouchDbOperations couchDb, CouchDbProperties properties) {
        this.couchDb = couchDb;
        this.properties = properties;
    }

    public AuditDocument save(AuditDocument document) {
        if (document.getTimestamp() == null) {
            document.setTimestamp(Instant.now());
        }
        if (document.getId() == null) {
            document.setId("audit:" + (document.getTradeId() != null ? document.getTradeId() : "global")
                    + ":" + document.getTimestamp().toEpochMilli()
                    + ":" + UUID.randomUUID().toString().substring(0, 8));
        }
        couchDb.putDocument(properties.getAuditEventsDatabase(), document.getId(), document);
        return document;
    }
}
