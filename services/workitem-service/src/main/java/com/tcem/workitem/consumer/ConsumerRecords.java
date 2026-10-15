package com.tcem.workitem.consumer;

import com.tcem.common.constants.HeaderKeys;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

import java.nio.charset.StandardCharsets;

/** Helpers for reading TCEM Kafka headers. */
final class ConsumerRecords {

    private ConsumerRecords() {}

    /** True when the record carries a positive {@code tcem.replayCount} header. */
    static boolean hasReplayHeader(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader(HeaderKeys.REPLAY_COUNT);
        if (header == null || header.value() == null) {
            return false;
        }
        try {
            return Integer.parseInt(new String(header.value(), StandardCharsets.UTF_8).trim()) > 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
