import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import type {
  Trade,
  ExceptionRecord,
  WorkItem,
  ConfirmationRecord,
  AuditEvent,
  DeadLetterEvent,
  OperationsMetrics,
  UserRole,
  ConnectionState,
  DataMode,
} from '../types/tcem';
import {
  INITIAL_TRADES,
  INITIAL_EXCEPTIONS,
  INITIAL_WORK_ITEMS,
  INITIAL_CONFIRMATIONS,
  INITIAL_DLQ_ENTRIES,
  INITIAL_AUDIT_TRAIL,
} from '../data/mockData';
import { evaluateExceptionRules } from '../services/recommendationEngine';
import { api } from '../services/api';
import {
  adaptAudit,
  adaptConfirmation,
  adaptDlq,
  adaptException,
  adaptTrade,
  adaptWorkItem,
} from '../services/adapters';
import {
  AppContext,
  type ToastMessage,
  type StreamEventNotice,
  isOpenReviewItem,
} from './AppContextInstance';

const INITIAL_STREAM_EVENTS: StreamEventNotice[] = [
  { id: 'ev-1', topic: 'trade.raw', service: 'ingestion-service', tradeId: 'TRD-10001', status: 'RECEIVED', timestamp: '10:30:00' },
  { id: 'ev-2', topic: 'trade.accepted', service: 'ingestion-service', tradeId: 'TRD-10001', status: 'ACCEPTED', timestamp: '10:30:01' },
  { id: 'ev-3', topic: 'trade.enriched', service: 'enrichment-service', tradeId: 'TRD-10001', status: 'ENRICHED', timestamp: '10:30:03' },
  { id: 'ev-4', topic: 'stp.validated', service: 'validation-service', tradeId: 'TRD-10001', status: 'STP_PASSED', timestamp: '10:30:05' },
  { id: 'ev-5', topic: 'confirmation.generated', service: 'validation-service', tradeId: 'TRD-10001', status: 'CONFIRMED', timestamp: '10:30:08' },
];

