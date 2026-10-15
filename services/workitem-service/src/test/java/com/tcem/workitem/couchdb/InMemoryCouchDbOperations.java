package com.tcem.workitem.couchdb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe, in-memory implementation of CouchDbOperations for fast,
 * deterministic unit and integration tests without external Docker/CouchDB network dependencies.
 */
public class InMemoryCouchDbOperations implements CouchDbOperations {

    private final ObjectMapper objectMapper;
    private final Map<String, Map<String, String>> databases = new ConcurrentHashMap<>();
    private final AtomicInteger revCounter = new AtomicInteger(1);
    private volatile boolean healthy = true;

    public InMemoryCouchDbOperations() {
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    public InMemoryCouchDbOperations(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }

    public void clear() {
        databases.clear();
    }

    @Override
    public boolean isHealthy() {
        return healthy;
    }

    @Override
    public boolean ensureDatabaseExists(String databaseName) {
        databases.computeIfAbsent(databaseName, k -> new ConcurrentHashMap<>());
        return true;
    }

    @Override
    public <T> Optional<T> getDocument(String databaseName, String docId, Class<T> responseType) {
        Map<String, String> db = databases.get(databaseName);
        if (db == null) {
            return Optional.empty();
        }
        String json = db.get(docId);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, responseType));
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize document '" + docId + "'", e);
        }
    }

    @Override
    public CouchDbResponse putDocument(String databaseName, String docId, Object document) {
        ensureDatabaseExists(databaseName);
        try {
            String json = objectMapper.writeValueAsString(document);
            databases.get(databaseName).put(docId, json);
            String rev = revCounter.getAndIncrement() + "-" + Integer.toHexString(json.hashCode());
            return CouchDbResponse.builder()
                    .ok(true)
                    .id(docId)
                    .rev(rev)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize document '" + docId + "'", e);
        }
    }

    @Override
    public <T> List<T> find(String databaseName, FindQueryRequest request, Class<T> docType) {
        Map<String, String> db = databases.get(databaseName);
        if (db == null) {
            return Collections.emptyList();
        }

        List<T> matched = new ArrayList<>();
        Map<String, Object> selector = request != null && request.getSelector() != null
                ? request.getSelector()
                : Collections.emptyMap();

        for (String json : db.values()) {
            try {
                JsonNode tree = objectMapper.readTree(json);
                boolean matches = true;

                for (Map.Entry<String, Object> entry : selector.entrySet()) {
                    String field = entry.getKey();
                    Object expectedVal = entry.getValue();

                    JsonNode fieldNode = tree.get(field);
                    if (fieldNode == null || fieldNode.isNull()) {
                        matches = false;
                        break;
                    }

                    String actualValStr = fieldNode.isTextual() ? fieldNode.asText() : fieldNode.toString();
                    if (!actualValStr.equalsIgnoreCase(String.valueOf(expectedVal))) {
                        matches = false;
                        break;
                    }
                }

                if (matches) {
                    matched.add(objectMapper.treeToValue(tree, docType));
                }
            } catch (Exception ignored) {
            }
        }

        int skip = request != null && request.getSkip() != null ? Math.max(0, request.getSkip()) : 0;
        int limit = request != null && request.getLimit() != null ? request.getLimit() : 50;

        if (skip >= matched.size()) {
            return Collections.emptyList();
        }
        int toIndex = Math.min(matched.size(), skip + limit);
        return matched.subList(skip, toIndex);
    }
}
