import type {
  Trade,
  ExceptionRecord,
  WorkItem,
  ConfirmationRecord,
  AuditEvent,
  DeadLetterEvent,
} from '../types/tcem';

export const INITIAL_TRADES: Trade[] = [
  // 1. Clean STP Happy Path
  {
    tradeId: 'TRD-10001',
    sourceSystemTradeId: 'SRC-77881',
    tradeVersion: 1,
    productType: 'FX_OPTION',
    tradeDate: '2026-01-15',
    effectiveDate: '2026-01-17',
    maturityDate: '2026-04-17',
    status: 'CONFIRMED',
    parties: [
      { partyRole: 'BANK', partyId: 'PTY-BANK-001', partyName: 'Apex Investment Banking AG' },
      { partyRole: 'COUNTERPARTY', partyId: 'PTY-CP-101', partyName: 'Meridian Capital Partners Ltd' },
    ],
    fxOptionEconomics: {
      notionalAmount: 1000000,
      notionalCurrency: 'USD',
      buySell: 'BUY',
      optionType: 'CALL',
      strike: 1.085,
      underlier: 'EUR/USD',
      premiumAmount: 12000,
      premiumCurrency: 'USD',
      premiumPaymentDate: '2026-01-17',
      internalPricingRef: 'PRC-FX-99012',
    },
    nonEconomicTerms: {
      jurisdiction: 'GENERIC-UK',
      confirmationMethod: 'ELECTRONIC',
      templateHint: 'FX_OPTION_STANDARD',
    },
    enrichmentMetadata: {
      resolvedTemplateId: 'TMPL-FX-OPT-001',
      resolvedTemplateVersion: '1.2.0',
      counterpartySTPEligible: true,
      productSTPEligible: true,
      enrichmentNotes: 'Fully enriched from reference master with 100% confidence',
      fieldProvenance: {
        legalEntityName: {
          sourceSystem: 'REF_MASTER',
          lookupKey: 'PTY-CP-101',
          retrievedAt: '2026-01-15T10:30:01Z',
          derivationMethod: 'EXACT_LOOKUP',
        },
        resolvedTemplateId: {
          sourceSystem: 'TEMPLATE_CATALOGUE',
          lookupKey: 'FX_OPTION_STANDARD',
          retrievedAt: '2026-01-15T10:30:02Z',
          derivationMethod: 'EXACT_LOOKUP',
        },
      },
    },
  },

  // 2. Missing Economic Data (Strike missing)
  {
    tradeId: 'TRD-10002',
    sourceSystemTradeId: 'SRC-77882',
    tradeVersion: 1,
    productType: 'FX_OPTION',
    tradeDate: '2026-01-15',
    effectiveDate: '2026-01-17',
    maturityDate: '2026-05-15',
    status: 'EXCEPTION_CAPTURED',
    parties: [
      { partyRole: 'BANK', partyId: 'PTY-BANK-001', partyName: 'Apex Investment Banking AG' },
      { partyRole: 'COUNTERPARTY', partyId: 'PTY-CP-102', partyName: 'Vanguard Global Hedging Corp' },
    ],
    fxOptionEconomics: {
      notionalAmount: 3500000,
      notionalCurrency: 'EUR',
      buySell: 'SELL',
      optionType: 'PUT',
      // strike missing deliberately!
      underlier: 'EUR/USD',
      premiumAmount: 45000,
      premiumCurrency: 'EUR',
      premiumPaymentDate: '2026-01-17',
    },
    nonEconomicTerms: {
      jurisdiction: 'GENERIC-US',
      confirmationMethod: 'ELECTRONIC',
      templateHint: 'FX_OPTION_STANDARD',
    },
    enrichmentMetadata: {
      resolvedTemplateId: 'TMPL-FX-OPT-001',
      resolvedTemplateVersion: '1.2.0',
      counterpartySTPEligible: true,
      productSTPEligible: true,
      enrichmentNotes: 'Enrichment complete; economics block missing strike',
    },
  },

  // 3. Missing Static Reference Data (Unknown Counterparty)
  {
    tradeId: 'TRD-10003',
    sourceSystemTradeId: 'SRC-77883',
    tradeVersion: 1,
    productType: 'FX_OPTION',
    tradeDate: '2026-01-15',
    effectiveDate: '2026-01-17',
    maturityDate: '2026-06-30',
    status: 'EXCEPTION_CAPTURED',
    parties: [
      { partyRole: 'BANK', partyId: 'PTY-BANK-001', partyName: 'Apex Investment Banking AG' },
      { partyRole: 'COUNTERPARTY', partyId: 'PTY-CP-999', partyName: 'Unregistered Offshore Hedge Fund' },
    ],
    fxOptionEconomics: {
      notionalAmount: 5000000,
      notionalCurrency: 'USD',
      buySell: 'BUY',
      optionType: 'CALL',
      strike: 1.092,
      underlier: 'EUR/USD',
      premiumAmount: 62000,
      premiumCurrency: 'USD',
      premiumPaymentDate: '2026-01-17',
    },
    nonEconomicTerms: {
      jurisdiction: 'GENERIC',
      confirmationMethod: 'ELECTRONIC',
      templateHint: 'FX_OPTION_STANDARD',
    },
    enrichmentMetadata: {
      counterpartySTPEligible: false,
      productSTPEligible: false,
      enrichmentNotes: 'Counterparty PTY-CP-999 not registered in reference data',
    },
  },

  // 4. Template Issue (No Matching Template)
  {
    tradeId: 'TRD-10004',
    sourceSystemTradeId: 'SRC-77884',
    tradeVersion: 1,
    productType: 'FX_OPTION',
    tradeDate: '2026-01-15',
    effectiveDate: '2026-01-18',
    maturityDate: '2026-07-20',
    status: 'WORKITEM_LINKED',
    parties: [
      { partyRole: 'BANK', partyId: 'PTY-BANK-001', partyName: 'Apex Investment Banking AG' },
      { partyRole: 'COUNTERPARTY', partyId: 'PTY-CP-101', partyName: 'Meridian Capital Partners Ltd' },
    ],
    fxOptionEconomics: {
      notionalAmount: 2000000,
      notionalCurrency: 'GBP',
      buySell: 'BUY',
      optionType: 'CALL',
      strike: 1.285,
      underlier: 'GBP/USD',
      premiumAmount: 28000,
      premiumCurrency: 'GBP',
      premiumPaymentDate: '2026-01-18',
    },
    nonEconomicTerms: {
      jurisdiction: 'GENERIC-UK',
      confirmationMethod: 'ELECTRONIC',
      templateHint: 'FX_EXOTIC_TARF_V3', // Unknown template hint
    },
    enrichmentMetadata: {
      counterpartySTPEligible: true,
      productSTPEligible: false,
      enrichmentNotes: 'Template hint FX_EXOTIC_TARF_V3 not found in catalogue',
    },
  },

  // 5. Business Rule Failure (Counterparty Not STP Eligible)
  {
    tradeId: 'TRD-10005',
    sourceSystemTradeId: 'SRC-77885',
    tradeVersion: 1,
    productType: 'FX_OPTION',
    tradeDate: '2026-01-15',
    effectiveDate: '2026-01-17',
    maturityDate: '2026-09-15',
    status: 'MANUAL_REVIEW',
    parties: [
      { partyRole: 'BANK', partyId: 'PTY-BANK-001', partyName: 'Apex Investment Banking AG' },
      { partyRole: 'COUNTERPARTY', partyId: 'PTY-CP-103', partyName: 'Pacific Rim Asset Management' },
    ],
    fxOptionEconomics: {
      notionalAmount: 4200000,
      notionalCurrency: 'USD',
      buySell: 'SELL',
      optionType: 'CALL',
      strike: 155.5,
      underlier: 'USD/JPY',
      premiumAmount: 75000,
      premiumCurrency: 'USD',
      premiumPaymentDate: '2026-01-17',
    },
    nonEconomicTerms: {
      jurisdiction: 'GENERIC-SG',
      confirmationMethod: 'ELECTRONIC',
      templateHint: 'FX_OPTION_STANDARD',
    },
    enrichmentMetadata: {
      resolvedTemplateId: 'TMPL-FX-OPT-001',
      resolvedTemplateVersion: '1.2.0',
      counterpartySTPEligible: false,
      productSTPEligible: true,
      enrichmentNotes: 'Counterparty restricted to Manual Review by Risk Policy',
    },
  },

  // 6. Technical Failure (Dead-Letter Queue item)
  {
    tradeId: 'TRD-10006',
    sourceSystemTradeId: 'SRC-77886',
    tradeVersion: 1,
    productType: 'FX_OPTION',
    tradeDate: '2026-01-15',
    effectiveDate: '2026-01-17',
    maturityDate: '2026-05-30',
    status: 'DEAD_LETTER',
    parties: [
      { partyRole: 'BANK', partyId: 'PTY-BANK-001', partyName: 'Apex Investment Banking AG' },
      { partyRole: 'COUNTERPARTY', partyId: 'PTY-CP-104', partyName: 'Nordic Sovereign Holdings' },
    ],
    fxOptionEconomics: {
      notionalAmount: 8000000,
      notionalCurrency: 'EUR',
      buySell: 'BUY',
      optionType: 'PUT',
      strike: 11.25,
      underlier: 'EUR/SEK',
      premiumAmount: 95000,
      premiumCurrency: 'EUR',
      premiumPaymentDate: '2026-01-17',
    },
    nonEconomicTerms: {
      jurisdiction: 'GENERIC-SE',
      confirmationMethod: 'ELECTRONIC',
      templateHint: 'FX_OPTION_STANDARD',
    },
  },

  // 7. Counterparty Matching Dispute
  {
    tradeId: 'TRD-10007',
    sourceSystemTradeId: 'SRC-77887',
    tradeVersion: 1,
    productType: 'FX_OPTION',
    tradeDate: '2026-01-15',
    effectiveDate: '2026-01-17',
    maturityDate: '2026-04-30',
    status: 'MANUAL_REVIEW',
    parties: [
      { partyRole: 'BANK', partyId: 'PTY-BANK-001', partyName: 'Apex Investment Banking AG' },
      { partyRole: 'COUNTERPARTY', partyId: 'PTY-CP-101', partyName: 'Meridian Capital Partners Ltd' },
    ],
    fxOptionEconomics: {
      notionalAmount: 2500000,
      notionalCurrency: 'USD',
      buySell: 'BUY',
      optionType: 'CALL',
      strike: 1.082, // Counterparty affirms 1.085!
      underlier: 'EUR/USD',
      premiumAmount: 31000,
      premiumCurrency: 'USD',
      premiumPaymentDate: '2026-01-17',
    },
    nonEconomicTerms: {
      jurisdiction: 'GENERIC-UK',
      confirmationMethod: 'ELECTRONIC',
      templateHint: 'FX_OPTION_STANDARD',
    },
    enrichmentMetadata: {
      resolvedTemplateId: 'TMPL-FX-OPT-001',
      resolvedTemplateVersion: '1.2.0',
      counterpartySTPEligible: true,
      productSTPEligible: true,
      enrichmentNotes: 'Matching check failed against counterparty affirmation file',
    },
  },

  // 8. Amended Trade with Duplicate Fingerprint Suppressed
  {
    tradeId: 'TRD-10008',
    sourceSystemTradeId: 'SRC-77888',
    tradeVersion: 2,
    productType: 'FX_OPTION',
    tradeDate: '2026-01-15',
    effectiveDate: '2026-01-17',
    maturityDate: '2026-04-15',
    status: 'CONFIRMED',
    parties: [
      { partyRole: 'BANK', partyId: 'PTY-BANK-001', partyName: 'Apex Investment Banking AG' },
      { partyRole: 'COUNTERPARTY', partyId: 'PTY-CP-102', partyName: 'Vanguard Global Hedging Corp' },
    ],
    fxOptionEconomics: {
      notionalAmount: 1500000,
      notionalCurrency: 'USD',
      buySell: 'BUY',
      optionType: 'PUT',
      strike: 1.08,
      underlier: 'EUR/USD',
      premiumAmount: 18000,
      premiumCurrency: 'USD',
      premiumPaymentDate: '2026-01-17',
      internalPricingRef: 'PRC-UPDATED-V2', // Non-confirmable attribute changed!
    },
    nonEconomicTerms: {
      jurisdiction: 'GENERIC-US',
      confirmationMethod: 'ELECTRONIC',
      templateHint: 'FX_OPTION_STANDARD',
    },
    enrichmentMetadata: {
      resolvedTemplateId: 'TMPL-FX-OPT-001',
      resolvedTemplateVersion: '1.2.0',
      counterpartySTPEligible: true,
      productSTPEligible: true,
      enrichmentNotes: 'Confirmable fingerprint identical to Version 1; duplicate confirmation suppressed.',
    },
  },

  // 9. Clean STP EUR/USD Put
  {
    tradeId: 'TRD-10009',
    sourceSystemTradeId: 'SRC-77889',
    tradeVersion: 1,
    productType: 'FX_OPTION',
    tradeDate: '2026-01-15',
    effectiveDate: '2026-01-17',
    maturityDate: '2026-03-31',
    status: 'CONFIRMED',
    parties: [
      { partyRole: 'BANK', partyId: 'PTY-BANK-001', partyName: 'Apex Investment Banking AG' },
      { partyRole: 'COUNTERPARTY', partyId: 'PTY-CP-104', partyName: 'Nordic Sovereign Holdings' },
    ],
    fxOptionEconomics: {
      notionalAmount: 6000000,
      notionalCurrency: 'USD',
      buySell: 'SELL',
      optionType: 'PUT',
      strike: 1.075,
      underlier: 'EUR/USD',
      premiumAmount: 72000,
      premiumCurrency: 'USD',
      premiumPaymentDate: '2026-01-17',
    },
    nonEconomicTerms: {
      jurisdiction: 'GENERIC-SE',
      confirmationMethod: 'ELECTRONIC',
      templateHint: 'FX_OPTION_STANDARD',
    },
    enrichmentMetadata: {
      resolvedTemplateId: 'TMPL-FX-OPT-001',
      resolvedTemplateVersion: '1.2.0',
      counterpartySTPEligible: true,
      productSTPEligible: true,
    },
  },

  // 10. Legal documentation gap (Stretch)
  {
    tradeId: 'TRD-10010',
    sourceSystemTradeId: 'SRC-77890',
    tradeVersion: 1,
    productType: 'FX_OPTION',
    tradeDate: '2026-01-15',
    effectiveDate: '2026-01-17',
    maturityDate: '2026-08-15',
    status: 'WORKITEM_LINKED',
    parties: [
      { partyRole: 'BANK', partyId: 'PTY-BANK-001', partyName: 'Apex Investment Banking AG' },
      { partyRole: 'COUNTERPARTY', partyId: 'PTY-CP-103', partyName: 'Pacific Rim Asset Management' },
    ],
    fxOptionEconomics: {
      notionalAmount: 1200000,
      notionalCurrency: 'USD',
      buySell: 'BUY',
      optionType: 'CALL',
      strike: 1.088,
      underlier: 'EUR/USD',
      premiumAmount: 15000,
      premiumCurrency: 'USD',
      premiumPaymentDate: '2026-01-17',
    },
    nonEconomicTerms: {
      jurisdiction: 'GENERIC-BESPOKE',
      confirmationMethod: 'ELECTRONIC',
      templateHint: 'FX_OPTION_STANDARD',
    },
    enrichmentMetadata: {
      resolvedTemplateId: 'TMPL-FX-OPT-001',
      resolvedTemplateVersion: '1.2.0',
      counterpartySTPEligible: true,
      productSTPEligible: true,
      enrichmentNotes: 'Cross-border netting rider not on file for jurisdiction GENERIC-BESPOKE',
    },
  },
];

