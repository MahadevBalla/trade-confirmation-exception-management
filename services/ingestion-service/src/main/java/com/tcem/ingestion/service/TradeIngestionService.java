package com.tcem.ingestion.service;

import com.tcem.ingestion.model.IngestionResult;

/**
 * Service contract for ingesting and processing incoming trade events from 'trade.raw' (FR1, FR2, FR3, FR12).
 */
public interface TradeIngestionService {

    /**
     * Ingests, checks idempotency, validates schema, persists, and routes a raw trade message.
     *
     * @param partitionKey partition key from the Kafka record (e.g. tradeId)
     * @param rawPayload   raw JSON payload string
     * @return outcome of the ingestion pipeline
     */
    IngestionResult processRawTrade(String partitionKey, String rawPayload);
}
