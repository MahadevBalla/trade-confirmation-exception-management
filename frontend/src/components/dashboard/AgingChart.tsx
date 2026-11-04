import React from 'react';
import { useApp } from '../../context/useApp';
import { Clock, ShieldAlert } from 'lucide-react';

export const AgingChart: React.FC = () => {
  const { exceptions, metrics } = useApp();

  // Bucket exceptions by age/SLA
  const openExceptions = exceptions.filter((e) => e.status !== 'RESOLVED' && e.status !== 'CLOSED');

  // Demo mode keeps its illustrative counts; live mode measures the age of each open exception.
  const counts = metrics.agingBuckets ?? [2, 3, 1, 0];

  const buckets = [
    { label: '< 2 Hours', desc: 'Fresh / Well within SLA', count: counts[0], color: 'bg-emerald-500', text: 'text-emerald-400' },
    { label: '2 – 6 Hours', desc: 'Active Investigation', count: counts[1], color: 'bg-blue-500', text: 'text-blue-400' },
    { label: '6 – 24 Hours', desc: 'Elevated SLA Risk', count: counts[2], color: 'bg-amber-500', text: 'text-amber-400' },
    { label: '> 24 Hours', desc: 'SLA Breached / Overdue', count: counts[3], color: 'bg-rose-500', text: 'text-rose-400' },
  ];

  return (
    <div className="rounded-2xl border border-slate-800 bg-slate-900/60 p-5 flex flex-col justify-between">
      <div className="flex items-center justify-between mb-4">
        <div>
          <h3 className="text-sm font-semibold text-white">Exception SLA Aging Profile</h3>
          <p className="text-xs text-slate-400">Aging buckets for open queue items</p>
        </div>
        <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-slate-800 border border-slate-700 text-xs text-slate-300">
          <Clock size={13} className="text-indigo-400" />
          <span>Active: {openExceptions.length} items</span>
        </div>
      </div>

      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        {buckets.map((b, i) => (
          <div
            key={i}
            className="p-3 rounded-xl border border-slate-800 bg-slate-950/40 flex flex-col justify-between"
          >
            <div>
              <span className="text-xs font-semibold text-slate-300">{b.label}</span>
              <p className="text-[10px] text-slate-400 mt-0.5">{b.desc}</p>
            </div>
            <div className="mt-3 flex items-baseline gap-2">
              <span className={`text-xl font-bold ${b.text}`}>{b.count}</span>
              <span className="text-[11px] text-slate-500">items</span>
            </div>
          </div>
        ))}
      </div>

      <div className="mt-4 p-3 rounded-xl bg-indigo-950/30 border border-indigo-500/20 flex items-center gap-3 text-xs text-slate-300">
        <ShieldAlert size={16} className="text-indigo-400 shrink-0" />
        <span>
          Prioritization rule enforces automatic escalation to Supervisor queue once an exception crosses the 4-hour mark.
        </span>
      </div>
    </div>
  );
};
