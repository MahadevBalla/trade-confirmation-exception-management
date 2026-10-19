package com.tcem.enrichment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "couchdb.auto-create-databases=false"
})
class EnrichmentServiceApplicationTests {

    @Test
    void contextLoads() {
        // Smoke test: fails the build if the Spring context can't start.
        // Replace/extend once the service has real beans to wire.
    }
}
