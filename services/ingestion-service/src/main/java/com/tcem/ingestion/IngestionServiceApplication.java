package com.tcem.ingestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the ingestion-service.
 *
 * Kafka ingestion service: synthetic trade producer/seed loader and inbound message validation (FR1, FR2, FR3).
 *
 * No business logic yet - this is intentionally a bare Spring Boot app so the
 * service builds, starts, and reports healthy via /actuator/health. Add
 * controllers, Kafka listeners/producers and CouchDB access as the assigned
 * feature work begins.
 */
@SpringBootApplication
public class IngestionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IngestionServiceApplication.class, args);
    }
}
