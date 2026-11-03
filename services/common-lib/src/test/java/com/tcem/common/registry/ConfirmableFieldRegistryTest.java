package com.tcem.common.registry;

import com.tcem.common.constants.ProductType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfirmableFieldRegistryTest {

    @Test
    @DisplayName("FX_OPTION: economics, dates, parties and confirmable terms are registered")
    void fxOption_containsConfirmableFields() {
        Set<String> fields = ConfirmableFieldRegistry.getConfirmableFields(ProductType.FX_OPTION);

        assertThat(fields).contains(
                "tradeId", "tradeDate", "effectiveDate", "maturityDate",
                "parties[COUNTERPARTY].partyId", "parties[BANK].partyId",
                "fxOptionEconomics.notionalAmount", "fxOptionEconomics.notionalCurrency",
                "fxOptionEconomics.buySell", "fxOptionEconomics.optionType",
                "fxOptionEconomics.strike", "fxOptionEconomics.underlier",
                "fxOptionEconomics.premiumAmount", "fxOptionEconomics.premiumCurrency",
                "fxOptionEconomics.premiumPaymentDate",
                "nonEconomicTerms.jurisdiction", "nonEconomicTerms.confirmationMethod",
                "nonEconomicTerms.governingLaw", "nonEconomicTerms.documentationVersion");
    }

    @Test
    @DisplayName("FX_OPTION: internal / processing-only fields are NOT confirmable")
    void fxOption_excludesInternalFields() {
        Set<String> fields = ConfirmableFieldRegistry.getConfirmableFields(ProductType.FX_OPTION);

        assertThat(fields).doesNotContain(
                "fxOptionEconomics.internalPricingRef", "fxOptionEconomics.hedgeId",
                "fxOptionEconomics.settlementType",
                "nonEconomicTerms.internalRoutingCode", "nonEconomicTerms.businessUnit",
                "nonEconomicTerms.templateHint",
                "tradeVersion", "sourceSystemTradeId", "enrichmentMetadata");
    }

    @Test
    @DisplayName("FX_FORWARD placeholder entry exists and includes both party paths")
    void fxForward_registered() {
        Set<String> fields = ConfirmableFieldRegistry.getConfirmableFields(ProductType.FX_FORWARD);

        assertThat(fields).contains("tradeId", "parties[COUNTERPARTY].partyId", "parties[BANK].partyId");
    }

    @Test
    @DisplayName("products without a registry entry are rejected with a helpful message")
    void unregisteredProducts_rejected() {
        for (ProductType product : new ProductType[]{ProductType.EQUITY_SWAP, ProductType.IRS, ProductType.UNKNOWN}) {
            assertThatThrownBy(() -> ConfirmableFieldRegistry.getConfirmableFields(product))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(product.name())
                    .hasMessageContaining("ConfirmableFieldRegistry");
        }
    }

    @Test
    @DisplayName("isConfirmable reflects registry membership and is false for unregistered products")
    void isConfirmable() {
        assertThat(ConfirmableFieldRegistry.isConfirmable(ProductType.FX_OPTION, "fxOptionEconomics.strike")).isTrue();
        assertThat(ConfirmableFieldRegistry.isConfirmable(ProductType.FX_OPTION, "fxOptionEconomics.hedgeId")).isFalse();
        assertThat(ConfirmableFieldRegistry.isConfirmable(ProductType.EQUITY_SWAP, "tradeId")).isFalse();
    }

    @Test
    @DisplayName("returned field sets are immutable")
    void fieldSets_areImmutable() {
        Set<String> fields = ConfirmableFieldRegistry.getConfirmableFields(ProductType.FX_OPTION);

        assertThatThrownBy(() -> fields.add("hacked")).isInstanceOf(UnsupportedOperationException.class);
    }
}
