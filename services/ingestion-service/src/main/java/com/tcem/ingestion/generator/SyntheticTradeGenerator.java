package com.tcem.ingestion.generator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.common.constants.EventType;
import com.tcem.common.constants.ProductType;
import com.tcem.common.model.message.SchemaVersions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Generates realistic synthetic FX Option post-trade messages for testing, demoing, and seeding (FR1).
 */
@Component
public class SyntheticTradeGenerator {

    private static final Logger log = LoggerFactory.getLogger(SyntheticTradeGenerator.class);

    private static final String[] UNDERLIERS = {"EUR/USD", "GBP/USD", "USD/JPY", "USD/CHF", "AUD/USD"};
    private static final double[] BASE_STRIKES = {1.0850, 1.2720, 154.50, 0.8850, 0.6550};
    private static final String[] VALID_COUNTERPARTIES = {"PTY-CP-101", "PTY-CP-102", "PTY-CP-103", "PTY-CP-104"};
    private static final String BANK_PARTY_ID = "PTY-BANK-001";

    private final ObjectMapper objectMapper;
    private final Random random = new Random(42);

    public SyntheticTradeGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Generates a batch of N synthetic trade JSON payloads across multiple test scenarios.
     *
     * @param count total number of trades to generate
     * @return list of serialized JSON strings ready to publish to trade.raw
     */
    public List<String> generateBatch(int count) {
        List<String> batch = new ArrayList<>();
        int validTarget = (int) (count * 0.70); // 70% happy path
        int edgeCases = count - validTarget;

        TradeScenarioType[] edgeScenarios = {
                TradeScenarioType.MISSING_ECONOMIC_STRIKE,
                TradeScenarioType.MISSING_ECONOMIC_NOTIONAL,
                TradeScenarioType.MISSING_ECONOMIC_PREMIUM,
                TradeScenarioType.MISSING_REFDATA_COUNTERPARTY,
                TradeScenarioType.TEMPLATE_ISSUE_MISSING,
                TradeScenarioType.BUSINESS_RULE_NON_STP,
                TradeScenarioType.DUPLICATE_TRADE,
                TradeScenarioType.MALFORMED_JSON,
                TradeScenarioType.AMEND_TRADE
        };

        for (int i = 1; i <= validTarget; i++) {
            batch.add(generateTradeJson(TradeScenarioType.VALID_STANDARD_STP, i));
        }

        for (int i = 1; i <= edgeCases; i++) {
            TradeScenarioType scenario = edgeScenarios[(i - 1) % edgeScenarios.length];
            batch.add(generateTradeJson(scenario, validTarget + i));
        }

        log.info("Generated batch of {} synthetic trades ({} valid STP, {} edge scenarios)",
                count, validTarget, edgeCases);
        return batch;
    }

    /**
     * Generates a single trade payload JSON for the requested scenario.
     */
    public String generateTradeJson(TradeScenarioType scenario, int sequenceNumber) {
        if (scenario == TradeScenarioType.MALFORMED_JSON) {
            return String.format("{\"messageHeader\":{\"messageId\":\"MSG-MALFORMED-%06d\"},\"trade\":{\"tradeId\":\"TRD-BAD-%06d\",",
                    sequenceNumber, sequenceNumber);
        }

        Map<String, Object> payloadMap = generateTradeMap(scenario, sequenceNumber);
        try {
            return objectMapper.writeValueAsString(payloadMap);
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize TradeEvent for sequence {}", sequenceNumber, ex);
            throw new RuntimeException("Serialization error generating trade", ex);
        }
    }

