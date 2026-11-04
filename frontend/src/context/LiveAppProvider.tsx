import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { DataMode, SignedInUser, WorkItem } from '../types/tcem';
import {
  AppContext,
  isOpenReviewItem,
  type ConnectionState,
  type ToastMessage,
} from './AppContextInstance';
import { api, ApiError } from '../services/api';
import {
  adaptAudit,
  adaptDlq,
  adaptException,
  adaptTrade,
  adaptWorkItem,
  computeMetrics,
  newestFirst,
  streamFromAudit,
  type AuditDoc,
  type ConfirmationDoc,
  type DlqDoc,
  type ExceptionDoc,
  type TradeDoc,
  type WorkItemDoc,
} from '../services/adapters';

const POLL_INTERVAL_MS = 3000;

/** The UI's scenario buttons and the generator scenarios they publish. */
const SCENARIOS: Record<string, { scenario: string; message: string }> = {
  CLEAN_STP: { scenario: 'VALID_STANDARD_STP', message: 'should pass STP and be confirmed.' },
  MISSING_STRIKE: { scenario: 'MISSING_ECONOMIC_STRIKE', message: 'should fail STP on the missing strike.' },
  TECHNICAL_DLQ: { scenario: 'MALFORMED_JSON', message: 'should arrive in the Dead-Letter Queue as non-replayable.' },
  MANUAL_DISPUTE: { scenario: 'BUSINESS_RULE_NON_STP', message: 'should fail STP and reach the manual review queue.' },
};

interface Sources {
  trades: TradeDoc[];
  confirmations: ConfirmationDoc[];
  exceptions: ExceptionDoc[];
  workItems: WorkItemDoc[];
  dlq: DlqDoc[];
  audit: AuditDoc[];
}

const EMPTY: Sources = { trades: [], confirmations: [], exceptions: [], workItems: [], dlq: [], audit: [] };

const errorMessage = (error: unknown) =>
  error instanceof ApiError || error instanceof Error ? error.message : 'Unknown error';

