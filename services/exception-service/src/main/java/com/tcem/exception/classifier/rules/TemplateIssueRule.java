package com.tcem.exception.classifier.rules;

import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.constants.ExceptionSubTheme;
import com.tcem.common.model.exception.ExceptionRecord;
import com.tcem.exception.classifier.ClassificationResult;
import com.tcem.exception.classifier.ClassificationRule;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Classification rule for {@link ExceptionCategory#TEMPLATE_ISSUE}.
 * <p>
 * Validates that the inbound subTheme belongs to the TEMPLATE_ISSUE family.
 * Template issues generally cannot be resolved by replay — they require a template fix.
 * </p>
 */
@Component
public class TemplateIssueRule implements ClassificationRule {

    private static final Set<ExceptionSubTheme> VALID_SUB_THEMES = Set.of(
            ExceptionSubTheme.NO_MATCHING_TEMPLATE,
            ExceptionSubTheme.PRODUCT_TEMPLATE_MISMATCH,
            ExceptionSubTheme.TEMPLATE_VERSION_MISMATCH
    );

    @Override
    public ExceptionCategory appliesTo() {
        return ExceptionCategory.TEMPLATE_ISSUE;
    }

    @Override
    public void evaluate(ExceptionRecord record, ClassificationResult result) {
        result.confirmedCategory(ExceptionCategory.TEMPLATE_ISSUE);

        ExceptionSubTheme inboundSubTheme = record.getSubTheme();

        if (inboundSubTheme != null && VALID_SUB_THEMES.contains(inboundSubTheme)) {
            result.confirmedSubTheme(inboundSubTheme)
                    .withConfidence(1.0);
        } else if (inboundSubTheme == ExceptionSubTheme.UNKNOWN || inboundSubTheme == null) {
            result.confirmedSubTheme(ExceptionSubTheme.UNKNOWN)
                    .withConfidence(0.8)
                    .withDiagnosticNote("Template issue confirmed but specific sub-theme is UNKNOWN");
        } else {
            result.confirmedSubTheme(inboundSubTheme)
                    .withConfidence(0.5)
                    .withDiagnosticNote("subTheme " + inboundSubTheme + " not in TEMPLATE_ISSUE family");
        }
    }
}

