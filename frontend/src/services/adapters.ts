/**
 * Pure conversions from backend documents to the types the screens use, and values derived from them.
 */
import type {
  AuditEvent,
  ConfirmationRecord,
  DeadLetterEvent,
  ExceptionRecord,
  MessageHeader,
  OperationsMetrics,
  ProcessingStatus,
  Trade,
  WorkItem,
} from '../types/tcem';
import type { StreamEventNotice } from '../context/AppContextInstance';

export interface TradeDoc {
  tradeId: string;
  sourceSystemTradeId?: string;
  tradeVersion?: number;
  productType?: Trade['productType'];
  messageId?: string;
  correlationId?: string;
  schemaVersion?: string;
  processingStatus?: ProcessingStatus;
  receivedTimestamp?: string;
  canonicalTrade?: Partial<Trade> | null;
}

export type ConfirmationDoc = ConfirmationRecord;

export type ExceptionDoc = Omit<ExceptionRecord, 'status'> & { status?: string };

export type WorkItemDoc = Omit<WorkItem, 'linkedExceptionId'> & {
  linkedExceptionId?: string;
  linkedExceptionIds?: string[];
  resolutionNotes?: string;
  reviewDecision?: string;
};

export interface DlqDoc {
  _id: string;
  tradeId?: string | null;
  messageId?: string;
  correlationId?: string;
  originalMessageHeader?: MessageHeader | null;
  originalPayload?: string;
  failedOnTopic?: string;
  failedInService?: string;
  failureReason?: string;
  exceptionClass?: string;
  stackTraceExcerpt?: string;
  failedAt?: string;
  replayCount?: number;
  replayable?: boolean;
  dlqStatus?: DeadLetterEvent['status'];
  aliasOf?: string;
}

export interface AuditDoc {
  _id?: string;
  tradeId?: string | null;
  correlationId?: string;
  eventType?: string;
  stage?: string;
  status?: ProcessingStatus;
  timestamp?: string;
  details?: string;
  sourceService?: string;
}

export const adaptTrade = (doc: TradeDoc): Trade => {
  const canonical = doc.canonicalTrade ?? {};
  return {
    ...canonical,
    tradeId: doc.tradeId,
    sourceSystemTradeId: canonical.sourceSystemTradeId ?? doc.sourceSystemTradeId ?? '',
    tradeVersion: canonical.tradeVersion ?? doc.tradeVersion ?? 1,
    productType: canonical.productType ?? doc.productType ?? 'UNKNOWN',
    tradeDate: canonical.tradeDate ?? '',
    effectiveDate: canonical.effectiveDate ?? '',
    maturityDate: canonical.maturityDate ?? '',
    parties: canonical.parties ?? [],
    status: doc.processingStatus ?? 'RECEIVED',
    receivedAt: doc.receivedTimestamp,
    sourceHeader: {
      messageId: doc.messageId,
      correlationId: doc.correlationId,
      schemaVersion: doc.schemaVersion,
      createdTimestamp: doc.receivedTimestamp,
    },
  };
};

export const adaptWorkItem = (doc: WorkItemDoc): WorkItem => ({
  ...doc,
  linkedExceptionId: doc.linkedExceptionId ?? doc.linkedExceptionIds?.[0],
});

/**
 * Exception documents keep the status they were captured with, so the live status comes from the linked work item.
 */
export const adaptException = (doc: ExceptionDoc, workItems: WorkItemDoc[]): ExceptionRecord => {
  const item = workItems.find(
    (w) => w.linkedExceptionId === doc.exceptionId || w.linkedExceptionIds?.includes(doc.exceptionId)
  );
  let status = (doc.status ?? 'OPEN') as ExceptionRecord['status'];
  if (item?.status === 'DONE') status = 'RESOLVED';
  else if (item?.status === 'CANCELLED') status = 'CLOSED';
  else if (item?.status === 'IN_PROGRESS') status = 'IN_PROGRESS';
  return {
    ...doc,
    status,
    linkedWorkItemId: item?.workItemId ?? doc.linkedWorkItemId,
    resolvedAt: item?.resolvedAt ?? doc.resolvedAt,
    resolutionNotes: item?.resolutionNotes ?? doc.resolutionNotes,
  };
};

