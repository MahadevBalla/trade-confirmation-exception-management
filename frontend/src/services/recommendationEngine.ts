import type {
  ExceptionCategory,
  ExceptionSubTheme,
  OwnerQueue,
  Priority,
  ResolutionDifficulty,
} from '../types/tcem';

export interface RecommendationResult {
  category: ExceptionCategory;
  subTheme: ExceptionSubTheme;
  recommendedAction: string;
  ownerQueue: OwnerQueue;
  replayMayResolve: boolean;
  resolutionDifficulty: ResolutionDifficulty;
  operationalImpact: Priority;
  confidence: number;
  reasonCode: string;
  affectedFields: string;
  explanation: string;
}

/**
 * Transparent rule-based Recommendation & Classification Engine (Spec §12, FR8, FR9).
 * Evaluates operational impact, resolution difficulty, replay feasibility, and optimal queue.
 */
export function evaluateExceptionRules(
  category: ExceptionCategory,
  subTheme: ExceptionSubTheme,
  notionalAmount?: number
): RecommendationResult {
  const isHighNotional = (notionalAmount ?? 0) >= 5_000_000;

  switch (category) {
    case 'MISSING_ECONOMIC_DATA': {
      let field = 'strike';
      if (subTheme === 'MISSING_NOTIONAL') field = 'notionalAmount';
      if (subTheme === 'MISSING_PREMIUM') field = 'premiumAmount';
      if (subTheme === 'MISSING_UNDERLIER') field = 'underlier';
      if (subTheme === 'MISSING_EXPIRY_DATE') field = 'maturityDate';
      if (subTheme === 'MISSING_OPTION_TYPE') field = 'optionType';

      return {
        category,
        subTheme,
        recommendedAction: `Inspect source trade payload for missing ${field}; request front-office blotter resend and trigger automated replay.`,
        ownerQueue: 'DATA_REMEDIATION',
        replayMayResolve: true,
        resolutionDifficulty: 'LOW',
        operationalImpact: isHighNotional ? 'CRITICAL' : 'HIGH',
        confidence: 1.0,
        reasonCode: `ERR_ECON_${subTheme}`,
        affectedFields: field,
        explanation: `Economic attribute '${field}' is mandatory for contract confirmation. Replaying after data remediation will proceed to STP.`,
      };
    }

    case 'MISSING_STATIC_REFERENCE_DATA': {
      let field = 'counterpartyId';
      if (subTheme === 'UNKNOWN_INSTRUMENT') field = 'underlier';
      if (subTheme === 'MISSING_JURISDICTION') field = 'jurisdiction';

      return {
        category,
        subTheme,
        recommendedAction: `Verify client LEI and onboarding status in Reference Master. Once static record is active, trigger replay to re-run enrichment.`,
        ownerQueue: 'REFERENCE_DATA',
        replayMayResolve: true,
        resolutionDifficulty: 'MEDIUM',
        operationalImpact: 'HIGH',
        confidence: 1.0,
        reasonCode: `ERR_REF_${subTheme}`,
        affectedFields: field,
        explanation: `Reference data missing for ${field}. Automated pipeline enrichment was suspended. Retry enrichment upon reference update.`,
      };
    }

    case 'TEMPLATE_ISSUE': {
      return {
        category,
        subTheme,
        recommendedAction: `Create template maintenance work item. Review ISDA schedule for product variation and configure template catalogue entry.`,
        ownerQueue: 'TEMPLATE_MAINTENANCE',
        replayMayResolve: false,
        resolutionDifficulty: 'HIGH',
        operationalImpact: 'MEDIUM',
        confidence: 1.0,
        reasonCode: `ERR_TMPL_${subTheme}`,
        affectedFields: 'templateHint, productType',
        explanation: `No active confirmation template matched product configuration. Manual template mapping or work item ticket required.`,
      };
    }

    case 'BUSINESS_RULE_FAILURE': {
      const isEligibleFailure = subTheme === 'COUNTERPARTY_NOT_STP_ELIGIBLE';
      return {
        category,
        subTheme,
        recommendedAction: isEligibleFailure
          ? `Route to Operations Review queue for four-eyes manual inspection or eligibility waiver approval.`
          : `Review trade value date and calendar holidays. Amend settlement date or request exception sign-off.`,
        ownerQueue: 'OPERATIONS_REVIEW',
        replayMayResolve: false,
        resolutionDifficulty: 'MEDIUM',
        operationalImpact: isHighNotional ? 'CRITICAL' : 'MEDIUM',
        confidence: 1.0,
        reasonCode: `ERR_RULE_${subTheme}`,
        affectedFields: isEligibleFailure ? 'parties.counterparty' : 'tradeDate, effectiveDate',
        explanation: `Business rule check violated STP criteria. Manual intervention in the Review Queue is necessary before confirmation can be emitted.`,
      };
    }

    case 'TECHNICAL_PROCESSING_FAILURE': {
      return {
        category,
        subTheme,
        recommendedAction: `Transient infrastructure error detected. Check Kafka consumer lag and CouchDB connectivity; trigger controlled replay.`,
        ownerQueue: 'TECHNOLOGY_SUPPORT',
        replayMayResolve: true,
        resolutionDifficulty: 'LOW',
        operationalImpact: 'CRITICAL',
        confidence: 1.0,
        reasonCode: `ERR_TECH_${subTheme}`,
        affectedFields: 'kafka.offset, network.timeout',
        explanation: `Message failed in technical pipeline. Message routed to DLQ with diagnostic stack trace. Safe for controlled replay.`,
      };
    }

    case 'COUNTERPARTY_MATCHING_DISPUTE': {
      return {
        category,
        subTheme,
        recommendedAction: `Generate bilateral term discrepancy report. Contact counterparty operations desk to reconcile strike/notional terms.`,
        ownerQueue: 'OPERATIONS_REVIEW',
        replayMayResolve: false,
        resolutionDifficulty: 'HIGH',
        operationalImpact: isHighNotional ? 'CRITICAL' : 'HIGH',
        confidence: 1.0,
        reasonCode: `ERR_MATCH_${subTheme}`,
        affectedFields: 'strike, notionalAmount',
        explanation: `Counterparty affirmation terms do not match internal bank booking. Requires manual reconciliation or bilateral amendment.`,
      };
    }

    case 'LEGAL_DOCUMENTATION_GAP': {
      return {
        category,
        subTheme,
        recommendedAction: `Route to Legal & Specialist review. Confirm active Master Agreement version or obtain confirmation rider.`,
        ownerQueue: 'SPECIALIST_REVIEW',
        replayMayResolve: false,
        resolutionDifficulty: 'HIGH',
        operationalImpact: 'MEDIUM',
        confidence: 1.0,
        reasonCode: `ERR_LEGAL_${subTheme}`,
        affectedFields: 'nonEconomicTerms.jurisdiction',
        explanation: `Legal documentation gap requires specialist review before binding electronic confirmation can be issued.`,
      };
    }

    case 'FALSE_POSITIVE_CONFIRMABLE_EVENT':
    default: {
      return {
        category: 'FALSE_POSITIVE_CONFIRMABLE_EVENT',
        subTheme: 'NON_CONFIRMABLE_ATTRIBUTE_CHANGED',
        recommendedAction: `Dismiss exception and mark non-confirmable. Update ConfirmableFieldRegistry configuration.`,
        ownerQueue: 'RULES_OWNER',
        replayMayResolve: false,
        resolutionDifficulty: 'LOW',
        operationalImpact: 'LOW',
        confidence: 0.95,
        reasonCode: 'ERR_CONFIRM_FALSE_POSITIVE',
        affectedFields: 'internalPricingRef',
        explanation: `Amendment touched only internal pricing metadata; confirmation fingerprint did not change. Duplicate suppressed.`,
      };
    }
  }
}
