package com.tcem.validation.rules;

import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.constants.ExceptionSubTheme;
import com.tcem.common.model.message.TradeEvent;
import com.tcem.common.model.reference.ReferenceDataEnrichment;
import com.tcem.common.model.trade.Trade;
import org.springframework.stereotype.Component;

/**
 * FR5 Rule 3 — Template Resolution.
 * <p>
 * Verifies that the enrichment-service successfully resolved a confirmation
 * template for this trade. A null {@code resolvedTemplateId} in the enrichment
 * metadata means no matching template was found, which is a fatal STP failure.
 * </p>
 */
@Component
public class TemplateResolutionRule implements StpRule {

    @Override
    public void evaluate(TradeEvent event, StpValidationResult result) {
        Trade trade = event.getTrade();
        if (trade == null) return; // EconomicFieldsRule already caught this

        ReferenceDataEnrichment meta = trade.getEnrichmentMetadata();
        if (meta == null) return; // BusinessEligibilityRule already flagged missing metadata

        String templateId = meta.getResolvedTemplateId();
        String templateVersion = meta.getResolvedTemplateVersion();

        if (isBlank(templateId)) {
            String hint = trade.getNonEconomicTerms() != null
                    ? trade.getNonEconomicTerms().getTemplateHint() : null;
            result.addFailure(ExceptionCategory.TEMPLATE_ISSUE,
                    ExceptionSubTheme.NO_MATCHING_TEMPLATE,
                    "No confirmation template resolved for product=" + trade.getProductType()
                            + ", templateHint=" + hint
                            + ". Check templates.json in enrichment-service.",
                    "enrichmentMetadata.resolvedTemplateId");
        }

        if (!isBlank(templateId) && isBlank(templateVersion)) {
            result.addFailure(ExceptionCategory.TEMPLATE_ISSUE,
                    ExceptionSubTheme.TEMPLATE_VERSION_MISMATCH,
                    "Template resolved but version is missing: templateId=" + templateId,
                    "enrichmentMetadata.resolvedTemplateVersion");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

