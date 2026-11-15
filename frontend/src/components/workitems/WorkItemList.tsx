import React, { useState, useMemo } from 'react';
import { useApp } from '../../context/useApp';
import { StatusBadge } from '../common/StatusBadge';
import {
  ListTodo,
  Plus,
  Search,
  ExternalLink,
  X,
} from 'lucide-react';
import type { WorkItemType, OwnerQueue, Priority } from '../../types/tcem';

export const WorkItemList: React.FC = () => {
  const { workItems, updateWorkItemStatus, createWorkItem, setSelectedTradeId, setActiveTab } = useApp();

  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [queueFilter, setQueueFilter] = useState<string>('ALL');
  const [showCreateModal, setShowCreateModal] = useState(false);

  // New Work Item Form state
  const [newTitle, setNewTitle] = useState('');
  const [newDescription, setNewDescription] = useState('');
  const [newTradeId, setNewTradeId] = useState('');
  const [newType, setNewType] = useState<WorkItemType>('DATA_FIX');
  const [newQueue, setNewQueue] = useState<OwnerQueue>('DATA_REMEDIATION');
  const [newPriority, setNewPriority] = useState<Priority>('HIGH');

  const filteredWorkItems = useMemo(() => {
    return workItems.filter((w) => {
      const matchesSearch =
        w.workItemId.toLowerCase().includes(searchTerm.toLowerCase()) ||
        w.title.toLowerCase().includes(searchTerm.toLowerCase()) ||
        w.tradeId.toLowerCase().includes(searchTerm.toLowerCase()) ||
        w.description.toLowerCase().includes(searchTerm.toLowerCase());

      const matchesStatus = statusFilter === 'ALL' || w.status === statusFilter;
      const matchesQueue = queueFilter === 'ALL' || w.ownerQueue === queueFilter;

      return matchesSearch && matchesStatus && matchesQueue;
    });
  }, [workItems, searchTerm, statusFilter, queueFilter]);

  const handleCreateSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    createWorkItem({
      title: newTitle,
      description: newDescription,
      tradeId: newTradeId || 'TRD-GENERAL',
      type: newType,
      ownerQueue: newQueue,
      priority: newPriority,
    });
    setShowCreateModal(false);
    setNewTitle('');
    setNewDescription('');
    setNewTradeId('');
  };

  return (
    <div className="space-y-4">
      {/* Search and Action Bar */}
      <div className="flex flex-col sm:flex-row items-center justify-between gap-3 p-4 rounded-2xl border border-slate-800 bg-slate-900/60">
        <div className="relative w-full sm:w-80">
          <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            type="text"
            placeholder="Search Work Item ID, Trade, Title..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full pl-9 pr-3 py-2 rounded-xl bg-slate-800/80 border border-slate-700 text-xs text-white placeholder-slate-400 focus:outline-none focus:ring-1 focus:ring-indigo-500"
          />
        </div>

        <div className="flex flex-wrap items-center gap-2 w-full sm:w-auto justify-end">
          <select
            value={queueFilter}
            onChange={(e) => setQueueFilter(e.target.value)}
            className="bg-slate-800 border border-slate-700 text-slate-200 text-xs rounded-xl px-3 py-2 focus:outline-none cursor-pointer"
          >
            <option value="ALL">All Queues</option>
            <option value="DATA_REMEDIATION">DATA_REMEDIATION</option>
            <option value="OPERATIONS_REVIEW">OPERATIONS_REVIEW</option>
            <option value="TEMPLATE_MAINTENANCE">TEMPLATE_MAINTENANCE</option>
            <option value="REFERENCE_DATA">REFERENCE_DATA</option>
            <option value="TECHNOLOGY_SUPPORT">TECHNOLOGY_SUPPORT</option>
            <option value="SPECIALIST_REVIEW">SPECIALIST_REVIEW</option>
            <option value="RULES_OWNER">RULES_OWNER</option>
          </select>

          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="bg-slate-800 border border-slate-700 text-slate-200 text-xs rounded-xl px-3 py-2 focus:outline-none cursor-pointer"
          >
            <option value="ALL">All Statuses</option>
            <option value="OPEN">Status: OPEN</option>
            <option value="IN_PROGRESS">Status: IN PROGRESS</option>
            <option value="DONE">Status: DONE</option>
          </select>

          <button
            onClick={() => setShowCreateModal(true)}
            className="flex items-center gap-1.5 px-3 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold shadow-md shadow-indigo-600/20 transition-colors shrink-0"
          >
            <Plus size={15} />
            <span>New Work Item</span>
          </button>
        </div>
      </div>

      {/* Work Items Table */}
      <div className="rounded-2xl border border-slate-800 bg-slate-900/60 overflow-hidden shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead className="bg-slate-950/60 text-slate-400 font-semibold border-b border-slate-800 uppercase tracking-wider text-[11px]">
              <tr>
                <th className="py-3.5 px-4">Ticket ID / Type</th>
                <th className="py-3.5 px-4">Title &amp; Context</th>
                <th className="py-3.5 px-4">Trade Link</th>
                <th className="py-3.5 px-4">Owner Queue</th>
                <th className="py-3.5 px-4">Priority</th>
                <th className="py-3.5 px-4">Status</th>
                <th className="py-3.5 px-4 text-right">Quick Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60">
              {filteredWorkItems.length > 0 ? (
                filteredWorkItems.map((w) => (
                  <tr key={w.workItemId} className="hover:bg-slate-800/40 transition-colors">
                    <td className="py-3.5 px-4">
                      <div className="font-bold text-slate-200">{w.workItemId}</div>
                      <span className="text-[10px] font-mono px-1.5 py-0.2 rounded bg-slate-800 text-indigo-300 mt-1 inline-block border border-slate-700">
                        {w.type}
                      </span>
                    </td>

                    <td className="py-3.5 px-4 max-w-md">
                      <div className="font-semibold text-slate-200">{w.title}</div>
                      <p className="text-[11px] text-slate-400 mt-0.5 line-clamp-2 leading-relaxed">
                        {w.description}
                      </p>
                    </td>

                    <td className="py-3.5 px-4">
                      <button
                        onClick={() => {
                          setSelectedTradeId(w.tradeId);
                          setActiveTab('trades');
                        }}
                        className="font-mono text-indigo-400 hover:text-indigo-300 font-semibold flex items-center gap-1"
                      >
                        <span>{w.tradeId}</span>
                        <ExternalLink size={12} />
                      </button>
                      {w.linkedExceptionId && (
                        <div className="text-[10px] text-slate-500 font-mono mt-0.5">
                          Exc: {w.linkedExceptionId}
                        </div>
                      )}
                    </td>

                    <td className="py-3.5 px-4">
                      <span className="font-medium text-slate-300 bg-slate-800 px-2 py-0.5 rounded border border-slate-700">
                        {w.ownerQueue}
                      </span>
                    </td>

                    <td className="py-3.5 px-4">
                      <StatusBadge status={w.priority} type="priority" />
                    </td>

                    <td className="py-3.5 px-4">
                      <span
                        className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold ${
                          w.status === 'DONE'
                            ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20'
                            : w.status === 'IN_PROGRESS'
                            ? 'bg-blue-500/10 text-blue-400 border border-blue-500/20'
                            : 'bg-amber-500/10 text-amber-400 border border-amber-500/20'
                        }`}
                      >
                        {w.status}
                      </span>
                    </td>

                    <td className="py-3.5 px-4 text-right">
                      {w.status !== 'DONE' ? (
                        <button
                          onClick={() => updateWorkItemStatus(w.workItemId, 'DONE')}
                          className="px-2.5 py-1 rounded-lg bg-emerald-600/20 hover:bg-emerald-600/30 text-emerald-400 border border-emerald-500/30 text-[11px] font-semibold transition-colors"
                        >
                          Mark Done
                        </button>
                      ) : (
                        <button
                          onClick={() => updateWorkItemStatus(w.workItemId, 'IN_PROGRESS')}
                          className="px-2.5 py-1 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 text-[11px] transition-colors"
                        >
                          Reopen
                        </button>
                      )}
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={7} className="py-12 text-center text-slate-500">
                    No work items found matching current filters.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Create Work Item Modal */}
      {showCreateModal && (
        <div className="fixed inset-0 z-50 bg-slate-950/80 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-lg p-6 shadow-2xl space-y-4 animate-in zoom-in-95">
            <div className="flex items-center justify-between border-b border-slate-800 pb-3">
              <h3 className="text-base font-bold text-white flex items-center gap-2">
                <ListTodo size={18} className="text-indigo-400" />
                <span>Create Mock Work Item (FR10)</span>
              </h3>
              <button
                onClick={() => setShowCreateModal(false)}
                className="text-slate-400 hover:text-white"
              >
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleCreateSubmit} className="space-y-4 text-xs">
              <div>
                <label className="block text-slate-300 font-semibold mb-1">Title</label>
                <input
                  type="text"
                  placeholder="e.g. Remediation required for missing strike price"
                  value={newTitle}
                  onChange={(e) => setNewTitle(e.target.value)}
                  className="w-full p-2.5 rounded-xl bg-slate-800 border border-slate-700 text-white focus:ring-1 focus:ring-indigo-500 focus:outline-none"
                  required
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-slate-300 font-semibold mb-1">Associated Trade ID</label>
                  <input
                    type="text"
                    placeholder="e.g. TRD-10002"
                    value={newTradeId}
                    onChange={(e) => setNewTradeId(e.target.value)}
                    className="w-full p-2.5 rounded-xl bg-slate-800 border border-slate-700 text-white focus:ring-1 focus:ring-indigo-500 focus:outline-none"
                    required
                  />
                </div>

                <div>
                  <label className="block text-slate-300 font-semibold mb-1">Work Item Type</label>
                  <select
                    value={newType}
                    onChange={(e) => setNewType(e.target.value as WorkItemType)}
                    className="w-full p-2.5 rounded-xl bg-slate-800 border border-slate-700 text-white focus:ring-1 focus:ring-indigo-500 focus:outline-none cursor-pointer"
                  >
                    <option value="DATA_FIX">DATA_FIX</option>
                    <option value="TEMPLATE_FIX">TEMPLATE_FIX</option>
                    <option value="RULE_UPDATE">RULE_UPDATE</option>
                    <option value="MANUAL_REVIEW">MANUAL_REVIEW</option>
                    <option value="ENHANCEMENT">ENHANCEMENT</option>
                  </select>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-slate-300 font-semibold mb-1">Assigned Queue</label>
                  <select
                    value={newQueue}
                    onChange={(e) => setNewQueue(e.target.value as OwnerQueue)}
                    className="w-full p-2.5 rounded-xl bg-slate-800 border border-slate-700 text-white focus:ring-1 focus:ring-indigo-500 focus:outline-none cursor-pointer"
                  >
                    <option value="DATA_REMEDIATION">DATA_REMEDIATION</option>
                    <option value="OPERATIONS_REVIEW">OPERATIONS_REVIEW</option>
                    <option value="TEMPLATE_MAINTENANCE">TEMPLATE_MAINTENANCE</option>
                    <option value="REFERENCE_DATA">REFERENCE_DATA</option>
                    <option value="TECHNOLOGY_SUPPORT">TECHNOLOGY_SUPPORT</option>
                    <option value="SPECIALIST_REVIEW">SPECIALIST_REVIEW</option>
                    <option value="RULES_OWNER">RULES_OWNER</option>
                  </select>
                </div>

                <div>
                  <label className="block text-slate-300 font-semibold mb-1">Priority</label>
                  <select
                    value={newPriority}
                    onChange={(e) => setNewPriority(e.target.value as Priority)}
                    className="w-full p-2.5 rounded-xl bg-slate-800 border border-slate-700 text-white focus:ring-1 focus:ring-indigo-500 focus:outline-none cursor-pointer"
                  >
                    <option value="CRITICAL">CRITICAL</option>
                    <option value="HIGH">HIGH</option>
                    <option value="MEDIUM">MEDIUM</option>
                    <option value="LOW">LOW</option>
                  </select>
                </div>
              </div>

              <div>
                <label className="block text-slate-300 font-semibold mb-1">Description &amp; Suggested Fix</label>
                <textarea
                  placeholder="Detail context, failing rule, and expected resolution..."
                  value={newDescription}
                  onChange={(e) => setNewDescription(e.target.value)}
                  className="w-full p-2.5 rounded-xl bg-slate-800 border border-slate-700 text-white focus:ring-1 focus:ring-indigo-500 focus:outline-none"
                  rows={3}
                  required
                />
              </div>

              <div className="flex justify-end gap-2 pt-2 border-t border-slate-800">
                <button
                  type="button"
                  onClick={() => setShowCreateModal(false)}
                  className="px-4 py-2 rounded-xl bg-slate-800 text-slate-300"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-semibold"
                >
                  Create Work Item
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
