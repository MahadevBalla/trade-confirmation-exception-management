import React, { useState } from 'react';
import { useApp } from '../../context/useApp';
import { StatusBadge } from '../common/StatusBadge';
import { JsonViewer } from '../common/JsonViewer';
import { ConfirmationView } from './ConfirmationView';
import {
  X,
  Building,
  DollarSign,
  FileText,
  History,
  ShieldCheck,
} from 'lucide-react';

export const TradeDetailModal: React.FC = () => {
  const { selectedTradeId, setSelectedTradeId, trades, confirmations, auditTrail } = useApp();
  const [activeTab, setActiveTab] = useState<'overview' | 'fpml' | 'confirmation' | 'audit'>('overview');

  if (!selectedTradeId) return null;

  const trade = trades.find((t) => t.tradeId === selectedTradeId);
  if (!trade) return null;

  const confirmation = confirmations.find((c) => c.tradeId === trade.tradeId);
  const tradeAudits = auditTrail.filter((a) => a.tradeId === trade.tradeId);

  // FpML-inspired canonical envelope (Spec §10)
  const canonicalEnvelope = {
    messageHeader: {
      messageId: `MSG-${trade.tradeId}`,
      correlationId: `CORR-${trade.tradeId}`,
      eventType: 'NEW_TRADE',
      schemaVersion: 'capstone-fpml-json-1.0',
      createdTimestamp: `${trade.tradeDate}T10:30:00Z`,
    },
    trade: {
      tradeId: trade.tradeId,
      sourceSystemTradeId: trade.sourceSystemTradeId,
      tradeVersion: trade.tradeVersion,
      productType: trade.productType,
      tradeDate: trade.tradeDate,
      effectiveDate: trade.effectiveDate,
      maturityDate: trade.maturityDate,
      parties: trade.parties,
      economics: trade.fxOptionEconomics,
      nonEconomicTerms: trade.nonEconomicTerms,
      enrichmentMetadata: trade.enrichmentMetadata,
    },
  };

  return (
    <div className="fixed inset-0 z-50 bg-slate-950/80 backdrop-blur-sm flex items-center justify-center p-4 sm:p-6 overflow-y-auto">
      <div className="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-4xl max-h-[90vh] flex flex-col shadow-2xl overflow-hidden animate-in zoom-in-95">
        {/* Header */}
        <div className="p-5 border-b border-slate-800 flex items-center justify-between bg-slate-900/90">
          <div>
            <div className="flex items-center gap-3">
              <h3 className="text-lg font-bold text-white tracking-tight">{trade.tradeId}</h3>
              <StatusBadge status={trade.status} />
              <span className="text-xs px-2 py-0.5 rounded bg-slate-800 text-slate-300 font-mono">
                v{trade.tradeVersion}
              </span>
              <span className="text-xs text-slate-400 font-mono">
                Source: {trade.sourceSystemTradeId}
              </span>
            </div>
            <p className="text-xs text-slate-400 mt-1">
              Product: <span className="text-slate-200 font-semibold">{trade.productType}</span> • Trade Date: <span className="text-slate-200">{trade.tradeDate}</span> • Maturity: <span className="text-slate-200">{trade.maturityDate}</span>
            </p>
          </div>

          <button
            onClick={() => setSelectedTradeId(null)}
            className="p-2 rounded-xl text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
          >
            <X size={18} />
          </button>
        </div>

        {/* Tabs */}
        <div className="flex border-b border-slate-800 px-5 gap-4 bg-slate-950/40 text-xs font-medium">
          <button
            onClick={() => setActiveTab('overview')}
            className={`py-3 flex items-center gap-2 border-b-2 transition-colors ${
              activeTab === 'overview'
                ? 'border-indigo-500 text-indigo-400 font-semibold'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            <DollarSign size={14} />
            <span>Overview &amp; Economics</span>
          </button>
          <button
            onClick={() => setActiveTab('fpml')}
            className={`py-3 flex items-center gap-2 border-b-2 transition-colors ${
              activeTab === 'fpml'
                ? 'border-indigo-500 text-indigo-400 font-semibold'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            <FileText size={14} />
            <span>Canonical FpML JSON</span>
          </button>
          <button
            onClick={() => setActiveTab('confirmation')}
            className={`py-3 flex items-center gap-2 border-b-2 transition-colors ${
              activeTab === 'confirmation'
                ? 'border-indigo-500 text-indigo-400 font-semibold'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            <ShieldCheck size={14} />
            <span>Confirmation Note</span>
            {confirmation && <span className="w-2 h-2 rounded-full bg-emerald-400"></span>}
          </button>
          <button
            onClick={() => setActiveTab('audit')}
            className={`py-3 flex items-center gap-2 border-b-2 transition-colors ${
              activeTab === 'audit'
                ? 'border-indigo-500 text-indigo-400 font-semibold'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            <History size={14} />
            <span>Audit History ({tradeAudits.length})</span>
          </button>
        </div>

        {/* Content Body */}
        <div className="p-6 overflow-y-auto space-y-6 flex-1">
          {activeTab === 'overview' && (
            <div className="space-y-6">
              {/* Parties Block */}
              <div>
                <h4 className="text-xs font-semibold uppercase tracking-wider text-slate-400 mb-3 flex items-center gap-2">
                  <Building size={14} />
                  <span>Contract Parties (FpML PartyTradeIdentifier)</span>
                </h4>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  {trade.parties.map((p, i) => (
                    <div
                      key={i}
                      className="p-3.5 rounded-xl border border-slate-800 bg-slate-950/50 flex flex-col justify-between"
                    >
                      <div className="flex items-center justify-between text-xs">
                        <span className="font-semibold text-slate-200">{p.legalEntityName || p.partyName || p.partyId}</span>
                        <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-slate-800 text-indigo-300 border border-slate-700">
                          {p.partyRole}
                        </span>
                      </div>
                      <span className="text-xs font-mono text-slate-400 mt-2">ID: {p.partyId}</span>
                    </div>
                  ))}
                </div>
              </div>

              {/* Economics Block */}
              <div>
                <h4 className="text-xs font-semibold uppercase tracking-wider text-slate-400 mb-3 flex items-center gap-2">
                  <DollarSign size={14} />
                  <span>Product Economics (FX Option Block)</span>
                </h4>
                {trade.fxOptionEconomics ? (
                  <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs">
                    <div className="p-3 rounded-xl border border-slate-800 bg-slate-950/50">
                      <span className="text-slate-400">Notional Amount</span>
                      <div className="font-semibold text-white mt-1">
                        {trade.fxOptionEconomics.notionalCurrency}{' '}
                        {trade.fxOptionEconomics.notionalAmount?.toLocaleString() ?? 'MISSING'}
                      </div>
                    </div>
                    <div className="p-3 rounded-xl border border-slate-800 bg-slate-950/50">
                      <span className="text-slate-400">Option Style / Type</span>
                      <div className="font-semibold text-white mt-1">
                        {trade.fxOptionEconomics.buySell} {trade.fxOptionEconomics.optionType ?? 'N/A'}
                      </div>
                    </div>
                    <div className="p-3 rounded-xl border border-slate-800 bg-slate-950/50">
                      <span className="text-slate-400">Strike Rate</span>
                      <div className={`font-semibold mt-1 ${trade.fxOptionEconomics.strike ? 'text-white' : 'text-rose-400 font-bold'}`}>
                        {trade.fxOptionEconomics.strike ?? 'MISSING (Exception)'}
                      </div>
                    </div>
                    <div className="p-3 rounded-xl border border-slate-800 bg-slate-950/50">
                      <span className="text-slate-400">Underlying Pair</span>
                      <div className="font-semibold text-white mt-1 font-mono">
                        {trade.fxOptionEconomics.underlier ?? 'EUR/USD'}
                      </div>
                    </div>
                    <div className="p-3 rounded-xl border border-slate-800 bg-slate-950/50">
                      <span className="text-slate-400">Premium Amount</span>
                      <div className="font-semibold text-white mt-1">
                        {trade.fxOptionEconomics.premiumCurrency}{' '}
                        {trade.fxOptionEconomics.premiumAmount?.toLocaleString() ?? 'N/A'}
                      </div>
                    </div>
                    <div className="p-3 rounded-xl border border-slate-800 bg-slate-950/50">
                      <span className="text-slate-400">Premium Payment Date</span>
                      <div className="font-semibold text-white mt-1">
                        {trade.fxOptionEconomics.premiumPaymentDate ?? 'N/A'}
                      </div>
                    </div>
                    <div className="p-3 rounded-xl border border-slate-800 bg-slate-950/50 col-span-2">
                      <span className="text-slate-400">Internal Pricing Reference</span>
                      <div className="font-semibold text-slate-300 font-mono mt-1 text-[11px]">
                        {trade.fxOptionEconomics.internalPricingRef ?? 'None (Non-Confirmable field)'}
                      </div>
                    </div>
                  </div>
                ) : (
                  <p className="text-xs text-rose-400">Economics block missing entirely.</p>
                )}
              </div>

              {/* Enrichment Metadata & Provenance (FR4) */}
              <div>
                <h4 className="text-xs font-semibold uppercase tracking-wider text-slate-400 mb-3 flex items-center gap-2">
                  <ShieldCheck size={14} />
                  <span>Reference Data Enrichment &amp; Provenance (FR4)</span>
                </h4>
                {trade.enrichmentMetadata ? (
                  <div className="p-4 rounded-xl border border-slate-800 bg-slate-950/50 space-y-3 text-xs">
                    <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                      <div>
                        <span className="text-slate-400">Resolved Template:</span>
                        <div className="font-mono text-slate-200 mt-0.5">
                          {trade.enrichmentMetadata.resolvedTemplateId || 'No Template Matched'}
                        </div>
                      </div>
                      <div>
                        <span className="text-slate-400">Template Version:</span>
                        <div className="font-mono text-slate-200 mt-0.5">
                          {trade.enrichmentMetadata.resolvedTemplateVersion || 'N/A'}
                        </div>
                      </div>
                      <div>
                        <span className="text-slate-400">Counterparty STP:</span>
                        <div className={`mt-0.5 font-semibold ${trade.enrichmentMetadata.counterpartySTPEligible ? 'text-emerald-400' : 'text-rose-400'}`}>
                          {trade.enrichmentMetadata.counterpartySTPEligible ? 'ELIGIBLE' : 'NOT ELIGIBLE'}
                        </div>
                      </div>
                      <div>
                        <span className="text-slate-400">Product STP:</span>
                        <div className={`mt-0.5 font-semibold ${trade.enrichmentMetadata.productSTPEligible ? 'text-emerald-400' : 'text-rose-400'}`}>
                          {trade.enrichmentMetadata.productSTPEligible ? 'ELIGIBLE' : 'NOT ELIGIBLE'}
                        </div>
                      </div>
                    </div>
                    {trade.enrichmentMetadata.enrichmentNotes && (
                      <p className="text-slate-400 pt-2 border-t border-slate-800 text-[11px]">
                        <strong className="text-slate-300">Enrichment Notes:</strong> {trade.enrichmentMetadata.enrichmentNotes}
                      </p>
                    )}
                  </div>
                ) : (
                  <p className="text-xs text-slate-500">Not enriched yet.</p>
                )}
              </div>
            </div>
          )}

          {activeTab === 'fpml' && (
            <div className="space-y-2">
              <p className="text-xs text-slate-400">
                Canonical message contract matching Capstone Spec Section 10:
              </p>
              <JsonViewer data={canonicalEnvelope} title="FpML-inspired JSON Message Payload" />
            </div>
          )}

          {activeTab === 'confirmation' && (
            <ConfirmationView confirmation={confirmation} />
          )}

          {activeTab === 'audit' && (
            <div className="space-y-3">
              <h4 className="text-xs font-semibold text-slate-300 uppercase tracking-wider">
                Immutable Status Transitions (CouchDB audit_events)
              </h4>
              <div className="relative pl-6 border-l border-slate-800 space-y-4">
                {tradeAudits.length > 0 ? (
                  tradeAudits.map((aud, i) => (
                    <div key={i} className="relative text-xs">
                      <div className="absolute -left-[31px] top-0.5 w-3 h-3 rounded-full bg-indigo-500 border-2 border-slate-900"></div>
                      <div className="flex items-center gap-2">
                        <span className="font-semibold text-slate-200">{aud.serviceName}</span>
                        <span className="text-slate-500">•</span>
                        <span className="font-mono text-[10px] text-slate-400">{aud.occurredAt}</span>
                        {aud.isReplay && (
                          <span className="px-1.5 py-0.2 rounded bg-cyan-500/20 text-cyan-400 text-[10px] font-semibold">
                            REPLAY
                          </span>
                        )}
                      </div>
                      <p className="text-slate-300 mt-1">{aud.detail}</p>
                      <div className="text-[11px] text-slate-500 font-mono mt-0.5">
                        Status Transition: {aud.fromStatus || 'NULL'} &rarr; <span className="text-indigo-400 font-semibold">{aud.toStatus}</span>
                      </div>
                    </div>
                  ))
                ) : (
                  <p className="text-xs text-slate-500">No audit records recorded yet.</p>
                )}
              </div>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="p-4 border-t border-slate-800 bg-slate-900/90 flex justify-end">
          <button
            onClick={() => setSelectedTradeId(null)}
            className="px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-medium transition-colors"
          >
            Close Inspector
          </button>
        </div>
      </div>
    </div>
  );
};
