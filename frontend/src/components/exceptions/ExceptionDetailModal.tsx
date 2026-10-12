import React, { useState } from 'react';
import { useApp } from '../../context/useApp';
import { StatusBadge } from '../common/StatusBadge';
import {
  X,
  Sparkles,
  CheckCircle2,
  ListTodo,
  Clock,
  ArrowRight,
} from 'lucide-react';

export const ExceptionDetailModal: React.FC = () => {
  const {
    selectedExceptionId,
    setSelectedExceptionId,
    exceptions,
    resolveException,
    createWorkItem,
    setSelectedTradeId,
    setActiveTab,
  } = useApp();

  const [resolveNotes, setResolveNotes] = useState('');
  const [showResolveForm, setShowResolveForm] = useState(false);

  if (!selectedExceptionId) return null;

  const exc = exceptions.find((e) => e.exceptionId === selectedExceptionId);
  if (!exc) return null;

  const isResolved = exc.status === 'RESOLVED' || exc.status === 'CLOSED';

  const handleResolveSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    resolveException(exc.exceptionId, resolveNotes || 'Manually verified and approved.');
    setShowResolveForm(false);
    setSelectedExceptionId(null);
  };

  return (
    <div className="fixed inset-0 z-50 bg-slate-950/80 backdrop-blur-sm flex items-center justify-center p-4 sm:p-6 overflow-y-auto">
      <div className="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-3xl max-h-[90vh] flex flex-col shadow-2xl overflow-hidden animate-in zoom-in-95">
        {/* Header */}
        <div className="p-5 border-b border-slate-800 flex items-center justify-between bg-slate-900/90">
          <div>
            <div className="flex items-center gap-3">
              <h3 className="text-lg font-bold text-white tracking-tight">{exc.exceptionId}</h3>
              <StatusBadge status={exc.category} type="category" />
              <StatusBadge status={exc.operationalImpact} type="priority" />
              <span className={`text-xs px-2 py-0.5 rounded font-medium ${isResolved ? 'bg-emerald-500/10 text-emerald-400' : 'bg-rose-500/10 text-rose-400'}`}>
                {exc.status}
              </span>
            </div>
            <p className="text-xs text-slate-400 mt-1">
              Associated Trade: <button
                onClick={() => {
                  setSelectedTradeId(exc.tradeId);
                  setSelectedExceptionId(null);
                  setActiveTab('trades');
                }}
                className="text-indigo-400 hover:underline font-semibold"
              >
                {exc.tradeId}
              </button> • Captured: <span className="text-slate-300">{exc.capturedAt}</span>
            </p>
          </div>

          <button
            onClick={() => setSelectedExceptionId(null)}
            className="p-2 rounded-xl text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
          >
            <X size={18} />
          </button>
        </div>

        {/* Modal Body */}
        <div className="p-6 overflow-y-auto space-y-6 flex-1 text-xs">
          {/* Reason Code & Affected Fields */}
          <div className="p-4 rounded-xl border border-rose-500/20 bg-rose-500/5 space-y-2">
            <div className="flex items-center justify-between">
              <span className="font-semibold text-rose-300">Failure Trigger</span>
              <span className="font-mono text-xs text-slate-400">{exc.reasonCode}</span>
            </div>
            <p className="text-slate-200">
              Sub-Theme: <strong className="text-rose-400 font-mono">{exc.subTheme}</strong>
            </p>
            <p className="text-slate-300">
              Affected Attributes: <span className="font-mono bg-slate-800/80 px-2 py-0.5 rounded text-amber-300">{exc.affectedFields}</span>
            </p>
          </div>

          {/* AI/Rule Recommendation Engine Card (FR8, FR9) */}
          <div className="p-5 rounded-xl border border-indigo-500/30 bg-gradient-to-br from-indigo-950/40 to-slate-900 space-y-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2 text-indigo-300 font-bold text-sm">
                <Sparkles size={16} className="text-indigo-400" />
                <span>Recommendation &amp; Classification Engine</span>
              </div>
              <span className="text-[11px] px-2 py-0.5 rounded-full bg-indigo-500/20 text-indigo-300 border border-indigo-500/30">
                Confidence: {Math.round(exc.classificationConfidence * 100)}% (Deterministic)
              </span>
            </div>

            <div className="p-3.5 rounded-lg bg-slate-900/80 border border-slate-800 space-y-1.5">
              <span className="text-[11px] font-semibold text-indigo-400 uppercase tracking-wider">
                Recommended Action:
              </span>
              <p className="text-slate-200 text-xs leading-relaxed font-medium">
                {exc.recommendedAction}
              </p>
            </div>

            {/* Recommendation Decision Metrics (4 Inputs as per Spec §12) */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 pt-1">
              <div className="p-2.5 rounded-lg bg-slate-900/80 border border-slate-800">
                <span className="text-slate-400 text-[11px]">Owner Queue:</span>
                <div className="font-semibold text-indigo-300 mt-0.5">{exc.ownerQueue}</div>
              </div>
              <div className="p-2.5 rounded-lg bg-slate-900/80 border border-slate-800">
                <span className="text-slate-400 text-[11px]">Resolution Difficulty:</span>
                <div className="font-semibold text-slate-200 mt-0.5">{exc.resolutionDifficulty}</div>
              </div>
              <div className="p-2.5 rounded-lg bg-slate-900/80 border border-slate-800">
                <span className="text-slate-400 text-[11px]">Replay Feasibility:</span>
                <div className={`font-semibold mt-0.5 ${exc.replayMayResolve ? 'text-emerald-400' : 'text-slate-400'}`}>
                  {exc.replayMayResolve ? 'Replay May Resolve' : 'Replay Ineligible'}
                </div>
              </div>
              <div className="p-2.5 rounded-lg bg-slate-900/80 border border-slate-800">
                <span className="text-slate-400 text-[11px]">SLA Deadline:</span>
                <div className="font-mono text-slate-200 mt-0.5 text-[11px] flex items-center gap-1">
                  <Clock size={12} className="text-amber-400" />
                  <span>{new Date(exc.slaDeadline).toLocaleTimeString()}</span>
                </div>
              </div>
            </div>
          </div>

          {/* Linked Work Item info */}
          <div className="p-4 rounded-xl border border-slate-800 bg-slate-950/50 flex items-center justify-between">
            <div>
              <span className="text-slate-400">Linked Work Item Ticket (FR10):</span>
              <div className="font-mono font-semibold text-slate-200 mt-0.5">
                {exc.linkedWorkItemId || 'None linked yet'}
              </div>
            </div>
            {!exc.linkedWorkItemId && !isResolved && (
              <button
                onClick={() =>
                  createWorkItem({
                    linkedExceptionId: exc.exceptionId,
                    tradeId: exc.tradeId,
                    type: 'DATA_FIX',
                    title: `Fix exception ${exc.exceptionId} for ${exc.tradeId}`,
                    description: exc.recommendedAction,
                    ownerQueue: exc.ownerQueue,
                    priority: exc.operationalImpact,
                  })
                }
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-blue-600 hover:bg-blue-500 text-white font-medium transition-colors"
              >
                <ListTodo size={14} />
                <span>Create Linked Work Item</span>
              </button>
            )}
          </div>

          {/* Resolve Form if toggled */}
          {showResolveForm && !isResolved && (
            <form onSubmit={handleResolveSubmit} className="p-4 rounded-xl border border-emerald-500/30 bg-emerald-500/5 space-y-3">
              <h4 className="font-semibold text-emerald-400">Resolve Exception</h4>
              <textarea
                placeholder="Enter operational resolution notes (e.g. corrected strike rate in blotter)..."
                value={resolveNotes}
                onChange={(e) => setResolveNotes(e.target.value)}
                className="w-full p-2.5 rounded-lg bg-slate-900 border border-slate-700 text-slate-200 text-xs focus:ring-1 focus:ring-emerald-500 focus:outline-none"
                rows={2}
                required
              />
              <div className="flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setShowResolveForm(false)}
                  className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-3 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white font-semibold"
                >
                  Confirm Resolution
                </button>
              </div>
            </form>
          )}
        </div>

        {/* Footer Actions */}
        <div className="p-4 border-t border-slate-800 bg-slate-900/90 flex items-center justify-between">
          <button
            onClick={() => {
              setSelectedTradeId(exc.tradeId);
              setSelectedExceptionId(null);
              setActiveTab('trades');
            }}
            className="flex items-center gap-1.5 text-slate-400 hover:text-slate-200 text-xs"
          >
            <span>Inspect Trade Details</span>
            <ArrowRight size={14} />
          </button>

          <div className="flex items-center gap-2">
            {!isResolved && !showResolveForm && (
              <button
                onClick={() => setShowResolveForm(true)}
                className="flex items-center gap-1.5 px-4 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-semibold shadow-sm transition-colors"
              >
                <CheckCircle2 size={15} />
                <span>Resolve Exception</span>
              </button>
            )}
            <button
              onClick={() => setSelectedExceptionId(null)}
              className="px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-medium transition-colors"
            >
              Close
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
