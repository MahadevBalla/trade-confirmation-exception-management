/**
 * TypeScript definitions mirroring common-lib Java DTOs, Kafka events, and domain models.
 * All types are fully erasable (no TS runtime enums) for TypeScript 5.8+ erasableSyntaxOnly compatibility.
 */

// ---------------------------------------------------------------------------
// Constants & Taxonomy
// ---------------------------------------------------------------------------

export type ProductType = 'FX_OPTION' | 'EQUITY_SWAP' | 'IR_SWAP' | 'IRS' | 'FX_FORWARD' | 'UNKNOWN';

export type EventType =
  | 'NEW_TRADE'
  | 'AMEND_TRADE'
  | 'CANCEL_TRADE'
  | 'REPLAY'
  | 'NOVATION'
  | 'COMPRESSION'
  | 'HEARTBEAT';

export type ProcessingStatus =
  | 'RECEIVED'
  | 'SCHEMA_INVALID'
  | 'ACCEPTED'
  | 'ENRICHING'
  | 'ENRICHMENT_FAILED'
  | 'ENRICHED'
  | 'VALIDATING'
  | 'STP_PASSED'
  | 'STP_FAILED'
  | 'CONFIRMED'
  | 'EXCEPTION_CAPTURED'
  | 'WORKITEM_LINKED'
  | 'MANUAL_REVIEW'
  | 'RESOLVED'
  | 'DEAD_LETTER'
  | 'REPLAYED';

export type ConfirmationStatus =
  | 'MATCHED'
  | 'MISMATCHED'
  | 'ALLEGED'
  | 'UNMATCHED'
  | 'FAILED'
  | 'SUPERSEDED';

export type ExceptionCategory =
  | 'MISSING_ECONOMIC_DATA'
  | 'MISSING_STATIC_REFERENCE_DATA'
  | 'TEMPLATE_ISSUE'
  | 'BUSINESS_RULE_FAILURE'
  | 'TECHNICAL_PROCESSING_FAILURE'
  | 'COUNTERPARTY_MATCHING_DISPUTE'
  | 'LEGAL_DOCUMENTATION_GAP'
  | 'FALSE_POSITIVE_CONFIRMABLE_EVENT';

export type ExceptionSubTheme =
  // MISSING_ECONOMIC_DATA
  | 'MISSING_STRIKE'
  | 'MISSING_NOTIONAL'
  | 'MISSING_PREMIUM'
  | 'MISSING_PREMIUM_CURRENCY'
  | 'MISSING_UNDERLIER'
  | 'MISSING_EXPIRY_DATE'
  | 'MISSING_OPTION_TYPE'
  // MISSING_STATIC_REFERENCE_DATA
  | 'UNKNOWN_COUNTERPARTY'
  | 'UNKNOWN_INSTRUMENT'
  | 'MISSING_JURISDICTION'
  | 'MISSING_TEMPLATE_CATALOGUE_ENTRY'
  // TEMPLATE_ISSUE
  | 'NO_MATCHING_TEMPLATE'
  | 'PRODUCT_TEMPLATE_MISMATCH'
  | 'TEMPLATE_VERSION_MISMATCH'
  // BUSINESS_RULE_FAILURE
  | 'COUNTERPARTY_NOT_STP_ELIGIBLE'
  | 'PRODUCT_NOT_STP_ELIGIBLE'
  | 'TRADE_DATE_MISMATCH'
  | 'VALUE_DATE_HOLIDAY'
  // TECHNICAL_PROCESSING_FAILURE
  | 'DUPLICATE_MESSAGE'
  | 'PARSE_FAILURE'
  | 'ENRICHMENT_TIMEOUT'
  | 'SCHEMA_VERSION_UNSUPPORTED'
  | 'DLQ_REPROCESSING_FAILURE'
  // COUNTERPARTY_MATCHING_DISPUTE
  | 'ECONOMIC_TERM_MISMATCH'
  | 'NON_ECONOMIC_TERM_MISMATCH'
  | 'PARTY_ROLE_MISMATCH'
  // LEGAL_DOCUMENTATION_GAP
  | 'MISSING_NETTING_AGREEMENT'
  | 'BESPOKE_CLAUSE_REQUIRED'
  // FALSE_POSITIVE_CONFIRMABLE_EVENT
  | 'NON_CONFIRMABLE_ATTRIBUTE_CHANGED'
  | 'UNKNOWN';

export type WorkItemType =
  | 'DATA_FIX'
  | 'TEMPLATE_FIX'
  | 'RULE_UPDATE'
  | 'MANUAL_REVIEW'
  | 'ENHANCEMENT';

export type WorkItemStatus = 'OPEN' | 'IN_PROGRESS' | 'DONE' | 'CANCELLED';

export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export type ResolutionDifficulty = 'LOW' | 'MEDIUM' | 'HIGH';

export type OwnerQueue =
  | 'DATA_REMEDIATION'
  | 'OPERATIONS_REVIEW'
  | 'TEMPLATE_MAINTENANCE'
  | 'TECHNOLOGY_SUPPORT'
  | 'REFERENCE_DATA'
  | 'SPECIALIST_REVIEW'
  | 'RULES_OWNER';

// ---------------------------------------------------------------------------
// Canonical Trade & FpML-inspired Message Models
// ---------------------------------------------------------------------------

export interface MessageHeader {
  messageId: string;
  correlationId: string;
  eventType: EventType;
  schemaVersion: string;
  createdTimestamp: string;
}

