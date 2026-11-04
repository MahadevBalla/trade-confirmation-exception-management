package com.tcem.validation.repository;

import com.tcem.validation.config.CouchDbProperties;
import com.tcem.validation.couchdb.CouchDbOperations;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Keeps the latest enriched {@code trade.enriched} message of each trade that failed STP, so a confirmation
 * can still be generated if a reviewer approves the trade later (see
 * {@link com.tcem.validation.consumer.ManualReviewApprovalConsumer}).
 * <p>
 * Stored in the {@code confirmations} database under its own {@code docType}; confirmation queries select
 * on {@code docType=CONFIRMATION} and never see these documents.
 */
@Repository
public class HeldTradeRepository {

    static final String DOC_TYPE = "HELD_TRADE";

    private final CouchDbOperations couchDb;
    private final String database;

    public HeldTradeRepository(CouchDbOperations couchDb, CouchDbProperties properties) {
        this.couchDb = couchDb;
        this.database = properties.getConfirmationsDatabase();
    }

    /** Stores the raw message, replacing any earlier one for the trade. */
    public void hold(String tradeId, String rawTradeEvent) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("docType", DOC_TYPE);
        document.put("tradeId", tradeId);
        document.put("payload", rawTradeEvent);
        document.put("heldAt", Instant.now().toString());
        couchDb.putDocument(database, documentId(tradeId), document);
    }

    /** Returns the raw message held for the trade, if any. */
    public Optional<String> find(String tradeId) {
        return couchDb.getDocument(database, documentId(tradeId), Map.class)
                .map(document -> (String) document.get("payload"));
    }

    private static String documentId(String tradeId) {
        return "held-trade-" + tradeId;
    }
}
