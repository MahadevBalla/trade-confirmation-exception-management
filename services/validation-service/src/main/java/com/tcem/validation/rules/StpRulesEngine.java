package com.tcem.validation.rules;

import com.tcem.common.model.message.TradeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates all registered {@link StpRule}s and returns the combined
 * {@link StpValidationResult}.
 * <p>
 * All rules are evaluated (no short-circuit) so the result always contains
 * the complete set of failures — enabling richer exception records.
 * </p>
 */
@Service
public class StpRulesEngine {

    private static final Logger log = LoggerFactory.getLogger(StpRulesEngine.class);

    private final List<StpRule> rules;

    /**
     * Spring injects all {@link StpRule} beans in their natural registration order.
     * Current order: EconomicFieldsRule → BusinessEligibilityRule → TemplateResolutionRule.
     */
    public StpRulesEngine(List<StpRule> rules) {
        this.rules = rules;
    }

    /**
     * Evaluates all STP rules against the given enriched trade event.
     *
     * @param event enriched {@code TradeEvent} from the {@code trade.enriched} topic
     * @return result carrying the pass/fail decision and all failure details
     */
    public StpValidationResult evaluate(TradeEvent event) {
        StpValidationResult result = new StpValidationResult();
        String tradeId = event.getTrade() != null ? event.getTrade().getTradeId() : "UNKNOWN";

        log.debug("Running {} STP rules for tradeId={}", rules.size(), tradeId);

        for (StpRule rule : rules) {
            try {
                rule.evaluate(event, result);
            } catch (Exception ex) {
                log.error("Unexpected error in STP rule {} for tradeId={}: {}",
                        rule.getClass().getSimpleName(), tradeId, ex.getMessage(), ex);
                // Add a technical failure so this trade doesn't silently pass
                result.addFailure(
                        com.tcem.common.constants.ExceptionCategory.TECHNICAL_PROCESSING_FAILURE,
                        com.tcem.common.constants.ExceptionSubTheme.DLQ_REPROCESSING_FAILURE,
                        "Rule " + rule.getClass().getSimpleName() + " threw: " + ex.getMessage(),
                        "unknown");
            }
        }

        log.info("STP evaluation complete [tradeId={}, result={}]", tradeId, result.summarize());
        return result;
    }
}

