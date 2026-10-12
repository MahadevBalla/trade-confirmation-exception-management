import React from 'react';
import { useApp } from '../../context/useApp';
import {
  Activity,
  CheckCircle2,
  AlertTriangle,
  RotateCcw,
  Clock,
  ListTodo,
} from 'lucide-react';

export const MetricsGrid: React.FC = () => {
  const { metrics, setActiveTab } = useApp();

  const cards = [
    {
      title: 'Total Ingested Trades',
      value: metrics.totalTrades,
      subtitle: `${metrics.stpPassedCount} successfully confirmed`,
      icon: Activity,
      color: 'from-blue-500/10 to-indigo-500/10 text-indigo-400 border-indigo-500/20',
      iconBg: 'bg-indigo-500/20 text-indigo-400',
      tab: 'trades',
    },
    {
      title: 'Straight-Through Rate (STP)',
      value: `${metrics.stpRatePercent}%`,
      subtitle: 'Target: > 85% without manual touch',
      icon: CheckCircle2,
      color: 'from-emerald-500/10 to-teal-500/10 text-emerald-400 border-emerald-500/20',
      iconBg: 'bg-emerald-500/20 text-emerald-400',
      tab: 'trades',
    },
    {
      title: 'Open Exceptions',
      value: metrics.openExceptionsCount,
      subtitle: 'Requiring operational remediation',
      icon: AlertTriangle,
      color: 'from-rose-500/10 to-pink-500/10 text-rose-400 border-rose-500/20',
      iconBg: 'bg-rose-500/20 text-rose-400',
      tab: 'exceptions',
    },
    {
      title: 'Active Work Items',
      value: metrics.activeWorkItemsCount,
      subtitle: 'Linked defect & data fix tickets',
      icon: ListTodo,
      color: 'from-amber-500/10 to-yellow-500/10 text-amber-400 border-amber-500/20',
      iconBg: 'bg-amber-500/20 text-amber-400',
      tab: 'workitems',
    },
    {
      title: 'Dead-Letter Queue (DLQ)',
      value: metrics.dlqCount,
      subtitle: 'Failed messages eligible for replay',
      icon: RotateCcw,
      color: 'from-red-600/10 to-red-500/10 text-red-400 border-red-500/20',
      iconBg: 'bg-red-500/20 text-red-400',
      tab: 'dlq',
    },
    {
      title: 'SLA Breach Risk',
      value: metrics.slaBreachRiskCount,
      subtitle: 'Exceptions with deadline < 2h',
      icon: Clock,
      color: 'from-purple-500/10 to-violet-500/10 text-purple-400 border-purple-500/20',
      iconBg: 'bg-purple-500/20 text-purple-400',
      tab: 'exceptions',
    },
  ];

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6 gap-4">
      {cards.map((card, i) => {
        const Icon = card.icon;
        return (
          <button
            key={i}
            onClick={() => setActiveTab(card.tab)}
            className={`p-4 rounded-2xl bg-gradient-to-b ${card.color} border bg-slate-900/60 text-left hover:scale-[1.02] transition-transform duration-200 cursor-pointer flex flex-col justify-between`}
          >
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-slate-400">{card.title}</span>
              <div className={`p-1.5 rounded-lg ${card.iconBg}`}>
                <Icon size={16} />
              </div>
            </div>
            <div className="mt-3">
              <div className="text-2xl font-bold text-white tracking-tight">{card.value}</div>
              <p className="text-[11px] text-slate-400 mt-1 leading-tight">{card.subtitle}</p>
            </div>
          </button>
        );
      })}
    </div>
  );
};
