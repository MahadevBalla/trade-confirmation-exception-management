package com.tcem.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tcem.common.constants.EventType;
import com.tcem.common.constants.ProductType;
import com.tcem.common.model.message.MessageHeader;
import com.tcem.common.model.message.SchemaVersions;
import com.tcem.common.model.message.TradeEvent;
import com.tcem.common.model.trade.FxOptionEconomics;
import com.tcem.common.model.trade.NonEconomicTerms;
import com.tcem.common.model.trade.Party;
import com.tcem.common.model.trade.Trade;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Shared builders for common-lib tests. */
public final class TestTrades {

    private TestTrades() {}

    public static ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    /** A fully populated FX option trade: every confirmable field has a value. */
    public static Trade fxOption() {
        FxOptionEconomics economics = new FxOptionEconomics();
        economics.setNotionalAmount(new BigDecimal("1000000"));
        economics.setNotionalCurrency("USD");
        economics.setBuySell("BUY");
        economics.setOptionType("CALL");
        economics.setStrike(new BigDecimal("1.085"));
        economics.setUnderlier("EUR/USD");
        economics.setPremiumAmount(new BigDecimal("12000"));
        economics.setPremiumCurrency("USD");
        economics.setPremiumPaymentDate("2026-01-17");
        economics.setInternalPricingRef("PRC-1");   // NOT confirmable
        economics.setHedgeId("HDG-1");              // NOT confirmable

        NonEconomicTerms terms = new NonEconomicTerms();
        terms.setJurisdiction("UK");
        terms.setConfirmationMethod("ELECTRONIC");
        terms.setGoverningLaw("ENGLISH");
        terms.setDocumentationVersion("2006");
        terms.setInternalRoutingCode("RT-1");       // NOT confirmable

        Trade trade = new Trade();
        trade.setTradeId("TRD-10001");
        trade.setTradeVersion(1);
        trade.setProductType(ProductType.FX_OPTION);
        trade.setTradeDate("2026-01-15");
        trade.setEffectiveDate("2026-01-17");
        trade.setMaturityDate("2026-04-17");
        trade.setParties(new ArrayList<>(List.of(
                party("BANK", "PTY-BANK-001"),
                party("COUNTERPARTY", "PTY-CP-101"))));
        trade.setFxOptionEconomics(economics);
        trade.setNonEconomicTerms(terms);
        return trade;
    }

    public static Party party(String role, String id) {
        Party party = new Party();
        party.setPartyRole(role);
        party.setPartyId(id);
        return party;
    }

    public static Party partyWithRole(Trade trade, String role) {
        return trade.getParties().stream()
                .filter(p -> role.equals(p.getPartyRole()))
                .findFirst()
                .orElseThrow();
    }

    public static MessageHeader header(String messageId, EventType eventType, String schemaVersion) {
        MessageHeader header = new MessageHeader();
        header.setMessageId(messageId);
        header.setCorrelationId("CORR-1");
        header.setEventType(eventType);
        header.setSchemaVersion(schemaVersion);
        header.setCreatedTimestamp(Instant.parse("2026-01-15T10:00:00Z"));
        header.setSourceService("test");
        return header;
    }

    public static TradeEvent event(Trade trade, String schemaVersion) {
        TradeEvent event = new TradeEvent();
        event.setMessageHeader(header("MSG-1", EventType.NEW_TRADE, schemaVersion));
        event.setTrade(trade);
        return event;
    }

    public static TradeEvent currentEvent(Trade trade) {
        return event(trade, SchemaVersions.CURRENT);
    }
}
