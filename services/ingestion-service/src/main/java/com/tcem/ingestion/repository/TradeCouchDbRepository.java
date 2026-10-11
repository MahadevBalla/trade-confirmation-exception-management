package com.tcem.ingestion.repository;

import com.tcem.common.constants.ProcessingStatus;
import com.tcem.ingestion.config.CouchDbProperties;
import com.tcem.ingestion.couchdb.CouchDbOperations;
import com.tcem.ingestion.couchdb.CouchDbResponse;
import com.tcem.ingestion.couchdb.FindQueryRequest;
import com.tcem.ingestion.model.document.TradeDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * CouchDB repository for trade documents in the 'trades' database (FR3).
 */
@Repository
public class TradeCouchDbRepository {

    private static final Logger log = LoggerFactory.getLogger(TradeCouchDbRepository.class);

    private final CouchDbOperations couchDb;
    private final CouchDbProperties properties;

    public TradeCouchDbRepository(CouchDbOperations couchDb, CouchDbProperties properties) {
        this.couchDb = couchDb;
        this.properties = properties;
    }

    /**
     * Finds a trade document by its tradeId.
     */
    public Optional<TradeDocument> findById(String tradeId) {
        return couchDb.getDocument(properties.getTradesDatabase(), tradeId, TradeDocument.class);
    }

    /**
     * Saves or updates a trade document in CouchDB.
     */
    public TradeDocument save(TradeDocument document) {
        if (document.getId() == null) {
            document.setId(document.getTradeId());
        }
        Instant now = Instant.now();
        if (document.getReceivedTimestamp() == null) {
            document.setReceivedTimestamp(now);
        }
        document.setUpdatedTimestamp(now);

        CouchDbResponse response = couchDb.putDocument(
                properties.getTradesDatabase(),
                document.getId(),
                document
        );
        if (response != null && response.getRev() != null) {
            document.setRev(response.getRev());
        }
        log.debug("Saved trade document '{}' with rev '{}'", document.getTradeId(), document.getRev());
        return document;
    }

    /**
     * Finds trades matching a specific processing status with pagination support.
     */
    public List<TradeDocument> findByStatus(ProcessingStatus status, int limit, int skip) {
        Map<String, Object> selector = new HashMap<>();
        selector.put("docType", "TRADE");
        if (status != null) {
            selector.put("processingStatus", status.name());
        }

        FindQueryRequest query = FindQueryRequest.builder()
                .selector(selector)
                .limit(limit > 0 ? limit : 50)
                .skip(skip >= 0 ? skip : 0)
                .build();

        return couchDb.find(properties.getTradesDatabase(), query, TradeDocument.class);
    }

    /**
     * Retrieves paginated trades.
     */
    public List<TradeDocument> findAll(int limit, int skip) {
        return findByStatus(null, limit, skip);
    }
}
