package com.tcem.exception;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

/**
 * Smoke test: verifies that the full Spring application context starts correctly.
 *
 * <p>Uses {@link EmbeddedKafka} so the Kafka consumer/producer beans wire up without
 * needing a real broker. CouchDB auto-creation and the Kafka listener auto-startup are
 * disabled so no external infrastructure is required at all.
 */
@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {
                "exception.created",
                "exception.classified",
                "manual.review.required",
                "deadletter.trade"
        }
)
@TestPropertySource(properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "couchdb.auto-create-databases=false"
})
class ExceptionServiceApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that all Spring beans (consumers, producers, repositories,
        // services, controllers, health indicators) wire correctly.
    }
}
