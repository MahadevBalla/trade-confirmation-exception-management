import type {
  AuditEvent,
  ConfirmationRecord,
  DeadLetterEvent,
  ExceptionRecord,
  MessageHeader,
  Trade,
  WorkItem,
} from '../types/tcem';
import type {
  LiveAuditDocument,
  LiveConfirmationDocument,
  LiveDlqDocument,
  LiveExceptionDocument,
  LiveTradeDocument,
  LiveWorkItemDocument,
} from './api';

export const adaptTrade = (doc: LiveTradeDocument): Trade => {
  const canonical = doc.canonicalTrade ?? {};
  return {
    tradeId: doc.tradeId,
    sourceSystemTradeId: doc.sourceSystemTradeId ?? '',
    tradeVersion: doc.tradeVersion ?? 1,
    productType: doc.productType ?? canonical.productType ?? 'UNKNOWN',
    tradeDate: canonical.tradeDate ?? '',
    effectiveDate: canonical.effectiveDate ?? '',
    maturityDate: canonical.maturityDate ?? '',
    parties: canonical.parties ?? [],
    fxOptionEconomics: canonical.fxOptionEconomics,
    nonEconomicTerms: canonical.nonEconomicTerms,
    enrichmentMetadata: canonical.enrichmentMetadata,
    status: doc.processingStatus ?? canonical.status ?? 'RECEIVED',
    receivedAt: doc.receivedTimestamp,
  };
};

export const adaptWorkItem = (doc: LiveWorkItemDocument): WorkItem => ({
  workItemId: doc.workItemId,
  linkedExceptionId: doc.linkedExceptionId ?? doc.linkedExceptionIds?.[0],
  tradeId: doc.tradeId,
  type: doc.type,
  title: doc.title,
  description: doc.description ?? '',
  status: doc.status,
  ownerQueue: doc.ownerQueue ?? 'OPERATIONS_REVIEW',
  priority: doc.priority ?? 'MEDIUM',
  createdAt: doc.createdAt ?? '',
  updatedAt: doc.updatedAt ?? doc.createdAt ?? '',
  resolvedAt: doc.resolvedAt,
});

export const adaptException = (
  doc: LiveExceptionDocument,
  workItems: LiveWorkItemDocument[]
): ExceptionRecord => {
  const item = workItems.find(
    (workItem) =>
      workItem.linkedExceptionId === doc.exceptionId ||
      workItem.linkedExceptionIds?.includes(doc.exceptionId)
  );
  const status = item?.status === 'DONE'
    ? 'RESOLVED'
    : item?.status === 'CANCELLED'
      ? 'CLOSED'
      : item?.status === 'IN_PROGRESS'
        ? 'IN_PROGRESS'
        : doc.status ?? 'OPEN';

  return {
    exceptionId: doc.exceptionId,
    tradeId: doc.tradeId,
    tradeVersion: doc.tradeVersion,
    sourceMessageId: doc.sourceMessageId,
    correlationId: doc.correlationId,
    category: doc.category,
    subTheme: doc.subTheme,
    classificationConfidence: doc.classificationConfidence,
    reasonCode: doc.reasonCode,
    affectedFields: doc.affectedFields ?? '',
    recommendedAction: doc.recommendedAction ?? '',
    ownerQueue: doc.ownerQueue ?? 'OPERATIONS_REVIEW',
    replayMayResolve: doc.replayMayResolve,
    resolutionDifficulty: doc.resolutionDifficulty ?? 'MEDIUM',
    operationalImpact: doc.operationalImpact ?? 'MEDIUM',
    capturedAt: doc.capturedAt ?? '',
    slaDeadline: doc.slaDeadline ?? '',
    status,
    resolvedAt: item?.resolvedAt ?? doc.resolvedAt,
    linkedWorkItemId: item?.workItemId ?? doc.linkedWorkItemId,
    resolutionNotes: item?.resolutionNotes,
  };
};

export const adaptConfirmation = (doc: LiveConfirmationDocument): ConfirmationRecord => doc;

export const adaptDlq = (doc: LiveDlqDocument): DeadLetterEvent => {
  const original = doc.originalMessageHeader;
  const header: MessageHeader = {
    messageId: original?.messageId ?? doc.messageId ?? '',
    correlationId: original?.correlationId ?? doc.correlationId ?? '',
    eventType: original?.eventType ?? 'NEW_TRADE',
    schemaVersion: original?.schemaVersion ?? '',
    createdTimestamp: original?.createdTimestamp ?? doc.failedAt ?? '',
  };
  return {
    id: doc._id,
    dlqId: doc._id,
    dlqStatus: doc.dlqStatus,
    status: doc.dlqStatus,
    tradeId: doc.tradeId ?? '',
    messageHeader: header,
    originalMessageHeader: header,
    originalPayload: doc.originalPayload ?? '',
    failedOnTopic: doc.failedOnTopic ?? '',
    failedInService: doc.failedInService ?? '',
    failureReason: doc.failureReason ?? '',
    exceptionClass: doc.exceptionClass,
    stackTraceExcerpt: doc.stackTraceExcerpt,
    failedAt: doc.failedAt ?? '',
    replayCount: doc.replayCount ?? 0,
    replayable: doc.replayable ?? false,
  };
};

export const adaptAudit = (doc: LiveAuditDocument): AuditEvent => ({
  auditEventId: doc._id ?? `${doc.tradeId ?? 'unknown'}:${doc.timestamp ?? ''}`,
  tradeId: doc.tradeId ?? '',
  messageId: doc.correlationId ?? '',
  correlationId: doc.correlationId ?? '',
  fromStatus: null,
  toStatus: doc.status ?? 'RECEIVED',
  serviceName: doc.sourceService ?? 'unknown',
  occurredAt: doc.timestamp ?? '',
  detail: doc.details ?? '',
  isReplay: doc.eventType === 'REPLAY',
  stage: doc.stage,
});
