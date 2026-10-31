import React, { useState } from 'react';
import { useApp } from '../../context/useApp';
import { StatusBadge } from '../common/StatusBadge';
import {
  FileCheck,
  CheckCircle2,
  XCircle,
  ArrowRight,
  ShieldAlert,
} from 'lucide-react';

export const ManualReviewQueue: React.FC = () => {
  const { trades, approveManualReview, rejectManualReview, setSelectedTradeId, setActiveTab } = useApp();

  const manualTrades = trades.filter((t) => t.status === 'MANUAL_REVIEW');
  const [selectedTrade, setSelectedTrade] = useState<string | null>(
    manualTrades.length > 0 ? manualTrades[0].tradeId : null
  );

  const [overrideNotes, setOverrideNotes] = useState('');
  const [rejectionReason, setRejectionReason] = useState('');
  const [actionType, setActionType] = useState<'APPROVE' | 'REJECT' | null>(null);

  const currentTrade = manualTrades.find((t) => t.tradeId === selectedTrade) || manualTrades[0];

  const handleApprove = (e: React.FormEvent) => {
    e.preventDefault();
    if (!currentTrade) return;
    approveManualReview(
      currentTrade.tradeId,
      overrideNotes || 'Approved by Operations Supervisor after bilateral reconciliation'
    );
    setActionType(null);
    setOverrideNotes('');
  };

  const handleReject = (e: React.FormEvent) => {
    e.preventDefault();
    if (!currentTrade) return;
    rejectManualReview(
      currentTrade.tradeId,
      rejectionReason || 'Irreconcilable economic mismatch between parties'
    );
    setActionType(null);
    setRejectionReason('');
  };

  // Mock bilateral comparison data for demo
  const counterpartyReported = {
    notional: currentTrade?.fxOptionEconomics?.notionalAmount ?? 2500000,
    currency: currentTrade?.fxOptionEconomics?.notionalCurrency ?? 'USD',
    strike: currentTrade?.tradeId === 'TRD-10007' ? 1.085 : currentTrade?.fxOptionEconomics?.strike ?? 1.082,
    underlier: currentTrade?.fxOptionEconomics?.underlier ?? 'EUR/USD',
    buySell: currentTrade?.fxOptionEconomics?.buySell === 'BUY' ? 'SELL' : 'BUY', // Inverse perspective
    optionType: currentTrade?.fxOptionEconomics?.optionType ?? 'CALL',
  };

  return (
    <div className="space-y-5">
      {/* Overview Banner */}
      <div className="p-5 rounded-2xl border border-amber-500/30 bg-gradient-to-r from-amber-950/30 via-slate-900 to-slate-900 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 text-amber-400 font-bold text-base">
            <FileCheck size={20} />
            <h2>Four-Eyes Manual Review &amp; Reconciliation Queue</h2>
          </div>
          <p className="text-xs text-slate-300 mt-1 max-w-2xl leading-relaxed">
            Trades in this queue failed automated straight-through processing due to counterparty terms mismatch or restrictive risk policy. Human review and four-eyes sign-off is required to approve electronic confirmation or reject to DLQ.
          </p>
        </div>

        <div className="px-3 py-1.5 rounded-xl bg-amber-500/20 text-amber-300 border border-amber-500/30 text-xs font-semibold shrink-0">
          Pending Review: {manualTrades.length} trades
        </div>
      </div>

      {manualTrades.length > 0 && currentTrade ? (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">
          {/* Left: Queue List */}
          <div className="lg:col-span-4 rounded-2xl border border-slate-800 bg-slate-900/60 p-4 space-y-2">
            <div className="text-xs font-semibold uppercase tracking-wider text-slate-400 px-2 pb-2 border-b border-slate-800">
              Select Trade to Review
            </div>

            <div className="space-y-2 max-h-[600px] overflow-y-auto pt-1">
              {manualTrades.map((t) => {
                const isSelected = (currentTrade?.tradeId ?? '') === t.tradeId;
                const cp = t.parties.find((p) => p.partyRole === 'COUNTERPARTY');
                return (
                  <button
                    key={t.tradeId}
                    onClick={() => {
                      setSelectedTrade(t.tradeId);
                      setActionType(null);
                    }}
                    className={`w-full text-left p-3 rounded-xl border transition-all ${
                      isSelected
                        ? 'border-amber-500/50 bg-amber-500/10 shadow-sm'
                        : 'border-slate-800/80 bg-slate-950/40 hover:bg-slate-800/40'
                    }`}
                  >
                    <div className="flex items-center justify-between text-xs">
                      <span className="font-bold text-white">{t.tradeId}</span>
                      <span className="text-[10px] font-mono px-1.5 py-0.2 rounded bg-slate-800 text-amber-300">
                        v{t.tradeVersion}
                      </span>
                    </div>
                    <div className="text-[11px] text-slate-400 mt-1 truncate">
                      {cp?.legalEntityName || cp?.partyName || cp?.partyId}
                    </div>
                    <div className="flex items-center justify-between text-[11px] text-slate-500 mt-2 font-mono">
                      <span>{t.productType}</span>
                      <span className="text-slate-300">
                        {t.fxOptionEconomics?.notionalCurrency}{' '}
                        {t.fxOptionEconomics?.notionalAmount?.toLocaleString()}
                      </span>
                    </div>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Right: Bilateral Discrepancy Inspector & Review Actions */}
          <div className="lg:col-span-8 space-y-4">
            <div className="p-5 rounded-2xl border border-slate-800 bg-slate-900/60 space-y-5">
              <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-800 pb-4">
                <div>
                  <div className="flex items-center gap-3">
                    <h3 className="text-base font-bold text-white">{currentTrade.tradeId}</h3>
                    <StatusBadge status={currentTrade.status} />
                    <span className="text-xs font-mono text-slate-400">
                      Source: {currentTrade.sourceSystemTradeId}
                    </span>
                  </div>
                  <p className="text-xs text-slate-400 mt-1">
                    Trade Date: {currentTrade.tradeDate} • Maturity: {currentTrade.maturityDate}
                  </p>
                </div>

                <button
                  onClick={() => {
                    setSelectedTradeId(currentTrade.tradeId);
                    setActiveTab('trades');
                  }}
                  className="flex items-center gap-1.5 text-xs text-indigo-400 hover:text-indigo-300"
                >
                  <span>Full Trade Details</span>
                  <ArrowRight size={13} />
                </button>
              </div>

              {/* Bilateral Comparison Table */}
              <div>
                <h4 className="text-xs font-semibold uppercase tracking-wider text-slate-300 mb-3 flex items-center gap-2">
                  <ShieldAlert size={15} className="text-amber-400" />
                  <span>Bilateral Term Discrepancy Reconciliation</span>
                </h4>

                <div className="border border-slate-800 rounded-xl overflow-hidden bg-slate-950/60 text-xs">
                  <table className="w-full text-left">
                    <thead className="bg-slate-900 text-slate-400 uppercase text-[10px] tracking-wider border-b border-slate-800">
                      <tr>
                        <th className="py-2.5 px-4">Contract Field</th>
                        <th className="py-2.5 px-4 text-indigo-300">Internal Bank Record</th>
                        <th className="py-2.5 px-4 text-cyan-300">Counterparty Affirmation</th>
                        <th className="py-2.5 px-4 text-center">Match Status</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-800/60 font-mono">
                      <tr>
                        <td className="py-2.5 px-4 text-slate-400 font-sans">Product Type</td>
                        <td className="py-2.5 px-4 text-white">{currentTrade.productType}</td>
                        <td className="py-2.5 px-4 text-white">{currentTrade.productType}</td>
                        <td className="py-2.5 px-4 text-center text-emerald-400">MATCH</td>
                      </tr>
                      <tr>
                        <td className="py-2.5 px-4 text-slate-400 font-sans">Underlier Pair</td>
                        <td className="py-2.5 px-4 text-white">{currentTrade.fxOptionEconomics?.underlier}</td>
                        <td className="py-2.5 px-4 text-white">{counterpartyReported.underlier}</td>
                        <td className="py-2.5 px-4 text-center text-emerald-400">MATCH</td>
                      </tr>
                      <tr>
                        <td className="py-2.5 px-4 text-slate-400 font-sans">Notional Amount</td>
                        <td className="py-2.5 px-4 text-white">
                          {currentTrade.fxOptionEconomics?.notionalCurrency}{' '}
                          {currentTrade.fxOptionEconomics?.notionalAmount?.toLocaleString()}
                        </td>
                        <td className="py-2.5 px-4 text-white">
                          {counterpartyReported.currency} {counterpartyReported.notional.toLocaleString()}
                        </td>
                        <td className="py-2.5 px-4 text-center text-emerald-400">MATCH</td>
                      </tr>
                      <tr className={currentTrade.tradeId === 'TRD-10007' ? 'bg-rose-500/10' : ''}>
                        <td className="py-2.5 px-4 text-slate-400 font-sans font-semibold">Strike Rate</td>
                        <td className="py-2.5 px-4 text-rose-300 font-bold">
                          {currentTrade.fxOptionEconomics?.strike ?? 'MISSING'}
                        </td>
                        <td className="py-2.5 px-4 text-cyan-300 font-bold">
                          {counterpartyReported.strike}
                        </td>
                        <td className="py-2.5 px-4 text-center">
                          {currentTrade.tradeId === 'TRD-10007' ? (
                            <span className="px-2 py-0.5 rounded bg-rose-500/20 text-rose-400 font-sans font-bold text-[10px]">
                              DISCREPANCY
                            </span>
                          ) : (
                            <span className="text-emerald-400">MATCH</span>
                          )}
                        </td>
                      </tr>
                      <tr>
                        <td className="py-2.5 px-4 text-slate-400 font-sans">Option Style</td>
                        <td className="py-2.5 px-4 text-white">{currentTrade.fxOptionEconomics?.optionType}</td>
                        <td className="py-2.5 px-4 text-white">{counterpartyReported.optionType}</td>
                        <td className="py-2.5 px-4 text-center text-emerald-400">MATCH</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>

              {/* Review Actions Bar */}
              <div className="pt-2 border-t border-slate-800">
                {!actionType && (
                  <div className="flex items-center justify-between gap-3">
                    <span className="text-xs text-slate-400">
                      Determine operational verdict for trade {currentTrade.tradeId}:
                    </span>

                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => setActionType('REJECT')}
                        className="flex items-center gap-1.5 px-4 py-2 rounded-xl bg-red-600/20 hover:bg-red-600/30 text-red-400 border border-red-500/30 text-xs font-semibold transition-colors"
                      >
                        <XCircle size={15} />
                        <span>Reject to DLQ</span>
                      </button>

                      <button
                        onClick={() => setActionType('APPROVE')}
                        className="flex items-center gap-1.5 px-4 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-semibold shadow-md shadow-emerald-600/20 transition-colors"
                      >
                        <CheckCircle2 size={15} />
                        <span>Approve &amp; Generate Confirmation</span>
                      </button>
                    </div>
                  </div>
                )}

                {/* Approve Form */}
                {actionType === 'APPROVE' && (
                  <form onSubmit={handleApprove} className="p-4 rounded-xl border border-emerald-500/30 bg-emerald-500/5 space-y-3">
                    <div className="flex items-center justify-between text-xs">
                      <span className="font-semibold text-emerald-400">
                        Approve Confirmation (Manual Override)
                      </span>
                      <button
                        type="button"
                        onClick={() => setActionType(null)}
                        className="text-slate-400 hover:text-slate-200"
                      >
                        Cancel
                      </button>
                    </div>
                    <textarea
                      placeholder="Enter supervisor sign-off reason (e.g. Counterparty verbal confirmation received for 1.0850)..."
                      value={overrideNotes}
                      onChange={(e) => setOverrideNotes(e.target.value)}
                      className="w-full p-2.5 rounded-lg bg-slate-900 border border-slate-700 text-slate-200 text-xs focus:ring-1 focus:ring-emerald-500 focus:outline-none"
                      rows={2}
                      required
                    />
                    <div className="flex justify-end gap-2">
                      <button
                        type="button"
                        onClick={() => setActionType(null)}
                        className="px-3 py-1.5 rounded-lg bg-slate-800 text-slate-300 text-xs"
                      >
                        Cancel
                      </button>
                      <button
                        type="submit"
                        className="px-4 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-semibold shadow-sm"
                      >
                        Sign-off &amp; Generate Confirmation
                      </button>
                    </div>
                  </form>
                )}

                {/* Reject Form */}
                {actionType === 'REJECT' && (
                  <form onSubmit={handleReject} className="p-4 rounded-xl border border-red-500/30 bg-red-500/5 space-y-3">
                    <div className="flex items-center justify-between text-xs">
                      <span className="font-semibold text-red-400">
                        Reject Trade to Dead-Letter Queue
                      </span>
                      <button
                        type="button"
                        onClick={() => setActionType(null)}
                        className="text-slate-400 hover:text-slate-200"
                      >
                        Cancel
                      </button>
                    </div>
                    <textarea
                      placeholder="Enter rejection reason (e.g. Counterparty disputed strike cannot be reconciled)..."
                      value={rejectionReason}
                      onChange={(e) => setRejectionReason(e.target.value)}
                      className="w-full p-2.5 rounded-lg bg-slate-900 border border-slate-700 text-slate-200 text-xs focus:ring-1 focus:ring-red-500 focus:outline-none"
                      rows={2}
                      required
                    />
                    <div className="flex justify-end gap-2">
                      <button
                        type="button"
                        onClick={() => setActionType(null)}
                        className="px-3 py-1.5 rounded-lg bg-slate-800 text-slate-300 text-xs"
                      >
                        Cancel
                      </button>
                      <button
                        type="submit"
                        className="px-4 py-1.5 rounded-lg bg-red-600 hover:bg-red-500 text-white text-xs font-semibold"
                      >
                        Confirm Rejection
                      </button>
                    </div>
                  </form>
                )}
              </div>
            </div>
          </div>
        </div>
      ) : (
        <div className="p-12 text-center rounded-2xl border border-slate-800 bg-slate-900/60 text-slate-400">
          <CheckCircle2 size={32} className="mx-auto mb-3 text-emerald-400" />
          <h3 className="text-base font-semibold text-slate-200">No Trades in Manual Review Queue</h3>
          <p className="text-xs text-slate-400 mt-1">
            All trades have either passed automated STP or been routed to specific remediation queues.
          </p>
        </div>
      )}
    </div>
  );
};
