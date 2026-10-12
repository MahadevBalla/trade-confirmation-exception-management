import React from 'react';
import { useApp } from '../../context/useApp';
import { CheckCircle2, AlertTriangle, FileCheck, RotateCcw } from 'lucide-react';

export const STPChart: React.FC = () => {
  const { trades, metrics } = useApp();

  const total = trades.length || 1;
  const confirmed = trades.filter((t) => t.status === 'CONFIRMED').length;
  const exceptions = trades.filter((t) => t.status === 'EXCEPTION_CAPTURED' || t.status === 'WORKITEM_LINKED').length;
  const manual = trades.filter((t) => t.status === 'MANUAL_REVIEW').length;
  const dlq = trades.filter((t) => t.status === 'DEAD_LETTER').length;

  const confirmedPct = Math.round((confirmed / total) * 100);
  const exceptionsPct = Math.round((exceptions / total) * 100);
  const manualPct = Math.round((manual / total) * 100);
  const dlqPct = Math.max(0, 100 - confirmedPct - exceptionsPct - manualPct);

  return (
    <div className="rounded-2xl border border-slate-800 bg-slate-900/60 p-5 flex flex-col justify-between">
      <div>
        <div className="flex items-center justify-between mb-4">
          <div>
            <h3 className="text-sm font-semibold text-white">STP vs. Exception Distribution</h3>
            <p className="text-xs text-slate-400">Straight-Through Processing efficiency across pipeline</p>
          </div>
          <div className="flex items-baseline gap-1">
            <span className="text-2xl font-bold text-emerald-400">{metrics.stpRatePercent}%</span>
            <span className="text-xs text-slate-400 font-medium">STP Rate</span>
          </div>
        </div>

        {/* Visual Segmented Bar */}
        <div className="h-4 w-full bg-slate-800 rounded-full overflow-hidden flex my-4">
          <div
            style={{ width: `${confirmedPct}%` }}
            className="bg-emerald-500 h-full transition-all duration-500"
            title={`Confirmed (STP): ${confirmedPct}%`}
          />
          <div
            style={{ width: `${exceptionsPct}%` }}
            className="bg-rose-500 h-full transition-all duration-500"
            title={`Exceptions: ${exceptionsPct}%`}
          />
          <div
            style={{ width: `${manualPct}%` }}
            className="bg-amber-500 h-full transition-all duration-500"
            title={`Manual Review: ${manualPct}%`}
          />
          <div
            style={{ width: `${dlqPct}%` }}
            className="bg-red-600 h-full transition-all duration-500"
            title={`Dead Letter: ${dlqPct}%`}
          />
        </div>
      </div>

      {/* Legend & Breakdown stats */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 pt-2 border-t border-slate-800/80">
        <div className="flex items-center gap-2">
          <div className="p-1 rounded bg-emerald-500/10 text-emerald-400">
            <CheckCircle2 size={14} />
          </div>
          <div>
            <div className="text-xs font-semibold text-slate-200">{confirmed} ({confirmedPct}%)</div>
            <div className="text-[11px] text-slate-400">Confirmed (STP)</div>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <div className="p-1 rounded bg-rose-500/10 text-rose-400">
            <AlertTriangle size={14} />
          </div>
          <div>
            <div className="text-xs font-semibold text-slate-200">{exceptions} ({exceptionsPct}%)</div>
            <div className="text-[11px] text-slate-400">Open Exceptions</div>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <div className="p-1 rounded bg-amber-500/10 text-amber-400">
            <FileCheck size={14} />
          </div>
          <div>
            <div className="text-xs font-semibold text-slate-200">{manual} ({manualPct}%)</div>
            <div className="text-[11px] text-slate-400">Manual Review</div>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <div className="p-1 rounded bg-red-600/10 text-red-400">
            <RotateCcw size={14} />
          </div>
          <div>
            <div className="text-xs font-semibold text-slate-200">{dlq} ({dlqPct}%)</div>
            <div className="text-[11px] text-slate-400">Dead-Letter Queue</div>
          </div>
        </div>
      </div>
    </div>
  );
};
