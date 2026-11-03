package com.tcem.validation.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.validation.couchdb.CouchDbClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Spring configuration wiring {@link CouchDbClient} and database auto-initialization.
 * Mirrors the ingestion-service pattern exactly.
 */
@Configuration
public class CouchDbConfig {

    private static final Logger log = LoggerFactory.getLogger(CouchDbConfig.class);

    @Bean
    public CouchDbClient couchDbClient(CouchDbProperties properties, ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.getConnectionTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));

        RestClient restClient = RestClient.builder()
                .baseUrl(properties.getUrl())
                .requestFactory(requestFactory)
                .defaultHeaders(headers -> headers.setBasicAuth(properties.getUsername(), properties.getPassword()))
                .build();

        return new CouchDbClient(restClient, objectMapper);
    }

    /**
     * Ensures the {@code confirmations} and {@code validation-idempotency} CouchDB databases exist on startup.
     * Logs a warning (never throws) when CouchDB is offline — allows IDE runs
     * without a live CouchDB.
     */
    @Bean
    public ApplicationRunner couchDbInitializer(CouchDbClient couchDbClient, CouchDbProperties properties) {
        return args -> {
            if (!properties.isAutoCreateDatabases()) {
                log.info("CouchDB database auto-creation is disabled");
                return;
            }
            log.info("Checking CouchDB connectivity at {}", properties.getUrl());
            if (!couchDbClient.isHealthy()) {
                log.warn("CouchDB at {} is not reachable. Database initialization skipped. "
                        + "(Start docker-compose when running services)", properties.getUrl());
                return;
            }
            couchDbClient.ensureDatabaseExists(properties.getConfirmationsDatabase());
            couchDbClient.ensureDatabaseExists(properties.getIdempotencyDatabase());
            log.info("CouchDB initialization complete – databases '{}', '{}'",
                    properties.getConfirmationsDatabase(), properties.getIdempotencyDatabase());
        };
    }
}

