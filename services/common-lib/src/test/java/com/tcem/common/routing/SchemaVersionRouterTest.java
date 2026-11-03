package com.tcem.common.routing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.common.TestTrades;
import com.tcem.common.constants.EventType;
import com.tcem.common.model.message.SchemaVersions;
import com.tcem.common.model.message.TradeEvent;
import com.tcem.common.routing.SchemaVersionRouter.UnsupportedSchemaVersionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaVersionRouterTest {

    private final ObjectMapper mapper = TestTrades.objectMapper();
    private final SchemaVersionRouter router = new SchemaVersionRouter(mapper);

    private String json(String schemaVersion) throws Exception {
        return mapper.writeValueAsString(TestTrades.event(TestTrades.fxOption(), schemaVersion));
    }

    @Test
    @DisplayName("v1.0 payload is deserialised into the canonical TradeEvent")
    void v1_0_routed() throws Exception {
        TradeEvent event = router.route(json(SchemaVersions.V1_0));

        assertThat(event.getMessageHeader().getMessageId()).isEqualTo("MSG-1");
        assertThat(event.getMessageHeader().getEventType()).isEqualTo(EventType.NEW_TRADE);
        assertThat(event.getMessageHeader().getSchemaVersion()).isEqualTo(SchemaVersions.V1_0);
        assertThat(event.getTrade().getTradeId()).isEqualTo("TRD-10001");
        assertThat(event.getTrade().getParties()).hasSize(2);
        assertThat(event.getTrade().getFxOptionEconomics().getStrike()).isEqualByComparingTo("1.085");
    }

    @Test
    @DisplayName("v1.1 (reserved) is currently treated as equivalent to v1.0")
    void v1_1_routed() throws Exception {
        TradeEvent event = router.route(json(SchemaVersions.V1_1));

        assertThat(event.getTrade().getTradeId()).isEqualTo("TRD-10001");
    }

    @Test
    @DisplayName("missing schemaVersion is treated as v1.0 for backward compatibility")
    void missingSchemaVersion_treatedAsV1_0() throws Exception {
        TradeEvent event = router.route(json(null));

        assertThat(event.getTrade().getTradeId()).isEqualTo("TRD-10001");
        assertThat(event.getMessageHeader().getSchemaVersion()).isNull();
    }

    @Test
    @DisplayName("missing messageHeader is treated as v1.0 (header stays null for the caller to reject)")
    void missingHeader_treatedAsV1_0() throws Exception {
        TradeEvent withoutHeader = new TradeEvent();
        withoutHeader.setTrade(TestTrades.fxOption());

        TradeEvent event = router.route(mapper.writeValueAsString(withoutHeader));

        assertThat(event.getMessageHeader()).isNull();
        assertThat(event.getTrade().getTradeId()).isEqualTo("TRD-10001");
    }

    @Test
    @DisplayName("unknown schemaVersion throws UnsupportedSchemaVersionException naming the version")
    void unknownVersion_throws() throws Exception {
        String payload = json("capstone-fpml-json-9.9");

        assertThatThrownBy(() -> router.route(payload))
                .isInstanceOf(UnsupportedSchemaVersionException.class)
                .hasMessageContaining("capstone-fpml-json-9.9");
    }

    @Test
    @DisplayName("malformed JSON surfaces as an IOException")
    void malformedJson_throwsIOException() {
        assertThatThrownBy(() -> router.route("{ not json"))
                .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("String and byte[] overloads behave identically")
    void overloadsAgree() throws Exception {
        String payload = json(SchemaVersions.V1_0);

        TradeEvent fromString = router.route(payload);
        TradeEvent fromBytes = router.route(payload.getBytes(StandardCharsets.UTF_8));

        assertThat(fromBytes).isEqualTo(fromString);
    }
}
