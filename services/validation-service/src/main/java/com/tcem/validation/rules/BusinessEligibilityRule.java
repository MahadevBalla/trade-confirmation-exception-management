package com.tcem.validation.rules;

import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.constants.ExceptionSubTheme;
import com.tcem.common.model.message.TradeEvent;
import com.tcem.common.model.reference.ReferenceDataEnrichment;
import com.tcem.common.model.trade.Party;
import com.tcem.common.model.trade.Trade;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * FR5 Rule 2 — Business Eligibility.
 * <p>
 * Checks that the counterparty and product are both flagged as STP-eligible
 * in the enrichment metadata set by the enrichment-service.
 * </p>
 * <ul>
 *   <li>If {@code enrichmentMetadata.counterpartySTPEligible == false} → BUSINESS_RULE_FAILURE/COUNTERPARTY_NOT_STP_ELIGIBLE</li>
 *   <li>If {@code enrichmentMetadata.productSTPEligible == false} → BUSINESS_RULE_FAILURE/PRODUCT_NOT_STP_ELIGIBLE</li>
 *   <li>Null flags are treated as ineligible (enrichment did not resolve the counterparty/product).</li>
 *   <li>Also checks that a COUNTERPARTY party exists in the trade; missing party → MISSING_STATIC_REFERENCE_DATA.</li>
 * </ul>
 */
@Component
public class BusinessEligibilityRule implements StpRule {

    @Override
    public void evaluate(TradeEvent event, StpValidationResult result) {
        Trade trade = event.getTrade();
        if (trade == null) return; // EconomicFieldsRule already caught this

        // ---- Party presence check ----
        String counterpartyId = extractCounterpartyId(trade);
        if (counterpartyId == null) {
            result.addFailure(ExceptionCategory.MISSING_STATIC_REFERENCE_DATA,
                    ExceptionSubTheme.UNKNOWN_COUNTERPARTY,
                    "No COUNTERPARTY party found in trade",
                    "parties[COUNTERPARTY].partyId");
            return;
        }

        ReferenceDataEnrichment meta = trade.getEnrichmentMetadata();
        if (meta == null) {
            result.addFailure(ExceptionCategory.TECHNICAL_PROCESSING_FAILURE,
                    ExceptionSubTheme.ENRICHMENT_TIMEOUT,
                    "Trade has no enrichment metadata; enrichment-service may not have processed it",
                    "enrichmentMetadata");
            return;
        }

        // ---- Counterparty STP eligibility ----
        Boolean cpEligible = meta.getCounterpartySTPEligible();
        if (!Boolean.TRUE.equals(cpEligible)) {
            result.addFailure(ExceptionCategory.BUSINESS_RULE_FAILURE,
                    ExceptionSubTheme.COUNTERPARTY_NOT_STP_ELIGIBLE,
                    "Counterparty " + counterpartyId + " is not STP-eligible (eligible=" + cpEligible + ")",
                    "enrichmentMetadata.counterpartySTPEligible");
        }

        // ---- Product STP eligibility ----
        Boolean productEligible = meta.getProductSTPEligible();
        if (!Boolean.TRUE.equals(productEligible)) {
            result.addFailure(ExceptionCategory.BUSINESS_RULE_FAILURE,
                    ExceptionSubTheme.PRODUCT_NOT_STP_ELIGIBLE,
                    "Product " + trade.getProductType() + " is not STP-eligible for counterparty " + counterpartyId
                            + " (eligible=" + productEligible + ")",
                    "enrichmentMetadata.productSTPEligible");
        }
    }

    private String extractCounterpartyId(Trade trade) {
        List<Party> parties = trade.getParties();
        if (parties == null) return null;
        return parties.stream()
                .filter(p -> "COUNTERPARTY".equalsIgnoreCase(p.getPartyRole()))
                .map(Party::getPartyId)
                .findFirst()
                .orElse(null);
    }
}

