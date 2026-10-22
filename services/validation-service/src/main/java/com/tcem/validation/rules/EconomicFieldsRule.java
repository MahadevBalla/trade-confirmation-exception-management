package com.tcem.validation.rules;

import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.constants.ExceptionSubTheme;
import com.tcem.common.model.message.TradeEvent;
import com.tcem.common.model.trade.FxOptionEconomics;
import com.tcem.common.model.trade.Trade;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * FR5 Rule 1 — Economic Fields Completeness.
 * <p>
 * Verifies that all confirmable FX Option economic fields are present and
 * non-null. Any missing confirmable field fails STP with
 * {@link ExceptionCategory#MISSING_ECONOMIC_DATA}.
 * </p>
 */
@Component
public class EconomicFieldsRule implements StpRule {

    @Override
    public void evaluate(TradeEvent event, StpValidationResult result) {
        Trade trade = event.getTrade();
        if (trade == null) {
            result.addFailure(ExceptionCategory.MISSING_ECONOMIC_DATA, ExceptionSubTheme.MISSING_NOTIONAL,
                    "Trade payload is null", "trade");
            return;
        }

        FxOptionEconomics eco = trade.getFxOptionEconomics();
        if (eco == null) {
            result.addFailure(ExceptionCategory.MISSING_ECONOMIC_DATA, ExceptionSubTheme.MISSING_NOTIONAL,
                    "FX Option economics block is absent", "fxOptionEconomics");
            return;
        }

        List<String> missingFields = new ArrayList<>();

        if (eco.getNotionalAmount() == null)
            missingFields.add("fxOptionEconomics.notionalAmount");
        if (isBlank(eco.getNotionalCurrency()))
            missingFields.add("fxOptionEconomics.notionalCurrency");
        if (isBlank(eco.getBuySell()))
            missingFields.add("fxOptionEconomics.buySell");
        if (isBlank(eco.getOptionType()))
            missingFields.add("fxOptionEconomics.optionType");
        if (eco.getStrike() == null)
            missingFields.add("fxOptionEconomics.strike");
        if (isBlank(eco.getUnderlier()))
            missingFields.add("fxOptionEconomics.underlier");
        if (eco.getPremiumAmount() == null)
            missingFields.add("fxOptionEconomics.premiumAmount");
        if (isBlank(eco.getPremiumCurrency()))
            missingFields.add("fxOptionEconomics.premiumCurrency");
        if (isBlank(eco.getPremiumPaymentDate()))
            missingFields.add("fxOptionEconomics.premiumPaymentDate");

        // Trade-level date fields (also confirmable)
        if (isBlank(trade.getTradeDate()))
            missingFields.add("tradeDate");
        if (isBlank(trade.getEffectiveDate()))
            missingFields.add("effectiveDate");
        if (isBlank(trade.getMaturityDate()))
            missingFields.add("maturityDate");

        if (!missingFields.isEmpty()) {
            // Choose the most descriptive sub-theme based on which fields are missing
            ExceptionSubTheme subTheme = pickSubTheme(missingFields);
            result.addFailure(ExceptionCategory.MISSING_ECONOMIC_DATA, subTheme,
                    "Required confirmable economic fields are absent: " + missingFields,
                    String.join(", ", missingFields));
        }
    }

    private ExceptionSubTheme pickSubTheme(List<String> fields) {
        if (fields.stream().anyMatch(f -> f.contains("strike")))     return ExceptionSubTheme.MISSING_STRIKE;
        if (fields.stream().anyMatch(f -> f.contains("notional")))   return ExceptionSubTheme.MISSING_NOTIONAL;
        if (fields.stream().anyMatch(f -> f.contains("premium")))    return ExceptionSubTheme.MISSING_PREMIUM;
        if (fields.stream().anyMatch(f -> f.contains("underlier")))  return ExceptionSubTheme.MISSING_UNDERLIER;
        if (fields.stream().anyMatch(f -> f.contains("optionType"))) return ExceptionSubTheme.MISSING_OPTION_TYPE;
        if (fields.stream().anyMatch(f -> f.contains("maturity") || f.contains("effectiveDate")))
            return ExceptionSubTheme.MISSING_EXPIRY_DATE;
        return ExceptionSubTheme.MISSING_NOTIONAL;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