export const INITIAL_EXCEPTIONS: ExceptionRecord[] = [
  {
    exceptionId: 'EXC-30001',
    tradeId: 'TRD-10002',
    tradeVersion: 1,
    sourceMessageId: 'MSG-000002',
    correlationId: 'CORR-10002',
    category: 'MISSING_ECONOMIC_DATA',
    subTheme: 'MISSING_STRIKE',
    classificationConfidence: 1.0,
    reasonCode: 'ERR_ECON_MISSING_STRIKE',
    affectedFields: 'strike',
    recommendedAction: 'Inspect source trade payload for missing strike; request front-office blotter resend and trigger automated replay.',
    ownerQueue: 'DATA_REMEDIATION',
    replayMayResolve: true,
    resolutionDifficulty: 'LOW',
    operationalImpact: 'HIGH',
    capturedAt: '2026-01-15T10:32:00Z',
    slaDeadline: '2026-01-15T14:32:00Z',
    status: 'OPEN',
    linkedWorkItemId: 'WI-40001',
  },
  {
    exceptionId: 'EXC-30002',
    tradeId: 'TRD-10003',
    tradeVersion: 1,
    sourceMessageId: 'MSG-000003',
    correlationId: 'CORR-10003',
    category: 'MISSING_STATIC_REFERENCE_DATA',
    subTheme: 'UNKNOWN_COUNTERPARTY',
    classificationConfidence: 1.0,
    reasonCode: 'ERR_REF_UNKNOWN_COUNTERPARTY',
    affectedFields: 'parties[1].partyId',
    recommendedAction: 'Verify client LEI and onboarding status in Reference Master. Once static record is active, trigger replay to re-run enrichment.',
    ownerQueue: 'REFERENCE_DATA',
    replayMayResolve: true,
    resolutionDifficulty: 'MEDIUM',
    operationalImpact: 'HIGH',
    capturedAt: '2026-01-15T10:33:15Z',
    slaDeadline: '2026-01-15T12:33:15Z',
    status: 'OPEN',
    linkedWorkItemId: 'WI-40002',
  },
  {
    exceptionId: 'EXC-30003',
    tradeId: 'TRD-10004',
    tradeVersion: 1,
    sourceMessageId: 'MSG-000004',
    correlationId: 'CORR-10004',
    category: 'TEMPLATE_ISSUE',
    subTheme: 'NO_MATCHING_TEMPLATE',
    classificationConfidence: 1.0,
    reasonCode: 'ERR_TMPL_NO_MATCHING_TEMPLATE',
    affectedFields: 'templateHint, productType',
    recommendedAction: 'Create template maintenance work item. Review ISDA schedule for product variation and configure template catalogue entry.',
    ownerQueue: 'TEMPLATE_MAINTENANCE',
    replayMayResolve: false,
    resolutionDifficulty: 'HIGH',
    operationalImpact: 'MEDIUM',
    capturedAt: '2026-01-15T10:35:00Z',
    slaDeadline: '2026-01-15T16:35:00Z',
    status: 'OPEN',
    linkedWorkItemId: 'WI-40003',
  },
  {
    exceptionId: 'EXC-30004',
    linkedWorkItemId: 'WI-40006',
    tradeId: 'TRD-10005',
    tradeVersion: 1,
    sourceMessageId: 'MSG-000005',
    correlationId: 'CORR-10005',
    category: 'BUSINESS_RULE_FAILURE',
    subTheme: 'COUNTERPARTY_NOT_STP_ELIGIBLE',
    classificationConfidence: 1.0,
    reasonCode: 'ERR_RULE_COUNTERPARTY_NOT_STP_ELIGIBLE',
    affectedFields: 'parties.counterparty',
    recommendedAction: 'Route to Operations Review queue for four-eyes manual inspection or eligibility waiver approval.',
    ownerQueue: 'OPERATIONS_REVIEW',
    replayMayResolve: false,
    resolutionDifficulty: 'MEDIUM',
    operationalImpact: 'MEDIUM',
    capturedAt: '2026-01-15T10:36:20Z',
    slaDeadline: '2026-01-15T13:36:20Z',
    status: 'IN_PROGRESS',
  },
  {
    exceptionId: 'EXC-30005',
    tradeId: 'TRD-10007',
    tradeVersion: 1,
    sourceMessageId: 'MSG-000007',
    correlationId: 'CORR-10007',
    category: 'COUNTERPARTY_MATCHING_DISPUTE',
    subTheme: 'ECONOMIC_TERM_MISMATCH',
    classificationConfidence: 1.0,
    reasonCode: 'ERR_MATCH_ECONOMIC_TERM_MISMATCH',
    affectedFields: 'strike',
    recommendedAction: 'Generate bilateral term discrepancy report. Contact counterparty operations desk to reconcile strike/notional terms.',
    ownerQueue: 'OPERATIONS_REVIEW',
    replayMayResolve: false,
    resolutionDifficulty: 'HIGH',
    operationalImpact: 'CRITICAL',
    capturedAt: '2026-01-15T10:38:00Z',
    slaDeadline: '2026-01-15T11:38:00Z', // Tight SLA!
    status: 'OPEN',
    linkedWorkItemId: 'WI-40004',
  },
  {
    exceptionId: 'EXC-30006',
    tradeId: 'TRD-10010',
    tradeVersion: 1,
    sourceMessageId: 'MSG-000010',
    correlationId: 'CORR-10010',
    category: 'LEGAL_DOCUMENTATION_GAP',
    subTheme: 'MISSING_NETTING_AGREEMENT',
    classificationConfidence: 1.0,
    reasonCode: 'ERR_LEGAL_MISSING_NETTING_AGREEMENT',
    affectedFields: 'nonEconomicTerms.jurisdiction',
    recommendedAction: 'Route to Legal & Specialist review. Confirm active Master Agreement version or obtain confirmation rider.',
    ownerQueue: 'SPECIALIST_REVIEW',
    replayMayResolve: false,
    resolutionDifficulty: 'HIGH',
    operationalImpact: 'MEDIUM',
    capturedAt: '2026-01-15T10:40:00Z',
    slaDeadline: '2026-01-16T10:40:00Z',
    status: 'OPEN',
    linkedWorkItemId: 'WI-40005',
  },
];

