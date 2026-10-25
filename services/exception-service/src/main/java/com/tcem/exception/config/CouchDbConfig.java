package com.tcem.exception.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.exception.couchdb.CouchDbClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Spring configuration providing CouchDbClient bean and database auto-initialization.
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
     * Ensures required CouchDB databases (exceptions, audit-events) exist on startup.
     * Logs a warning rather than failing if CouchDB is offline during local IDE or test runs.
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
                        + "(Ensure CouchDB is running via docker compose up -d couchdb when running services)",
                        properties.getUrl());
                return;
            }

            couchDbClient.ensureDatabaseExists(properties.getExceptionsDatabase());
            couchDbClient.ensureDatabaseExists(properties.getAuditEventsDatabase());
            log.info("CouchDB initialization complete for databases: '{}', '{}'",
                    properties.getExceptionsDatabase(), properties.getAuditEventsDatabase());
        };
    }
}

