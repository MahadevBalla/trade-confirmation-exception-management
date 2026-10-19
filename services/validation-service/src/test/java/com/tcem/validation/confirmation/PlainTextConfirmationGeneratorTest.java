package com.tcem.validation.confirmation;

import com.tcem.common.constants.ProductType;
import com.tcem.common.model.trade.FxOptionEconomics;
import com.tcem.common.model.trade.NonEconomicTerms;
import com.tcem.common.model.trade.Party;
import com.tcem.common.model.trade.Trade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PlainTextConfirmationGenerator}.
 *
 * Verifies:
 * - Core trade fields appear in the output
 * - Party information is rendered correctly
 * - FX Option economics are present and formatted
 * - Non-economic terms appear
 * - Template reference is included
 * - Null / missing optional fields fall back to "N/A"
 * - Output is deterministic across multiple calls with the same input
 */
class PlainTextConfirmationGeneratorTest {

    private PlainTextConfirmationGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new PlainTextConfirmationGenerator();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Trade buildFullTrade() {
        Trade trade = new Trade();
        trade.setTradeId("TRD-10001");
        trade.setTradeVersion(1);
        trade.setProductType(ProductType.FX_OPTION);
        trade.setTradeDate("2026-01-15");
        trade.setEffectiveDate("2026-01-17");
        trade.setMaturityDate("2026-04-17");

        Party bank = new Party();
        bank.setPartyRole("BANK");
        bank.setPartyId("BANK-001");
        bank.setLegalEntityName("Global Bank PLC");
        bank.setLei("GBANKPLC0000000001");

        Party cp = new Party();
        cp.setPartyRole("COUNTERPARTY");
        cp.setPartyId("CP-001");
        cp.setLegalEntityName("Alpha Corp");
        cp.setLei("ALPHACORP000000001");

        trade.setParties(List.of(bank, cp));

        FxOptionEconomics eco = new FxOptionEconomics();
        eco.setBuySell("BUY");
        eco.setOptionType("CALL");
        eco.setNotionalAmount(new BigDecimal("1000000"));
        eco.setNotionalCurrency("USD");
        eco.setStrike(new BigDecimal("1.2500"));
        eco.setUnderlier("EUR/USD");
        eco.setPremiumAmount(new BigDecimal("25000"));
        eco.setPremiumCurrency("USD");
        eco.setPremiumPaymentDate("2026-01-17");
        trade.setFxOptionEconomics(eco);

        NonEconomicTerms net = new NonEconomicTerms();
        net.setJurisdiction("ENGLISH");
        net.setGoverningLaw("ENGLISH_LAW");
        net.setConfirmationMethod("SWIFT");
        net.setDocumentationVersion("ISDA_2002");
        trade.setNonEconomicTerms(net);

        return trade;
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Generated body contains trade ID and version")
    void testContainsTradeIdentifiers() {
        Trade trade = buildFullTrade();
        String body = generator.generate(trade, "TMPL-FX-OPT-001", "v1.0");

        assertThat(body)
                .contains("TRD-10001")
                .contains("1");
    }

    @Test
    @DisplayName("Generated body contains trade dates")
    void testContainsTradeDates() {
        Trade trade = buildFullTrade();
        String body = generator.generate(trade, "TMPL-FX-OPT-001", "v1.0");

        assertThat(body)
                .contains("2026-01-15")   // trade date
                .contains("2026-01-17")   // effective date
                .contains("2026-04-17");  // maturity date
    }

    @Test
    @DisplayName("Generated body contains both party names and IDs")
    void testContainsPartyInfo() {
        Trade trade = buildFullTrade();
        String body = generator.generate(trade, "TMPL-FX-OPT-001", "v1.0");

        assertThat(body)
                .contains("Global Bank PLC")
                .contains("BANK-001")
                .contains("Alpha Corp")
                .contains("CP-001");
    }

    @Test
    @DisplayName("Generated body contains FX Option economics")
    void testContainsEconomicFields() {
        Trade trade = buildFullTrade();
        String body = generator.generate(trade, "TMPL-FX-OPT-001", "v1.0");

        assertThat(body)
                .contains("BUY")
                .contains("CALL")
                .contains("1000000")
                .contains("USD")
                .contains("1.2500")
                .contains("EUR/USD")
                .contains("25000")
                .contains("2026-01-17");   // premium payment date
    }

    @Test
    @DisplayName("Generated body contains non-economic terms")
    void testContainsNonEconomicTerms() {
        Trade trade = buildFullTrade();
        String body = generator.generate(trade, "TMPL-FX-OPT-001", "v1.0");

        assertThat(body)
                .contains("ENGLISH")
                .contains("ENGLISH_LAW")
                .contains("SWIFT")
                .contains("ISDA_2002");
    }

    @Test
    @DisplayName("Generated body contains template reference")
    void testContainsTemplateReference() {
        Trade trade = buildFullTrade();
        String body = generator.generate(trade, "TMPL-FX-OPT-001", "v1.0");

        assertThat(body)
                .contains("TMPL-FX-OPT-001")
                .contains("v1.0");
    }

    @Test
    @DisplayName("Null economics block renders N/A placeholder")
    void testNullEconomicsRendersNa() {
        Trade trade = buildFullTrade();
        trade.setFxOptionEconomics(null);

        String body = generator.generate(trade, "TMPL-FX-OPT-001", "v1.0");

        assertThat(body).contains("N/A");
    }

    @Test
    @DisplayName("Null non-economic terms renders N/A placeholder")
    void testNullNonEconomicTermsRendersNa() {
        Trade trade = buildFullTrade();
        trade.setNonEconomicTerms(null);

        String body = generator.generate(trade, "TMPL-FX-OPT-001", "v1.0");

        assertThat(body).contains("N/A");
    }

    @Test
    @DisplayName("Null template ID and version render as N/A")
    void testNullTemplateRendersNa() {
        Trade trade = buildFullTrade();
        String body = generator.generate(trade, null, null);

        assertThat(body).contains("N/A");
    }

    @Test
    @DisplayName("Output is deterministic: same trade produces identical body on repeated calls")
    void testDeterministicOutput() {
        Trade trade = buildFullTrade();
        String body1 = generator.generate(trade, "TMPL-FX-OPT-001", "v1.0");
        String body2 = generator.generate(trade, "TMPL-FX-OPT-001", "v1.0");

        assertThat(body1).isEqualTo(body2);
    }

    @Test
    @DisplayName("Body contains structural section headers")
    void testSectionHeaders() {
        Trade trade = buildFullTrade();
        String body = generator.generate(trade, "TMPL-FX-OPT-001", "v1.0");

        assertThat(body)
                .contains("TRADE DETAILS")
                .contains("PARTIES")
                .contains("FX OPTION ECONOMICS")
                .contains("NON-ECONOMIC TERMS")
                .contains("TEMPLATE REFERENCE");
    }

    @Test
    @DisplayName("Null parties list renders N/A placeholder")
    void testNullPartiesRendersNa() {
        Trade trade = buildFullTrade();
        trade.setParties(null);

        String body = generator.generate(trade, "TMPL-FX-OPT-001", "v1.0");

        assertThat(body).contains("N/A");
    }
}