export const INITIAL_WORK_ITEMS: WorkItem[] = [
  {
    workItemId: 'WI-40001',
    linkedExceptionId: 'EXC-30001',
    tradeId: 'TRD-10002',
    type: 'DATA_FIX',
    title: 'Missing Strike Price for TRD-10002',
    description: 'Front office trade blotter omitted contract strike price for EUR 3,500,000 Sell Put. Contact desk trader to re-key and trigger pipeline replay.',
    status: 'OPEN',
    ownerQueue: 'DATA_REMEDIATION',
    priority: 'HIGH',
    createdAt: '2026-01-15T10:32:05Z',
    updatedAt: '2026-01-15T10:32:05Z',
  },
  {
    workItemId: 'WI-40002',
    linkedExceptionId: 'EXC-30002',
    tradeId: 'TRD-10003',
    type: 'DATA_FIX',
    title: 'Counterparty PTY-CP-999 Unregistered in Reference Master',
    description: 'Inbound trade cites unverified counterparty entity PTY-CP-999. Static Data team must verify KYC / LEI registry before enabling STP.',
    status: 'IN_PROGRESS',
    ownerQueue: 'REFERENCE_DATA',
    priority: 'HIGH',
    createdAt: '2026-01-15T10:33:20Z',
    updatedAt: '2026-01-15T10:45:00Z',
  },
  {
    workItemId: 'WI-40003',
    linkedExceptionId: 'EXC-30003',
    tradeId: 'TRD-10004',
    type: 'TEMPLATE_FIX',
    title: 'Exotic TARF Template Variant Missing in Catalogue',
    description: 'Trade specifies templateHint FX_EXOTIC_TARF_V3. Template maintenance team must configure confirmation template rule in CouchDB template store.',
    status: 'OPEN',
    ownerQueue: 'TEMPLATE_MAINTENANCE',
    priority: 'MEDIUM',
    createdAt: '2026-01-15T10:35:10Z',
    updatedAt: '2026-01-15T10:35:10Z',
  },
  {
    workItemId: 'WI-40004',
    linkedExceptionId: 'EXC-30005',
    tradeId: 'TRD-10007',
    type: 'MANUAL_REVIEW',
    title: 'Bilateral Discrepancy on Strike Rate for TRD-10007',
    description: 'Internal record strike is 1.082; Meridian Capital counterparty affirmation states 1.085. Call counterparty middle office to resolve economic dispute.',
    status: 'OPEN',
    ownerQueue: 'OPERATIONS_REVIEW',
    priority: 'CRITICAL',
    createdAt: '2026-01-15T10:38:15Z',
    updatedAt: '2026-01-15T10:38:15Z',
  },
  {
    workItemId: 'WI-40006',
    linkedExceptionId: 'EXC-30004',
    tradeId: 'TRD-10005',
    type: 'MANUAL_REVIEW',
    title: 'Manual Review of Business Rule Failure for TRD-10005',
    description: 'Trade failed a business rule and was routed to the manual review queue for an operations decision.',
    status: 'OPEN',
    ownerQueue: 'OPERATIONS_REVIEW',
    priority: 'HIGH',
    createdAt: '2026-01-15T10:36:00Z',
    updatedAt: '2026-01-15T10:36:00Z',
  },
  {
    workItemId: 'WI-40005',
    linkedExceptionId: 'EXC-30006',
    tradeId: 'TRD-10010',
    type: 'RULE_UPDATE',
    title: 'Missing Netting Agreement for Bespoke Jurisdiction',
    description: 'Legal review required for jurisdiction GENERIC-BESPOKE under Pacific Rim Asset Management trade TRD-10010.',
    status: 'OPEN',
    ownerQueue: 'SPECIALIST_REVIEW',
    priority: 'MEDIUM',
    createdAt: '2026-01-15T10:40:05Z',
    updatedAt: '2026-01-15T10:40:05Z',
  },
];

