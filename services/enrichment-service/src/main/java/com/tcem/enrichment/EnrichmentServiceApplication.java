package com.tcem.enrichment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the enrichment-service.
 *
 * Enrichment service: enriches trades against mock reference data - counterparties, instruments, templates (FR4).
 *
 * No business logic yet - this is intentionally a bare Spring Boot app so the
 * service builds, starts, and reports healthy via /actuator/health. Add
 * controllers, Kafka listeners/producers and CouchDB access as the assigned
 * feature work begins.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class EnrichmentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnrichmentServiceApplication.class, args);
    }
}
