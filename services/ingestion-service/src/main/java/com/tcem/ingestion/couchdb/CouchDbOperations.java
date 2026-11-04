package com.tcem.ingestion.couchdb;

import java.util.List;
import java.util.Optional;

/**
 * Interface defining CouchDB operations for document storage and retrieval.
 */
public interface CouchDbOperations {

    /**
     * Checks if the CouchDB server is reachable.
     */
    boolean isHealthy();

    /**
     * Ensures that the specified database exists, creating it if necessary.
     */
    boolean ensureDatabaseExists(String databaseName);

    /**
     * Creates a Mango index on the given fields if it does not exist yet.
     */
    default void ensureIndex(String databaseName, String indexName, List<String> fields) {}

    /**
     * Retrieves a document by its ID.
     */
    <T> Optional<T> getDocument(String databaseName, String docId, Class<T> responseType);

    /**
     * Stores or updates a document with conflict handling.
     */
    CouchDbResponse putDocument(String databaseName, String docId, Object document);

    /**
     * Queries CouchDB using Mango _find syntax.
     */
    <T> List<T> find(String databaseName, FindQueryRequest request, Class<T> docType);
}
