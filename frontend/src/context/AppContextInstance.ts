import { createContext } from 'react';
import type {
  Trade,
  ExceptionRecord,
  WorkItem,
  ConfirmationRecord,
  AuditEvent,
  DeadLetterEvent,
  OperationsMetrics,
  DataMode,
  SignedInUser,
  UserRole,
} from '../types/tcem';

export interface ToastMessage {
  id: string;
  type: 'success' | 'info' | 'warning' | 'error';
  title: string;
  message: string;
  timestamp: Date;
}

export interface StreamEventNotice {
  id: string;
  topic: string;
  service: string;
  tradeId: string;
  status: string;
  timestamp: string;
}

export interface ConnectionState {
  status: 'connecting' | 'connected' | 'partial' | 'offline';
  unavailable: string[];
  lastUpdated: string | null;
}

export interface AppContextType {
  // Data source: real backend data, or the in-browser simulation
  dataMode: DataMode;
  setDataMode: (mode: DataMode) => void;
  connection: ConnectionState;
  /** Live data only: what is known about each service, keyed by service name. */
  serviceHealth: Record<string, string> | null;
  seedTrades: () => void;
  /** Live data only: the signed-in user. Demo mode has no sign-in and uses the role selector. */
  currentUser: SignedInUser | null;
  signOut: () => void;
  canReview: boolean;
  canReplay: boolean;

  // Navigation & Role
  activeTab: string;
  setActiveTab: (tab: string) => void;
  userRole: UserRole;
  setUserRole: (role: UserRole) => void;

  // Domain state
  trades: Trade[];
  exceptions: ExceptionRecord[];
  workItems: WorkItem[];
  confirmations: ConfirmationRecord[];
  dlqEntries: DeadLetterEvent[];
  auditTrail: AuditEvent[];
  streamEvents: StreamEventNotice[];

  // Selection
  selectedTradeId: string | null;
  setSelectedTradeId: (id: string | null) => void;
  selectedExceptionId: string | null;
  setSelectedExceptionId: (id: string | null) => void;

  // Metrics
  metrics: OperationsMetrics;

  // Live simulation & actions
  isStreaming: boolean;
  setIsStreaming: (active: boolean) => void;
  triggerSyntheticScenario: (scenarioType: string) => void;
  replayDLQ: (dlqId: string) => void;
  resolveException: (exceptionId: string, notes: string) => void;
  approveManualReview: (workItemId: string, notes: string) => void;
  rejectManualReview: (workItemId: string, reason: string) => void;
  createWorkItem: (item: Partial<WorkItem>) => void;
  updateWorkItemStatus: (id: string, status: WorkItem['status']) => void;

  // Toasts
  toasts: ToastMessage[];
  removeToast: (id: string) => void;
  addToast: (type: ToastMessage['type'], title: string, message: string) => void;
}

export const AppContext = createContext<AppContextType | undefined>(undefined);

/** An open MANUAL_REVIEW work item: what the review queue lists and what a decision acts on. */
export const isOpenReviewItem = (item: WorkItem): boolean =>
  item.type === 'MANUAL_REVIEW' && (item.status === 'OPEN' || item.status === 'IN_PROGRESS');
