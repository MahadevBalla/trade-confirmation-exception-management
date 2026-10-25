package com.tcem.exception.classifier.rules;

import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.constants.ExceptionSubTheme;
import com.tcem.common.model.exception.ExceptionRecord;
import com.tcem.exception.classifier.ClassificationResult;
import com.tcem.exception.classifier.ClassificationRule;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Classification rule for {@link ExceptionCategory#COUNTERPARTY_MATCHING_DISPUTE}.
 * <p>
 * Matching disputes always require human review regardless of confidence, as they
 * involve economic or non-economic discrepancies between parties.
 * </p>
 */
@Component
public class CounterpartyDisputeRule implements ClassificationRule {

    private static final Set<ExceptionSubTheme> VALID_SUB_THEMES = Set.of(
            ExceptionSubTheme.ECONOMIC_TERM_MISMATCH,
            ExceptionSubTheme.NON_ECONOMIC_TERM_MISMATCH,
            ExceptionSubTheme.PARTY_ROLE_MISMATCH
    );

    @Override
    public ExceptionCategory appliesTo() {
        return ExceptionCategory.COUNTERPARTY_MATCHING_DISPUTE;
    }

    @Override
    public void evaluate(ExceptionRecord record, ClassificationResult result) {
        result.confirmedCategory(ExceptionCategory.COUNTERPARTY_MATCHING_DISPUTE);

        ExceptionSubTheme inboundSubTheme = record.getSubTheme();

        if (inboundSubTheme != null && VALID_SUB_THEMES.contains(inboundSubTheme)) {
            result.confirmedSubTheme(inboundSubTheme)
                    .withConfidence(1.0);
        } else if (inboundSubTheme == ExceptionSubTheme.UNKNOWN || inboundSubTheme == null) {
            result.confirmedSubTheme(ExceptionSubTheme.UNKNOWN)
                    .withConfidence(0.8)
                    .withDiagnosticNote("Counterparty dispute confirmed but mismatch type is UNKNOWN");
        } else {
            result.confirmedSubTheme(inboundSubTheme)
                    .withConfidence(0.5)
                    .withDiagnosticNote("subTheme " + inboundSubTheme + " not in COUNTERPARTY_MATCHING_DISPUTE family");
        }
    }
}

