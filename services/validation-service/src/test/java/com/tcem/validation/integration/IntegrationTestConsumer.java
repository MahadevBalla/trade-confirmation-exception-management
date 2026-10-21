package com.tcem.validation.integration;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Lightweight wrapper around a {@link Consumer} for reading from an embedded Kafka topic
 * in integration tests.
 *
 * <p>Uses manual partition assignment (no consumer-group rebalance) and starts at the
 * beginning of the log. Tests filter records by message key to avoid cross-talk.
 * Call {@link #close()} in {@code @AfterEach}.
 */
class IntegrationTestConsumer implements AutoCloseable {

    private final Consumer<String, String> consumer;

    private IntegrationTestConsumer(Consumer<String, String> consumer) {
        this.consumer = consumer;
    }

    static IntegrationTestConsumer create(EmbeddedKafkaBroker broker, String topic) {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                "it-consumer-" + topic + "-" + System.nanoTime(),
                "false",
                broker
        );
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        consumerProps.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_uncommitted");

        KafkaConsumer<String, String> kafkaConsumer = new KafkaConsumer<>(consumerProps);
        TopicPartition partition = new TopicPartition(topic, 0);
        kafkaConsumer.assign(List.of(partition));
        kafkaConsumer.seekToBeginning(List.of(partition));
        kafkaConsumer.position(partition);
        kafkaConsumer.poll(Duration.ofMillis(50));
        kafkaConsumer.seekToBeginning(List.of(partition));

        return new IntegrationTestConsumer(kafkaConsumer);
    }

    Consumer<String, String> consumer() {
        return consumer;
    }

    @Override
    public void close() {
        consumer.close();
    }
}
