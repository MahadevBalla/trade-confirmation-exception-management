package com.tcem.exception.classifier.rules;

import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.constants.ExceptionSubTheme;
import com.tcem.common.model.exception.ExceptionRecord;
import com.tcem.exception.classifier.ClassificationResult;
import com.tcem.exception.classifier.ClassificationRule;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Classification rule for {@link ExceptionCategory#MISSING_ECONOMIC_DATA}.
 * <p>
 * Validates that the inbound subTheme belongs to the MISSING_ECONOMIC_DATA family
 * and that {@code affectedFields} is non-blank. Deducts confidence when the
 * subTheme is UNKNOWN or does not match the expected set.
 * </p>
 */
@Component
public class MissingEconomicDataRule implements ClassificationRule {

    private static final Set<ExceptionSubTheme> VALID_SUB_THEMES = Set.of(
            ExceptionSubTheme.MISSING_STRIKE,
            ExceptionSubTheme.MISSING_NOTIONAL,
            ExceptionSubTheme.MISSING_PREMIUM,
            ExceptionSubTheme.MISSING_PREMIUM_CURRENCY,
            ExceptionSubTheme.MISSING_UNDERLIER,
            ExceptionSubTheme.MISSING_EXPIRY_DATE,
            ExceptionSubTheme.MISSING_OPTION_TYPE
    );

    @Override
    public ExceptionCategory appliesTo() {
        return ExceptionCategory.MISSING_ECONOMIC_DATA;
    }

    @Override
    public void evaluate(ExceptionRecord record, ClassificationResult result) {
        result.confirmedCategory(ExceptionCategory.MISSING_ECONOMIC_DATA);

        ExceptionSubTheme inboundSubTheme = record.getSubTheme();
        String affectedFields = record.getAffectedFields();

        if (inboundSubTheme != null && VALID_SUB_THEMES.contains(inboundSubTheme)) {
            result.confirmedSubTheme(inboundSubTheme);
            // Full confidence only when affectedFields is also populated
            if (affectedFields != null && !affectedFields.isBlank()) {
                result.withConfidence(1.0);
            } else {
                result.withConfidence(0.8)
                        .withDiagnosticNote("subTheme confirmed but affectedFields is blank");
            }
        } else if (inboundSubTheme == ExceptionSubTheme.UNKNOWN || inboundSubTheme == null) {
            result.confirmedSubTheme(ExceptionSubTheme.UNKNOWN)
                    .withConfidence(0.5)
                    .withDiagnosticNote("subTheme is UNKNOWN; manual review may be required");
        } else {
            // subTheme does not belong to MISSING_ECONOMIC_DATA family
            result.confirmedSubTheme(inboundSubTheme)
                    .withConfidence(0.5)
                    .withDiagnosticNote("subTheme " + inboundSubTheme + " not in MISSING_ECONOMIC_DATA family");
        }
    }
}

