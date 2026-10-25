package com.tcem.exception.classifier;

import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.model.exception.ExceptionRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orchestrates all registered {@link ClassificationRule} implementations to produce
 * a {@link ClassificationResult} for a given {@link ExceptionRecord}.
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li>Select the rule that matches {@code record.getCategory()} via {@link ClassificationRule#appliesTo()}.</li>
 *   <li>If no matching rule exists, fall back with confidence 0.3 and {@code UNKNOWN} subTheme.</li>
 *   <li>Invoke the matched rule; catch any uncaught exception defensively and deduct confidence.</li>
 * </ol>
 */
@Service
public class ClassificationRulesEngine {

    private static final Logger log = LoggerFactory.getLogger(ClassificationRulesEngine.class);

    private final Map<ExceptionCategory, ClassificationRule> rulesByCategory;

    public ClassificationRulesEngine(List<ClassificationRule> rules) {
        this.rulesByCategory = rules.stream()
                .collect(Collectors.toMap(ClassificationRule::appliesTo, Function.identity()));
        log.info("ClassificationRulesEngine loaded with {} rules: {}", rules.size(),
                rulesByCategory.keySet());
    }

    /**
     * Classifies the given {@link ExceptionRecord} and returns a {@link ClassificationResult}.
     *
     * @param record the inbound exception record (must not be null)
     * @return a fully populated ClassificationResult
     */
    public ClassificationResult classify(ExceptionRecord record) {
        ClassificationResult result = new ClassificationResult();

        if (record == null) {
            log.warn("classify() called with null ExceptionRecord; returning low-confidence result");
            return result.withConfidence(0.3)
                    .withDiagnosticNote("ExceptionRecord was null");
        }

        ExceptionCategory category = record.getCategory();
        if (category == null) {
            log.warn("ExceptionRecord has null category for tradeId='{}'; cannot classify",
                    record.getTradeId());
            return result.withConfidence(0.3)
                    .withDiagnosticNote("category is null; cannot classify");
        }

        ClassificationRule rule = rulesByCategory.get(category);
        if (rule == null) {
            log.warn("No classification rule registered for category '{}' (tradeId='{}')",
                    category, record.getTradeId());
            return result
                    .confirmedCategory(category)
                    .withConfidence(0.5)
                    .withDiagnosticNote("No rule registered for category " + category);
        }

        try {
            rule.evaluate(record, result);
            log.debug("Classified exception for tradeId='{}': category={}, subTheme={}, confidence={}",
                    record.getTradeId(), result.getConfirmedCategory(),
                    result.getConfirmedSubTheme(), result.getClassificationConfidence());
        } catch (Exception ex) {
            log.error("Rule '{}' threw an unexpected exception for tradeId='{}': {}",
                    rule.getClass().getSimpleName(), record.getTradeId(), ex.getMessage(), ex);
            result.confirmedCategory(category)
                    .withConfidence(0.3)
                    .withDiagnosticNote("Rule threw exception: " + ex.getMessage());
        }

        return result;
    }
}

