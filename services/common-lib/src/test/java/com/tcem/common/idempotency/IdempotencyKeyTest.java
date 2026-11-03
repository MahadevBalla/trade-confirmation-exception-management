package com.tcem.common.idempotency;

import com.tcem.common.constants.EventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyKeyTest {

    private static final String SCHEMA = "capstone-fpml-json-1.0";

    @Test
    @DisplayName("storage key format is idempotency:{tradeId}:{messageId}:{schemaVersion}:{eventType}")
    void storageKeyFormat() {
        IdempotencyKey key = IdempotencyKey.of("TRD-1", "MSG-1", SCHEMA, EventType.NEW_TRADE);

        assertThat(key.toStorageKey())
                .isEqualTo("idempotency:TRD-1:MSG-1:capstone-fpml-json-1.0:NEW_TRADE");
        assertThat(key).hasToString("IdempotencyKey{" + key.toStorageKey() + "}");
    }

    @Test
    @DisplayName("getters return the components")
    void getters() {
        IdempotencyKey key = IdempotencyKey.of("TRD-1", "MSG-1", SCHEMA, EventType.AMEND_TRADE);

        assertThat(key.getTradeId()).isEqualTo("TRD-1");
        assertThat(key.getMessageId()).isEqualTo("MSG-1");
        assertThat(key.getSchemaVersion()).isEqualTo(SCHEMA);
        assertThat(key.getEventType()).isEqualTo(EventType.AMEND_TRADE);
    }

    @Test
    @DisplayName("equal components give equal keys and identical hash codes")
    void equalsAndHashCode() {
        IdempotencyKey a = IdempotencyKey.of("TRD-1", "MSG-1", SCHEMA, EventType.NEW_TRADE);
        IdempotencyKey b = IdempotencyKey.of("TRD-1", "MSG-1", SCHEMA, EventType.NEW_TRADE);

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a.toStorageKey()).isEqualTo(b.toStorageKey());
    }

    @Test
    @DisplayName("any differing component gives a different key")
    void differentComponents_differentKeys() {
        IdempotencyKey base = IdempotencyKey.of("TRD-1", "MSG-1", SCHEMA, EventType.NEW_TRADE);

        assertThat(base).isNotEqualTo(IdempotencyKey.of("TRD-2", "MSG-1", SCHEMA, EventType.NEW_TRADE));
        assertThat(base).isNotEqualTo(IdempotencyKey.of("TRD-1", "MSG-2", SCHEMA, EventType.NEW_TRADE));
        assertThat(base).isNotEqualTo(IdempotencyKey.of("TRD-1", "MSG-1", "capstone-fpml-json-1.1", EventType.NEW_TRADE));
        assertThat(base).isNotEqualTo(IdempotencyKey.of("TRD-1", "MSG-1", SCHEMA, EventType.AMEND_TRADE));
        assertThat(base).isNotEqualTo(null);
        assertThat(base).isNotEqualTo("idempotency:TRD-1:MSG-1:capstone-fpml-json-1.0:NEW_TRADE");
    }

    @Test
    @DisplayName("REPLAY yields a different key than the original event type (replay relies on this)")
    void replayEventType_differsFromOriginal() {
        IdempotencyKey original = IdempotencyKey.of("TRD-1", "MSG-1", SCHEMA, EventType.NEW_TRADE);
        IdempotencyKey replay = IdempotencyKey.of("TRD-1", "MSG-1", SCHEMA, EventType.REPLAY);

        assertThat(replay.toStorageKey()).isNotEqualTo(original.toStorageKey());
    }

    @Test
    @DisplayName("null components are rejected")
    void nullComponents_rejected() {
        assertThatThrownBy(() -> IdempotencyKey.of(null, "MSG-1", SCHEMA, EventType.NEW_TRADE))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("tradeId");
        assertThatThrownBy(() -> IdempotencyKey.of("TRD-1", null, SCHEMA, EventType.NEW_TRADE))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("messageId");
        assertThatThrownBy(() -> IdempotencyKey.of("TRD-1", "MSG-1", null, EventType.NEW_TRADE))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("schemaVersion");
        assertThatThrownBy(() -> IdempotencyKey.of("TRD-1", "MSG-1", SCHEMA, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("eventType");
    }
}
