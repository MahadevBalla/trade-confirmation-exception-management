package com.tcem.exception.classifier.rules;

import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.constants.ExceptionSubTheme;
import com.tcem.common.model.exception.ExceptionRecord;
import com.tcem.exception.classifier.ClassificationResult;
import com.tcem.exception.classifier.ClassificationRule;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Classification rule for {@link ExceptionCategory#BUSINESS_RULE_FAILURE}.
 * <p>
 * Business rule failures typically require operations review and cannot be resolved
 * by replay alone — the eligibility or rule itself must change first.
 * </p>
 */
@Component
public class BusinessRuleFailureRule implements ClassificationRule {

    private static final Set<ExceptionSubTheme> VALID_SUB_THEMES = Set.of(
            ExceptionSubTheme.COUNTERPARTY_NOT_STP_ELIGIBLE,
            ExceptionSubTheme.PRODUCT_NOT_STP_ELIGIBLE,
            ExceptionSubTheme.TRADE_DATE_MISMATCH,
            ExceptionSubTheme.VALUE_DATE_HOLIDAY
    );

    @Override
    public ExceptionCategory appliesTo() {
        return ExceptionCategory.BUSINESS_RULE_FAILURE;
    }

    @Override
    public void evaluate(ExceptionRecord record, ClassificationResult result) {
        result.confirmedCategory(ExceptionCategory.BUSINESS_RULE_FAILURE);

        ExceptionSubTheme inboundSubTheme = record.getSubTheme();

        if (inboundSubTheme != null && VALID_SUB_THEMES.contains(inboundSubTheme)) {
            result.confirmedSubTheme(inboundSubTheme)
                    .withConfidence(1.0);
        } else if (inboundSubTheme == ExceptionSubTheme.UNKNOWN || inboundSubTheme == null) {
            result.confirmedSubTheme(ExceptionSubTheme.UNKNOWN)
                    .withConfidence(0.8)
                    .withDiagnosticNote("Business rule failure confirmed but specific sub-theme unknown");
        } else {
            result.confirmedSubTheme(inboundSubTheme)
                    .withConfidence(0.5)
                    .withDiagnosticNote("subTheme " + inboundSubTheme + " not in BUSINESS_RULE_FAILURE family");
        }
    }
}

