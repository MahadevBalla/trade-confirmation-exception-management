package com.tcem.validation.couchdb;

import java.util.List;
import java.util.Optional;

/**
 * CouchDB operations interface for document storage and retrieval.
 */
public interface CouchDbOperations {

    boolean isHealthy();

    boolean ensureDatabaseExists(String databaseName);

    <T> Optional<T> getDocument(String databaseName, String docId, Class<T> responseType);

    CouchDbResponse putDocument(String databaseName, String docId, Object document);

    <T> List<T> find(String databaseName, FindQueryRequest request, Class<T> docType);
}

