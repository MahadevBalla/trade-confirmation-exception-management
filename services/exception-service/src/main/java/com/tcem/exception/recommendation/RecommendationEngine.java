package com.tcem.exception.recommendation;

import com.tcem.common.constants.ExceptionCategory;
import com.tcem.common.model.exception.ExceptionRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Recommendation Engine (FR9).
 * <p>
 * Given a classified {@link ExceptionRecord}, this service enriches it in-place with:
 * <ul>
 *   <li>{@code recommendedAction} — human-readable next step</li>
 *   <li>{@code ownerQueue}        — team responsible for resolution</li>
 *   <li>{@code replayMayResolve} — whether a data-fix + replay can auto-resolve</li>
 *   <li>{@code resolutionDifficulty} — LOW / MEDIUM / HIGH</li>
 *   <li>{@code operationalImpact}    — LOW / MEDIUM / HIGH / CRITICAL</li>
 * </ul>
 * </p>
 * <p>
 * Mapping is deterministic and driven purely by {@link ExceptionCategory}.
 * Downstream, the SLA calculator uses {@code operationalImpact} to set
 * {@code slaDeadline}, and the workitem-service uses {@code ownerQueue} to
 * route work items.
 * </p>
 */
@Service
public class RecommendationEngine {

    private static final Logger log = LoggerFactory.getLogger(RecommendationEngine.class);

    /**
     * Enriches {@code record} in-place with recommendation fields.
     * Safe to call with a null record (logs a warning and returns immediately).
     *
     * @param record the classified exception record to enrich
     */
    public void enrich(ExceptionRecord record) {
        if (record == null) {
            log.warn("RecommendationEngine.enrich() called with null record — skipping");
            return;
        }

        ExceptionCategory category = record.getCategory();

        if (category == null) {
            log.warn("ExceptionRecord [exceptionId={}] has null category — applying default recommendation",
                    record.getExceptionId());
            applyDefault(record);
            return;
        }

        switch (category) {
            case MISSING_ECONOMIC_DATA:
                record.setRecommendedAction(
                        "Locate missing field in source system; correct and replay");
                record.setOwnerQueue("DATA_REMEDIATION");
                record.setReplayMayResolve(true);
                record.setResolutionDifficulty("LOW");
                record.setOperationalImpact("HIGH");
                break;

            case MISSING_STATIC_REFERENCE_DATA:
                record.setRecommendedAction(
                        "Update reference data catalogue; replay after fix");
                record.setOwnerQueue("REFERENCE_DATA");
                record.setReplayMayResolve(true);
                record.setResolutionDifficulty("MEDIUM");
                record.setOperationalImpact("MEDIUM");
                break;

            case TEMPLATE_ISSUE:
                record.setRecommendedAction(
                        "Review and update confirmation template; no replay needed");
                record.setOwnerQueue("TEMPLATE_MAINTENANCE");
                record.setReplayMayResolve(false);
                record.setResolutionDifficulty("MEDIUM");
                record.setOperationalImpact("MEDIUM");
                break;

            case BUSINESS_RULE_FAILURE:
                record.setRecommendedAction(
                        "Manual review required; assess STP eligibility");
                record.setOwnerQueue("OPERATIONS_REVIEW");
                record.setReplayMayResolve(false);
                record.setResolutionDifficulty("HIGH");
                record.setOperationalImpact("HIGH");
                break;

            case TECHNICAL_PROCESSING_FAILURE:
                record.setRecommendedAction(
                        "Investigate infrastructure/pipeline; replay when resolved");
                record.setOwnerQueue("TECHNOLOGY_SUPPORT");
                record.setReplayMayResolve(true);
                record.setResolutionDifficulty("LOW");
                record.setOperationalImpact("LOW");
                break;

            case COUNTERPARTY_MATCHING_DISPUTE:
                record.setRecommendedAction(
                        "Escalate to operations; counterparty confirmation required");
                record.setOwnerQueue("OPERATIONS_REVIEW");
                record.setReplayMayResolve(false);
                record.setResolutionDifficulty("HIGH");
                record.setOperationalImpact("CRITICAL");
                break;

            case LEGAL_DOCUMENTATION_GAP:
                record.setRecommendedAction(
                        "Escalate to legal/specialist team");
                record.setOwnerQueue("SPECIALIST_REVIEW");
                record.setReplayMayResolve(false);
                record.setResolutionDifficulty("HIGH");
                record.setOperationalImpact("HIGH");
                break;

            case FALSE_POSITIVE_CONFIRMABLE_EVENT:
                record.setRecommendedAction(
                        "Flag for classification rule review");
                record.setOwnerQueue("RULES_OWNER");
                record.setReplayMayResolve(false);
                record.setResolutionDifficulty("LOW");
                record.setOperationalImpact("LOW");
                break;

            default:
                log.warn("No recommendation mapping for category '{}' on exceptionId={} — applying default",
                        category, record.getExceptionId());
                applyDefault(record);
                return;
        }

        log.debug("Recommendation applied for exceptionId={}: queue={}, replay={}, difficulty={}, impact={}",
                record.getExceptionId(),
                record.getOwnerQueue(),
                record.isReplayMayResolve(),
                record.getResolutionDifficulty(),
                record.getOperationalImpact());
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private void applyDefault(ExceptionRecord record) {
        record.setRecommendedAction(
                "Manual review required; unable to determine recommendation automatically");
        record.setOwnerQueue("OPERATIONS_REVIEW");
        record.setReplayMayResolve(false);
        record.setResolutionDifficulty("MEDIUM");
        record.setOperationalImpact("MEDIUM");
    }
}

