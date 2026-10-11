package com.tcem.ingestion.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.common.constants.EventType;
import com.tcem.common.constants.ProductType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SyntheticTradeGeneratorTest {

    private SyntheticTradeGenerator generator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        generator = new SyntheticTradeGenerator(objectMapper);
    }

    @Test
    void generateTradeMap_validStandardStp_createsCompleteTrade() {
        Map<String, Object> map = generator.generateTradeMap(TradeScenarioType.VALID_STANDARD_STP, 1);

        assertThat(map).isNotNull();
        Map<String, Object> header = (Map<String, Object>) map.get("messageHeader");
        assertThat(header).isNotNull();
        assertThat(header.get("eventType")).isEqualTo(EventType.NEW_TRADE.name());
        assertThat(header.get("messageId")).isEqualTo("MSG-000001");

        Map<String, Object> trade = (Map<String, Object>) map.get("trade");
        assertThat(trade).isNotNull();
        assertThat(trade.get("tradeId")).isEqualTo("TRD-000001");
        assertThat(trade.get("productType")).isEqualTo(ProductType.FX_OPTION.name());

        List<Map<String, String>> parties = (List<Map<String, String>>) trade.get("parties");
        assertThat(parties).hasSize(2);

        Map<String, Object> economics = (Map<String, Object>) trade.get("fxOptionEconomics");
        assertThat(economics).isNotNull();
        assertThat(economics.get("strike")).isNotNull();
        assertThat(economics.get("notionalAmount")).isNotNull();
        assertThat(economics.get("premiumAmount")).isNotNull();
    }

    @Test
    void generateTradeMap_missingStrike_producesNullStrike() {
        Map<String, Object> map = generator.generateTradeMap(TradeScenarioType.MISSING_ECONOMIC_STRIKE, 2);
        Map<String, Object> trade = (Map<String, Object>) map.get("trade");
        Map<String, Object> economics = (Map<String, Object>) trade.get("fxOptionEconomics");

        assertThat(economics.get("strike")).isNull();
        assertThat(economics.get("notionalAmount")).isNotNull();
    }

    @Test
    void generateTradeMap_missingNotional_producesNullNotional() {
        Map<String, Object> map = generator.generateTradeMap(TradeScenarioType.MISSING_ECONOMIC_NOTIONAL, 3);
        Map<String, Object> trade = (Map<String, Object>) map.get("trade");
        Map<String, Object> economics = (Map<String, Object>) trade.get("fxOptionEconomics");

        assertThat(economics.get("notionalAmount")).isNull();
        assertThat(economics.get("strike")).isNotNull();
    }

    @Test
    void generateTradeMap_unknownCounterparty_setsUnregisteredId() {
        Map<String, Object> map = generator.generateTradeMap(TradeScenarioType.MISSING_REFDATA_COUNTERPARTY, 4);
        Map<String, Object> trade = (Map<String, Object>) map.get("trade");
        List<Map<String, String>> parties = (List<Map<String, String>>) trade.get("parties");

        boolean hasUnknownCp = parties.stream()
                .anyMatch(p -> "PTY-CP-UNKNOWN-999".equals(p.get("partyId")));
        assertThat(hasUnknownCp).isTrue();
    }

    @Test
    void generateTradeJson_malformedJson_producesInvalidJson() {
        String json = generator.generateTradeJson(TradeScenarioType.MALFORMED_JSON, 5);

        assertThat(json).contains("MSG-MALFORMED-000005");
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> objectMapper.readTree(json));
    }

    @Test
    void generateBatch_createsExactRequestedCount() throws Exception {
        List<String> batch = generator.generateBatch(500);

        assertThat(batch).hasSize(500);
        assertThat(batch.get(0)).contains("TRD-000001");

        // Verify that the first item parses as valid JSON
        JsonNode node = objectMapper.readTree(batch.get(0));
        assertThat(node.get("trade").get("tradeId").asText()).isEqualTo("TRD-000001");
    }
}
