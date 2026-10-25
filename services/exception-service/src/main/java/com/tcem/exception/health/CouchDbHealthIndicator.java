package com.tcem.exception.health;

import com.tcem.exception.config.CouchDbProperties;
import com.tcem.exception.couchdb.CouchDbOperations;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Spring Boot Actuator health indicator reporting CouchDB connectivity for the exception-service.
 */
@Component
public class CouchDbHealthIndicator implements HealthIndicator {

    private final CouchDbOperations couchDb;
    private final CouchDbProperties properties;

    public CouchDbHealthIndicator(CouchDbOperations couchDb, CouchDbProperties properties) {
        this.couchDb = couchDb;
        this.properties = properties;
    }

    @Override
    public Health health() {
        if (couchDb.isHealthy()) {
            return Health.up()
                    .withDetail("url", properties.getUrl())
                    .withDetail("exceptionsDatabase", properties.getExceptionsDatabase())
                    .withDetail("auditEventsDatabase", properties.getAuditEventsDatabase())
                    .build();
        }
        return Health.down()
                .withDetail("url", properties.getUrl())
                .withDetail("reason", "CouchDB unreachable or returning non-200")
                .build();
    }
}

