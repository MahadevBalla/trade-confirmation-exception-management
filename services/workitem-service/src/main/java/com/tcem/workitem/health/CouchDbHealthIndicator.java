package com.tcem.workitem.health;

import com.tcem.workitem.config.CouchDbProperties;
import com.tcem.workitem.couchdb.CouchDbOperations;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Spring Boot Actuator health indicator reporting CouchDB connectivity.
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
                    .withDetail("dlqEntriesDatabase", properties.getDlqEntriesDatabase())
                    .withDetail("workItemsDatabase", properties.getWorkItemsDatabase())
                    .build();
        }
        return Health.down()
                .withDetail("url", properties.getUrl())
                .withDetail("reason", "CouchDB unreachable or returning non-200")
                .build();
    }
}
