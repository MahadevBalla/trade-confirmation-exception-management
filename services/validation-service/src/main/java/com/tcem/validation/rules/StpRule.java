package com.tcem.validation.rules;

import com.tcem.common.model.message.TradeEvent;

/**
 * STP rule contract.
 * <p>
 * Each rule inspects a {@link TradeEvent} and appends failures to the
 * {@link StpValidationResult}. Rules are stateless and may be evaluated
 * in any order (they accumulate failures rather than short-circuit).
 * </p>
 */
public interface StpRule {

    /**
     * Evaluates this rule against the enriched trade event.
     *
     * @param event  the enriched trade event from {@code trade.enriched}
     * @param result mutable result accumulator; call {@code result.addFailure(...)} on violations
     */
    void evaluate(TradeEvent event, StpValidationResult result);
}