export const AppProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [dataMode, setCurrentDataMode] = useState<DataMode>('demo');
  const dataModeRef = useRef<DataMode>('demo');
  const modeGeneration = useRef(0);
  const scenarioSequence = useRef(0);
  const [connection, setConnection] = useState<ConnectionState>({
    status: 'connected',
    unavailable: [],
    lastUpdated: null,
  });
  const [activeTab, setActiveTab] = useState<string>('dashboard');
  const [userRole, setUserRole] = useState<UserRole>('OPERATIONS_SPECIALIST');

  const [trades, setTrades] = useState<Trade[]>(INITIAL_TRADES);
  const [exceptions, setExceptions] = useState<ExceptionRecord[]>(INITIAL_EXCEPTIONS);
  const [workItems, setWorkItems] = useState<WorkItem[]>(INITIAL_WORK_ITEMS);
  const [confirmations, setConfirmations] = useState<ConfirmationRecord[]>(INITIAL_CONFIRMATIONS);
  const [dlqEntries, setDlqEntries] = useState<DeadLetterEvent[]>(INITIAL_DLQ_ENTRIES);
  const [auditTrail, setAuditTrail] = useState<AuditEvent[]>(INITIAL_AUDIT_TRAIL);
  const [streamEvents, setStreamEvents] = useState<StreamEventNotice[]>(INITIAL_STREAM_EVENTS);

  const [selectedTradeId, setSelectedTradeId] = useState<string | null>(null);
  const [selectedExceptionId, setSelectedExceptionId] = useState<string | null>(null);
  const [isStreaming, setIsStreaming] = useState<boolean>(false);
  const [toasts, setToasts] = useState<ToastMessage[]>([]);
  const [currentTime, setCurrentTime] = useState(() => Date.now());

  const setDataMode = useCallback((mode: DataMode) => {
    if (dataModeRef.current === mode) return;
    dataModeRef.current = mode;
    modeGeneration.current += 1;
    setCurrentDataMode(mode);
    setSelectedTradeId(null);
    setSelectedExceptionId(null);
    setIsStreaming(false);
    if (mode === 'demo') {
      setTrades(INITIAL_TRADES);
      setExceptions(INITIAL_EXCEPTIONS);
      setWorkItems(INITIAL_WORK_ITEMS);
      setConfirmations(INITIAL_CONFIRMATIONS);
      setDlqEntries(INITIAL_DLQ_ENTRIES);
      setAuditTrail(INITIAL_AUDIT_TRAIL);
      setStreamEvents(INITIAL_STREAM_EVENTS);
      setConnection({ status: 'connected', unavailable: [], lastUpdated: null });
    } else {
      setTrades([]);
      setExceptions([]);
      setWorkItems([]);
      setConfirmations([]);
      setDlqEntries([]);
      setAuditTrail([]);
      setStreamEvents([]);
      setConnection({ status: 'connecting', unavailable: [], lastUpdated: null });
    }
  }, []);

  const addToast = useCallback((type: ToastMessage['type'], title: string, message: string) => {
    const id = `toast-${Date.now()}-${Math.random().toString(36).substring(2, 6)}`;
    setToasts((prev) => [...prev, { id, type, title, message, timestamp: new Date() }]);
    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
    }, 5000);
  }, []);

  const removeToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const refreshLiveData = useCallback(async () => {
    const generation = modeGeneration.current;
    const results = await Promise.allSettled([
      api.trades(),
      api.confirmations(),
      api.exceptions(),
      api.workItems(),
      api.dlq(),
      api.audit(),
    ]);
    if (dataModeRef.current !== 'live' || generation !== modeGeneration.current) return;
    const names = ['trades', 'confirmations', 'exceptions', 'work items', 'DLQ', 'audit'] as const;
    const unavailable = results.flatMap((result, index) => result.status === 'rejected' ? [names[index]] : []);
    const workItemDocs = results[3].status === 'fulfilled' ? results[3].value : undefined;

    if (results[0].status === 'fulfilled') setTrades(results[0].value.map(adaptTrade));
    if (results[1].status === 'fulfilled') setConfirmations(results[1].value.map(adaptConfirmation));
    if (results[2].status === 'fulfilled') {
      setExceptions(results[2].value.map((item) => adaptException(item, workItemDocs ?? [])));
    }
    if (workItemDocs) setWorkItems(workItemDocs.map(adaptWorkItem));
    if (results[4].status === 'fulfilled') {
      setDlqEntries(results[4].value.filter((entry) => !entry.aliasOf).map(adaptDlq));
    }
    if (results[5].status === 'fulfilled') {
      const events = results[5].value.map(adaptAudit);
      setAuditTrail(events);
      const topicByStatus: Partial<Record<AuditEvent['toStatus'], string>> = {
        RECEIVED: 'trade.raw',
        ACCEPTED: 'trade.accepted',
        ENRICHED: 'trade.enriched',
        STP_PASSED: 'stp.validated',
        STP_FAILED: 'stp.validated',
        CONFIRMED: 'confirmation.generated',
        EXCEPTION_CAPTURED: 'exception.classified',
        MANUAL_REVIEW: 'manual.review.required',
        DEAD_LETTER: 'deadletter.trade',
        REPLAYED: 'deadletter.trade',
      };
      setStreamEvents(events.filter((event) => event.tradeId).slice(0, 50).map((event) => ({
        id: event.auditEventId,
        topic: topicByStatus[event.toStatus] ?? event.stage ?? 'pipeline',
        service: event.serviceName,
        tradeId: event.tradeId,
        status: event.toStatus,
        timestamp: event.occurredAt ? new Date(event.occurredAt).toLocaleTimeString() : '',
      })));
    }

    setConnection((previous) => ({
      status: unavailable.length === names.length ? 'offline' : unavailable.length > 0 ? 'partial' : 'connected',
      unavailable: [...unavailable],
      lastUpdated: unavailable.length === names.length ? previous.lastUpdated : new Date().toLocaleTimeString(),
    }));
  }, []);

  useEffect(() => {
    if (dataMode !== 'live') return;

    let cancelled = false;
    let timer: ReturnType<typeof setTimeout>;
    const poll = async () => {
      await refreshLiveData();
      if (!cancelled) timer = setTimeout(poll, 5000);
    };
    void poll();
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [dataMode, refreshLiveData]);

  useEffect(() => {
    if (dataMode !== 'live') return;
    const timer = setInterval(() => setCurrentTime(Date.now()), 60000);
    return () => clearInterval(timer);
  }, [dataMode]);

  const performLiveAction = useCallback(async (
    action: () => Promise<unknown>,
    title: string,
    successMessage: string,
    type: ToastMessage['type'] = 'success'
  ) => {
    try {
      await action();
      addToast(type, title, successMessage);
      await refreshLiveData();
    } catch (error) {
      addToast('error', `${title} failed`, error instanceof Error ? error.message : 'The API request failed.');
    }
  }, [addToast, refreshLiveData]);

  // Compute Operations Metrics
  const metrics: OperationsMetrics = useMemo(() => {
    const totalTrades = trades.length;
    const stpPassedCount = trades.filter((t) => t.status === 'CONFIRMED').length;
    const stpRatePercent = totalTrades > 0 ? Math.round((stpPassedCount / totalTrades) * 100) : 0;
    const openExceptionsCount = exceptions.filter((e) => e.status === 'OPEN' || e.status === 'IN_PROGRESS').length;
    const activeWorkItemsCount = workItems.filter((w) => w.status === 'OPEN' || w.status === 'IN_PROGRESS').length;
    const dlqCount = dlqEntries.filter((d) => d.status === 'PENDING_REPLAY').length;

    // SLA risk count (deadline within the next two hours or already passed)
    const now = dataMode === 'live' ? currentTime : new Date('2026-01-15T12:00:00Z').getTime();
    const slaBreachRiskCount = exceptions.filter((e) => {
      if (e.status === 'RESOLVED' || e.status === 'CLOSED') return false;
      const deadline = new Date(e.slaDeadline).getTime();
      return deadline - now <= 2 * 60 * 60 * 1000;
    }).length;

    const resolvedWorkItems = workItems.filter(
      (item): item is WorkItem & { resolvedAt: string } => Boolean(item.resolvedAt && item.createdAt)
    );
    const avgResolutionTimeMinutes = resolvedWorkItems.length > 0
      ? resolvedWorkItems.reduce((total, item) => {
          const minutes = (new Date(item.resolvedAt).getTime() - new Date(item.createdAt).getTime()) / 60000;
          return total + minutes;
        }, 0) / resolvedWorkItems.length
      : dataMode === 'live' ? 0 : 14.5;

    return {
      totalTrades,
      stpRatePercent,
      stpPassedCount,
      exceptionsCount: exceptions.length,
      openExceptionsCount,
      activeWorkItemsCount,
      dlqCount,
      slaBreachRiskCount,
      avgResolutionTimeMinutes: Math.round(avgResolutionTimeMinutes * 10) / 10,
    };
  }, [currentTime, dataMode, trades, exceptions, workItems, dlqEntries]);

  // Push new event into the streaming monitor
  const pushStreamEvent = useCallback((topic: string, service: string, tradeId: string, status: string) => {
    const timeStr = new Date().toTimeString().split(' ')[0];
    setStreamEvents((prev) => [
      { id: `ev-${Date.now()}-${Math.random().toString(36).substring(2, 5)}`, topic, service, tradeId, status, timestamp: timeStr },
      ...prev.slice(0, 49),
    ]);
  }, []);

  // Controlled Replay (FR12)
  const replayDLQ = useCallback((dlqId: string) => {
    if (dataMode === 'live') {
      void performLiveAction(
        () => api.replay(dlqId),
        'Replay requested',
        `Replay request for ${dlqId} was accepted by the backend.`,
        'info'
      );
      return;
    }
    const dlq = dlqEntries.find((d) => (d.dlqId || d.id) === dlqId);
    if (!dlq) return;

    const newReplayCount = dlq.replayCount + 1;
    const nowIso = new Date().toISOString();

    // 1. Update DLQ Entry
    setDlqEntries((prev) =>
      prev.map((d) =>
        (d.dlqId || d.id) === dlqId
          ? {
              ...d,
              replayCount: newReplayCount,
              status: 'REPLAY_SUCCEEDED',
            }
          : d
      )
    );

    // 2. Append Replay Audit Event
    const replayAudit: AuditEvent = {
      auditEventId: `AUD-${Date.now().toString().slice(-5)}`,
      tradeId: dlq.tradeId,
      messageId: dlq.originalMessageHeader.messageId,
      correlationId: dlq.originalMessageHeader.correlationId,
      fromStatus: 'DEAD_LETTER',
      toStatus: 'REPLAYED',
      serviceName: 'workitem-service',
      occurredAt: nowIso,
      detail: `Message replayed (attempt #${newReplayCount}). Idempotency reset; re-published to ${dlq.failedOnTopic}.`,
      actorUserId: `USER-${userRole}`,
      isReplay: true,
    };
    setAuditTrail((prev) => [replayAudit, ...prev]);

    // 3. Re-process Trade to CONFIRMED
    setTrades((prev) =>
      prev.map((t) =>
        t.tradeId === dlq.tradeId
          ? {
              ...t,
              status: 'CONFIRMED',
              enrichmentMetadata: {
                ...t.enrichmentMetadata,
                resolvedTemplateId: 'TMPL-FX-OPT-001',
                resolvedTemplateVersion: '1.2.0',
                counterpartySTPEligible: true,
                productSTPEligible: true,
                enrichmentNotes: 'Successfully enriched on automated replay.',
              },
            }
          : t
      )
    );

    // 4. Generate confirmation
    const newConf: ConfirmationRecord = {
      confirmationId: `CONF-${Date.now().toString().slice(-5)}`,
      tradeId: dlq.tradeId,
      tradeVersion: 1,
      sourceMessageId: dlq.originalMessageHeader.messageId,
      templateId: 'TMPL-FX-OPT-001',
      templateVersion: '1.2.0',
      status: 'MATCHED',
      confirmationFingerprint: `hash-replay-${Date.now()}`,
      generatedAt: nowIso,
      generatedBy: 'validation-service',
      confirmationBody: `ISDA MASTER CONFIRMATION (REPLAY SUCCESSFUL)\nTrade ID: ${dlq.tradeId}\nReplay Attempt: ${newReplayCount}\nStatus: MATCHED ELECTRONIC`,
    };
    setConfirmations((prev) => [newConf, ...prev]);

    pushStreamEvent('deadletter.trade', 'workitem-service', dlq.tradeId, 'REPLAYED');
    pushStreamEvent('trade.accepted', 'enrichment-service', dlq.tradeId, 'ENRICHED');
    pushStreamEvent('confirmation.generated', 'validation-service', dlq.tradeId, 'CONFIRMED');

    addToast('success', 'Replay Dispatched Successfully', `Trade ${dlq.tradeId} replayed, idempotency key cleared, and confirmation generated.`);
  }, [dataMode, performLiveAction, dlqEntries, userRole, pushStreamEvent, addToast]);

  // Resolve Exception
  const resolveException = useCallback((exceptionId: string, notes: string) => {
    const exc = exceptions.find((e) => e.exceptionId === exceptionId);
    if (!exc) return;
    if (dataMode === 'live') {
      const workItemId = exc.linkedWorkItemId;
      if (!workItemId) {
        addToast('warning', 'Cannot resolve exception', `Exception ${exceptionId} has no linked work item in the backend.`);
        return;
      }
      void performLiveAction(
        () => api.updateWorkItem(workItemId, { status: 'DONE', notes }),
        'Exception resolution requested',
        `Work item ${workItemId} was marked done.`
      );
      return;
    }

    const nowIso = new Date().toISOString();

    setExceptions((prev) =>
      prev.map((e) =>
        e.exceptionId === exceptionId
          ? {
              ...e,
              status: 'RESOLVED',
              resolvedAt: nowIso,
              resolutionNotes: notes,
            }
          : e
      )
    );

    // Update associated trade
    setTrades((prev) =>
      prev.map((t) =>
        t.tradeId === exc.tradeId
          ? {
              ...t,
              status: 'CONFIRMED',
            }
          : t
      )
    );

    // Close associated work item
    if (exc.linkedWorkItemId) {
      setWorkItems((prev) =>
        prev.map((w) =>
          w.workItemId === exc.linkedWorkItemId
            ? { ...w, status: 'DONE', resolvedAt: nowIso }
            : w
        )
      );
    }

    // Append Audit Record
    const audit: AuditEvent = {
      auditEventId: `AUD-${Date.now().toString().slice(-5)}`,
      tradeId: exc.tradeId,
      messageId: exc.sourceMessageId,
      correlationId: exc.correlationId,
      fromStatus: 'EXCEPTION_CAPTURED',
      toStatus: 'CONFIRMED',
      serviceName: 'exception-service',
      occurredAt: nowIso,
      detail: `Exception ${exceptionId} resolved manually. Notes: ${notes}`,
      actorUserId: `USER-${userRole}`,
      isReplay: false,
    };
    setAuditTrail((prev) => [audit, ...prev]);

    pushStreamEvent('exception.classified', 'exception-service', exc.tradeId, 'RESOLVED');
    pushStreamEvent('confirmation.generated', 'validation-service', exc.tradeId, 'CONFIRMED');

    addToast('success', 'Exception Resolved', `Exception ${exceptionId} marked as resolved. Trade ${exc.tradeId} confirmed.`);
  }, [dataMode, exceptions, performLiveAction, userRole, pushStreamEvent, addToast]);

  // Approve in Manual Review. Takes the work item id, like POST /workitems/{id}/review.
  const approveManualReview = useCallback((workItemId: string, notes: string) => {
    const reviewItem = workItems.find((w) => isOpenReviewItem(w) && w.workItemId === workItemId);
    if (!reviewItem) return;
    if (dataMode === 'live') {
      void performLiveAction(
        () => api.review(workItemId, 'APPROVE', notes),
        'Manual review approval requested',
        `Approval for ${workItemId} was accepted by the backend.`
      );
      return;
    }
    const { tradeId } = reviewItem;
    const trade = trades.find((t) => t.tradeId === tradeId);
    if (!trade) return;

    const nowIso = new Date().toISOString();

    // The confirmation closes the trade's open work items, including this review.
    setWorkItems((prev) =>
      prev.map((w) =>
        w.tradeId === tradeId && (w.status === 'OPEN' || w.status === 'IN_PROGRESS')
          ? { ...w, status: 'DONE', updatedAt: nowIso, resolvedAt: nowIso }
          : w
      )
    );

    setTrades((prev) =>
      prev.map((t) =>
        t.tradeId === tradeId
          ? {
              ...t,
              status: 'CONFIRMED',
              enrichmentMetadata: {
                ...t.enrichmentMetadata,
                counterpartySTPEligible: true,
                enrichmentNotes: `Manual review override approved. ${notes}`,
              },
            }
          : t
      )
    );

    // Generate Confirmation
    const confRecord: ConfirmationRecord = {
      confirmationId: `CONF-MANUAL-${Date.now().toString().slice(-5)}`,
      tradeId: trade.tradeId,
      tradeVersion: trade.tradeVersion,
      sourceMessageId: `MSG-MANUAL-${Date.now().toString().slice(-4)}`,
      templateId: 'TMPL-FX-OPT-001',
      templateVersion: '1.2.0',
      status: 'MATCHED',
      confirmationFingerprint: `sha256-manual-override-${Date.now()}`,
      generatedAt: nowIso,
      generatedBy: 'operations-manual-review',
      confirmationBody: `================================================================================
                    MANUAL OVERRIDE CONFIRMATION - FX OPTION
================================================================================
Trade ID:                 ${trade.tradeId}
Review Status:            APPROVED BY OPERATIONS SUPERVISOR
Override Reason:          ${notes}
Generated Timestamp:      ${nowIso}
Settlement:               STANDARD DELIVERY (T+2)
================================================================================`,
    };
    setConfirmations((prev) => [confRecord, ...prev]);

    // Close any associated open exceptions
    setExceptions((prev) =>
      prev.map((e) =>
        e.tradeId === tradeId && e.status !== 'RESOLVED'
          ? { ...e, status: 'RESOLVED', resolvedAt: nowIso, resolutionNotes: `Approved in manual review: ${notes}` }
          : e
      )
    );

    // Audit Event
    const audit: AuditEvent = {
      auditEventId: `AUD-${Date.now().toString().slice(-5)}`,
      tradeId,
      messageId: `MSG-REV-${tradeId}`,
      correlationId: `CORR-REV-${tradeId}`,
      fromStatus: 'MANUAL_REVIEW',
      toStatus: 'CONFIRMED',
      serviceName: 'workitem-service',
      occurredAt: nowIso,
      detail: `Trade approved in Manual Review queue. Notes: ${notes}`,
      actorUserId: `USER-${userRole}`,
      isReplay: false,
    };
    setAuditTrail((prev) => [audit, ...prev]);

    pushStreamEvent('manual.review.required', 'workitem-service', tradeId, 'APPROVED');
    pushStreamEvent('confirmation.generated', 'validation-service', tradeId, 'CONFIRMED');

    addToast('success', 'Manual Review Approved', `Trade ${tradeId} was approved and confirmation was generated.`);
  }, [dataMode, performLiveAction, trades, workItems, userRole, pushStreamEvent, addToast]);

  // Reject in Manual Review. Takes the work item id, like POST /workitems/{id}/review.
  const rejectManualReview = useCallback((workItemId: string, reason: string) => {
    const reviewItem = workItems.find((w) => isOpenReviewItem(w) && w.workItemId === workItemId);
    if (!reviewItem) return;
    if (dataMode === 'live') {
      void performLiveAction(
        () => api.review(workItemId, 'REJECT', reason),
        'Manual review rejection requested',
        `Rejection for ${workItemId} was accepted by the backend.`,
        'warning'
      );
      return;
    }
    const { tradeId } = reviewItem;
    const trade = trades.find((t) => t.tradeId === tradeId);
    if (!trade) return;

    const nowIso = new Date().toISOString();

    setWorkItems((prev) =>
      prev.map((w) =>
        w.workItemId === workItemId ? { ...w, status: 'DONE', updatedAt: nowIso, resolvedAt: nowIso } : w
      )
    );

    setTrades((prev) =>
      prev.map((t) => (t.tradeId === tradeId ? { ...t, status: 'DEAD_LETTER' } : t))
    );

    // Create DLQ entry
    const dlq: DeadLetterEvent = {
      dlqId: `DLQ-${Date.now().toString().slice(-5)}`,
      tradeId,
      messageHeader: {
        messageId: `DLQ-${Date.now().toString().slice(-4)}`,
        correlationId: `CORR-${tradeId}`,
        eventType: 'NEW_TRADE',
        schemaVersion: 'capstone-fpml-json-1.0',
        createdTimestamp: nowIso,
      },
      originalMessageHeader: {
        messageId: `MSG-ORIG-${tradeId}`,
        correlationId: `CORR-${tradeId}`,
        eventType: 'NEW_TRADE',
        schemaVersion: 'capstone-fpml-json-1.0',
        createdTimestamp: nowIso,
      },
      failedOnTopic: 'manual.review.required',
      failedInService: 'manual-review-queue',
      failureReason: `Rejected by operations during manual review: ${reason}`,
      failedAt: nowIso,
      replayCount: 0,
      replayable: false,
      status: 'PERMANENTLY_FAILED',
      originalPayload: JSON.stringify(trade, null, 2),
    };
    setDlqEntries((prev) => [dlq, ...prev]);

    // Audit Event
    const audit: AuditEvent = {
      auditEventId: `AUD-${Date.now().toString().slice(-5)}`,
      tradeId,
      messageId: `MSG-REJ-${tradeId}`,
      correlationId: `CORR-REJ-${tradeId}`,
      fromStatus: 'MANUAL_REVIEW',
      toStatus: 'DEAD_LETTER',
      serviceName: 'workitem-service',
      occurredAt: nowIso,
      detail: `Trade rejected by reviewer to DLQ: ${reason}`,
      actorUserId: `USER-${userRole}`,
      isReplay: false,
    };
    setAuditTrail((prev) => [audit, ...prev]);

    pushStreamEvent('manual.review.required', 'workitem-service', tradeId, 'REJECTED');
    pushStreamEvent('deadletter.trade', 'workitem-service', tradeId, 'PERMANENTLY_FAILED');

    addToast('warning', 'Trade Rejected', `Trade ${tradeId} was rejected and routed to Dead-Letter Queue.`);
  }, [dataMode, performLiveAction, trades, workItems, userRole, pushStreamEvent, addToast]);

  // Create Work Item
  const createWorkItem = useCallback((item: Partial<WorkItem>) => {
    if (dataMode === 'live') {
      void performLiveAction(
        () => api.createWorkItem({
          tradeId: item.tradeId || 'TRD-GENERAL',
          type: item.type || 'DATA_FIX',
          title: item.title || 'Manual Work Item',
          description: item.description || '',
          ownerQueue: item.ownerQueue || 'DATA_REMEDIATION',
          priority: item.priority || 'MEDIUM',
          linkedExceptionId: item.linkedExceptionId,
        }),
        'Work item creation requested',
        item.title || 'New work item was created.'
      );
      return;
    }
    const id = `WI-${Date.now().toString().slice(-5)}`;
    const nowIso = new Date().toISOString();

    const newItem: WorkItem = {
      workItemId: id,
      tradeId: item.tradeId || 'TRD-GENERAL',
      linkedExceptionId: item.linkedExceptionId,
      type: item.type || 'DATA_FIX',
      title: item.title || 'Manual Work Item',
      description: item.description || '',
      status: 'OPEN',
      ownerQueue: item.ownerQueue || 'DATA_REMEDIATION',
      priority: item.priority || 'MEDIUM',
      createdAt: nowIso,
      updatedAt: nowIso,
    };

    setWorkItems((prev) => [newItem, ...prev]);
    addToast('success', 'Work Item Created', `Work Item ${id} created for ${newItem.ownerQueue}.`);
  }, [dataMode, performLiveAction, addToast]);

  // Update Work Item Status
  const updateWorkItemStatus = useCallback((id: string, status: WorkItem['status']) => {
    if (dataMode === 'live') {
      void performLiveAction(
        () => api.updateWorkItem(id, { status }),
        'Work item update requested',
        `Work item ${id} was updated to ${status}.`,
        'info'
      );
      return;
    }
    const nowIso = new Date().toISOString();
    setWorkItems((prev) =>
      prev.map((w) =>
        w.workItemId === id
          ? {
              ...w,
              status,
              updatedAt: nowIso,
              resolvedAt: status === 'DONE' ? nowIso : w.resolvedAt,
            }
          : w
      )
    );
    addToast('info', 'Work Item Updated', `Work Item ${id} status changed to ${status}.`);
  }, [dataMode, performLiveAction, addToast]);

  // Synthetic Scenario Injector (Adheres to FR1: simulated message stream without UI trade creation)
  const triggerSyntheticScenario = useCallback((scenarioType: string) => {
    if (dataMode === 'live') {
      const scenarios: Record<string, string> = {
        CLEAN_STP: 'VALID_STANDARD_STP',
        MISSING_STRIKE: 'MISSING_ECONOMIC_STRIKE',
        TECHNICAL_DLQ: 'MALFORMED_JSON',
      };
      const scenario = scenarios[scenarioType];
      if (!scenario) {
        addToast('warning', 'Scenario unavailable in live mode', 'Switch to demo mode to run this simulation.');
        return;
      }
      scenarioSequence.current += 1;
      const sequence = 1000 + ((Math.floor(Date.now() / 1000) + scenarioSequence.current) % 900000);
      void performLiveAction(
        () => api.generateScenario(scenario, sequence),
        'Scenario published',
        'The trade was published to the live Kafka pipeline.',
        'info'
      );
      return;
    }
    const tradeNum = 10000 + trades.length + 1;
    const tradeId = `TRD-${tradeNum}`;
    const nowIso = new Date().toISOString();

    if (scenarioType === 'CLEAN_STP') {
      const newTrade: Trade = {
        tradeId,
        sourceSystemTradeId: `SRC-${tradeNum}`,
        tradeVersion: 1,
        productType: 'FX_OPTION',
        tradeDate: '2026-01-15',
        effectiveDate: '2026-01-17',
        maturityDate: '2026-05-15',
        status: 'CONFIRMED',
        parties: [
          { partyRole: 'BANK', partyId: 'PTY-BANK-001', partyName: 'Apex Investment Banking AG' },
          { partyRole: 'COUNTERPARTY', partyId: 'PTY-CP-101', partyName: 'Meridian Capital Partners Ltd' },
        ],
        fxOptionEconomics: {
          notionalAmount: 2000000,
          notionalCurrency: 'USD',
          buySell: 'BUY',
          optionType: 'CALL',
          strike: 1.085,
          underlier: 'EUR/USD',
          premiumAmount: 24000,
          premiumCurrency: 'USD',
          premiumPaymentDate: '2026-01-17',
        },
        nonEconomicTerms: { jurisdiction: 'GENERIC-UK', confirmationMethod: 'ELECTRONIC', templateHint: 'FX_OPTION_STANDARD' },
        enrichmentMetadata: {
          resolvedTemplateId: 'TMPL-FX-OPT-001',
          resolvedTemplateVersion: '1.2.0',
          counterpartySTPEligible: true,
          productSTPEligible: true,
        },
      };

      const newConf: ConfirmationRecord = {
        confirmationId: `CONF-${Date.now().toString().slice(-5)}`,
        tradeId,
        tradeVersion: 1,
        sourceMessageId: `MSG-${Date.now().toString().slice(-5)}`,
        templateId: 'TMPL-FX-OPT-001',
        templateVersion: '1.2.0',
        status: 'MATCHED',
        confirmationFingerprint: `sha256-stp-${tradeId}`,
        generatedAt: nowIso,
        generatedBy: 'validation-service',
        confirmationBody: `ISDA MASTER CONFIRMATION - CLEAN STP\nTrade ID: ${tradeId}\nNotional: USD 2,000,000\nStatus: MATCHED`,
      };

      setTrades((prev) => [newTrade, ...prev]);
      setConfirmations((prev) => [newConf, ...prev]);

      // Audit trail
      const audits: AuditEvent[] = [
        { auditEventId: `AUD-${Date.now()}-1`, tradeId, messageId: `MSG-${tradeId}`, correlationId: `CORR-${tradeId}`, fromStatus: null, toStatus: 'RECEIVED', serviceName: 'ingestion-service', occurredAt: nowIso, detail: 'Received synthetic trade event from Kafka broker', isReplay: false },
        { auditEventId: `AUD-${Date.now()}-2`, tradeId, messageId: `MSG-${tradeId}`, correlationId: `CORR-${tradeId}`, fromStatus: 'RECEIVED', toStatus: 'ACCEPTED', serviceName: 'ingestion-service', occurredAt: nowIso, detail: 'Schema validated against FpML model', isReplay: false },
        { auditEventId: `AUD-${Date.now()}-3`, tradeId, messageId: `MSG-${tradeId}`, correlationId: `CORR-${tradeId}`, fromStatus: 'ACCEPTED', toStatus: 'ENRICHED', serviceName: 'enrichment-service', occurredAt: nowIso, detail: 'Counterparty static data enriched', isReplay: false },
        { auditEventId: `AUD-${Date.now()}-4`, tradeId, messageId: `MSG-${tradeId}`, correlationId: `CORR-${tradeId}`, fromStatus: 'ENRICHED', toStatus: 'STP_PASSED', serviceName: 'validation-service', occurredAt: nowIso, detail: 'Straight-Through Processing criteria satisfied', isReplay: false },
        { auditEventId: `AUD-${Date.now()}-5`, tradeId, messageId: `MSG-${tradeId}`, correlationId: `CORR-${tradeId}`, fromStatus: 'STP_PASSED', toStatus: 'CONFIRMED', serviceName: 'validation-service', occurredAt: nowIso, detail: `Confirmation ${newConf.confirmationId} generated`, isReplay: false },
      ];
      setAuditTrail((prev) => [...audits, ...prev]);

      pushStreamEvent('trade.raw', 'synthetic-producer', tradeId, 'RECEIVED');
      pushStreamEvent('trade.accepted', 'ingestion-service', tradeId, 'ACCEPTED');
      pushStreamEvent('trade.enriched', 'enrichment-service', tradeId, 'ENRICHED');
      pushStreamEvent('stp.validated', 'validation-service', tradeId, 'STP_PASSED');
      pushStreamEvent('confirmation.generated', 'validation-service', tradeId, 'CONFIRMED');

      addToast('success', 'Scenario Ingested: Clean STP', `Trade ${tradeId} ingested and completed STP in <1s.`);
    } else if (scenarioType === 'MISSING_STRIKE') {
      const newTrade: Trade = {
        tradeId,
        sourceSystemTradeId: `SRC-${tradeNum}`,
        tradeVersion: 1,
        productType: 'FX_OPTION',
        tradeDate: '2026-01-15',
        effectiveDate: '2026-01-17',
        maturityDate: '2026-06-15',
        status: 'EXCEPTION_CAPTURED',
        parties: [
          { partyRole: 'BANK', partyId: 'PTY-BANK-001', partyName: 'Apex Investment Banking AG' },
          { partyRole: 'COUNTERPARTY', partyId: 'PTY-CP-102', partyName: 'Vanguard Global Hedging Corp' },
        ],
        fxOptionEconomics: {
          notionalAmount: 4000000,
          notionalCurrency: 'USD',
          buySell: 'BUY',
          optionType: 'CALL',
          underlier: 'EUR/USD',
          premiumAmount: 48000,
          premiumCurrency: 'USD',
        },
        nonEconomicTerms: { jurisdiction: 'GENERIC-US', confirmationMethod: 'ELECTRONIC', templateHint: 'FX_OPTION_STANDARD' },
      };

      const rec = evaluateExceptionRules('MISSING_ECONOMIC_DATA', 'MISSING_STRIKE', 4000000);
      const excId = `EXC-${Date.now().toString().slice(-5)}`;
      const wiId = `WI-${Date.now().toString().slice(-5)}`;

      const newExc: ExceptionRecord = {
        exceptionId: excId,
        tradeId,
        tradeVersion: 1,
        sourceMessageId: `MSG-${Date.now().toString().slice(-5)}`,
        correlationId: `CORR-${tradeId}`,
        category: rec.category,
        subTheme: rec.subTheme,
        classificationConfidence: rec.confidence,
        reasonCode: rec.reasonCode,
        affectedFields: rec.affectedFields,
        recommendedAction: rec.recommendedAction,
        ownerQueue: rec.ownerQueue,
        replayMayResolve: rec.replayMayResolve,
        resolutionDifficulty: rec.resolutionDifficulty,
        operationalImpact: rec.operationalImpact,
        capturedAt: nowIso,
        slaDeadline: new Date(Date.now() + 4 * 60 * 60 * 1000).toISOString(),
        status: 'OPEN',
        linkedWorkItemId: wiId,
      };

      const newWi: WorkItem = {
        workItemId: wiId,
        linkedExceptionId: excId,
        tradeId,
        type: 'DATA_FIX',
        title: `Remediate Missing Strike for ${tradeId}`,
        description: `Synthetic trade ${tradeId} failed validation due to null strike field.`,
        status: 'OPEN',
        ownerQueue: rec.ownerQueue,
        priority: rec.operationalImpact,
        createdAt: nowIso,
        updatedAt: nowIso,
      };

      setTrades((prev) => [newTrade, ...prev]);
      setExceptions((prev) => [newExc, ...prev]);
      setWorkItems((prev) => [newWi, ...prev]);

      const audits: AuditEvent[] = [
        { auditEventId: `AUD-${Date.now()}-1`, tradeId, messageId: `MSG-${tradeId}`, correlationId: `CORR-${tradeId}`, fromStatus: 'ENRICHED', toStatus: 'STP_FAILED', serviceName: 'validation-service', occurredAt: nowIso, detail: 'Validation failed: Strike rate is mandatory', isReplay: false },
        { auditEventId: `AUD-${Date.now()}-2`, tradeId, messageId: `MSG-${tradeId}`, correlationId: `CORR-${tradeId}`, fromStatus: 'STP_FAILED', toStatus: 'EXCEPTION_CAPTURED', serviceName: 'exception-service', occurredAt: nowIso, detail: `Exception ${excId} classified as MISSING_ECONOMIC_DATA`, isReplay: false },
        { auditEventId: `AUD-${Date.now()}-3`, tradeId, messageId: `MSG-${tradeId}`, correlationId: `CORR-${tradeId}`, fromStatus: 'EXCEPTION_CAPTURED', toStatus: 'WORKITEM_LINKED', serviceName: 'workitem-service', occurredAt: nowIso, detail: `Work Item ${wiId} generated and assigned to DATA_REMEDIATION`, isReplay: false },
      ];
      setAuditTrail((prev) => [...audits, ...prev]);

      pushStreamEvent('trade.raw', 'synthetic-producer', tradeId, 'RECEIVED');
      pushStreamEvent('stp.validated', 'validation-service', tradeId, 'STP_FAILED');
      pushStreamEvent('exception.created', 'exception-service', tradeId, 'EXCEPTION_CAPTURED');
      pushStreamEvent('workitem.linked', 'workitem-service', tradeId, 'WORKITEM_LINKED');

      addToast('warning', 'Scenario Ingested: Missing Strike', `Trade ${tradeId} triggered exception ${excId} and linked work item ${wiId}.`);
    } else if (scenarioType === 'TECHNICAL_DLQ') {
      const newTrade: Trade = {
        tradeId,
        sourceSystemTradeId: `SRC-${tradeNum}`,
        tradeVersion: 1,
        productType: 'FX_OPTION',
        tradeDate: '2026-01-15',
        effectiveDate: '2026-01-17',
        maturityDate: '2026-07-01',
        status: 'DEAD_LETTER',
        parties: [
          { partyRole: 'BANK', partyId: 'PTY-BANK-001', partyName: 'Apex Investment Banking AG' },
          { partyRole: 'COUNTERPARTY', partyId: 'PTY-CP-104', partyName: 'Nordic Sovereign Holdings' },
        ],
        fxOptionEconomics: {
          notionalAmount: 5500000,
          notionalCurrency: 'EUR',
          buySell: 'BUY',
          optionType: 'PUT',
          strike: 1.09,
          underlier: 'EUR/USD',
          premiumAmount: 65000,
          premiumCurrency: 'EUR',
        },
      };

      const dlqId = `DLQ-${Date.now().toString().slice(-5)}`;
      const newDlq: DeadLetterEvent = {
        dlqId,
        tradeId,
        messageHeader: {
          messageId: `DLQ-MSG-${Date.now().toString().slice(-4)}`,
          correlationId: `CORR-${tradeId}`,
          eventType: 'NEW_TRADE',
          schemaVersion: 'capstone-fpml-json-1.0',
          createdTimestamp: nowIso,
        },
        originalMessageHeader: {
          messageId: `MSG-${Date.now().toString().slice(-4)}`,
          correlationId: `CORR-${tradeId}`,
          eventType: 'NEW_TRADE',
          schemaVersion: 'capstone-fpml-json-1.0',
          createdTimestamp: nowIso,
        },
        failedOnTopic: 'trade.accepted',
        failedInService: 'enrichment-service',
        failureReason: 'Enrichment service circuit breaker tripped: transient reference DB connection pool timeout',
        exceptionClass: 'org.springframework.dao.DataAccessResourceFailureException',
        stackTraceExcerpt: `org.springframework.dao.DataAccessResourceFailureException: Unable to acquire connection from pool within 3000ms\n  at com.tcem.enrichment.client.CouchDbPool.acquire(CouchDbPool.java:42)\n  at com.tcem.enrichment.service.EnrichmentService.lookup(EnrichmentService.java:88)`,
        failedAt: nowIso,
        replayCount: 0,
        replayable: true,
        status: 'PENDING_REPLAY',
        originalPayload: JSON.stringify(newTrade, null, 2),
      };

      setTrades((prev) => [newTrade, ...prev]);
      setDlqEntries((prev) => [newDlq, ...prev]);

      const audit: AuditEvent = {
        auditEventId: `AUD-${Date.now()}`,
        tradeId,
        messageId: `MSG-${tradeId}`,
        correlationId: `CORR-${tradeId}`,
        fromStatus: 'ACCEPTED',
        toStatus: 'DEAD_LETTER',
        serviceName: 'enrichment-service',
        occurredAt: nowIso,
        detail: `Enrichment timeout; routed to deadletter.trade as ${dlqId}`,
        isReplay: false,
      };
      setAuditTrail((prev) => [audit, ...prev]);

      pushStreamEvent('trade.raw', 'synthetic-producer', tradeId, 'RECEIVED');
      pushStreamEvent('deadletter.trade', 'enrichment-service', tradeId, 'DEAD_LETTER');

      addToast('error', 'Scenario Ingested: Technical Failure (DLQ)', `Trade ${tradeId} failed with timeout and routed to Dead-Letter Queue ${dlqId}.`);
    } else if (scenarioType === 'MANUAL_DISPUTE') {
      const newTrade: Trade = {
        tradeId,
        sourceSystemTradeId: `SRC-${tradeNum}`,
        tradeVersion: 1,
        productType: 'FX_OPTION',
        tradeDate: '2026-01-15',
        effectiveDate: '2026-01-17',
        maturityDate: '2026-08-01',
        status: 'MANUAL_REVIEW',
        parties: [
          { partyRole: 'BANK', partyId: 'PTY-BANK-001', partyName: 'Apex Investment Banking AG' },
          { partyRole: 'COUNTERPARTY', partyId: 'PTY-CP-101', partyName: 'Meridian Capital Partners Ltd' },
        ],
        fxOptionEconomics: {
          notionalAmount: 3000000,
          notionalCurrency: 'USD',
          buySell: 'BUY',
          optionType: 'CALL',
          strike: 1.0825, // Internal says 1.0825, CP says 1.0850
          underlier: 'EUR/USD',
          premiumAmount: 36000,
          premiumCurrency: 'USD',
        },
        nonEconomicTerms: { jurisdiction: 'GENERIC-UK', confirmationMethod: 'ELECTRONIC', templateHint: 'FX_OPTION_STANDARD' },
      };

      const rec = evaluateExceptionRules('COUNTERPARTY_MATCHING_DISPUTE', 'ECONOMIC_TERM_MISMATCH', 3000000);
      const excId = `EXC-${Date.now().toString().slice(-5)}`;

      const newExc: ExceptionRecord = {
        exceptionId: excId,
        tradeId,
        tradeVersion: 1,
        sourceMessageId: `MSG-${Date.now().toString().slice(-4)}`,
        correlationId: `CORR-${tradeId}`,
        category: rec.category,
        subTheme: rec.subTheme,
        classificationConfidence: rec.confidence,
        reasonCode: rec.reasonCode,
        affectedFields: rec.affectedFields,
        recommendedAction: rec.recommendedAction,
        ownerQueue: rec.ownerQueue,
        replayMayResolve: false,
        resolutionDifficulty: rec.resolutionDifficulty,
        operationalImpact: rec.operationalImpact,
        capturedAt: nowIso,
        slaDeadline: new Date(Date.now() + 2 * 60 * 60 * 1000).toISOString(),
        status: 'OPEN',
      };

      const newWi: WorkItem = {
        workItemId: `WI-${Date.now().toString().slice(-5)}`,
        linkedExceptionId: excId,
        tradeId,
        type: 'MANUAL_REVIEW',
        title: `Bilateral Discrepancy on Strike Rate for ${tradeId}`,
        description: 'Internal record strike is 1.0825; counterparty affirmation states 1.0850.',
        status: 'OPEN',
        ownerQueue: rec.ownerQueue,
        priority: rec.operationalImpact,
        createdAt: nowIso,
        updatedAt: nowIso,
      };

      setTrades((prev) => [newTrade, ...prev]);
      setExceptions((prev) => [{ ...newExc, linkedWorkItemId: newWi.workItemId }, ...prev]);
      setWorkItems((prev) => [newWi, ...prev]);

      const audit: AuditEvent = {
        auditEventId: `AUD-${Date.now()}`,
        tradeId,
        messageId: `MSG-${tradeId}`,
        correlationId: `CORR-${tradeId}`,
        fromStatus: 'VALIDATING',
        toStatus: 'MANUAL_REVIEW',
        serviceName: 'validation-service',
        occurredAt: nowIso,
        detail: 'Discrepancy detected between bank booking and counterparty report. Routed to Manual Review.',
        isReplay: false,
      };
      setAuditTrail((prev) => [audit, ...prev]);

      pushStreamEvent('trade.raw', 'synthetic-producer', tradeId, 'RECEIVED');
      pushStreamEvent('manual.review.required', 'validation-service', tradeId, 'MANUAL_REVIEW');

      addToast('warning', 'Scenario Ingested: Matching Dispute', `Trade ${tradeId} has terms discrepancy and requires Manual Review.`);
    }
  }, [dataMode, performLiveAction, trades, pushStreamEvent, addToast]);

  // Automated background streaming simulation
  useEffect(() => {
    if (!isStreaming || dataMode !== 'demo') return;

    const interval = setInterval(() => {
      const scenarios = ['CLEAN_STP', 'CLEAN_STP', 'MISSING_STRIKE', 'CLEAN_STP'];
      const randomScenario = scenarios[Math.floor(Math.random() * scenarios.length)];
      triggerSyntheticScenario(randomScenario);
    }, 4500);

    return () => clearInterval(interval);
  }, [dataMode, isStreaming, triggerSyntheticScenario]);

  return (
    <AppContext.Provider
      value={{
        dataMode,
        setDataMode,
        connection,
        activeTab,
        setActiveTab,
        userRole,
        setUserRole,
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
        replayDLQ,
        resolveException,
        approveManualReview,
        rejectManualReview,
        createWorkItem,
        updateWorkItemStatus,
        toasts,
        removeToast,
        addToast,
      }}
    >
      {children}
    </AppContext.Provider>
  );
};