export const LiveAppProvider: React.FC<{
  children: React.ReactNode;
  setDataMode: (mode: DataMode) => void;
  user: SignedInUser;
  signOut: () => void;
}> = ({ children, setDataMode, user, signOut }) => {
  const userRole = user.role;
  const [activeTab, setActiveTab] = useState('dashboard');
  const [selectedTradeId, setSelectedTradeId] = useState<string | null>(null);
  const [selectedExceptionId, setSelectedExceptionId] = useState<string | null>(null);
  const [isStreaming, setIsStreaming] = useState(false);
  const [toasts, setToasts] = useState<ToastMessage[]>([]);
  const [sources, setSources] = useState<Sources>(EMPTY);
  const [selectedAudit, setSelectedAudit] = useState<AuditDoc[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [failed, setFailed] = useState<(keyof Sources)[]>([]);
  const [lastUpdated, setLastUpdated] = useState<string | null>(null);
  const [now, setNow] = useState(() => Date.now());

  const sourcesRef = useRef<Sources>(EMPTY);
  const sequenceRef = useRef(0);
  const refreshRef = useRef<() => Promise<void>>(() => Promise.resolve());

  const removeToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const addToast = useCallback(
    (type: ToastMessage['type'], title: string, message: string) => {
      const id = `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
      setToasts((prev) => [...prev, { id, type, title, message, timestamp: new Date() }]);
      setTimeout(() => removeToast(id), 6000);
    },
    [removeToast]
  );

  // A source that fails keeps its last good data, and is reported through `connection`.
  const refresh = useCallback(async () => {
    const names: (keyof Sources)[] = ['trades', 'confirmations', 'exceptions', 'workItems', 'dlq', 'audit'];
    const results = await Promise.allSettled([
      api.trades(),
      api.confirmations(),
      api.exceptions(),
      api.workItems(),
      api.dlq(),
      api.recentAudit(),
    ]);
    const next: Sources = { ...sourcesRef.current };
    const failedNow: (keyof Sources)[] = [];
    results.forEach((result, index) => {
      const name = names[index];
      if (result.status === 'fulfilled') {
        (next as unknown as Record<string, unknown>)[name] = result.value;
      } else {
        failedNow.push(name);
      }
    });
    // Skip the state update, and so a re-render of every table, when a poll brought nothing new.
    if (JSON.stringify(next) !== JSON.stringify(sourcesRef.current)) {
      sourcesRef.current = next;
      setSources(next);
    }
    setFailed((prev) => (prev.join() === failedNow.join() ? prev : failedNow));
    if (failedNow.length < names.length) {
      setLastUpdated(new Date().toLocaleTimeString());
    }
    setNow(Date.now());
    setIsLoading(false);
  }, []);

  useEffect(() => {
    refreshRef.current = refresh;
  }, [refresh]);

  // Poll after each refresh completes, so a slow backend never builds up overlapping requests.
  useEffect(() => {
    let stopped = false;
    let timer: ReturnType<typeof setTimeout>;
    const loop = async () => {
      await refresh();
      if (!stopped) timer = setTimeout(loop, POLL_INTERVAL_MS);
    };
    void loop();
    return () => {
      stopped = true;
      clearTimeout(timer);
    };
  }, [refresh]);

  // The global audit list holds recent events only, so the open trade's full history is fetched separately.
  useEffect(() => {
    if (!selectedTradeId) return;
    let cancelled = false;
    const load = () =>
      api
        .tradeAudit(selectedTradeId)
        .then((docs) => {
          if (!cancelled) setSelectedAudit(docs);
        })
        .catch(() => {
          if (!cancelled) setSelectedAudit([]);
        });
    void load();
    const timer = setInterval(load, POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      clearInterval(timer);
    };
  }, [selectedTradeId]);

  const trades = useMemo(
    () => newestFirst(sources.trades.map(adaptTrade), (t) => t.receivedAt),
    [sources.trades]
  );
  const workItems = useMemo(
    () => newestFirst(sources.workItems.map(adaptWorkItem), (w) => w.createdAt),
    [sources.workItems]
  );
  const exceptions = useMemo(
    () =>
      newestFirst(
        sources.exceptions.map((doc) => adaptException(doc, sources.workItems)),
        (e) => e.capturedAt
      ),
    [sources.exceptions, sources.workItems]
  );
  const confirmations = useMemo(
    () => newestFirst(sources.confirmations, (c) => c.generatedAt),
    [sources.confirmations]
  );
  const dlqEntries = useMemo(
    () =>
      newestFirst(
        sources.dlq.filter((d) => !d.aliasOf).map(adaptDlq),
        (d) => d.failedAt
      ),
    [sources.dlq]
  );
  const recentAudit = useMemo(() => sources.audit.map(adaptAudit), [sources.audit]);
  const auditTrail = useMemo(() => {
    if (!selectedTradeId) return recentAudit;
    const known = new Set(recentAudit.map((a) => a.auditEventId));
    return [...recentAudit, ...selectedAudit.map(adaptAudit).filter((a) => !known.has(a.auditEventId))];
  }, [recentAudit, selectedAudit, selectedTradeId]);
  const streamEvents = useMemo(() => streamFromAudit(recentAudit), [recentAudit]);
  const metrics = useMemo(
    () => computeMetrics(trades, exceptions, workItems, dlqEntries, now),
    [trades, exceptions, workItems, dlqEntries, now]
  );

  const connection: ConnectionState = useMemo(
    () => ({
      status: isLoading ? 'connecting' : failed.length === 0 ? 'connected' : failed.length === 6 ? 'offline' : 'partial',
      unavailable: failed,
      lastUpdated,
    }),
    [isLoading, failed, lastUpdated]
  );

  const serviceHealth = useMemo(() => {
    const state = (...names: (keyof Sources)[]) =>
      isLoading ? 'Checking' : names.some((n) => failed.includes(n)) ? 'Unreachable' : 'API responding';
    return {
      'ingestion-service': state('trades', 'audit'),
      'enrichment-service': 'No API to check',
      'validation-service': state('confirmations'),
      'exception-service': state('exceptions'),
      'workitem-service': state('workItems', 'dlq'),
    };
  }, [isLoading, failed]);

  /** Runs a backend action, reports the outcome, and refreshes so the screen shows the result straight away. */
  const act = useCallback(
    async (action: () => Promise<unknown>, title: string, success: string, type: ToastMessage['type'] = 'success') => {
      try {
        await action();
        addToast(type, title, success);
        await refreshRef.current();
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) {
          signOut();
          return;
        }
        addToast('error', `${title} failed`, errorMessage(error));
      }
    },
    [addToast, signOut]
  );

  const triggerSyntheticScenario = useCallback(
    (scenarioType: string) => {
      const definition = SCENARIOS[scenarioType];
      if (!definition) return;
      sequenceRef.current += 1;
      // Generated trade ids are 6 digits. Stay above the seed's 1-500 and vary between page loads.
      const sequence = 1000 + ((Math.floor(Date.now() / 1000) + sequenceRef.current) % 900000);
      void act(
        () => api.generateScenario(definition.scenario, sequence),
        'Trade published to Kafka',
        `Sequence ${sequence} ${definition.message}`,
        'info'
      );
    },
    [act]
  );

  useEffect(() => {
    if (!isStreaming) return;
    const interval = setInterval(() => {
      const scenarios = ['CLEAN_STP', 'CLEAN_STP', 'CLEAN_STP', 'MISSING_STRIKE'];
      triggerSyntheticScenario(scenarios[Math.floor(Math.random() * scenarios.length)]);
    }, 4500);
    return () => clearInterval(interval);
  }, [isStreaming, triggerSyntheticScenario]);

  const openReviewItem = useCallback(
    (workItemId: string) => workItems.find((w) => isOpenReviewItem(w) && w.workItemId === workItemId),
    [workItems]
  );

  const value = {
    activeTab,
    setActiveTab,
    userRole,
    setUserRole: () => undefined,
    currentUser: user,
    signOut,
    canReview: userRole === 'SUPERVISOR' || userRole === 'SYSTEM_ADMIN',
    canReplay: userRole === 'SYSTEM_ADMIN',
    trades,
    exceptions,
    workItems,
    confirmations,
    dlqEntries,
    auditTrail,
    streamEvents,
    selectedTradeId,
    setSelectedTradeId,
    selectedExceptionId,
    setSelectedExceptionId,
    metrics,
    isStreaming,
    setIsStreaming,
    triggerSyntheticScenario,
    dataMode: 'live' as const,
    setDataMode,
    connection,
    serviceHealth,
    seedTrades: () =>
      void act(api.seed, 'Seed published to Kafka', 'The 500 seed trades are flowing through the pipeline.', 'info'),
    replayDLQ: (dlqId: string) =>
      void act(() => api.replay(dlqId), 'Replay published', `${dlqId} was republished to the topic it failed on.`, 'info'),
    resolveException: (exceptionId: string, notes: string) => {
      const exception = exceptions.find((e) => e.exceptionId === exceptionId);
      const workItemId = exception?.linkedWorkItemId;
      if (!workItemId) {
        addToast('warning', 'Cannot resolve yet', `Exception ${exceptionId} has no linked work item.`);
        return;
      }
      void act(
        () => api.updateWorkItem(workItemId, { status: 'DONE', notes }),
        'Exception resolved',
        `Work item ${workItemId} closed for exception ${exceptionId}.`
      );
    },
    approveManualReview: (workItemId: string, notes: string) => {
      const item = openReviewItem(workItemId);
      if (!item) return;
      void act(
        () => api.review(workItemId, 'APPROVE', notes),
        'Manual review approved',
        `Trade ${item.tradeId} approved. The confirmation will appear once validation-service generates it.`
      );
    },
    rejectManualReview: (workItemId: string, reason: string) => {
      const item = openReviewItem(workItemId);
      if (!item) return;
      void act(
        () => api.review(workItemId, 'REJECT', reason),
        'Trade rejected',
        `Trade ${item.tradeId} was sent to the Dead-Letter Queue.`,
        'warning'
      );
    },
    createWorkItem: (item: Partial<WorkItem>) =>
      void act(() => api.createWorkItem(item), 'Work item created', item.title ?? 'New work item'),
    updateWorkItemStatus: (id: string, status: WorkItem['status']) =>
      void act(
        () => api.updateWorkItem(id, { status }),
        'Work item updated',
        `Work item ${id} is now ${status}.`,
        'info'
      ),
    toasts,
    removeToast,
    addToast,
  };

  return <AppContext.Provider value={value}>{children}</AppContext.Provider>;
};