export const INITIAL_CONFIRMATIONS: ConfirmationRecord[] = [
  {
    confirmationId: 'CONF-20001',
    tradeId: 'TRD-10001',
    tradeVersion: 1,
    sourceMessageId: 'MSG-000001',
    templateId: 'TMPL-FX-OPT-001',
    templateVersion: '1.2.0',
    status: 'MATCHED',
    confirmationFingerprint: '4f83b2a9e32047d7c6b98e1fa9801ec6a21356bce4812304910385ffae8213b1',
    generatedAt: '2026-01-15T10:30:08Z',
    generatedBy: 'validation-service',
    confirmationBody: `================================================================================
                    ISDA MASTER CONFIRMATION - FX OPTION
================================================================================
Trade ID:                 TRD-10001
Source Trade ID:          SRC-77881
Trade Date:               2026-01-15
Effective Date:           2026-01-17
Maturity / Expiry Date:   2026-04-17

PARTIES:
  Buyer:                  Apex Investment Banking AG (PTY-BANK-001)
  Seller:                 Meridian Capital Partners Ltd (PTY-CP-101)

ECONOMIC TERMS:
  Product:                FX Vanilla Call Option
  Underlying Pair:        EUR/USD
  Notional Amount:        USD 1,000,000.00
  Strike Rate:            1.0850
  Option Style:           European Call
  Premium Amount:         USD 12,000.00
  Premium Payment Date:   2026-01-17
  Settlement Type:        Deliverable (T+2 Spot)

GOVERNING TERMS:
  Governing Law:          English Law
  Confirmation Method:    ELECTRONIC MATCHED
  Confirmation Hash:      4f83b2a9e32047d7c6b98e1fa9801ec6a21356bce4812304910385ffae8213b1
================================================================================`,
  },
  {
    confirmationId: 'CONF-20002',
    tradeId: 'TRD-10008',
    tradeVersion: 2,
    sourceMessageId: 'MSG-000008',
    templateId: 'TMPL-FX-OPT-001',
    templateVersion: '1.2.0',
    status: 'MATCHED',
    confirmationFingerprint: '9c72e411b084ad2287f394156cebb0801a3577dce72819028409247192bc7e43',
    previousConfirmationFingerprint: '9c72e411b084ad2287f394156cebb0801a3577dce72819028409247192bc7e43',
    generatedAt: '2026-01-15T10:39:12Z',
    generatedBy: 'validation-service',
    isDuplicateSuppressed: true,
    confirmationBody: `================================================================================
                    ISDA MASTER CONFIRMATION - FX OPTION (VERSION 2)
================================================================================
Trade ID:                 TRD-10008 (Version 2)
Source Trade ID:          SRC-77888
Notice:                   * CONFIRMABLE TERMS UNCHANGED *
                          Duplicate confirmation transmission suppressed by
                          Fingerprint Hash match: 9c72e411b084ad...

PARTIES:
  Buyer:                  Apex Investment Banking AG (PTY-BANK-001)
  Seller:                 Vanguard Global Hedging Corp (PTY-CP-102)

ECONOMIC TERMS:
  Product:                FX Vanilla Put Option
  Underlying Pair:        EUR/USD
  Notional Amount:        USD 1,500,000.00
  Strike Rate:            1.0800
  Premium Amount:         USD 18,000.00
================================================================================`,
  },
  {
    confirmationId: 'CONF-20003',
    tradeId: 'TRD-10009',
    tradeVersion: 1,
    sourceMessageId: 'MSG-000009',
    templateId: 'TMPL-FX-OPT-001',
    templateVersion: '1.2.0',
    status: 'MATCHED',
    confirmationFingerprint: '3a11b6980ec29815049bba142387e0293847a98bc14589827364512938475628',
    generatedAt: '2026-01-15T10:39:55Z',
    generatedBy: 'validation-service',
    confirmationBody: `================================================================================
                    ISDA MASTER CONFIRMATION - FX OPTION
================================================================================
Trade ID:                 TRD-10009
Source Trade ID:          SRC-77889
Trade Date:               2026-01-15
Effective Date:           2026-01-17
Maturity / Expiry Date:   2026-03-31

PARTIES:
  Buyer:                  Apex Investment Banking AG (PTY-BANK-001)
  Seller:                 Nordic Sovereign Holdings (PTY-CP-104)

ECONOMIC TERMS:
  Product:                FX Vanilla Put Option
  Underlying Pair:        EUR/USD
  Notional Amount:        USD 6,000,000.00
  Strike Rate:            1.0750
  Premium Amount:         USD 72,000.00
================================================================================`,
  },
];

