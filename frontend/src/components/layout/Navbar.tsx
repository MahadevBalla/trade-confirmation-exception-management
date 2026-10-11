import React, { useState } from 'react';
import { useApp } from '../../context/useApp';
import type { UserRole } from '../../types/tcem';
import {
  Activity,
  Play,
  Square,
  Sparkles,
  ChevronDown,
  Layers,
  Cpu,
} from 'lucide-react';

export const Navbar: React.FC = () => {
  const {
    userRole,
    setUserRole,
    isStreaming,
    setIsStreaming,
    triggerSyntheticScenario,
    metrics,
  } = useApp();

  const [showScenarioMenu, setShowScenarioMenu] = useState(false);

  const scenarios = [
    { id: 'CLEAN_STP', label: '1. Clean STP Happy Path', desc: 'FX Option -> STP Passed -> Confirmation Generated' },
    { id: 'MISSING_STRIKE', label: '2. Missing Economic Strike', desc: 'Triggers MISSING_ECONOMIC_DATA -> Data Remediation Queue' },
    { id: 'TECHNICAL_DLQ', label: '3. Technical Timeout (DLQ)', desc: 'Routes to deadletter.trade with diagnostic stack trace' },
    { id: 'MANUAL_DISPUTE', label: '4. Counterparty Matching Dispute', desc: 'Terms discrepancy -> Routes to Manual Review Queue' },
  ];

  return (
    <header className="sticky top-0 z-30 flex h-auto min-h-16 flex-wrap items-center justify-between gap-y-2 border-b border-slate-800 bg-slate-900/90 px-3 py-2 backdrop-blur sm:px-6">
      {/* Brand & Broker Status */}
      <div className="flex items-center gap-4">
        <div className="flex items-center gap-2.5">
          <div className="w-9 h-9 rounded-lg bg-gradient-to-tr from-indigo-600 to-cyan-500 flex items-center justify-center shadow-lg shadow-indigo-500/20">
            <Layers className="text-white w-5 h-5" />
          </div>
          <div>
            <h1 className="text-base font-bold text-white tracking-tight flex items-center gap-2">
              TCEM Platform
              <span className="text-[10px] font-semibold tracking-wider uppercase px-1.5 py-0.5 rounded bg-indigo-500/20 text-indigo-300 border border-indigo-500/30">
                Capstone Ops
              </span>
            </h1>
            <p className="text-xs text-slate-400">Trade Confirmation & Exception Management</p>
          </div>
        </div>

        {/* Kafka Broker Live Pill */}
        <div className="hidden lg:flex items-center gap-2 px-2.5 py-1 rounded-full bg-slate-800/80 border border-slate-700/60 text-xs">
          <span className="relative flex h-2 w-2">
            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
            <span className="relative inline-flex rounded-full h-2 w-2 bg-emerald-500"></span>
          </span>
          <span className="text-slate-300 font-mono">Kafka: Online</span>
          <span className="text-slate-500">•</span>
          <span className="text-slate-400">CouchDB: Connected</span>
        </div>
      </div>

      {/* Right Controls: Ingestion Simulator, Streaming Toggle, Role Switcher */}
      <div className="flex w-full items-center justify-between gap-2 sm:w-auto sm:justify-end sm:gap-3">
        {/* Synthetic Ingestion Batch Trigger (FR1: Ingestion only via simulated broker events) */}
        <div className="relative">
          <button
            onClick={() => setShowScenarioMenu(!showScenarioMenu)}
            className="flex items-center gap-2 rounded-lg bg-indigo-600 px-2 py-1.5 text-xs font-medium text-white shadow-md shadow-indigo-600/25 transition-all hover:bg-indigo-500 sm:px-3"
            title="Simulate synthetic trade message batch through Kafka"
          >
            <Sparkles size={14} />
            <span className="hidden sm:inline">Ingest Synthetic Batch</span>
            <ChevronDown size={14} className="hidden sm:block" />
          </button>

          {showScenarioMenu && (
            <div
              className="absolute right-0 mt-2 w-72 rounded-xl border border-slate-800 bg-slate-900 shadow-2xl py-2 z-50 animate-in fade-in slide-in-from-top-2"
              onMouseLeave={() => setShowScenarioMenu(false)}
            >
              <div className="px-3 py-1.5 border-b border-slate-800 text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
                Simulate Kafka Inbound Event
              </div>
              {scenarios.map((sc) => (
                <button
                  key={sc.id}
                  onClick={() => {
                    triggerSyntheticScenario(sc.id);
                    setShowScenarioMenu(false);
                  }}
                  className="w-full text-left px-3 py-2 hover:bg-slate-800/80 transition-colors flex flex-col"
                >
                  <span className="text-xs font-medium text-slate-200">{sc.label}</span>
                  <span className="text-[11px] text-slate-400">{sc.desc}</span>
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Live Auto-Stream Toggle */}
        <button
          onClick={() => setIsStreaming(!isStreaming)}
          className={`flex items-center gap-1.5 rounded-lg border px-2 py-1.5 text-xs font-medium transition-colors sm:px-3 ${
            isStreaming
              ? 'bg-rose-500/10 border-rose-500/30 text-rose-400 hover:bg-rose-500/20'
              : 'bg-slate-800 hover:bg-slate-700/80 border-slate-700 text-slate-300'
          }`}
          title={isStreaming ? 'Stop streaming simulator' : 'Start auto-generating synthetic trade events'}
        >
          {isStreaming ? (
            <>
              <Square size={12} className="fill-current text-rose-400" />
              <span className="hidden sm:inline">Stop Stream</span>
            </>
          ) : (
            <>
              <Play size={12} className="fill-current text-emerald-400" />
              <span className="hidden sm:inline">Auto Stream</span>
            </>
          )}
        </button>

        {/* Role Selector (Spec §9 Stretch, §13 Security) */}
        <div className="flex items-center gap-1.5 pl-2 border-l border-slate-800">
          <Cpu size={14} className="text-slate-400 hidden sm:block" />
          <select
            value={userRole}
            onChange={(e) => setUserRole(e.target.value as UserRole)}
            className="max-w-[170px] cursor-pointer rounded-lg border border-slate-700 bg-slate-800/90 px-2.5 py-1.5 text-xs text-slate-200 focus:outline-none focus:ring-1 focus:ring-indigo-500 sm:max-w-none"
          >
            <option value="OPERATIONS_SPECIALIST">Role: Operations Specialist</option>
            <option value="SUPERVISOR">Role: Supervisor / Lead</option>
            <option value="SYSTEM_ADMIN">Role: System Administrator</option>
          </select>
        </div>

        {/* Live STP badge */}
        <div className="hidden xl:flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-xs font-semibold">
          <Activity size={14} />
          <span>STP {metrics.stpRatePercent}%</span>
        </div>
      </div>
    </header>
  );
};
