package com.tcem.workitem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the workitem-service.
 *
 * Work-item service: mock work-item tracker/linker, audit trail, and replay/DLQ handling (FR10, FR12).
 *
 * No business logic yet - this is intentionally a bare Spring Boot app so the
 * service builds, starts, and reports healthy via /actuator/health. Add
 * controllers, Kafka listeners/producers and CouchDB access as the assigned
 * feature work begins.
 */
@SpringBootApplication
public class WorkitemServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkitemServiceApplication.class, args);
    }
}
