package com.tcem.enrichment.config;

import com.tcem.enrichment.idempotency.EnrichmentIdempotencyRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class CouchDbConfig {
    private static final Logger log = LoggerFactory.getLogger(CouchDbConfig.class);
    @Bean
    RestClient enrichmentCouchDbRestClient(CouchDbProperties properties) {
        return RestClient.builder().baseUrl(properties.getUrl())
                .defaultHeaders(headers -> headers.setBasicAuth(properties.getUsername(), properties.getPassword()))
                .build();
    }

    @Bean
    ApplicationRunner enrichmentIdempotencyDatabaseInitializer(EnrichmentIdempotencyRepository repository) {
        return args -> {
            try {
                repository.ensureDatabase();
            } catch (RuntimeException ex) {
                log.warn("CouchDB idempotency database initialization skipped: {}", ex.getMessage());
            }
        };
    }
}