export const INITIAL_DLQ_ENTRIES: DeadLetterEvent[] = [
  {
    dlqId: 'DLQ-90001',
    tradeId: 'TRD-10006',
    messageHeader: {
      messageId: 'DLQ-MSG-90001',
      correlationId: 'CORR-10006',
      eventType: 'NEW_TRADE',
      schemaVersion: 'capstone-fpml-json-1.0',
      createdTimestamp: '2026-01-15T10:37:02Z',
    },
    originalMessageHeader: {
      messageId: 'MSG-000006',
      correlationId: 'CORR-10006',
      eventType: 'NEW_TRADE',
      schemaVersion: 'capstone-fpml-json-1.0',
      createdTimestamp: '2026-01-15T10:36:45Z',
    },
    failedOnTopic: 'trade.accepted',
    failedInService: 'enrichment-service',
    failureReason: 'Enrichment timeout after 3 retries: CouchDB reference socket read timeout (5000ms)',
    exceptionClass: 'java.util.concurrent.TimeoutException',
    stackTraceExcerpt: `com.tcem.enrichment.exception.EnrichmentTimeoutException: CouchDB reference socket read timeout (5000ms)
    at com.tcem.enrichment.client.CouchDbReferenceClient.fetchCounterparty(CouchDbReferenceClient.java:84)
    at com.tcem.enrichment.service.EnrichmentService.enrichTrade(EnrichmentService.java:112)
    at com.tcem.enrichment.listener.TradeAcceptedConsumer.onMessage(TradeAcceptedConsumer.java:45)
    at org.springframework.kafka.listener.KafkaMessageListenerContainer$ListenerConsumer.run(KafkaMessageListenerContainer.java:1250)
    Caused by: java.net.SocketTimeoutException: Read timed out`,
    failedAt: '2026-01-15T10:37:00Z',
    replayCount: 0,
    replayable: true,
    status: 'PENDING_REPLAY',
    originalPayload: JSON.stringify({
      messageHeader: {
        messageId: 'MSG-000006',
        correlationId: 'CORR-10006',
        eventType: 'NEW_TRADE',
        schemaVersion: 'capstone-fpml-json-1.0',
        createdTimestamp: '2026-01-15T10:36:45Z',
      },
      trade: {
        tradeId: 'TRD-10006',
        sourceSystemTradeId: 'SRC-77886',
        productType: 'FX_OPTION',
        parties: [
          { partyRole: 'BANK', partyId: 'PTY-BANK-001' },
          { partyRole: 'COUNTERPARTY', partyId: 'PTY-CP-104' },
        ],
        economics: {
          notionalAmount: 8000000,
          notionalCurrency: 'EUR',
          buySell: 'BUY',
          optionType: 'PUT',
          strike: 11.25,
          underlier: 'EUR/SEK',
          premiumAmount: 95000,
          premiumCurrency: 'EUR',
        },
      },
    }, null, 2),
  },
];