export interface Party {
  partyRole: 'BANK' | 'COUNTERPARTY' | 'BROKER' | 'CLEARING_HOUSE';
  partyId: string;
  partyName?: string;
  legalEntityName?: string;
  lei?: string;
  country?: string;
}

export interface FxOptionEconomics {
  notionalAmount?: number;
  notionalCurrency?: string;
  buySell?: 'BUY' | 'SELL';
  optionType?: 'CALL' | 'PUT';
  strike?: number;
  underlier?: string;
  premiumAmount?: number;
  premiumCurrency?: string;
  premiumPaymentDate?: string;
  internalPricingRef?: string;
}

export interface NonEconomicTerms {
  jurisdiction?: string;
  confirmationMethod?: string;
  templateHint?: string;
}

export interface EnrichmentSource {
  sourceSystem: string;
  lookupKey: string;
  retrievedAt: string;
  derivationMethod: 'EXACT_LOOKUP' | 'DEFAULTED' | 'DERIVED' | 'FALLBACK';
}

export interface ReferenceDataEnrichment {
  resolvedTemplateId?: string;
  resolvedTemplateVersion?: string;
  counterpartySTPEligible?: boolean;
  productSTPEligible?: boolean;
  fieldProvenance?: Record<string, EnrichmentSource>;
  enrichmentNotes?: string;
}

export interface Trade {
  tradeId: string;
  sourceSystemTradeId: string;
  tradeVersion: number;
  productType: ProductType;
  tradeDate: string;
  effectiveDate: string;
  maturityDate: string;
  parties: Party[];
  fxOptionEconomics?: FxOptionEconomics;
  nonEconomicTerms?: NonEconomicTerms;
  enrichmentMetadata?: ReferenceDataEnrichment;
  status: ProcessingStatus;
  receivedAt?: string;
}

export interface TradeEvent {
  messageHeader: MessageHeader;
  trade: Trade;
}

// ---------------------------------------------------------------------------
// Confirmation Models
// ---------------------------------------------------------------------------

export interface ConfirmationRecord {
  confirmationId: string;
  tradeId: string;
  tradeVersion: number;
  sourceMessageId: string;
  templateId: string;
  templateVersion: string;
  status: ConfirmationStatus;
  confirmationFingerprint: string;
  previousConfirmationFingerprint?: string;
  confirmationBody: string;
  generatedAt: string;
  generatedBy: string;
  isDuplicateSuppressed?: boolean;
}

// ---------------------------------------------------------------------------
// Exception Models
// ---------------------------------------------------------------------------

export interface ExceptionRecord {
  exceptionId: string;
  tradeId: string;
  tradeVersion: number;
  sourceMessageId: string;
  correlationId: string;
  category: ExceptionCategory;
  subTheme: ExceptionSubTheme;
  classificationConfidence: number;
  reasonCode: string;
  affectedFields: string;
  recommendedAction: string;
  ownerQueue: OwnerQueue;
  replayMayResolve: boolean;
  resolutionDifficulty: ResolutionDifficulty;
  operationalImpact: Priority;
  capturedAt: string;
  slaDeadline: string;
  status: 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED' | 'REPLAYED';
  resolvedAt?: string;
  linkedWorkItemId?: string;
  resolutionNotes?: string;
}

// ---------------------------------------------------------------------------
// Work Item Models
// ---------------------------------------------------------------------------

export interface WorkItem {
  workItemId: string;
  linkedExceptionId?: string;
  tradeId: string;
  type: WorkItemType;
  title: string;
  description: string;
  status: WorkItemStatus;
  ownerQueue: OwnerQueue;
  priority: Priority;
  createdAt: string;
  updatedAt: string;
  resolvedAt?: string;
}

// ---------------------------------------------------------------------------
// Audit Event Models
// ---------------------------------------------------------------------------

export interface AuditEvent {
  auditEventId: string;
  tradeId: string;
  messageId: string;
  correlationId: string;
  fromStatus?: ProcessingStatus | null;
  toStatus: ProcessingStatus;
  serviceName: string;
  occurredAt: string;
  detail: string;
  actorUserId?: string;
  isReplay: boolean;
  stage?: string;
}

export type DataMode = 'demo' | 'live';

export interface ConnectionState {
  status: 'connecting' | 'connected' | 'partial' | 'offline';
  unavailable: string[];
  lastUpdated: string | null;
}

// ---------------------------------------------------------------------------
// Dead Letter Event Models
// ---------------------------------------------------------------------------

export interface DeadLetterEvent {
  id?: string;
  dlqId?: string;
  dlqStatus?: string;
  status?: 'PENDING_REPLAY' | 'REPLAYING' | 'REPLAY_SUCCEEDED' | 'PERMANENTLY_FAILED';
  messageHeader: MessageHeader;
  originalMessageHeader: MessageHeader;
  originalPayload: string;
  failedOnTopic: string;
  failedInService: string;
  failureReason: string;
  exceptionClass?: string;
  stackTraceExcerpt?: string;
  failedAt: string;
  replayCount: number;
  replayable: boolean;
  tradeId: string;
}

// ---------------------------------------------------------------------------
// Operations Metrics & Filters
// ---------------------------------------------------------------------------

export interface OperationsMetrics {
  totalTrades: number;
  stpRatePercent: number;
  stpPassedCount: number;
  exceptionsCount: number;
  openExceptionsCount: number;
  activeWorkItemsCount: number;
  dlqCount: number;
  slaBreachRiskCount: number;
  avgResolutionTimeMinutes: number;
}

export type UserRole = 'OPERATIONS_SPECIALIST' | 'SUPERVISOR' | 'SYSTEM_ADMIN';
