import React from 'react';
import { MetricsGrid } from './MetricsGrid';
import { STPChart } from './STPChart';
import { CategoryBreakdown } from './CategoryBreakdown';
import { AgingChart } from './AgingChart';
import { StreamTicker } from './StreamTicker';
import { useApp } from '../../context/useApp';
import { Sparkles, Zap } from 'lucide-react';

export const DashboardView: React.FC = () => {
  const { triggerSyntheticScenario, dataMode } = useApp();
  const verb = dataMode === 'live' ? 'Publish' : 'Simulate';

  return (
    <div className="space-y-6">
      {/* Banner / Ingestion Quick Action Bar */}
      <div className="p-5 rounded-2xl border border-indigo-500/20 bg-gradient-to-r from-indigo-950/40 via-slate-900 to-slate-900 flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 text-indigo-400 font-bold text-base">
            <Zap size={18} />
            <h2>Real-Time Post-Trade Operations Center</h2>
          </div>
          <p className="text-xs text-slate-300 mt-1 max-w-2xl leading-relaxed">
            Consumes post-trade events via Apache Kafka, enriches reference attributes, executes Straight-Through Processing rules, generates electronic ISDA confirmations, and classifies exceptions for targeted remediation.
          </p>
        </div>

        {/* Quick Demo Scenario Buttons */}
        <div className="flex flex-wrap items-center gap-2">
          <button
            onClick={() => triggerSyntheticScenario('CLEAN_STP')}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-emerald-600/20 hover:bg-emerald-600/30 text-emerald-400 border border-emerald-500/30 text-xs font-semibold transition-all"
            title="Inject clean trade that passes straight through to confirmation"
          >
            <Sparkles size={13} />
            <span>{verb} Clean STP</span>
          </button>
          <button
            onClick={() => triggerSyntheticScenario('MISSING_STRIKE')}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-rose-600/20 hover:bg-rose-600/30 text-rose-400 border border-rose-500/30 text-xs font-semibold transition-all"
            title="Inject trade with missing strike to trigger exception pipeline"
          >
            <Sparkles size={13} />
            <span>{verb} Missing Strike</span>
          </button>
          <button
            onClick={() => triggerSyntheticScenario('TECHNICAL_DLQ')}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-red-600/20 hover:bg-red-600/30 text-red-400 border border-red-500/30 text-xs font-semibold transition-all"
            title={dataMode === 'live' ? 'Publish invalid JSON, which goes to the Dead-Letter Queue and cannot be replayed' : 'Inject technical timeout to test Dead-Letter Queue & Replay'}
          >
            <Sparkles size={13} />
            <span>{dataMode === 'live' ? 'Publish Malformed Message' : 'Simulate DLQ Failure'}</span>
          </button>
        </div>
      </div>

      {/* KPI Cards Grid */}
      <MetricsGrid />

      {/* Analytics Row: STP Chart & Root Cause Categories */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <STPChart />
        <CategoryBreakdown />
      </div>

      {/* Aging Distribution */}
      <AgingChart />

      {/* Live Kafka Stream Ticker */}
      <StreamTicker />
    </div>
  );
};
