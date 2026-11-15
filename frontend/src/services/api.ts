import type {
  ConfirmationRecord,
  DeadLetterEvent,
  ExceptionRecord,
  EventType,
  ProcessingStatus,
  ProductType,
  Trade,
  WorkItem,
} from '../types/tcem';

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`/api${path}`, {
    ...init,
    signal: init?.signal ?? AbortSignal.timeout(init?.method ? 30000 : 8000),
    headers: {
      ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      ...init?.headers,
    },
  });
  const text = await response.text();
  let body: unknown;
  try {
    body = text ? JSON.parse(text) : undefined;
  } catch {
    throw new Error(`The API returned an invalid response for ${path}.`);
  }
  if (!response.ok) {
    const detail = body as { error?: string; message?: string } | undefined;
    throw new Error(detail?.error || detail?.message || `Request failed with HTTP ${response.status}.`);
  }
  return body as T;
}

async function fetchPages<T>(
  path: (page: number) => string,
  pageSize: number,
  rows: (body: { content?: T[]; trades?: T[]; exceptions?: T[] }) => T[] | undefined
): Promise<T[]> {
  const result: T[] = [];
  for (let page = 0; ; page += 1) {
    const body = await request<unknown>(path(page));
    if (!body || typeof body !== 'object' || Array.isArray(body)) {
      throw new Error(`The API returned an unexpected list response for ${path(page)}.`);
    }
    const items = rows(body as { content?: T[]; trades?: T[]; exceptions?: T[] });
    if (!Array.isArray(items)) {
      throw new Error(`The API returned an unexpected list response for ${path(page)}.`);
    }
    result.push(...items);
    if (items.length < pageSize) break;
    if (result.length > 5000) {
      throw new Error('The live data result exceeds the 5,000-record frontend limit. Apply filters before loading it.');
    }
  }
  return result;
}

export interface LiveTradeDocument {
  tradeId: string;
  sourceSystemTradeId?: string;
  tradeVersion?: number;
  productType?: ProductType;
  messageId?: string;
  correlationId?: string;
  schemaVersion?: string;
  processingStatus?: ProcessingStatus;
  receivedTimestamp?: string;
  canonicalTrade?: Partial<Trade> | null;
}

export interface LiveExceptionDocument {
  exceptionId: string;
  tradeId: string;
  tradeVersion: number;
  sourceMessageId: string;
  correlationId: string;
  category: ExceptionRecord['category'];
  subTheme: ExceptionRecord['subTheme'];
  classificationConfidence: number;
  reasonCode: string;
  affectedFields?: string;
  recommendedAction?: string;
  ownerQueue?: ExceptionRecord['ownerQueue'];
  replayMayResolve: boolean;
  resolutionDifficulty?: ExceptionRecord['resolutionDifficulty'];
  operationalImpact?: ExceptionRecord['operationalImpact'];
  capturedAt?: string;
  slaDeadline?: string;
  status?: ExceptionRecord['status'];
  resolvedAt?: string;
  linkedWorkItemId?: string;
}

export interface LiveWorkItemDocument {
  workItemId: string;
  linkedExceptionId?: string;
  linkedExceptionIds?: string[];
  tradeId: string;
  type: WorkItem['type'];
  title: string;
  description?: string;
  status: WorkItem['status'];
  ownerQueue?: WorkItem['ownerQueue'];
  priority?: WorkItem['priority'];
  createdAt?: string;
  updatedAt?: string;
  resolvedAt?: string;
  resolutionNotes?: string;
}

export interface LiveDlqDocument {
  _id: string;
  aliasOf?: string;
  tradeId?: string;
  messageId?: string;
  correlationId?: string;
  originalMessageHeader?: {
    messageId?: string;
    correlationId?: string;
    eventType?: EventType;
    schemaVersion?: string;
    createdTimestamp?: string;
  };
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
}

export interface LiveAuditDocument {
  _id?: string;
  tradeId?: string;
  correlationId?: string;
  eventType?: string;
  stage?: string;
  status?: ProcessingStatus;
  timestamp?: string;
  details?: string;
  sourceService?: string;
}

export interface LiveConfirmationDocument {
  confirmationId: string;
  tradeId: string;
  tradeVersion: number;
  sourceMessageId: string;
  templateId: string;
  templateVersion: string;
  status: ConfirmationRecord['status'];
  confirmationFingerprint: string;
  previousConfirmationFingerprint?: string;
  confirmationBody: string;
  generatedAt: string;
  generatedBy: string;
}

export const api = {
  trades: () =>
    fetchPages<LiveTradeDocument>(
      (page) => `/trades?page=${page}&size=50`,
      50,
      (body) => body.trades
    ),
  confirmations: () =>
    fetchPages<LiveConfirmationDocument>(
      (page) => `/validation/confirmations?page=${page}&size=100`,
      100,
      (body) => body.content
    ),
  exceptions: () =>
    fetchPages<LiveExceptionDocument>(
      (page) => `/exceptions?page=${page}&size=200`,
      200,
      (body) => body.exceptions
    ),
  workItems: () =>
    fetchPages<LiveWorkItemDocument>(
      (page) => `/workitems?page=${page}&size=100`,
      100,
      (body) => body.content
    ),
  dlq: () =>
    fetchPages<LiveDlqDocument>(
      (page) => `/dlq?page=${page}&size=100`,
      100,
      (body) => body.content
    ),
  audit: () =>
    fetchPages<LiveAuditDocument>(
      (page) => `/audit?page=${page}&size=100`,
      100,
      (body) => body.content
    ),
  generateScenario: (scenario: string, sequence: number) =>
    request<unknown>(`/trades/generate/scenario?scenario=${encodeURIComponent(scenario)}&sequence=${sequence}&publishToKafka=true`, {
      method: 'POST',
    }),
  review: (workItemId: string, decision: 'APPROVE' | 'REJECT', notes: string) =>
    request<LiveWorkItemDocument>(`/workitems/${encodeURIComponent(workItemId)}/review`, {
      method: 'POST',
      body: JSON.stringify({ decision, notes }),
    }),
  replay: (dlqId: string) =>
    request<LiveDlqDocument>(`/dlq/${encodeURIComponent(dlqId)}/replay`, { method: 'POST' }),
  createWorkItem: (item: Pick<WorkItem, 'tradeId' | 'type' | 'title' | 'description' | 'ownerQueue' | 'priority'> & {
    linkedExceptionId?: string;
  }) =>
    request<LiveWorkItemDocument>('/workitems', { method: 'POST', body: JSON.stringify(item) }),
  updateWorkItem: (workItemId: string, update: { status?: WorkItem['status']; notes?: string }) =>
    request<LiveWorkItemDocument>(`/workitems/${encodeURIComponent(workItemId)}`, {
      method: 'PATCH',
      body: JSON.stringify(update),
    }),
};
