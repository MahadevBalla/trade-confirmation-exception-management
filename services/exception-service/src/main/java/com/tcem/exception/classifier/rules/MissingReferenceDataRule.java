package com.tcem.exception.classifier.rules;

import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.constants.ExceptionSubTheme;
import com.tcem.common.model.exception.ExceptionRecord;
import com.tcem.exception.classifier.ClassificationResult;
import com.tcem.exception.classifier.ClassificationRule;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Classification rule for {@link ExceptionCategory#MISSING_STATIC_REFERENCE_DATA}.
 * <p>
 * Validates that the inbound subTheme belongs to the MISSING_STATIC_REFERENCE_DATA
 * family. Deducts confidence when the subTheme is UNKNOWN or mismatched.
 * </p>
 */
@Component
public class MissingReferenceDataRule implements ClassificationRule {

    private static final Set<ExceptionSubTheme> VALID_SUB_THEMES = Set.of(
            ExceptionSubTheme.UNKNOWN_COUNTERPARTY,
            ExceptionSubTheme.UNKNOWN_INSTRUMENT,
            ExceptionSubTheme.MISSING_JURISDICTION,
            ExceptionSubTheme.MISSING_TEMPLATE_CATALOGUE_ENTRY
    );

    @Override
    public ExceptionCategory appliesTo() {
        return ExceptionCategory.MISSING_STATIC_REFERENCE_DATA;
    }

    @Override
    public void evaluate(ExceptionRecord record, ClassificationResult result) {
        result.confirmedCategory(ExceptionCategory.MISSING_STATIC_REFERENCE_DATA);

        ExceptionSubTheme inboundSubTheme = record.getSubTheme();
        String affectedFields = record.getAffectedFields();

        if (inboundSubTheme != null && VALID_SUB_THEMES.contains(inboundSubTheme)) {
            result.confirmedSubTheme(inboundSubTheme);
            if (affectedFields != null && !affectedFields.isBlank()) {
                result.withConfidence(1.0);
            } else {
                result.withConfidence(0.8)
                        .withDiagnosticNote("subTheme confirmed but affectedFields is blank");
            }
        } else if (inboundSubTheme == ExceptionSubTheme.UNKNOWN || inboundSubTheme == null) {
            result.confirmedSubTheme(ExceptionSubTheme.UNKNOWN)
                    .withConfidence(0.5)
                    .withDiagnosticNote("subTheme is UNKNOWN; cannot confirm reference data gap");
        } else {
            result.confirmedSubTheme(inboundSubTheme)
                    .withConfidence(0.5)
                    .withDiagnosticNote("subTheme " + inboundSubTheme + " not in MISSING_STATIC_REFERENCE_DATA family");
        }
    }
}

