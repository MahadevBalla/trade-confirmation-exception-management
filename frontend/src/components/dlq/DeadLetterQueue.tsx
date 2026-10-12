import React, { useState } from 'react';
import { useApp } from '../../context/useApp';
import { JsonViewer } from '../common/JsonViewer';
import {
  RotateCcw,
  AlertOctagon,
  ChevronDown,
  ChevronUp,
  ShieldCheck,
  CheckCircle2,
  Code2,
} from 'lucide-react';

export const DeadLetterQueue: React.FC = () => {
  const { dlqEntries, replayDLQ, setSelectedTradeId, setActiveTab } = useApp();

  const [expandedDlqId, setExpandedDlqId] = useState<string | null>(null);
  const [replayingId, setReplayingId] = useState<string | null>(null);

  const handleReplayClick = (dlqId: string) => {
    setReplayingId(dlqId);
    setTimeout(() => {
      replayDLQ(dlqId);
      setReplayingId(null);
    }, 800);
  };

  return (
    <div className="space-y-5">
      {/* Overview Card */}
      <div className="p-5 rounded-2xl border border-red-500/30 bg-gradient-to-r from-red-950/30 via-slate-900 to-slate-900 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 text-red-400 font-bold text-base">
            <AlertOctagon size={20} />
            <h2>Dead-Letter Queue &amp; Replay Console (FR12)</h2>
          </div>
          <p className="text-xs text-slate-300 mt-1 max-w-2xl leading-relaxed">
            Irrecoverable or transient pipeline failures are captured on the <span className="font-mono text-red-300">deadletter.trade</span> Kafka topic with diagnostic exception stack traces. Safe replay resets idempotency keys and re-publishes to upstream topics with an incremented replay attempt count.
          </p>
        </div>

        <div className="px-3 py-1.5 rounded-xl bg-red-500/20 text-red-300 border border-red-500/30 text-xs font-semibold shrink-0">
          DLQ Messages: {dlqEntries.length}
        </div>
      </div>

      {/* DLQ List */}
      <div className="space-y-3">
        {dlqEntries.length > 0 ? (
          dlqEntries.map((entry) => {
            const isExpanded = expandedDlqId === entry.dlqId;
            const isReplaying = replayingId === entry.dlqId;
            const isSucceeded = entry.status === 'REPLAY_SUCCEEDED';

            return (
              <div
                key={entry.dlqId}
                className={`rounded-2xl border transition-all ${
                  isSucceeded
                    ? 'border-emerald-500/30 bg-emerald-950/10'
                    : 'border-slate-800 bg-slate-900/60'
                }`}
              >
                {/* Header Summary Row */}
                <div className="p-4 flex flex-col md:flex-row items-start md:items-center justify-between gap-3">
                  <div className="flex items-start gap-3">
                    <div className="p-2 rounded-xl bg-red-500/10 text-red-400 shrink-0 mt-0.5">
                      <RotateCcw size={18} />
                    </div>
                    <div>
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className="font-bold text-white text-xs">{entry.dlqId}</span>
                        <span className="text-[11px] font-mono px-2 py-0.5 rounded bg-slate-800 text-slate-300 border border-slate-700">
                          Failed on: {entry.failedOnTopic}
                        </span>
                        <span className="text-[11px] font-mono px-2 py-0.5 rounded bg-slate-800 text-slate-300 border border-slate-700">
                          Service: {entry.failedInService}
                        </span>
                        <span
                          className={`text-xs px-2 py-0.5 rounded-full font-semibold ${
                            isSucceeded
                              ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
                              : 'bg-red-500/20 text-red-400 border border-red-500/30'
                          }`}
                        >
                          {entry.status}
                        </span>
                      </div>

                      <p className="text-xs text-rose-300 mt-1 font-medium leading-relaxed">
                        {entry.failureReason}
                      </p>

                      <div className="flex items-center gap-3 text-[11px] text-slate-500 mt-1.5 font-mono">
                        <span>
                          Trade:{' '}
                          <button
                            onClick={() => {
                              setSelectedTradeId(entry.tradeId);
                              setActiveTab('trades');
                            }}
                            className="text-indigo-400 hover:underline font-semibold"
                          >
                            {entry.tradeId}
                          </button>
                        </span>
                        <span>•</span>
                        <span>Failed at: {entry.failedAt}</span>
                        <span>•</span>
                        <span className="text-slate-400">Replay Attempts: {entry.replayCount}</span>
                      </div>
                    </div>
                  </div>

                  {/* Actions */}
                  <div className="flex items-center gap-2 self-end md:self-center shrink-0">
                    {entry.replayable && !isSucceeded && (
                      <button
                        onClick={() => handleReplayClick(entry.dlqId)}
                        disabled={isReplaying}
                        className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl bg-red-600 hover:bg-red-500 text-white text-xs font-semibold shadow-md shadow-red-600/20 transition-all disabled:opacity-50"
                      >
                        <RotateCcw size={14} className={isReplaying ? 'animate-spin' : ''} />
                        <span>{isReplaying ? 'Replaying...' : 'Replay Message'}</span>
                      </button>
                    )}

                    {isSucceeded && (
                      <span className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 text-xs font-semibold">
                        <CheckCircle2 size={14} />
                        <span>Replay Succeeded</span>
                      </span>
                    )}

                    <button
                      onClick={() => setExpandedDlqId(isExpanded ? null : entry.dlqId)}
                      className="p-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 transition-colors"
                      title={isExpanded ? 'Collapse Diagnostics' : 'Expand Diagnostics'}
                    >
                      {isExpanded ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                    </button>
                  </div>
                </div>

                {/* Expanded Diagnostic Section */}
                {isExpanded && (
                  <div className="p-4 border-t border-slate-800/80 bg-slate-950/70 space-y-4 text-xs animate-in slide-in-from-top-1">
                    {/* Replay Protocol Explanation */}
                    <div className="p-3 rounded-xl border border-slate-800 bg-slate-900/60 flex items-start gap-3">
                      <ShieldCheck size={18} className="text-indigo-400 shrink-0 mt-0.5" />
                      <div className="space-y-1 text-slate-300">
                        <span className="font-semibold text-slate-200">Replay Protocol Contract (Spec §7 FR12):</span>
                        <p className="text-[11px] text-slate-400 leading-relaxed">
                          Replay triggers workitem-service to clear the idempotency key for <code className="text-indigo-300">{entry.originalMessageHeader.messageId}</code>, increments <code className="text-indigo-300">replayCount</code> to {entry.replayCount + 1}, sets <code className="text-indigo-300">eventType = REPLAY</code>, and republishes the verbatim JSON payload to topic <code className="text-indigo-300">{entry.failedOnTopic}</code>.
                        </p>
                      </div>
                    </div>

                    {/* Java Stack Trace */}
                    {entry.stackTraceExcerpt && (
                      <div>
                        <div className="flex items-center gap-2 text-slate-400 font-semibold mb-1.5 text-[11px]">
                          <Code2 size={14} />
                          <span>Diagnostic Stack Trace Excerpt</span>
                        </div>
                        <pre className="p-3 rounded-xl border border-slate-800 bg-slate-950 font-mono text-[11px] text-rose-400/90 overflow-x-auto whitespace-pre-wrap max-h-48 leading-relaxed">
                          {entry.stackTraceExcerpt}
                        </pre>
                      </div>
                    )}

                    {/* Original Inbound Payload */}
                    {entry.originalPayload && (
                      <div>
                        <div className="flex items-center justify-between text-slate-400 font-semibold mb-1.5 text-[11px]">
                          <span>Original Failing Message Payload (Preserved for Replay)</span>
                        </div>
                        <JsonViewer data={JSON.parse(entry.originalPayload)} title="Kafka Message Payload" />
                      </div>
                    )}
                  </div>
                )}
              </div>
            );
          })
        ) : (
          <div className="p-12 text-center rounded-2xl border border-slate-800 bg-slate-900/60 text-slate-400">
            <CheckCircle2 size={32} className="mx-auto mb-3 text-emerald-400" />
            <h3 className="text-base font-semibold text-slate-200">Dead-Letter Queue is Empty</h3>
            <p className="text-xs text-slate-400 mt-1">
              No failed messages currently queued on <code className="text-slate-300">deadletter.trade</code>.
            </p>
          </div>
        )}
      </div>
    </div>
  );
};
