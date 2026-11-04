/**
 * Client for the backend REST APIs, through the gateway's /api routes (frontend/nginx.conf, vite.config.ts).
 */
import type {
  AuditDoc,
  ConfirmationDoc,
  DlqDoc,
  ExceptionDoc,
  TradeDoc,
  WorkItemDoc,
} from './adapters';

let authorization: string | null = null;

/** Sets the credentials sent with every request; null signs out. */
export const setAuthorization = (value: string | null) => {
  authorization = value;
};

export const basicAuthorization = (username: string, password: string) =>
  `Basic ${btoa(`${username}:${password}`)}`;

export class ApiError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.status = status;
  }
}

// Without a limit, one service that has stopped responding would freeze every screen, since a poll waits for all sources.
const READ_TIMEOUT_MS = 8000;
const WRITE_TIMEOUT_MS = 30000;

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`/api${path}`, {
    ...init,
    signal: AbortSignal.timeout(init?.method ? WRITE_TIMEOUT_MS : READ_TIMEOUT_MS),
    headers: {
      ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      ...(authorization ? { Authorization: authorization } : {}),
    },
  });
  const text = await response.text();
  let body: unknown = null;
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    body = null;
  }
  if (!response.ok) {
    const detail = body as { message?: string; error?: string } | null;
    throw new ApiError(detail?.message || detail?.error || `Request failed with HTTP ${response.status}`, response.status);
  }
  return body as T;
}

/** Follows a paginated list to the end. pageSize must not exceed the endpoint's cap, or paging stops early. */
async function fetchAllPages<T>(
  path: (page: number) => string,
  rows: (body: never) => T[] | undefined,
  pageSize: number,
  maxRows = 5000
): Promise<T[]> {
  const all: T[] = [];
  for (let page = 0; all.length < maxRows; page += 1) {
    const body = await request<never>(path(page));
    const pageRows = rows(body) ?? [];
    all.push(...pageRows);
    if (pageRows.length < pageSize) break;
  }
  return all;
}

type Content<T> = { content?: T[] };

export const api = {
  me: () => request<{ username: string; role: string }>('/auth/me'),
  trades: () =>
    fetchAllPages<TradeDoc>((p) => `/trades?page=${p}&size=500`, (b: { trades?: TradeDoc[] }) => b.trades, 500),
  confirmations: () =>
    fetchAllPages<ConfirmationDoc>(
      (p) => `/validation/confirmations?page=${p}&size=500`,
      (b: Content<ConfirmationDoc>) => b.content,
      500
    ),
  exceptions: () =>
    fetchAllPages<ExceptionDoc>(
      (p) => `/exceptions?page=${p}&size=200`,
      (b: { exceptions?: ExceptionDoc[] }) => b.exceptions,
      200
    ),
  workItems: () =>
    fetchAllPages<WorkItemDoc>((p) => `/workitems?page=${p}&size=100`, (b: Content<WorkItemDoc>) => b.content, 100),
  dlq: () => fetchAllPages<DlqDoc>((p) => `/dlq?page=${p}&size=100`, (b: Content<DlqDoc>) => b.content, 100),
  recentAudit: (size = 300) =>
    request<Content<AuditDoc>>(`/audit?size=${size}`).then((b) => b.content ?? []),
  tradeAudit: (tradeId: string) => request<AuditDoc[]>(`/trades/${encodeURIComponent(tradeId)}/audit`),

  generateScenario: (scenario: string, sequence: number) =>
    request<{ tradePayload?: unknown }>(
      `/trades/generate/scenario?scenario=${scenario}&sequence=${sequence}&publishToKafka=true`,
      { method: 'POST' }
    ),
  seed: () => request<unknown>('/trades/seed?publishToKafka=true', { method: 'POST' }),
  review: (workItemId: string, decision: 'APPROVE' | 'REJECT', notes: string) =>
    request<WorkItemDoc>(`/workitems/${encodeURIComponent(workItemId)}/review`, {
      method: 'POST',
      body: JSON.stringify({ decision, notes }),
    }),
  replay: (dlqId: string) => request<DlqDoc>(`/dlq/${encodeURIComponent(dlqId)}/replay`, { method: 'POST' }),
  createWorkItem: (item: Record<string, unknown>) =>
    request<WorkItemDoc>('/workitems', { method: 'POST', body: JSON.stringify(item) }),
  updateWorkItem: (workItemId: string, update: { status?: string; notes?: string }) =>
    request<WorkItemDoc>(`/workitems/${encodeURIComponent(workItemId)}`, {
      method: 'PATCH',
      body: JSON.stringify(update),
    }),
};
