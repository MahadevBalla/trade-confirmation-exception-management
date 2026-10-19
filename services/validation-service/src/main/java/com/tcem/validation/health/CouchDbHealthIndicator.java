package com.tcem.validation.health;

import com.tcem.validation.couchdb.CouchDbClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Exposes CouchDB connectivity status via Spring Boot Actuator {@code /actuator/health}.
 */
@Component
public class CouchDbHealthIndicator implements HealthIndicator {

    private final CouchDbClient couchDbClient;

    public CouchDbHealthIndicator(CouchDbClient couchDbClient) {
        this.couchDbClient = couchDbClient;
    }

    @Override
    public Health health() {
        if (couchDbClient.isHealthy()) {
            return Health.up().withDetail("couchdb", "reachable").build();
        }
        return Health.down().withDetail("couchdb", "unreachable").build();
    }
}

