import React, { useState, useMemo } from 'react';
import { useApp } from '../../context/useApp';
import { StatusBadge } from '../common/StatusBadge';
import { ExceptionDetailModal } from './ExceptionDetailModal';
import { Search, Filter, Eye, Clock, CheckCircle2 } from 'lucide-react';

export const ExceptionQueue: React.FC = () => {
  const { exceptions, setSelectedExceptionId, resolveException } = useApp();

  const [searchTerm, setSearchTerm] = useState('');
  const [categoryFilter, setCategoryFilter] = useState<string>('ALL');
  const [queueFilter, setQueueFilter] = useState<string>('ALL');
  const [impactFilter, setImpactFilter] = useState<string>('ALL');

  const filteredExceptions = useMemo(() => {
    return exceptions.filter((e) => {
      const matchesSearch =
        e.exceptionId.toLowerCase().includes(searchTerm.toLowerCase()) ||
        e.tradeId.toLowerCase().includes(searchTerm.toLowerCase()) ||
        e.reasonCode.toLowerCase().includes(searchTerm.toLowerCase()) ||
        e.affectedFields.toLowerCase().includes(searchTerm.toLowerCase());

      const matchesCategory = categoryFilter === 'ALL' || e.category === categoryFilter;
      const matchesQueue = queueFilter === 'ALL' || e.ownerQueue === queueFilter;
      const matchesImpact = impactFilter === 'ALL' || e.operationalImpact === impactFilter;

      return matchesSearch && matchesCategory && matchesQueue && matchesImpact;
    });
  }, [exceptions, searchTerm, categoryFilter, queueFilter, impactFilter]);

  const queues: { id: string; label: string }[] = [
    { id: 'ALL', label: 'All Queues' },
    { id: 'DATA_REMEDIATION', label: 'Data Remediation' },
    { id: 'OPERATIONS_REVIEW', label: 'Operations Review' },
    { id: 'TEMPLATE_MAINTENANCE', label: 'Template Maintenance' },
    { id: 'REFERENCE_DATA', label: 'Reference Data' },
    { id: 'TECHNOLOGY_SUPPORT', label: 'Technology Support' },
  ];

  return (
    <div className="space-y-4">
      {/* Search and Filters */}
      <div className="flex flex-col lg:flex-row items-center justify-between gap-3 p-4 rounded-2xl border border-slate-800 bg-slate-900/60">
        <div className="flex flex-wrap items-center gap-2 w-full lg:w-auto">
          <div className="relative w-full sm:w-64">
            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              placeholder="Search Exception, Trade, Field..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="w-full pl-9 pr-3 py-2 rounded-xl bg-slate-800/80 border border-slate-700 text-xs text-white placeholder-slate-400 focus:outline-none focus:ring-1 focus:ring-indigo-500"
            />
          </div>

          <select
            value={categoryFilter}
            onChange={(e) => setCategoryFilter(e.target.value)}
            className="bg-slate-800 border border-slate-700 text-slate-200 text-xs rounded-xl px-2.5 py-2 focus:outline-none cursor-pointer"
          >
            <option value="ALL">All Categories</option>
            <option value="MISSING_ECONOMIC_DATA">Missing Economic Data</option>
            <option value="MISSING_STATIC_REFERENCE_DATA">Missing Static Data</option>
            <option value="TEMPLATE_ISSUE">Template Issue</option>
            <option value="BUSINESS_RULE_FAILURE">Business Rule Failure</option>
            <option value="TECHNICAL_PROCESSING_FAILURE">Technical Failure</option>
            <option value="COUNTERPARTY_MATCHING_DISPUTE">Matching Dispute</option>
          </select>

          <select
            value={impactFilter}
            onChange={(e) => setImpactFilter(e.target.value)}
            className="bg-slate-800 border border-slate-700 text-slate-200 text-xs rounded-xl px-2.5 py-2 focus:outline-none cursor-pointer"
          >
            <option value="ALL">All Impacts</option>
            <option value="CRITICAL">Impact: CRITICAL</option>
            <option value="HIGH">Impact: HIGH</option>
            <option value="MEDIUM">Impact: MEDIUM</option>
            <option value="LOW">Impact: LOW</option>
          </select>
        </div>

        {/* Queue Filters */}
        <div className="flex items-center gap-1.5 overflow-x-auto w-full lg:w-auto pb-1 lg:pb-0">
          <Filter size={14} className="text-slate-500 mr-1 shrink-0" />
          {queues.map((q) => (
            <button
              key={q.id}
              onClick={() => setQueueFilter(q.id)}
              className={`px-3 py-1.5 rounded-lg text-xs font-medium whitespace-nowrap transition-colors ${
                queueFilter === q.id
                  ? 'bg-indigo-600 text-white font-semibold shadow-sm'
                  : 'bg-slate-800/60 text-slate-400 hover:text-slate-200 hover:bg-slate-800'
              }`}
            >
              {q.label}
            </button>
          ))}
        </div>
      </div>

      {/* Exception Table */}
      <div className="rounded-2xl border border-slate-800 bg-slate-900/60 overflow-hidden shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead className="bg-slate-950/60 text-slate-400 font-semibold border-b border-slate-800 uppercase tracking-wider text-[11px]">
              <tr>
                <th className="py-3.5 px-4">Exception ID / Trade</th>
                <th className="py-3.5 px-4">Category &amp; Sub-Theme</th>
                <th className="py-3.5 px-4">Owner Queue</th>
                <th className="py-3.5 px-4">Impact / Difficulty</th>
                <th className="py-3.5 px-4">SLA Deadline</th>
                <th className="py-3.5 px-4">Status</th>
                <th className="py-3.5 px-4 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60">
              {filteredExceptions.length > 0 ? (
                filteredExceptions.map((e) => {
                  const isResolved = e.status === 'RESOLVED' || e.status === 'CLOSED';
                  return (
                    <tr
                      key={e.exceptionId}
                      onClick={() => setSelectedExceptionId(e.exceptionId)}
                      className="hover:bg-slate-800/40 transition-colors cursor-pointer group"
                    >
                      <td className="py-3.5 px-4">
                        <div className="font-bold text-slate-200 group-hover:text-indigo-400 transition-colors">
                          {e.exceptionId}
                        </div>
                        <div className="text-[11px] text-slate-400 font-mono mt-0.5">
                          Trade: <span className="text-slate-300 font-semibold">{e.tradeId}</span>
                        </div>
                      </td>

                      <td className="py-3.5 px-4">
                        <StatusBadge status={e.category} type="category" />
                        <div className="text-[11px] text-slate-400 font-mono mt-1">
                          {e.subTheme}
                        </div>
                      </td>

                      <td className="py-3.5 px-4">
                        <span className="font-medium text-slate-300 bg-slate-800 px-2 py-0.5 rounded border border-slate-700">
                          {e.ownerQueue}
                        </span>
                      </td>

                      <td className="py-3.5 px-4">
                        <div className="flex items-center gap-1.5">
                          <StatusBadge status={e.operationalImpact} type="priority" />
                          <span className="text-[10px] px-1.5 py-0.2 rounded bg-slate-800 text-slate-400">
                            Diff: {e.resolutionDifficulty}
                          </span>
                        </div>
                      </td>

                      <td className="py-3.5 px-4">
                        <div className="flex items-center gap-1 text-slate-300 font-mono">
                          <Clock size={12} className="text-amber-400" />
                          <span>{new Date(e.slaDeadline).toLocaleTimeString()}</span>
                        </div>
                      </td>

                      <td className="py-3.5 px-4">
                        <span
                          className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold ${
                            isResolved
                              ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20'
                              : 'bg-rose-500/10 text-rose-400 border border-rose-500/20'
                          }`}
                        >
                          {e.status}
                        </span>
                      </td>

                      <td className="py-3.5 px-4 text-right">
                        <div className="flex items-center justify-end gap-1.5">
                          {!isResolved && (
                            <button
                              onClick={(ev) => {
                                ev.stopPropagation();
                                resolveException(e.exceptionId, 'Direct resolution from queue list');
                              }}
                              className="p-1.5 rounded-lg bg-emerald-600/20 hover:bg-emerald-600/30 text-emerald-400 border border-emerald-500/30 transition-colors"
                              title="Resolve exception"
                            >
                              <CheckCircle2 size={14} />
                            </button>
                          )}
                          <button
                            onClick={(ev) => {
                              ev.stopPropagation();
                              setSelectedExceptionId(e.exceptionId);
                            }}
                            className="p-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white transition-colors"
                            title="View recommendation & details"
                          >
                            <Eye size={14} />
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })
              ) : (
                <tr>
                  <td colSpan={7} className="py-12 text-center text-slate-500">
                    No exceptions match current filters.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Exception Detail & Recommendation Modal */}
      <ExceptionDetailModal />
    </div>
  );
};
