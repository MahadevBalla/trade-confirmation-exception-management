package com.tcem.workitem.producer;

import com.tcem.common.constants.HeaderKeys;
import com.tcem.common.constants.EventType;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * Republishes a dead-lettered payload to its original topic for controlled replay (FR12).
 * Stamps the replay count and event type as Kafka headers so consumers can recognise the replay.
 */
@Service
public class ReplayProducer {

    private static final Logger log = LoggerFactory.getLogger(ReplayProducer.class);

    private final KafkaOperations<String, String> kafkaOperations;

    public ReplayProducer(KafkaOperations<String, String> kafkaOperations) {
        this.kafkaOperations = kafkaOperations;
    }

    public CompletableFuture<SendResult<String, String>> replay(String topic, String key, String payload, int replayCount) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, payload);
        record.headers().add(HeaderKeys.REPLAY_COUNT, Integer.toString(replayCount).getBytes(StandardCharsets.UTF_8));
        record.headers().add(HeaderKeys.EVENT_TYPE, EventType.REPLAY.name().getBytes(StandardCharsets.UTF_8));
        record.headers().add(HeaderKeys.SOURCE_SERVICE, "workitem-service".getBytes(StandardCharsets.UTF_8));
        log.warn("Replaying message with key '{}' to topic '{}' (replayCount={})", key, topic, replayCount);
        return kafkaOperations.send(record);
    }
}
