package com.tcem.workitem.repository;

import com.tcem.workitem.config.CouchDbProperties;
import com.tcem.workitem.couchdb.CouchDbOperations;
import com.tcem.workitem.couchdb.CouchDbResponse;
import com.tcem.workitem.couchdb.FindQueryRequest;
import com.tcem.workitem.model.document.DlqEntryDocument;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * CouchDB repository for DLQ entries in 'dlq-entries' (FR12).
 */
@Repository
public class DlqEntryRepository {

    private final CouchDbOperations couchDb;
    private final CouchDbProperties properties;

    public DlqEntryRepository(CouchDbOperations couchDb, CouchDbProperties properties) {
        this.couchDb = couchDb;
        this.properties = properties;
    }

    public DlqEntryDocument save(DlqEntryDocument document) {
        document.setUpdatedAt(Instant.now());
        CouchDbResponse response = couchDb.putDocument(properties.getDlqEntriesDatabase(), document.getId(), document);
        if (response != null && response.getRev() != null) {
            document.setRev(response.getRev());
        }
        return document;
    }

    public Optional<DlqEntryDocument> findById(String id) {
        return couchDb.getDocument(properties.getDlqEntriesDatabase(), id, DlqEntryDocument.class);
    }

    /** Like {@link #findById} but follows an alias marker to the entry it was merged into. */
    public Optional<DlqEntryDocument> findEntry(String id) {
        Optional<DlqEntryDocument> doc = findById(id);
        if (doc.isPresent() && doc.get().getAliasOf() != null) {
            return findById(doc.get().getAliasOf());
        }
        return doc;
    }

    /** Entries for a trade currently awaiting the outcome of a replay. */
    public List<DlqEntryDocument> findReplayingByTrade(String tradeId) {
        if (tradeId == null || tradeId.isBlank()) {
            return List.of();
        }
        FindQueryRequest query = FindQueryRequest.builder()
                .selector(Map.of("docType", "DLQ_ENTRY", "tradeId", tradeId,
                        "dlqStatus", DlqEntryDocument.STATUS_REPLAYING))
                .limit(50)
                .build();
        return couchDb.find(properties.getDlqEntriesDatabase(), query, DlqEntryDocument.class);
    }

    /**
     * Lists entries newest-first, optionally filtered by DLQ status.
     * Ordering is applied to the fetched page, since Mango sorting needs a server-side index.
     */
    public List<DlqEntryDocument> findPage(String dlqStatus, int page, int size) {
        Map<String, Object> selector = new HashMap<>();
        selector.put("docType", "DLQ_ENTRY");
        if (dlqStatus != null && !dlqStatus.isBlank()) {
            selector.put("dlqStatus", dlqStatus);
        }
        FindQueryRequest query = FindQueryRequest.builder()
                .selector(selector)
                .limit(size)
                .skip(page * size)
                .build();
        return couchDb.find(properties.getDlqEntriesDatabase(), query, DlqEntryDocument.class).stream()
                .sorted(Comparator.comparing(DlqEntryDocument::getReceivedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }
}
