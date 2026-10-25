package com.tcem.exception.classifier.rules;

import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.constants.ExceptionSubTheme;
import com.tcem.common.model.exception.ExceptionRecord;
import com.tcem.exception.classifier.ClassificationResult;
import com.tcem.exception.classifier.ClassificationRule;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Classification rule for {@link ExceptionCategory#TECHNICAL_PROCESSING_FAILURE}.
 * <p>
 * Technical failures (parse errors, duplicates, timeouts) are often transient and
 * may resolve on replay. Confidence is high when the subTheme matches the expected set.
 * </p>
 */
@Component
public class TechnicalFailureRule implements ClassificationRule {

    private static final Set<ExceptionSubTheme> VALID_SUB_THEMES = Set.of(
            ExceptionSubTheme.DUPLICATE_MESSAGE,
            ExceptionSubTheme.PARSE_FAILURE,
            ExceptionSubTheme.ENRICHMENT_TIMEOUT,
            ExceptionSubTheme.SCHEMA_VERSION_UNSUPPORTED,
            ExceptionSubTheme.DLQ_REPROCESSING_FAILURE
    );

    @Override
    public ExceptionCategory appliesTo() {
        return ExceptionCategory.TECHNICAL_PROCESSING_FAILURE;
    }

    @Override
    public void evaluate(ExceptionRecord record, ClassificationResult result) {
        result.confirmedCategory(ExceptionCategory.TECHNICAL_PROCESSING_FAILURE);

        ExceptionSubTheme inboundSubTheme = record.getSubTheme();

        if (inboundSubTheme != null && VALID_SUB_THEMES.contains(inboundSubTheme)) {
            result.confirmedSubTheme(inboundSubTheme)
                    .withConfidence(1.0);
        } else if (inboundSubTheme == ExceptionSubTheme.UNKNOWN || inboundSubTheme == null) {
            result.confirmedSubTheme(ExceptionSubTheme.UNKNOWN)
                    .withConfidence(0.8)
                    .withDiagnosticNote("Technical failure confirmed but specific sub-theme is UNKNOWN");
        } else {
            result.confirmedSubTheme(inboundSubTheme)
                    .withConfidence(0.5)
                    .withDiagnosticNote("subTheme " + inboundSubTheme + " not in TECHNICAL_PROCESSING_FAILURE family");
        }
    }
}

