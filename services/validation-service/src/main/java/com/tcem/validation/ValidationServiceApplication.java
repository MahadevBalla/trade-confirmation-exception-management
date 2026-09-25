package com.tcem.validation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the validation-service.
 *
 * STP validation & confirmation service: rules engine that decides straight-through-processing vs exception, and generates confirmations (FR5, FR6).
 *
 * No business logic yet - this is intentionally a bare Spring Boot app so the
 * service builds, starts, and reports healthy via /actuator/health. Add
 * controllers, Kafka listeners/producers and CouchDB access as the assigned
 * feature work begins.
 */
@SpringBootApplication
public class ValidationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ValidationServiceApplication.class, args);
    }
}
