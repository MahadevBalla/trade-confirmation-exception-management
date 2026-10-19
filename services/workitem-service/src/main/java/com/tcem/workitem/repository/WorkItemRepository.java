package com.tcem.workitem.repository;

import com.tcem.workitem.config.CouchDbProperties;
import com.tcem.workitem.couchdb.CouchDbOperations;
import com.tcem.workitem.couchdb.CouchDbResponse;
import com.tcem.workitem.couchdb.FindQueryRequest;
import com.tcem.workitem.model.document.WorkItemDocument;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * CouchDB repository for the mock work-item tracker in 'work-items' (FR10).
 * Selectors are equality-only so the same queries work against CouchDB Mango and the in-memory test double.
 */
@Repository
public class WorkItemRepository {

    /** Upper bound for unpaged scans (per-trade lookups, dashboard summary). */
    static final int SCAN_LIMIT = 1000;

    private final CouchDbOperations couchDb;
    private final CouchDbProperties properties;

    public WorkItemRepository(CouchDbOperations couchDb, CouchDbProperties properties) {
        this.couchDb = couchDb;
        this.properties = properties;
    }

    public WorkItemDocument save(WorkItemDocument document) {
        document.setUpdatedAt(Instant.now());
        CouchDbResponse response = couchDb.putDocument(properties.getWorkItemsDatabase(), document.getId(), document);
        if (response != null && response.getRev() != null) {
            document.setRev(response.getRev());
        }
        return document;
    }

    public Optional<WorkItemDocument> findById(String workItemId) {
        return couchDb.getDocument(properties.getWorkItemsDatabase(), workItemId, WorkItemDocument.class);
    }

    /**
     * Lists work items newest-first. {@code filters} maps document fields (status, type, ownerQueue,
     * tradeId, priority) to required values; blank values are ignored.
     */
    public List<WorkItemDocument> findPage(Map<String, String> filters, int page, int size) {
        return query(filters, size, page * size);
    }

    public List<WorkItemDocument> findByTrade(String tradeId) {
        if (tradeId == null || tradeId.isBlank()) {
            return List.of();
        }
        return query(Map.of("tradeId", tradeId), SCAN_LIMIT, 0);
    }

    public List<WorkItemDocument> findOpenByTrade(String tradeId) {
        return findByTrade(tradeId).stream().filter(WorkItemDocument::isOpen).toList();
    }

    public List<WorkItemDocument> findAll() {
        return query(Map.of(), SCAN_LIMIT, 0);
    }

    private List<WorkItemDocument> query(Map<String, String> filters, int limit, int skip) {
        Map<String, Object> selector = new HashMap<>();
        selector.put("docType", "WORK_ITEM");
        if (filters != null) {
            filters.forEach((field, value) -> {
                if (value != null && !value.isBlank()) {
                    selector.put(field, value);
                }
            });
        }
        FindQueryRequest query = FindQueryRequest.builder()
                .selector(selector)
                .limit(limit)
                .skip(skip)
                .build();
        return couchDb.find(properties.getWorkItemsDatabase(), query, WorkItemDocument.class).stream()
                .sorted(Comparator.comparing(WorkItemDocument::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }
}
