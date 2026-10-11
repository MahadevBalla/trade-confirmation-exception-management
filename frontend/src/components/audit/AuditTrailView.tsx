import React, { useState, useMemo } from 'react';
import { useApp } from '../../context/useApp';
import { StatusBadge } from '../common/StatusBadge';
import { History, Search, Filter, RotateCcw, ExternalLink } from 'lucide-react';

export const AuditTrailView: React.FC = () => {
  const { auditTrail, setSelectedTradeId, setActiveTab } = useApp();

  const [searchTerm, setSearchTerm] = useState('');
  const [serviceFilter, setServiceFilter] = useState('ALL');

  const filteredAudits = useMemo(() => {
    return auditTrail.filter((a) => {
      const matchesSearch =
        a.tradeId.toLowerCase().includes(searchTerm.toLowerCase()) ||
        a.auditEventId.toLowerCase().includes(searchTerm.toLowerCase()) ||
        a.detail.toLowerCase().includes(searchTerm.toLowerCase()) ||
        a.serviceName.toLowerCase().includes(searchTerm.toLowerCase());

      const matchesService = serviceFilter === 'ALL' || a.serviceName === serviceFilter;

      return matchesSearch && matchesService;
    });
  }, [auditTrail, searchTerm, serviceFilter]);

  const services = [
    { id: 'ALL', label: 'All Microservices' },
    { id: 'ingestion-service', label: 'ingestion-service' },
    { id: 'enrichment-service', label: 'enrichment-service' },
    { id: 'validation-service', label: 'validation-service' },
    { id: 'exception-service', label: 'exception-service' },
    { id: 'workitem-service', label: 'workitem-service' },
  ];

  return (
    <div className="space-y-4">
      {/* Banner */}
      <div className="p-5 rounded-2xl border border-indigo-500/30 bg-gradient-to-r from-indigo-950/30 via-slate-900 to-slate-900 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 text-indigo-400 font-bold text-base">
            <History size={20} />
            <h2>Immutable Audit Trail Explorer (FR3, NFR Auditability)</h2>
          </div>
          <p className="text-xs text-slate-300 mt-1 max-w-2xl leading-relaxed">
            Every state transition, automated pipeline step, and manual supervisor action is recorded as an append-only immutable event stored in the CouchDB <code className="text-indigo-300">audit_events</code> database.
          </p>
        </div>

        <div className="px-3 py-1.5 rounded-xl bg-indigo-500/20 text-indigo-300 border border-indigo-500/30 text-xs font-semibold shrink-0">
          Recorded Events: {auditTrail.length}
        </div>
      </div>

      {/* Filter Bar */}
      <div className="flex flex-col sm:flex-row items-center justify-between gap-3 p-4 rounded-2xl border border-slate-800 bg-slate-900/60">
        <div className="relative w-full sm:w-80">
          <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            type="text"
            placeholder="Search Event ID, Trade ID, Detail..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full pl-9 pr-3 py-2 rounded-xl bg-slate-800/80 border border-slate-700 text-xs text-white placeholder-slate-400 focus:outline-none focus:ring-1 focus:ring-indigo-500"
          />
        </div>

        <div className="flex items-center gap-2 w-full sm:w-auto overflow-x-auto pb-1 sm:pb-0">
          <Filter size={14} className="text-slate-500 shrink-0" />
          {services.map((s) => (
            <button
              key={s.id}
              onClick={() => setServiceFilter(s.id)}
              className={`px-3 py-1.5 rounded-lg text-xs font-medium whitespace-nowrap transition-colors ${
                serviceFilter === s.id
                  ? 'bg-indigo-600 text-white font-semibold'
                  : 'bg-slate-800/60 text-slate-400 hover:text-slate-200'
              }`}
            >
              {s.label}
            </button>
          ))}
        </div>
      </div>

      {/* Audit Timeline Table */}
      <div className="rounded-2xl border border-slate-800 bg-slate-900/60 overflow-hidden shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead className="bg-slate-950/60 text-slate-400 font-semibold border-b border-slate-800 uppercase tracking-wider text-[11px]">
              <tr>
                <th className="py-3.5 px-4">Event ID / Time</th>
                <th className="py-3.5 px-4">Trade Reference</th>
                <th className="py-3.5 px-4">Service</th>
                <th className="py-3.5 px-4">State Transition</th>
                <th className="py-3.5 px-4">Event Detail</th>
                <th className="py-3.5 px-4">Actor</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60">
              {filteredAudits.length > 0 ? (
                filteredAudits.map((a) => (
                  <tr key={a.auditEventId} className="hover:bg-slate-800/40 transition-colors">
                    <td className="py-3.5 px-4">
                      <div className="font-bold text-slate-200 font-mono">{a.auditEventId}</div>
                      <div className="text-[11px] text-slate-500 font-mono mt-0.5">{a.occurredAt}</div>
                      {a.isReplay && (
                        <span className="mt-1 inline-flex items-center gap-1 text-[10px] px-1.5 py-0.2 rounded bg-cyan-500/20 text-cyan-400 font-semibold border border-cyan-500/30">
                          <RotateCcw size={10} />
                          <span>REPLAY</span>
                        </span>
                      )}
                    </td>

                    <td className="py-3.5 px-4">
                      <button
                        onClick={() => {
                          setSelectedTradeId(a.tradeId);
                          setActiveTab('trades');
                        }}
                        className="font-mono text-indigo-400 hover:text-indigo-300 font-semibold flex items-center gap-1"
                      >
                        <span>{a.tradeId}</span>
                        <ExternalLink size={12} />
                      </button>
                      <div className="text-[10px] text-slate-500 font-mono mt-0.5">
                        Msg: {a.messageId}
                      </div>
                    </td>

                    <td className="py-3.5 px-4">
                      <span className="font-mono text-[11px] px-2 py-0.5 rounded bg-slate-800 border border-slate-700 text-slate-300">
                        {a.serviceName}
                      </span>
                    </td>

                    <td className="py-3.5 px-4">
                      <div className="flex items-center gap-1.5 text-slate-400 font-mono text-[11px]">
                        <span>{a.fromStatus || 'INIT'}</span>
                        <span className="text-slate-600">&rarr;</span>
                        <StatusBadge status={a.toStatus} />
                      </div>
                    </td>

                    <td className="py-3.5 px-4 max-w-sm">
                      <p className="text-slate-200 leading-relaxed text-xs">{a.detail}</p>
                    </td>

                    <td className="py-3.5 px-4">
                      <span className="text-slate-400 text-[11px]">
                        {a.actorUserId || 'system (automated)'}
                      </span>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-slate-500">
                    No audit records match the current filters.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
