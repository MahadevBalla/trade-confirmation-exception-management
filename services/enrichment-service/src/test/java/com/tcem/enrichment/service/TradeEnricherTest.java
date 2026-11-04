package com.tcem.enrichment.service;

import com.tcem.common.constants.EventType;
import com.tcem.common.constants.ProductType;
import com.tcem.common.model.message.MessageHeader;
import com.tcem.common.model.message.TradeEvent;
import com.tcem.common.model.trade.FxOptionEconomics;
import com.tcem.common.model.trade.NonEconomicTerms;
import com.tcem.common.model.trade.Party;
import com.tcem.common.model.trade.Trade;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "couchdb.auto-create-databases=false"
})
class TradeEnricherTest {
    @Autowired private TradeEnricher enricher;

    @Test
    void enrichesKnownReferencesWithoutChangingInboundIdentityOrEconomics() {
        TradeEvent event = event("PTY-CP-101", "EUR/USD", "FX_OPTION_STANDARD");
        MessageHeader originalHeader = event.getMessageHeader();
        BigDecimal originalStrike = event.getTrade().getFxOptionEconomics().getStrike();

        TradeEvent result = enricher.enrich(event);

        Party counterparty = result.getTrade().getParties().get(1);
        assertThat(result.getMessageHeader()).isSameAs(originalHeader);
        assertThat(result.getTrade().getFxOptionEconomics().getStrike()).isEqualByComparingTo(originalStrike);
        assertThat(counterparty.getLegalEntityName()).isEqualTo("Meridian Capital Partners Ltd");
        assertThat(counterparty.getLei()).isEqualTo("213800B1DYX92Z5P4471");
        assertThat(counterparty.getCountry()).isEqualTo("UK");
        assertThat(result.getTrade().getFxOptionEconomics().getInternalPricingRef()).isEqualTo("FXOPT-EURUSD-V1");
        assertThat(result.getTrade().getEnrichmentMetadata().getResolvedTemplateId()).isEqualTo("TMPL-FX-OPT-001");
        assertThat(result.getTrade().getEnrichmentMetadata().getCounterpartySTPEligible()).isTrue();
        assertThat(result.getTrade().getEnrichmentMetadata().getProductSTPEligible()).isTrue();
        assertThat(result.getTrade().getEnrichmentMetadata().getFieldProvenance())
                .containsKeys("resolvedTemplateId", "fxOptionEconomics.internalPricingRef", "productSTPEligible");
    }

    @Test
    void leavesMissingReferenceFieldsUnresolvedForDownstreamValidation() {
        TradeEvent result = enricher.enrich(event("PTY-CP-UNKNOWN-999", "CAD/USD", "FX_OPTION_NONEXISTENT_TEMPLATE"));

        Party unknown = result.getTrade().getParties().get(1);
        assertThat(unknown.getLegalEntityName()).isNull();
        assertThat(result.getTrade().getFxOptionEconomics().getInternalPricingRef()).isNull();
        assertThat(result.getTrade().getEnrichmentMetadata().getResolvedTemplateId()).isNull();
        assertThat(result.getTrade().getEnrichmentMetadata().getCounterpartySTPEligible()).isNull();
        assertThat(result.getTrade().getEnrichmentMetadata().getProductSTPEligible()).isNull();
        assertThat(result.getTrade().getEnrichmentMetadata().getEnrichmentNotes()).contains("PTY-CP-UNKNOWN-999", "CAD/USD", "FX_OPTION_NONEXISTENT_TEMPLATE");
    }

    @Test
    void exposesFrontendEligibilityForNonStpCounterparty() {
        TradeEvent result = enricher.enrich(event("PTY-CP-103", "GBP/USD", "FX_OPTION_STANDARD"));
        assertThat(result.getTrade().getEnrichmentMetadata().getCounterpartySTPEligible()).isFalse();
        assertThat(result.getTrade().getEnrichmentMetadata().getProductSTPEligible()).isTrue();
    }

    @Test
    void exposesExplicitlyIneligibleCounterpartyProductConfiguration() {
        TradeEvent result = enricher.enrich(event("PTY-CP-104", "USD/JPY", "FX_OPTION_STANDARD"));
        assertThat(result.getTrade().getEnrichmentMetadata().getCounterpartySTPEligible()).isTrue();
        assertThat(result.getTrade().getEnrichmentMetadata().getProductSTPEligible()).isFalse();
    }

    @Test
    void productEligibilityDoesNotDependOnInstrumentResolution() {
        TradeEvent result = enricher.enrich(event("PTY-CP-101", "CAD/USD", "FX_OPTION_STANDARD"));
        assertThat(result.getTrade().getFxOptionEconomics().getInternalPricingRef()).isNull();
        assertThat(result.getTrade().getEnrichmentMetadata().getProductSTPEligible()).isTrue();
    }

    @Test
    void leavesProductEligibilityNullWhenNoConfigurationExists() {
        TradeEvent result = enricher.enrich(event("PTY-CP-101", "EUR/USD", "FX_OPTION_STANDARD", ProductType.FX_FORWARD));
        assertThat(result.getTrade().getEnrichmentMetadata().getProductSTPEligible()).isNull();
        assertThat(result.getTrade().getEnrichmentMetadata().getEnrichmentNotes()).contains("product configuration PTY-CP-101:FX_FORWARD");
    }

    private TradeEvent event(String counterpartyId, String underlier, String templateHint) {
        return event(counterpartyId, underlier, templateHint, ProductType.FX_OPTION);
    }

    private TradeEvent event(String counterpartyId, String underlier, String templateHint, ProductType productType) {
        MessageHeader header = new MessageHeader("MSG-1", "CORR-1", EventType.NEW_TRADE,
                "capstone-fpml-json-1.0", Instant.parse("2026-01-01T00:00:00Z"), "ingestion-service", 0);
        FxOptionEconomics economics = new FxOptionEconomics();
        economics.setUnderlier(underlier); economics.setStrike(new BigDecimal("1.085")); economics.setNotionalAmount(BigDecimal.TEN);
        NonEconomicTerms terms = new NonEconomicTerms(); terms.setTemplateHint(templateHint);
        Trade trade = new Trade();
        trade.setTradeId("TRD-1"); trade.setTradeVersion(1); trade.setProductType(productType);
        trade.setFxOptionEconomics(economics); trade.setNonEconomicTerms(terms);
        trade.setParties(List.of(party("BANK", "PTY-BANK-001"), party("COUNTERPARTY", counterpartyId)));
        return new TradeEvent(header, trade);
    }

    private Party party(String role, String id) { Party party = new Party(); party.setPartyRole(role); party.setPartyId(id); return party; }

    @Test
    void tradesWithMissingEconomicsAreEnrichedForStpToReject() {
        for (boolean noBlock : new boolean[] {true, false}) {
            TradeEvent event = event("PTY-CP-101", "EUR/USD", "FX_OPTION_STANDARD");
            if (noBlock) {
                event.getTrade().setFxOptionEconomics(null);
            } else {
                event.getTrade().getFxOptionEconomics().setUnderlier(null);
            }

            TradeEvent enriched = enricher.enrich(event);

            org.junit.jupiter.api.Assertions.assertNotNull(enriched.getTrade().getEnrichmentMetadata());
        }
    }
}
