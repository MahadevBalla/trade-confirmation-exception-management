package com.tcem.common.registry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcem.common.TestTrades;
import com.tcem.common.constants.ProductType;
import com.tcem.common.model.trade.Trade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfirmationFingerprintTest {

    private final ObjectMapper mapper = TestTrades.objectMapper();

    private String fp(Trade trade) {
        return ConfirmationFingerprint.compute(trade, mapper);
    }

    // ------------------------------------------------------------------
    // Basic properties
    // ------------------------------------------------------------------

    @Test
    @DisplayName("fingerprint is a 64-char lowercase hex SHA-256 and deterministic")
    void deterministicSha256Hex() {
        String first = fp(TestTrades.fxOption());
        String second = fp(TestTrades.fxOption());

        assertThat(first).matches("[0-9a-f]{64}");
        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("null productType is rejected")
    void nullProductType_rejected() {
        Trade trade = TestTrades.fxOption();
        trade.setProductType(null);

        assertThatThrownBy(() -> fp(trade)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null productType");
    }

    @Test
    @DisplayName("product type without registry entry is rejected")
    void unregisteredProduct_rejected() {
        Trade trade = TestTrades.fxOption();
        trade.setProductType(ProductType.EQUITY_SWAP);

        assertThatThrownBy(() -> fp(trade)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EQUITY_SWAP");
    }

    // ------------------------------------------------------------------
    // Audit C1-01 regression: party paths must actually be resolved
    // ------------------------------------------------------------------

    @Test
    @DisplayName("counterparty change (novation) changes the fingerprint")
    void counterpartyChange_changesFingerprint() {
        Trade before = TestTrades.fxOption();
        Trade after = TestTrades.fxOption();
        TestTrades.partyWithRole(after, "COUNTERPARTY").setPartyId("PTY-CP-999");

        assertThat(fp(after)).isNotEqualTo(fp(before));
        assertThat(ConfirmationFingerprint.hasConfirmableChanges(before, after, mapper)).isTrue();
    }

    @Test
    @DisplayName("bank party change changes the fingerprint")
    void bankChange_changesFingerprint() {
        Trade before = TestTrades.fxOption();
        Trade after = TestTrades.fxOption();
        TestTrades.partyWithRole(after, "BANK").setPartyId("PTY-BANK-002");

        assertThat(fp(after)).isNotEqualTo(fp(before));
    }

    @Test
    @DisplayName("order of the parties list does not affect the fingerprint")
    void partyOrder_isIrrelevant() {
        Trade before = TestTrades.fxOption();
        Trade after = TestTrades.fxOption();
        Collections.reverse(after.getParties());

        assertThat(fp(after)).isEqualTo(fp(before));
    }

    @Test
    @DisplayName("missing counterparty does not throw and differs from a trade that has one")
    void missingParty_isNullMarker() {
        Trade with = TestTrades.fxOption();
        Trade without = TestTrades.fxOption();
        without.getParties().removeIf(p -> "COUNTERPARTY".equals(p.getPartyRole()));

        assertThat(fp(without)).isNotEqualTo(fp(with));
    }

    @Test
    @DisplayName("null parties list does not throw")
    void nullParties_doesNotThrow() {
        Trade trade = TestTrades.fxOption();
        trade.setParties(null);

        assertThat(fp(trade)).matches("[0-9a-f]{64}");
    }

    // ------------------------------------------------------------------
    // Confirmable vs non-confirmable fields
    // ------------------------------------------------------------------

    @Test
    @DisplayName("economic change (strike) changes the fingerprint")
    void strikeChange_changesFingerprint() {
        Trade before = TestTrades.fxOption();
        Trade after = TestTrades.fxOption();
        after.getFxOptionEconomics().setStrike(new BigDecimal("1.090"));

        assertThat(fp(after)).isNotEqualTo(fp(before));
    }

    @Test
    @DisplayName("non-confirmable changes leave the fingerprint unchanged")
    void nonConfirmableChanges_keepFingerprint() {
        Trade before = TestTrades.fxOption();
        Trade after = TestTrades.fxOption();
        after.setTradeVersion(2);
        after.setSourceSystemTradeId("SRC-NEW");
        after.getFxOptionEconomics().setInternalPricingRef("PRC-2");
        after.getFxOptionEconomics().setHedgeId("HDG-2");
        after.getNonEconomicTerms().setInternalRoutingCode("RT-2");
        TestTrades.partyWithRole(after, "COUNTERPARTY").setLegalEntityName("Enriched Name Ltd");

        assertThat(fp(after)).isEqualTo(fp(before));
        assertThat(ConfirmationFingerprint.hasConfirmableChanges(before, after, mapper)).isFalse();
    }

    @Test
    @DisplayName("every FX_OPTION and FX_FORWARD registry path resolves to a value on a fully populated trade")
    void everyRegistryPath_resolves() {
        for (ProductType product : new ProductType[]{ProductType.FX_OPTION, ProductType.FX_FORWARD}) {
            Trade trade = TestTrades.fxOption();
            trade.setProductType(product);
            JsonNode tree = mapper.valueToTree(trade);

            for (String path : ConfirmableFieldRegistry.getConfirmableFields(product)) {
                assertThat(ConfirmationFingerprint.extractValue(tree, path))
                        .as("%s path '%s' must resolve (otherwise changes to it are never detected)", product, path)
                        .isNotNull();
            }
        }
    }

    // ------------------------------------------------------------------
    // extractValue path syntax
    // ------------------------------------------------------------------

    @Test
    @DisplayName("extractValue: plain dot path")
    void extractValue_dotPath() {
        JsonNode tree = mapper.valueToTree(TestTrades.fxOption());

        assertThat(ConfirmationFingerprint.extractValue(tree, "fxOptionEconomics.optionType")).isEqualTo("CALL");
        assertThat(ConfirmationFingerprint.extractValue(tree, "tradeId")).isEqualTo("TRD-10001");
    }

    @Test
    @DisplayName("extractValue: shorthand and explicit array selectors")
    void extractValue_arraySelectors() {
        JsonNode tree = mapper.valueToTree(TestTrades.fxOption());

        assertThat(ConfirmationFingerprint.extractValue(tree, "parties[COUNTERPARTY].partyId")).isEqualTo("PTY-CP-101");
        assertThat(ConfirmationFingerprint.extractValue(tree, "parties[BANK].partyId")).isEqualTo("PTY-BANK-001");
        assertThat(ConfirmationFingerprint.extractValue(tree, "parties[partyRole=COUNTERPARTY].partyId")).isEqualTo("PTY-CP-101");
        assertThat(ConfirmationFingerprint.extractValue(tree, "parties[partyId=PTY-BANK-001].partyRole")).isEqualTo("BANK");
    }

    @Test
    @DisplayName("extractValue: unknown path, unknown selector and non-array target return null")
    void extractValue_missingReturnsNull() {
        JsonNode tree = mapper.valueToTree(TestTrades.fxOption());

        assertThat(ConfirmationFingerprint.extractValue(tree, "does.not.exist")).isNull();
        assertThat(ConfirmationFingerprint.extractValue(tree, "parties[BROKER].partyId")).isNull();
        assertThat(ConfirmationFingerprint.extractValue(tree, "tradeId[COUNTERPARTY].partyId")).isNull();
        assertThat(ConfirmationFingerprint.extractValue(tree, "parties[COUNTERPARTY].lei")).isNull();
    }

    @Test
    @DisplayName("extractValue: first match wins when a role appears twice (deterministic)")
    void extractValue_firstMatchWins() {
        Trade trade = TestTrades.fxOption();
        trade.setParties(new ArrayList<>(trade.getParties()));
        trade.getParties().add(TestTrades.party("COUNTERPARTY", "PTY-CP-SECOND"));
        JsonNode tree = mapper.valueToTree(trade);

        assertThat(ConfirmationFingerprint.extractValue(tree, "parties[COUNTERPARTY].partyId")).isEqualTo("PTY-CP-101");
    }
}
