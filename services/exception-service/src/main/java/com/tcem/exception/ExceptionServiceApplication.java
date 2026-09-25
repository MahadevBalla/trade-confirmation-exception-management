package com.tcem.exception;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the exception-service.
 *
 * Exception service: captures, classifies and recommends next actions for exceptions (FR7, FR8, FR9).
 *
 * No business logic yet - this is intentionally a bare Spring Boot app so the
 * service builds, starts, and reports healthy via /actuator/health. Add
 * controllers, Kafka listeners/producers and CouchDB access as the assigned
 * feature work begins.
 */
@SpringBootApplication
public class ExceptionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExceptionServiceApplication.class, args);
    }
}
