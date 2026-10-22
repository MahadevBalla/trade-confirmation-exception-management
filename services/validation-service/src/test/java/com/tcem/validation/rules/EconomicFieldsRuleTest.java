package com.tcem.validation.rules;

import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.constants.ExceptionSubTheme;
import com.tcem.common.model.message.TradeEvent;
import com.tcem.common.model.trade.FxOptionEconomics;
import com.tcem.common.model.trade.Trade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link EconomicFieldsRule} (FR5 Rule 1).
 * <p>
 * Covers: null trade, null economics block, each individually missing
 * confirmable field, missing trade-level dates, and the fully valid happy path.
 * </p>
 */
@DisplayName("EconomicFieldsRule")
class EconomicFieldsRuleTest {

    private EconomicFieldsRule rule;
    private StpValidationResult result;

    @BeforeEach
    void setUp() {
        rule = new EconomicFieldsRule();
        result = new StpValidationResult();
    }

    // -----------------------------------------------------------------------
    // Helper — builds a fully valid TradeEvent that must pass STP
    // -----------------------------------------------------------------------

    private TradeEvent validEvent() {
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

        Trade trade = new Trade();
        trade.setTradeId("TRD-10001");
        trade.setTradeDate("2026-01-15");
        trade.setEffectiveDate("2026-01-17");
        trade.setMaturityDate("2026-04-17");
        trade.setFxOptionEconomics(eco);

        TradeEvent event = new TradeEvent();
        event.setTrade(trade);
        return event;
    }

