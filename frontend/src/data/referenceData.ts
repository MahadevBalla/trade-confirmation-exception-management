import type { ExceptionCategory, OwnerQueue, Priority } from '../types/tcem';

export interface CounterpartyRef {
  id: string;
  name: string;
  lei: string;
  jurisdiction: string;
  stpEligible: boolean;
  creditRating: string;
}

export interface ConfirmationTemplateRef {
  templateId: string;
  templateVersion: string;
  productType: string;
  description: string;
  governingLaw: string;
  settlementConvention: string;
  mandatoryFields: string[];
}

export const MOCK_COUNTERPARTIES: Record<string, CounterpartyRef> = {
  'PTY-BANK-001': {
    id: 'PTY-BANK-001',
    name: 'Apex Investment Banking AG',
    lei: '5493006MHB84DD0ZWV18',
    jurisdiction: 'GENERIC-DE',
    stpEligible: true,
    creditRating: 'AA+',
  },
  'PTY-CP-101': {
    id: 'PTY-CP-101',
    name: 'Meridian Capital Partners Ltd',
    lei: '213800B1DYX92Z5P4471',
    jurisdiction: 'GENERIC-UK',
    stpEligible: true,
    creditRating: 'A-',
  },
  'PTY-CP-102': {
    id: 'PTY-CP-102',
    name: 'Vanguard Global Hedging Corp',
    lei: '549300O8G3412B9D6142',
    jurisdiction: 'GENERIC-US',
    stpEligible: true,
    creditRating: 'AAA',
  },
  'PTY-CP-103': {
    id: 'PTY-CP-103',
    name: 'Pacific Rim Asset Management',
    lei: '984500732A1BC9D8F102',
    jurisdiction: 'GENERIC-SG',
    stpEligible: false, // Flagged for manual review by policy
    creditRating: 'BBB+',
  },
  'PTY-CP-104': {
    id: 'PTY-CP-104',
    name: 'Nordic Sovereign Holdings',
    lei: '529900T8Z91B33CC8811',
    jurisdiction: 'GENERIC-SE',
    stpEligible: true,
    creditRating: 'AA',
  },
};

export const MOCK_TEMPLATES: Record<string, ConfirmationTemplateRef> = {
  'TMPL-FX-OPT-001': {
    templateId: 'TMPL-FX-OPT-001',
    templateVersion: '1.2.0',
    productType: 'FX_OPTION',
    description: 'ISDA Master Standard FX Vanilla Call/Put Confirmation',
    governingLaw: 'English Law',
    settlementConvention: 'T+2 Spot Convention',
    mandatoryFields: ['notionalAmount', 'notionalCurrency', 'buySell', 'optionType', 'strike', 'underlier', 'premiumAmount', 'premiumCurrency'],
  },
  'TMPL-FX-OPT-BARRIER': {
    templateId: 'TMPL-FX-OPT-BARRIER',
    templateVersion: '2.0.0',
    productType: 'FX_OPTION',
    description: 'ISDA Schedule for FX Knock-in / Knock-out Barrier Options',
    governingLaw: 'New York Law',
    settlementConvention: 'T+2 Delivery',
    mandatoryFields: ['notionalAmount', 'notionalCurrency', 'buySell', 'optionType', 'strike', 'underlier', 'barrierLevel'],
  },
  'TMPL-EQ-SWAP-001': {
    templateId: 'TMPL-EQ-SWAP-001',
    templateVersion: '1.0.0',
    productType: 'EQUITY_SWAP',
    description: 'Single Stock Equity Total Return Swap Master Confirmation',
    governingLaw: 'English Law',
    settlementConvention: 'Cash Settlement on Scheduled Valuation',
    mandatoryFields: ['notionalAmount', 'equityUnderlier', 'fixedRate', 'spread'],
  },
};

export interface CategoryInfo {
  label: string;
  description: string;
  defaultOwner: OwnerQueue;
  defaultPriority: Priority;
  color: string;
  badgeBg: string;
}

export const CATEGORY_METADATA: Record<ExceptionCategory, CategoryInfo> = {
  MISSING_ECONOMIC_DATA: {
    label: 'Missing Economic Data',
    description: 'Strike, notional, premium, rate, or underlier missing from inbound trade',
    defaultOwner: 'DATA_REMEDIATION',
    defaultPriority: 'HIGH',
    color: 'text-rose-500',
    badgeBg: 'bg-rose-500/10 text-rose-400 border-rose-500/30',
  },
  MISSING_STATIC_REFERENCE_DATA: {
    label: 'Missing Static Reference Data',
    description: 'Counterparty, instrument, or jurisdiction not found in reference catalogues',
    defaultOwner: 'REFERENCE_DATA',
    defaultPriority: 'HIGH',
    color: 'text-amber-500',
    badgeBg: 'bg-amber-500/10 text-amber-400 border-amber-500/30',
  },
  TEMPLATE_ISSUE: {
    label: 'Template Issue',
    description: 'No valid template found or product/template version mismatch',
    defaultOwner: 'TEMPLATE_MAINTENANCE',
    defaultPriority: 'MEDIUM',
    color: 'text-purple-500',
    badgeBg: 'bg-purple-500/10 text-purple-400 border-purple-500/30',
  },
  BUSINESS_RULE_FAILURE: {
    label: 'Business Rule Failure',
    description: 'Client or product ineligible for STP; regulatory or policy constraint',
    defaultOwner: 'OPERATIONS_REVIEW',
    defaultPriority: 'MEDIUM',
    color: 'text-blue-500',
    badgeBg: 'bg-blue-500/10 text-blue-400 border-blue-500/30',
  },
  TECHNICAL_PROCESSING_FAILURE: {
    label: 'Technical Processing Failure',
    description: 'Enrichment timeout, parse failure, broker disconnect, or system fault',
    defaultOwner: 'TECHNOLOGY_SUPPORT',
    defaultPriority: 'CRITICAL',
    color: 'text-red-500',
    badgeBg: 'bg-red-500/10 text-red-400 border-red-500/30',
  },
  COUNTERPARTY_MATCHING_DISPUTE: {
    label: 'Counterparty Dispute',
    description: 'Economic or non-economic discrepancy between reported party terms',
    defaultOwner: 'OPERATIONS_REVIEW',
    defaultPriority: 'HIGH',
    color: 'text-orange-500',
    badgeBg: 'bg-orange-500/10 text-orange-400 border-orange-500/30',
  },
  LEGAL_DOCUMENTATION_GAP: {
    label: 'Legal Documentation Gap',
    description: 'Missing ISDA Master Agreement, netting schedule, or bespoke clause',
    defaultOwner: 'SPECIALIST_REVIEW',
    defaultPriority: 'MEDIUM',
    color: 'text-indigo-500',
    badgeBg: 'bg-indigo-500/10 text-indigo-400 border-indigo-500/30',
  },
  FALSE_POSITIVE_CONFIRMABLE_EVENT: {
    label: 'False Positive Confirmable',
    description: 'Non-confirmable processing metadata amendment incorrectly triggered pipeline',
    defaultOwner: 'RULES_OWNER',
    defaultPriority: 'LOW',
    color: 'text-slate-500',
    badgeBg: 'bg-slate-500/10 text-slate-400 border-slate-500/30',
  },
};
