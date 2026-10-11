package com.tcem.common.model.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tcem.common.model.trade.Trade;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Top-level Kafka message envelope for all trade lifecycle events.
 * <p>
 * This is the JSON shape serialised to and from every Kafka topic in the
 * trade pipeline (trade.raw → trade.enriched → stp.validated, etc.).
 * The {@link MessageHeader} governs routing and idempotency; the
 * {@link Trade} block carries the canonical trade state at the time of
 * the event.
 * </p>
 *
 * <h3>Example JSON (spec §10 canonical message)</h3>
 * <pre>{@code
 * {
 *   "messageHeader": {
 *     "messageId": "MSG-000001",
 *     "correlationId": "CORR-10001",
 *     "eventType": "NEW_TRADE",
 *     "schemaVersion": "capstone-fpml-json-1.0",
 *     "createdTimestamp": "2026-01-15T10:30:00Z",
 *     "sourceService": "seed-loader",
 *     "replayCount": 0
 *   },
 *   "trade": {
 *     "tradeId": "TRD-10001",
 *     "sourceSystemTradeId": "SRC-77881",
 *     "tradeVersion": 1,
 *     "productType": "FX_OPTION",
 *     "tradeDate": "2026-01-15",
 *     "effectiveDate": "2026-01-17",
 *     "maturityDate": "2026-04-17",
 *     "parties": [
 *       { "partyRole": "BANK",         "partyId": "PTY-BANK-001" },
 *       { "partyRole": "COUNTERPARTY", "partyId": "PTY-CP-101" }
 *     ],
 *     "fxOptionEconomics": {
 *       "notionalAmount": 1000000,
 *       "notionalCurrency": "USD",
 *       "buySell": "BUY",
 *       "optionType": "CALL",
 *       "strike": 1.085,
 *       "underlier": "EUR/USD",
 *       "premiumAmount": 12000,
 *       "premiumCurrency": "USD"
 *     },
 *     "nonEconomicTerms": {
 *       "jurisdiction": "GENERIC",
 *       "confirmationMethod": "ELECTRONIC",
 *       "templateHint": "FX_OPTION_STANDARD"
 *     }
 *   }
 * }
 * }</pre>
 *
 * <h3>Versioning</h3>
 * The {@link com.tcem.common.routing.SchemaVersionRouter} inspects
 * {@code messageHeader.schemaVersion} and migrates legacy payloads to the
 * current canonical model before any downstream service processes them.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TradeEvent {

    /** Envelope header – routing, idempotency, versioning. */
    private MessageHeader messageHeader;

    /**
     * Canonical trade state at the time this event was produced.
     * For a {@code NEW_TRADE} this is the initial booking.
     * For an {@code AMEND_TRADE} this is the full amended state
     * (not a diff – always send the full trade).
     */
    private Trade trade;

    public MessageHeader getMessageHeader() {
        return messageHeader;
    }

    public void setMessageHeader(MessageHeader messageHeader) {
        this.messageHeader = messageHeader;
    }

    public Trade getTrade() {
        return trade;
    }

    public void setTrade(Trade trade) {
        this.trade = trade;
    }
}