export const INITIAL_AUDIT_TRAIL: AuditEvent[] = [
  {
    auditEventId: 'AUD-50001',
    tradeId: 'TRD-10001',
    messageId: 'MSG-000001',
    correlationId: 'CORR-10001',
    fromStatus: null,
    toStatus: 'RECEIVED',
    serviceName: 'ingestion-service',
    occurredAt: '2026-01-15T10:30:00Z',
    detail: 'Inbound trade received on trade.raw from synthetic producer',
    isReplay: false,
  },
  {
    auditEventId: 'AUD-50002',
    tradeId: 'TRD-10001',
    messageId: 'MSG-000001',
    correlationId: 'CORR-10001',
    fromStatus: 'RECEIVED',
    toStatus: 'ACCEPTED',
    serviceName: 'ingestion-service',
    occurredAt: '2026-01-15T10:30:01Z',
    detail: 'Schema validation passed; published to trade.accepted',
    isReplay: false,
  },
  {
    auditEventId: 'AUD-50003',
    tradeId: 'TRD-10001',
    messageId: 'MSG-000001',
    correlationId: 'CORR-10001',
    fromStatus: 'ACCEPTED',
    toStatus: 'ENRICHED',
    serviceName: 'enrichment-service',
    occurredAt: '2026-01-15T10:30:03Z',
    detail: 'Reference data enrichment successful (Counterparty PTY-CP-101 verified, template TMPL-FX-OPT-001 attached)',
    isReplay: false,
  },
  {
    auditEventId: 'AUD-50004',
    tradeId: 'TRD-10001',
    messageId: 'MSG-000001',
    correlationId: 'CORR-10001',
    fromStatus: 'ENRICHED',
    toStatus: 'STP_PASSED',
    serviceName: 'validation-service',
    occurredAt: '2026-01-15T10:30:05Z',
    detail: 'STP rule validation passed without discrepancies',
    isReplay: false,
  },
  {
    auditEventId: 'AUD-50005',
    tradeId: 'TRD-10001',
    messageId: 'MSG-000001',
    correlationId: 'CORR-10001',
    fromStatus: 'STP_PASSED',
    toStatus: 'CONFIRMED',
    serviceName: 'validation-service',
    occurredAt: '2026-01-15T10:30:08Z',
    detail: 'Confirmation CONF-20001 generated; published to confirmation.generated topic',
    isReplay: false,
  },
  {
    auditEventId: 'AUD-50006',
    tradeId: 'TRD-10002',
    messageId: 'MSG-000002',
    correlationId: 'CORR-10002',
    fromStatus: 'ENRICHED',
    toStatus: 'STP_FAILED',
    serviceName: 'validation-service',
    occurredAt: '2026-01-15T10:32:00Z',
    detail: 'STP validation failed: mandatory field strike is missing',
    isReplay: false,
  },
  {
    auditEventId: 'AUD-50007',
    tradeId: 'TRD-10002',
    messageId: 'MSG-000002',
    correlationId: 'CORR-10002',
    fromStatus: 'STP_FAILED',
    toStatus: 'EXCEPTION_CAPTURED',
    serviceName: 'exception-service',
    occurredAt: '2026-01-15T10:32:01Z',
    detail: 'Exception EXC-30001 classified as MISSING_ECONOMIC_DATA (MISSING_STRIKE)',
    isReplay: false,
  },
  {
    auditEventId: 'AUD-50008',
    tradeId: 'TRD-10002',
    messageId: 'MSG-000002',
    correlationId: 'CORR-10002',
    fromStatus: 'EXCEPTION_CAPTURED',
    toStatus: 'WORKITEM_LINKED',
    serviceName: 'workitem-service',
    occurredAt: '2026-01-15T10:32:05Z',
    detail: 'Work Item WI-40001 created and linked to exception EXC-30001',
    isReplay: false,
  },
  {
    auditEventId: 'AUD-50009',
    tradeId: 'TRD-10006',
    messageId: 'MSG-000006',
    correlationId: 'CORR-10006',
    fromStatus: 'ACCEPTED',
    toStatus: 'DEAD_LETTER',
    serviceName: 'enrichment-service',
    occurredAt: '2026-01-15T10:37:00Z',
    detail: 'Enrichment socket timeout; routed to deadletter.trade with DLQ-90001',
    isReplay: false,
  },
];