    /**
     * Generates a Map representation of the trade matching the canonical schema.
     */
    public Map<String, Object> generateTradeMap(TradeScenarioType scenario, int sequenceNumber) {
        String tradeId = String.format("TRD-%06d", sequenceNumber);
        String messageId = String.format("MSG-%06d", sequenceNumber);
        String correlationId = String.format("CORR-%06d", sequenceNumber);

        if (scenario == TradeScenarioType.DUPLICATE_TRADE) {
            tradeId = "TRD-DUP-0001";
            messageId = "MSG-DUP-0001";
            correlationId = "CORR-DUP-0001";
        }

        int underlierIndex = (sequenceNumber - 1) % UNDERLIERS.length;
        String underlier = UNDERLIERS[underlierIndex];
        double strike = BASE_STRIKES[underlierIndex];
        String optionType = (sequenceNumber % 2 == 0) ? "CALL" : "PUT";
        String buySell = (sequenceNumber % 3 == 0) ? "SELL" : "BUY";
        double notional = 1_000_000 + ((sequenceNumber % 10) * 250_000);
        double premium = notional * 0.012;

        String counterpartyId = VALID_COUNTERPARTIES[(sequenceNumber - 1) % VALID_COUNTERPARTIES.length];
        String templateHint = "FX_OPTION_STANDARD";
        String jurisdiction = "GENERIC";
        EventType eventType = EventType.NEW_TRADE;
        int tradeVersion = 1;

        LocalDate tradeDate = LocalDate.now().minusDays(1);
        LocalDate effectiveDate = tradeDate.plusDays(2);
        LocalDate maturityDate = tradeDate.plusMonths(3);

        // Apply scenario-specific modifications
        switch (scenario) {
            case MISSING_ECONOMIC_STRIKE -> strike = 0.0;
            case MISSING_ECONOMIC_NOTIONAL -> notional = 0.0;
            case MISSING_ECONOMIC_PREMIUM -> premium = 0.0;
            case MISSING_REFDATA_COUNTERPARTY -> counterpartyId = "PTY-CP-UNKNOWN-999";
            case TEMPLATE_ISSUE_MISSING -> templateHint = "FX_OPTION_NONEXISTENT_TEMPLATE";
            case BUSINESS_RULE_NON_STP -> jurisdiction = "SANCTIONED_ZONE";
            case AMEND_TRADE -> {
                eventType = EventType.AMEND_TRADE;
                tradeVersion = 2;
                notional = notional * 1.1; // 10% notional increase on amendment
            }
            default -> {}
        }

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("messageId", messageId);
        header.put("correlationId", correlationId);
        header.put("eventType", eventType.name());
        header.put("schemaVersion", SchemaVersions.V1_0);
        header.put("createdTimestamp", Instant.now().toString());
        header.put("sourceService", "ingestion-service-generator");
        header.put("replayCount", 0);

        Map<String, Object> trade = new LinkedHashMap<>();
        trade.put("tradeId", tradeId);
        trade.put("sourceSystemTradeId", String.format("SRC-%05d", sequenceNumber));
        trade.put("tradeVersion", tradeVersion);
        trade.put("productType", ProductType.FX_OPTION.name());
        trade.put("tradeDate", tradeDate.toString());
        trade.put("effectiveDate", effectiveDate.toString());
        trade.put("maturityDate", maturityDate.toString());

        List<Map<String, String>> parties = List.of(
                Map.of("partyRole", "BANK", "partyId", BANK_PARTY_ID, "partyName", "Global Investment Bank"),
                Map.of("partyRole", "COUNTERPARTY", "partyId", counterpartyId, "partyName", "Client Financial Entity")
        );
        trade.put("parties", parties);

        Map<String, Object> economics = new LinkedHashMap<>();
        if (notional > 0) {
            economics.put("notionalAmount", notional);
        }
        economics.put("notionalCurrency", "USD");
        economics.put("buySell", buySell);
        economics.put("optionType", optionType);
        if (strike > 0) {
            economics.put("strike", strike);
        }
        economics.put("underlier", underlier);
        if (premium > 0) {
            economics.put("premiumAmount", premium);
            economics.put("premiumPaymentDate", effectiveDate.toString());
        }
        economics.put("premiumCurrency", "USD");
        economics.put("settlementType", "CASH");
        trade.put("fxOptionEconomics", economics);

        Map<String, Object> nonEconomic = new LinkedHashMap<>();
        nonEconomic.put("jurisdiction", jurisdiction);
        nonEconomic.put("confirmationMethod", "ELECTRONIC");
        nonEconomic.put("templateHint", templateHint);
        nonEconomic.put("governingLaw", "NEW_YORK");
        trade.put("nonEconomicTerms", nonEconomic);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("messageHeader", header);
        root.put("trade", trade);

        return root;
    }
}
