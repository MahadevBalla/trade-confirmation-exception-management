package com.tcem.workitem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the workitem-service.
 *
 * Work-item service: mock work-item tracker/linker, audit trail, and replay/DLQ handling (FR10, FR12).
 * Consumes 'deadletter.trade', stores DLQ entries, auto-creates DATA_FIX work items, publishes
 * 'workitem.linked', and exposes the DLQ / work-item REST API including controlled replay.
 */
@SpringBootApplication
public class WorkitemServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkitemServiceApplication.class, args);
    }
}