export const adaptDlq = (doc: DlqDoc): DeadLetterEvent => {
  const header: MessageHeader = doc.originalMessageHeader ?? {
    messageId: doc.messageId ?? '',
    correlationId: doc.correlationId ?? '',
    eventType: 'NEW_TRADE',
    schemaVersion: '',
    createdTimestamp: doc.failedAt ?? '',
  };
  return {
    id: doc._id,
    dlqId: doc._id,
    status: doc.dlqStatus,
    dlqStatus: doc.dlqStatus,
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

export const adaptAudit = (doc: AuditDoc): AuditEvent => ({
  auditEventId: doc._id ?? `${doc.tradeId}:${doc.stage}:${doc.timestamp}`,
  tradeId: doc.tradeId ?? '',
  messageId: doc.correlationId ?? '-',
  correlationId: doc.correlationId ?? '-',
  fromStatus: null,
  stage: doc.stage,
  toStatus: doc.status ?? 'RECEIVED',
  serviceName: doc.sourceService ?? 'unknown',
  occurredAt: doc.timestamp ?? '',
  detail: doc.details ?? '',
  isReplay: doc.eventType === 'REPLAY',
});

/** Audit entries record what happened rather than a Kafka message, so this topic is the closest match. */
const TOPIC_BY_STATUS: Partial<Record<ProcessingStatus, string>> = {
  RECEIVED: 'trade.raw',
  ACCEPTED: 'trade.accepted',
  SCHEMA_INVALID: 'exception.created',
  ENRICHED: 'trade.enriched',
  STP_PASSED: 'stp.validated',
  STP_FAILED: 'stp.validated',
  CONFIRMED: 'confirmation.generated',
  EXCEPTION_CAPTURED: 'exception.classified',
  MANUAL_REVIEW: 'manual.review.required',
  WORKITEM_LINKED: 'workitem.linked',
  RESOLVED: 'workitem.linked',
  DEAD_LETTER: 'deadletter.trade',
  REPLAYED: 'deadletter.trade',
  CANCELLED: 'stp.validated',
};

export const streamFromAudit = (audit: AuditEvent[], limit = 30): StreamEventNotice[] =>
  audit
    .filter((a) => a.tradeId)
    .slice(0, limit)
    .map((a) => ({
      id: a.auditEventId,
      topic: TOPIC_BY_STATUS[a.toStatus] ?? a.stage ?? 'pipeline',
      service: a.serviceName,
      tradeId: a.tradeId,
      status: a.toStatus,
      timestamp: a.occurredAt ? new Date(a.occurredAt).toLocaleTimeString() : '',
    }));

const isOpen = (status: string) => status !== 'RESOLVED' && status !== 'CLOSED';

/** Open exceptions counted into the aging chart's buckets: under 2h, 2-6h, 6-24h, over 24h. */
export const agingBuckets = (exceptions: ExceptionRecord[], now: number): [number, number, number, number] => {
  const counts: [number, number, number, number] = [0, 0, 0, 0];
  exceptions
    .filter((e) => isOpen(e.status) && e.capturedAt)
    .forEach((e) => {
      const hours = (now - new Date(e.capturedAt).getTime()) / 3600000;
      counts[hours < 2 ? 0 : hours < 6 ? 1 : hours < 24 ? 2 : 3] += 1;
    });
  return counts;
};

export const computeMetrics = (
  trades: Trade[],
  exceptions: ExceptionRecord[],
  workItems: WorkItem[],
  dlqEntries: DeadLetterEvent[],
  now: number
): OperationsMetrics => {
  const stpPassedCount = trades.filter((t) => t.status === 'CONFIRMED').length;
  const resolved = workItems.filter((w) => w.resolvedAt && w.createdAt);
  const resolutionMinutes = resolved.map(
    (w) => (new Date(w.resolvedAt as string).getTime() - new Date(w.createdAt).getTime()) / 60000
  );
  return {
    totalTrades: trades.length,
    stpRatePercent: trades.length > 0 ? Math.round((stpPassedCount / trades.length) * 100) : 0,
    stpPassedCount,
    exceptionsCount: exceptions.length,
    openExceptionsCount: exceptions.filter((e) => isOpen(e.status)).length,
    activeWorkItemsCount: workItems.filter((w) => w.status === 'OPEN' || w.status === 'IN_PROGRESS').length,
    dlqCount: dlqEntries.filter((d) => d.status === 'PENDING_REPLAY').length,
    slaBreachRiskCount: exceptions.filter(
      (e) => isOpen(e.status) && e.slaDeadline && new Date(e.slaDeadline).getTime() - now <= 2 * 60 * 60 * 1000
    ).length,
    avgResolutionTimeMinutes:
      resolutionMinutes.length > 0
        ? Math.round((resolutionMinutes.reduce((sum, m) => sum + m, 0) / resolutionMinutes.length) * 10) / 10
        : 0,
    manualReviewCount: workItems.filter((w) => w.type === 'MANUAL_REVIEW').length,
    agingBuckets: agingBuckets(exceptions, now),
  };
};

export const newestFirst = <T>(rows: T[], when: (row: T) => string | undefined): T[] =>
  [...rows].sort((a, b) => (when(b) ?? '').localeCompare(when(a) ?? ''));