    // -----------------------------------------------------------------------
    // Happy path
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("passes when all confirmable economic fields are present")
    void evaluate_allFieldsPresent_stpPassed() {
        rule.evaluate(validEvent(), result);
        assertThat(result.isStpPassed()).isTrue();
        assertThat(result.getFailures()).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Null trade / null economics block
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("fails with MISSING_NOTIONAL when trade payload is null")
    void evaluate_nullTrade_failsMissingNotional() {
        TradeEvent event = new TradeEvent();
        event.setTrade(null);

        rule.evaluate(event, result);

        assertThat(result.isStpPassed()).isFalse();
        assertThat(result.getFailures()).hasSize(1);
        assertThat(result.getFailures().get(0).getCategory())
                .isEqualTo(ExceptionCategory.MISSING_ECONOMIC_DATA);
        assertThat(result.getFailures().get(0).getAffectedFields()).isEqualTo("trade");
    }

    @Test
    @DisplayName("fails with MISSING_NOTIONAL when fxOptionEconomics block is null")
    void evaluate_nullEconomicsBlock_failsMissingNotional() {
        Trade trade = new Trade();
        trade.setTradeId("TRD-10001");
        trade.setFxOptionEconomics(null);

        TradeEvent event = new TradeEvent();
        event.setTrade(trade);

        rule.evaluate(event, result);

        assertThat(result.isStpPassed()).isFalse();
        assertThat(result.getFailures()).hasSize(1);
        assertThat(result.getFailures().get(0).getAffectedFields())
                .isEqualTo("fxOptionEconomics");
    }

    // -----------------------------------------------------------------------
    // Individual confirmable field gaps
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("fails when notionalAmount is null — sub-theme MISSING_NOTIONAL")
    void evaluate_nullNotionalAmount_failsMissingNotional() {
        TradeEvent event = validEvent();
        event.getTrade().getFxOptionEconomics().setNotionalAmount(null);

        rule.evaluate(event, result);

        assertThat(result.isStpPassed()).isFalse();
        assertThat(result.getFailures().get(0).getSubTheme())
                .isEqualTo(ExceptionSubTheme.MISSING_NOTIONAL);
        assertThat(result.getFailures().get(0).getAffectedFields())
                .contains("fxOptionEconomics.notionalAmount");
    }

    @Test
    @DisplayName("fails when notionalCurrency is blank — sub-theme MISSING_NOTIONAL")
    void evaluate_blankNotionalCurrency_failsMissingNotional() {
        TradeEvent event = validEvent();
        event.getTrade().getFxOptionEconomics().setNotionalCurrency("  ");

        rule.evaluate(event, result);

        assertThat(result.isStpPassed()).isFalse();
        assertThat(result.getFailures().get(0).getAffectedFields())
                .contains("fxOptionEconomics.notionalCurrency");
    }

    @Test
    @DisplayName("fails when strike is null — sub-theme MISSING_STRIKE")
    void evaluate_nullStrike_failsMissingStrike() {
        TradeEvent event = validEvent();
        event.getTrade().getFxOptionEconomics().setStrike(null);

        rule.evaluate(event, result);

        assertThat(result.isStpPassed()).isFalse();
        assertThat(result.getFailures().get(0).getSubTheme())
                .isEqualTo(ExceptionSubTheme.MISSING_STRIKE);
    }

    @Test
    @DisplayName("fails when underlier is null — sub-theme MISSING_UNDERLIER")
    void evaluate_nullUnderlier_failsMissingUnderlier() {
        TradeEvent event = validEvent();
        event.getTrade().getFxOptionEconomics().setUnderlier(null);

        rule.evaluate(event, result);

        assertThat(result.isStpPassed()).isFalse();
        assertThat(result.getFailures().get(0).getSubTheme())
                .isEqualTo(ExceptionSubTheme.MISSING_UNDERLIER);
    }

    @Test
    @DisplayName("fails when premiumAmount is null — sub-theme MISSING_PREMIUM")
    void evaluate_nullPremiumAmount_failsMissingPremium() {
        TradeEvent event = validEvent();
        event.getTrade().getFxOptionEconomics().setPremiumAmount(null);

        rule.evaluate(event, result);

        assertThat(result.isStpPassed()).isFalse();
        assertThat(result.getFailures().get(0).getSubTheme())
                .isEqualTo(ExceptionSubTheme.MISSING_PREMIUM);
    }

    @Test
    @DisplayName("fails when premiumCurrency is blank — sub-theme MISSING_PREMIUM")
    void evaluate_blankPremiumCurrency_failsMissingPremium() {
        TradeEvent event = validEvent();
        event.getTrade().getFxOptionEconomics().setPremiumCurrency("");

        rule.evaluate(event, result);

        assertThat(result.isStpPassed()).isFalse();
        assertThat(result.getFailures().get(0).getAffectedFields())
                .contains("fxOptionEconomics.premiumCurrency");
    }

    @Test
    @DisplayName("fails when premiumPaymentDate is null — sub-theme MISSING_PREMIUM")
    void evaluate_nullPremiumPaymentDate_failsMissingPremium() {
        TradeEvent event = validEvent();
        event.getTrade().getFxOptionEconomics().setPremiumPaymentDate(null);

        rule.evaluate(event, result);

        assertThat(result.isStpPassed()).isFalse();
        assertThat(result.getFailures().get(0).getAffectedFields())
                .contains("fxOptionEconomics.premiumPaymentDate");
    }

    @Test
    @DisplayName("fails when buySell is null — affected field recorded")
    void evaluate_nullBuySell_failsWithAffectedField() {
        TradeEvent event = validEvent();
        event.getTrade().getFxOptionEconomics().setBuySell(null);

        rule.evaluate(event, result);

        assertThat(result.isStpPassed()).isFalse();
        assertThat(result.getFailures().get(0).getAffectedFields())
                .contains("fxOptionEconomics.buySell");
    }

    @Test
    @DisplayName("fails when optionType is blank — affected field recorded")
    void evaluate_blankOptionType_failsWithAffectedField() {
        TradeEvent event = validEvent();
        event.getTrade().getFxOptionEconomics().setOptionType("");

        rule.evaluate(event, result);

        assertThat(result.isStpPassed()).isFalse();
        assertThat(result.getFailures().get(0).getAffectedFields())
                .contains("fxOptionEconomics.optionType");
    }

    // -----------------------------------------------------------------------
    // Trade-level date fields
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("fails when tradeDate is null — MISSING_EXPIRY_DATE sub-theme or similar")
    void evaluate_nullTradeDate_failsMissingDate() {
        TradeEvent event = validEvent();
        event.getTrade().setTradeDate(null);

        rule.evaluate(event, result);

        assertThat(result.isStpPassed()).isFalse();
        assertThat(result.getFailures().get(0).getAffectedFields()).contains("tradeDate");
    }

    @Test
    @DisplayName("fails when effectiveDate is blank")
    void evaluate_blankEffectiveDate_failsMissingDate() {
        TradeEvent event = validEvent();
        event.getTrade().setEffectiveDate("  ");

        rule.evaluate(event, result);

        assertThat(result.isStpPassed()).isFalse();
        assertThat(result.getFailures().get(0).getAffectedFields()).contains("effectiveDate");
    }

    @Test
    @DisplayName("fails when maturityDate is null")
    void evaluate_nullMaturityDate_failsMissingDate() {
        TradeEvent event = validEvent();
        event.getTrade().setMaturityDate(null);

        rule.evaluate(event, result);

        assertThat(result.isStpPassed()).isFalse();
        assertThat(result.getFailures().get(0).getAffectedFields()).contains("maturityDate");
    }

    // -----------------------------------------------------------------------
    // Non-short-circuit: multiple fields missing in one call
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("records a single failure with all missing fields listed (non-short-circuit)")
    void evaluate_multipleFieldsMissing_singleFailureWithAllFields() {
        TradeEvent event = validEvent();
        event.getTrade().getFxOptionEconomics().setNotionalAmount(null);
        event.getTrade().getFxOptionEconomics().setStrike(null);
        event.getTrade().getFxOptionEconomics().setPremiumAmount(null);
        event.getTrade().setTradeDate(null);

        rule.evaluate(event, result);

        // EconomicFieldsRule emits ONE failure with all missing fields in affectedFields
        assertThat(result.getFailures()).hasSize(1);
        String affectedFields = result.getFailures().get(0).getAffectedFields();
        assertThat(affectedFields).contains("fxOptionEconomics.notionalAmount");
        assertThat(affectedFields).contains("fxOptionEconomics.strike");
        assertThat(affectedFields).contains("fxOptionEconomics.premiumAmount");
        assertThat(affectedFields).contains("tradeDate");
    }
}

