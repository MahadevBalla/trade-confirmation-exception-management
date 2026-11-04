package com.tcem.ingestion.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.ingestion.couchdb.CouchDbClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Spring configuration providing CouchDbClient and database auto-initialization.
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
     * Initializes required CouchDB databases (trades, audit-events) on application startup.
     * Logs a warning rather than failing startup if CouchDB is offline during local IDE / test runs.
     */
    @Bean
    public ApplicationRunner couchDbInitializer(CouchDbClient couchDbClient, CouchDbProperties properties) {
        return args -> {
            if (!properties.isAutoCreateDatabases()) {
                log.info("CouchDB database auto-creation is disabled");
                return;
            }

            log.info("Checking CouchDB connectivity and database initialization at {}", properties.getUrl());
            boolean healthy = couchDbClient.isHealthy();
            if (!healthy) {
                log.warn("CouchDB at {} is not reachable. Database initialization will be skipped. "
                        + "(Ensure CouchDB is running via docker-compose up -d couchdb when running services)",
                        properties.getUrl());
                return;
            }

            couchDbClient.ensureDatabaseExists(properties.getTradesDatabase());
            couchDbClient.ensureDatabaseExists(properties.getAuditEventsDatabase());
            couchDbClient.ensureIndex(properties.getAuditEventsDatabase(), "audit-by-timestamp", java.util.List.of("timestamp"));
            log.info("CouchDB initialization complete for databases: '{}', '{}'",
                    properties.getTradesDatabase(), properties.getAuditEventsDatabase());
        };
    }
}
