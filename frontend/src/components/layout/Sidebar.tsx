import React from 'react';
import { useApp } from '../../context/useApp';
import {
  LayoutDashboard,
  GitBranch,
  AlertTriangle,
  FileCheck,
  CheckSquare,
  RotateCcw,
  History,
  Network,
} from 'lucide-react';

export const Sidebar: React.FC = () => {
  const { activeTab, setActiveTab, metrics, trades } = useApp();

  const manualReviewCount = trades.filter((t) => t.status === 'MANUAL_REVIEW').length;

  const navItems = [
    {
      id: 'dashboard',
      label: 'Operations Overview',
      icon: LayoutDashboard,
      badge: null,
      badgeColor: '',
    },
    {
      id: 'trades',
      label: 'Trade Lifecycle',
      icon: GitBranch,
      badge: metrics.totalTrades,
      badgeColor: 'bg-slate-800 text-slate-300',
    },
    {
      id: 'exceptions',
      label: 'Exception Queue',
      icon: AlertTriangle,
      badge: metrics.openExceptionsCount > 0 ? metrics.openExceptionsCount : null,
      badgeColor: 'bg-rose-500/20 text-rose-400 border border-rose-500/30',
    },
    {
      id: 'manualReview',
      label: 'Manual Review',
      icon: FileCheck,
      badge: manualReviewCount > 0 ? manualReviewCount : null,
      badgeColor: 'bg-amber-500/20 text-amber-400 border border-amber-500/30 animate-pulse',
    },
    {
      id: 'workitems',
      label: 'Work Items Tracker',
      icon: CheckSquare,
      badge: metrics.activeWorkItemsCount > 0 ? metrics.activeWorkItemsCount : null,
      badgeColor: 'bg-blue-500/20 text-blue-400 border border-blue-500/30',
    },
    {
      id: 'dlq',
      label: 'Dead-Letter Queue',
      icon: RotateCcw,
      badge: metrics.dlqCount > 0 ? metrics.dlqCount : null,
      badgeColor: 'bg-red-500/20 text-red-400 border border-red-500/30',
    },
    {
      id: 'audit',
      label: 'Audit Trail Explorer',
      icon: History,
      badge: null,
      badgeColor: '',
    },
    {
      id: 'topology',
      label: 'Event Topology Map',
      icon: Network,
      badge: null,
      badgeColor: '',
    },
  ];

  return (
    <aside className="w-full shrink-0 border-b border-slate-800 bg-slate-900/60 p-2 lg:flex lg:w-64 lg:flex-col lg:justify-between lg:border-b-0 lg:border-r lg:p-4">
      <div>
        <div className="mb-2 hidden border-b border-slate-800/80 px-3 pb-3 lg:block">
          <span className="text-[11px] font-semibold tracking-wider text-slate-400 uppercase">
            Operations Workflows
          </span>
        </div>

        <nav className="flex gap-1 overflow-x-auto lg:flex-col lg:gap-0.5 lg:overflow-visible">
          {navItems.map((item) => {
            const Icon = item.icon;
            const isActive = activeTab === item.id;

            return (
              <button
                key={item.id}
                onClick={() => setActiveTab(item.id)}
                className={`flex w-auto shrink-0 items-center justify-between rounded-lg px-3 py-2.5 text-xs font-medium transition-all lg:w-full lg:rounded-xl ${
                  isActive
                    ? 'bg-indigo-600/20 text-indigo-300 border border-indigo-500/30 shadow-sm'
                    : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/60'
                }`}
              >
                <div className="flex items-center gap-3">
                  <Icon
                    size={17}
                    className={isActive ? 'text-indigo-400' : 'text-slate-500'}
                  />
                  <span className="whitespace-nowrap lg:whitespace-normal">{item.label}</span>
                </div>

                {item.badge !== null && (
                  <span
                    className={`text-[11px] px-2 py-0.5 rounded-full font-semibold ${item.badgeColor}`}
                  >
                    {item.badge}
                  </span>
                )}
              </button>
            );
          })}
        </nav>
      </div>

      {/* Footer Info Box */}
      <div className="hidden rounded-xl border border-slate-800/80 bg-slate-950/40 p-3 text-[11px] space-y-1.5 text-slate-400 lg:block">
        <div className="flex items-center justify-between text-slate-300 font-medium">
          <span>Target SLA</span>
          <span className="text-emerald-400">&lt; 10s STP</span>
        </div>
        <div className="flex items-center justify-between">
          <span>Design Target</span>
          <span>1M trades/day</span>
        </div>
        <div className="flex items-center justify-between">
          <span>Schema Model</span>
          <span className="font-mono text-[10px] text-indigo-300">FpML-JSON 1.0</span>
        </div>
      </div>
    </aside>
  );
};
