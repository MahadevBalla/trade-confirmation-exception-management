package com.tcem.ingestion.couchdb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Lightweight, native CouchDB HTTP REST client built on Spring Boot 3.3 RestClient.
 */
public class CouchDbClient implements CouchDbOperations {

    private static final Logger log = LoggerFactory.getLogger(CouchDbClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public CouchDbClient(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isHealthy() {
        try {
            String response = restClient.get()
                    .uri("/")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
            return response != null && response.contains("couchdb");
        } catch (Exception ex) {
            log.debug("CouchDB health check failed: {}", ex.getMessage());
            return false;
        }
    }

    @Override
    public boolean ensureDatabaseExists(String databaseName) {
        try {
            restClient.get()
                    .uri("/{db}", databaseName)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .toBodilessEntity();
            log.info("CouchDB database '{}' already exists", databaseName);
            return true;
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                try {
                    restClient.put()
                            .uri("/{db}", databaseName)
                            .accept(MediaType.APPLICATION_JSON)
                            .retrieve()
                            .toBodilessEntity();
                    log.info("Successfully created CouchDB database '{}'", databaseName);
                    return true;
                } catch (HttpClientErrorException putEx) {
                    if (putEx.getStatusCode().value() == 412) {
                        log.info("CouchDB database '{}' already created concurrently", databaseName);
                        return true;
                    }
                    log.error("Failed to create CouchDB database '{}': {}", databaseName, putEx.getMessage());
                    return false;
                } catch (Exception putEx) {
                    log.error("Failed to create CouchDB database '{}': {}", databaseName, putEx.getMessage());
                    return false;
                }
            }
            log.warn("CouchDB check for '{}' returned error: {}", databaseName, ex.getMessage());
            return false;
        } catch (Exception ex) {
            log.warn("Unable to connect to CouchDB to verify database '{}': {}", databaseName, ex.getMessage());
            return false;
        }
    }

    @Override
    public <T> Optional<T> getDocument(String databaseName, String docId, Class<T> responseType) {
        try {
            T document = restClient.get()
                    .uri("/{db}/{id}", databaseName, docId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(responseType);
            return Optional.ofNullable(document);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw new CouchDbException("HTTP error fetching document '" + docId + "' from '" + databaseName + "'", ex);
        } catch (Exception ex) {
            throw new CouchDbException("Failed to fetch document '" + docId + "' from '" + databaseName + "'", ex);
        }
    }

    @Override
    public CouchDbResponse putDocument(String databaseName, String docId, Object document) {
        try {
            JsonNode jsonNode = objectMapper.valueToTree(document);
            if (!jsonNode.isObject()) {
                throw new CouchDbException("Document must serialize to a JSON object");
            }
            ObjectNode objectNode = (ObjectNode) jsonNode;
            objectNode.put("_id", docId);

            if (!objectNode.hasNonNull("_rev")) {
                fetchCurrentRev(databaseName, docId).ifPresent(rev -> objectNode.put("_rev", rev));
            }

            return executePut(databaseName, docId, objectNode);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.CONFLICT) {
                log.debug("Revision conflict on put for doc '{}', retrying with latest rev", docId);
                return retryWithLatestRev(databaseName, docId, document);
            }
            throw new CouchDbException("HTTP error persisting document '" + docId + "' to '" + databaseName + "'", ex);
        } catch (Exception ex) {
            throw new CouchDbException("Failed to persist document '" + docId + "' to '" + databaseName + "'", ex);
        }
    }

    @Override
    public <T> List<T> find(String databaseName, FindQueryRequest request, Class<T> docType) {
        try {
            String rawResponse = restClient.post()
                    .uri("/{db}/_find", databaseName)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);

            if (rawResponse == null) {
                return List.of();
            }

            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode docsNode = root.get("docs");
            if (docsNode == null || !docsNode.isArray()) {
                return List.of();
            }

            List<T> results = new ArrayList<>();
            for (JsonNode node : docsNode) {
                results.add(objectMapper.treeToValue(node, docType));
            }
            return results;
        } catch (Exception ex) {
            throw new CouchDbException("Failed to execute _find query on '" + databaseName + "'", ex);
        }
    }

    private CouchDbResponse executePut(String databaseName, String docId, ObjectNode payload) {
        return restClient.put()
                .uri("/{db}/{id}", databaseName, docId)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(CouchDbResponse.class);
    }

    private Optional<String> fetchCurrentRev(String databaseName, String docId) {
        try {
            String rawJson = restClient.get()
                    .uri("/{db}/{id}", databaseName, docId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
            if (rawJson != null) {
                JsonNode existing = objectMapper.readTree(rawJson);
                if (existing.hasNonNull("_rev")) {
                    return Optional.of(existing.get("_rev").asText());
                }
            }
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                // Doc does not exist yet
            }
        } catch (Exception ex) {
            log.debug("Could not fetch current rev for '{}': {}", docId, ex.getMessage());
        }
        return Optional.empty();
    }

    private CouchDbResponse retryWithLatestRev(String databaseName, String docId, Object document) {
        try {
            Optional<String> latestRev = fetchCurrentRev(databaseName, docId);
            JsonNode jsonNode = objectMapper.valueToTree(document);
            ObjectNode objectNode = (ObjectNode) jsonNode;
            objectNode.put("_id", docId);
            latestRev.ifPresent(rev -> objectNode.put("_rev", rev));

            return executePut(databaseName, docId, objectNode);
        } catch (Exception ex) {
            throw new CouchDbException("Retry put failed for document '" + docId + "'", ex);
        }
    }
}
