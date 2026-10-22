package com.tcem.validation.rules;

import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.constants.ExceptionSubTheme;
import com.tcem.common.constants.ProductType;
import com.tcem.common.model.message.TradeEvent;
import com.tcem.common.model.reference.ReferenceDataEnrichment;
import com.tcem.common.model.trade.FxOptionEconomics;
import com.tcem.common.model.trade.Party;
import com.tcem.common.model.trade.Trade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link StpRulesEngine}.
 * <p>
 * Wires the three real rule implementations directly (no Spring context) to
 * verify orchestration behaviour: all-pass, all-fail with accumulation, partial
 * failures, and the defensive fallback when a rule throws unexpectedly.
 * </p>
 */
@DisplayName("StpRulesEngine")
class StpRulesEngineTest {

    /** Engine under test, wired with all three real rule implementations. */
    private StpRulesEngine engine;

    @BeforeEach
    void setUp() {
        engine = new StpRulesEngine(List.of(
                new EconomicFieldsRule(),
                new BusinessEligibilityRule(),
                new TemplateResolutionRule()
        ));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private TradeEvent fullyValidEvent() {
        FxOptionEconomics eco = new FxOptionEconomics();
        eco.setNotionalAmount(new BigDecimal("1000000"));
        eco.setNotionalCurrency("USD");
        eco.setBuySell("BUY");
        eco.setOptionType("CALL");
        eco.setStrike(new BigDecimal("1.085"));
        eco.setUnderlier("EUR/USD");
        eco.setPremiumAmount(new BigDecimal("12000"));
        eco.setPremiumCurrency("USD");
        eco.setPremiumPaymentDate("2026-01-17");

        Party bank = new Party();
        bank.setPartyRole("BANK");
        bank.setPartyId("PTY-BANK-001");

        Party cp = new Party();
        cp.setPartyRole("COUNTERPARTY");
        cp.setPartyId("PTY-CP-101");

        ReferenceDataEnrichment meta = new ReferenceDataEnrichment();
        meta.setCounterpartySTPEligible(true);
        meta.setProductSTPEligible(true);
        meta.setResolvedTemplateId("TMPL-FX-OPT-001");
        meta.setResolvedTemplateVersion("1.2.0");

        Trade trade = new Trade();
        trade.setTradeId("TRD-10001");
        trade.setProductType(ProductType.FX_OPTION);
        trade.setTradeDate("2026-01-15");
        trade.setEffectiveDate("2026-01-17");
        trade.setMaturityDate("2026-04-17");
        trade.setFxOptionEconomics(eco);
        trade.setParties(List.of(bank, cp));
        trade.setEnrichmentMetadata(meta);

        TradeEvent event = new TradeEvent();
        event.setTrade(trade);
        return event;
    }

    // -----------------------------------------------------------------------
    // Full STP pass
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("returns STP_PASSED when all rules pass on a fully valid trade")
    void evaluate_fullyValid_stpPassed() {
        StpValidationResult result = engine.evaluate(fullyValidEvent());

        assertThat(result.isStpPassed()).isTrue();
        assertThat(result.getFailures()).isEmpty();
        assertThat(result.summarize()).isEqualTo("STP_PASSED");
    }

    // -----------------------------------------------------------------------
    // Full STP fail — all three rules fire failures
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("accumulates failures from all three rules when trade is completely invalid")
    void evaluate_completelyInvalid_accumulatesAllRuleFailures() {
        // Null trade triggers EconomicFieldsRule; because trade is null,
        // BusinessEligibilityRule and TemplateResolutionRule silently skip.
        // Use a different scenario: valid structure but all flags wrong.
        TradeEvent event = fullyValidEvent();
        event.getTrade().getFxOptionEconomics().setNotionalAmount(null);           // EconomicFieldsRule
        event.getTrade().getEnrichmentMetadata().setCounterpartySTPEligible(false);// BusinessEligibilityRule
        event.getTrade().getEnrichmentMetadata().setProductSTPEligible(false);     // BusinessEligibilityRule
        event.getTrade().getEnrichmentMetadata().setResolvedTemplateId(null);      // TemplateResolutionRule

        StpValidationResult result = engine.evaluate(event);

        assertThat(result.isStpPassed()).isFalse();
        // At least: 1 economic + 2 eligibility + 1 template = 4 failures
        assertThat(result.getFailures()).hasSizeGreaterThanOrEqualTo(4);

        boolean hasEconomic = result.getFailures().stream()
                .anyMatch(f -> f.getCategory() == ExceptionCategory.MISSING_ECONOMIC_DATA);
        boolean hasCpEligibility = result.getFailures().stream()
                .anyMatch(f -> f.getSubTheme() == ExceptionSubTheme.COUNTERPARTY_NOT_STP_ELIGIBLE);
        boolean hasProdEligibility = result.getFailures().stream()
                .anyMatch(f -> f.getSubTheme() == ExceptionSubTheme.PRODUCT_NOT_STP_ELIGIBLE);
        boolean hasTemplate = result.getFailures().stream()
                .anyMatch(f -> f.getSubTheme() == ExceptionSubTheme.NO_MATCHING_TEMPLATE);

        assertThat(hasEconomic).isTrue();
        assertThat(hasCpEligibility).isTrue();
        assertThat(hasProdEligibility).isTrue();
        assertThat(hasTemplate).isTrue();
    }

    // -----------------------------------------------------------------------
    // Partial failures (only one rule fires)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("fails only on template rule when economics and eligibility are valid")
    void evaluate_onlyTemplateMissing_singleRuleFails() {
        TradeEvent event = fullyValidEvent();
        event.getTrade().getEnrichmentMetadata().setResolvedTemplateId(null);

        StpValidationResult result = engine.evaluate(event);

        assertThat(result.isStpPassed()).isFalse();
        assertThat(result.getFailures()).hasSize(1);
        assertThat(result.getFailures().get(0).getSubTheme())
                .isEqualTo(ExceptionSubTheme.NO_MATCHING_TEMPLATE);
    }

    @Test
    @DisplayName("fails only on eligibility rule when economics and template are valid")
    void evaluate_onlyEligibilityFails_remainingRulesPass() {
        TradeEvent event = fullyValidEvent();
        event.getTrade().getEnrichmentMetadata().setCounterpartySTPEligible(false);

        StpValidationResult result = engine.evaluate(event);

        assertThat(result.isStpPassed()).isFalse();
        assertThat(result.getFailures()).hasSize(1);
        assertThat(result.getFailures().get(0).getSubTheme())
                .isEqualTo(ExceptionSubTheme.COUNTERPARTY_NOT_STP_ELIGIBLE);
    }

    // -----------------------------------------------------------------------
    // Defensive fallback: a rule that throws must not propagate
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("catches unexpected rule exception and records TECHNICAL_PROCESSING_FAILURE without propagating")
    void evaluate_ruleThrowsException_addsTechnicalFailureAndContinues() {
        StpRule explosiveRule = (event, result) -> {
            throw new RuntimeException("Simulated rule explosion");
        };
        StpRulesEngine engineWithBadRule = new StpRulesEngine(
                List.of(explosiveRule, new EconomicFieldsRule())
        );

        // Fully valid event so EconomicFieldsRule should pass; only the explosive rule fires
        StpValidationResult result = engineWithBadRule.evaluate(fullyValidEvent());

        assertThat(result.isStpPassed()).isFalse();
        boolean hasTechnicalFailure = result.getFailures().stream()
                .anyMatch(f -> f.getCategory() == ExceptionCategory.TECHNICAL_PROCESSING_FAILURE);
        assertThat(hasTechnicalFailure).isTrue();
    }

    @Test
    @DisplayName("continues evaluating remaining rules after one rule throws")
    void evaluate_ruleThrowsException_remainingRulesStillRun() {
        StpRule explosiveFirst = (event, result) -> {
            throw new RuntimeException("First rule explodes");
        };
        // Second rule adds an explicit failure so we can assert it ran
        StpRule secondRule = (event, result) -> result.addFailure(
                ExceptionCategory.MISSING_ECONOMIC_DATA,
                ExceptionSubTheme.MISSING_NOTIONAL,
                "Explicitly injected by second rule",
                "notionalAmount");

        StpRulesEngine engineWith2Rules = new StpRulesEngine(List.of(explosiveFirst, secondRule));

        StpValidationResult result = engineWith2Rules.evaluate(fullyValidEvent());

        assertThat(result.isStpPassed()).isFalse();
        // Must have: 1 TECHNICAL_PROCESSING_FAILURE from the explosive rule
        //          + 1 MISSING_ECONOMIC_DATA from the second rule
        assertThat(result.getFailures()).hasSize(2);
    }

    // -----------------------------------------------------------------------
    // Engine with empty rule list
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("passes with no failures when rule list is empty")
    void evaluate_noRules_stpPassed() {
        StpRulesEngine emptyEngine = new StpRulesEngine(List.of());

        StpValidationResult result = emptyEngine.evaluate(fullyValidEvent());

        assertThat(result.isStpPassed()).isTrue();
        assertThat(result.getFailures()).isEmpty();
    }
}

