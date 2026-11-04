import React, { useState, useMemo } from 'react';
import { useApp } from '../../context/useApp';
import { StatusBadge } from '../common/StatusBadge';
import { TradeDetailModal } from './TradeDetailModal';
import { Search, Filter, Eye } from 'lucide-react';

export const TradeList: React.FC = () => {
  const { trades, setSelectedTradeId } = useApp();
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');

  const filteredTrades = useMemo(() => {
    return trades.filter((t) => {
      const matchesSearch =
        t.tradeId.toLowerCase().includes(searchTerm.toLowerCase()) ||
        t.sourceSystemTradeId.toLowerCase().includes(searchTerm.toLowerCase()) ||
        t.parties.some(
          (p) =>
            p.partyId.toLowerCase().includes(searchTerm.toLowerCase()) ||
            (p.legalEntityName && p.legalEntityName.toLowerCase().includes(searchTerm.toLowerCase())) ||
            (p.partyName && p.partyName.toLowerCase().includes(searchTerm.toLowerCase()))
        ) ||
        (t.fxOptionEconomics?.notionalCurrency &&
          t.fxOptionEconomics.notionalCurrency.toLowerCase().includes(searchTerm.toLowerCase()));

      const matchesStatus = statusFilter === 'ALL' || t.status === statusFilter;

      return matchesSearch && matchesStatus;
    });
  }, [trades, searchTerm, statusFilter]);

  const statuses = [
    { id: 'ALL', label: 'All Trades' },
    { id: 'CONFIRMED', label: 'Confirmed (STP)' },
    { id: 'EXCEPTION_CAPTURED', label: 'Exceptions' },
    { id: 'MANUAL_REVIEW', label: 'Manual Review' },
    { id: 'WORKITEM_LINKED', label: 'Work Item Linked' },
    { id: 'DEAD_LETTER', label: 'Dead Letter (DLQ)' },
    { id: 'SCHEMA_INVALID', label: 'Schema Invalid' },
    { id: 'CANCELLED', label: 'Cancelled' },
  ];

  return (
    <div className="space-y-4">
      {/* Search and Filters Bar */}
      <div className="flex flex-col sm:flex-row items-center justify-between gap-3 p-4 rounded-2xl border border-slate-800 bg-slate-900/60">
        <div className="relative w-full sm:w-80">
          <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            type="text"
            placeholder="Search by Trade ID, Party, or Currency..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full pl-9 pr-3 py-2 rounded-xl bg-slate-800/80 border border-slate-700 text-xs text-white placeholder-slate-400 focus:outline-none focus:ring-1 focus:ring-indigo-500"
          />
        </div>

        {/* Status Filters */}
        <div className="flex items-center gap-1.5 overflow-x-auto w-full sm:w-auto pb-1 sm:pb-0">
          <Filter size={14} className="text-slate-500 mr-1 shrink-0" />
          {statuses.map((st) => (
            <button
              key={st.id}
              onClick={() => setStatusFilter(st.id)}
              className={`px-3 py-1.5 rounded-lg text-xs font-medium whitespace-nowrap transition-colors ${
                statusFilter === st.id
                  ? 'bg-indigo-600 text-white font-semibold shadow-sm'
                  : 'bg-slate-800/60 text-slate-400 hover:text-slate-200 hover:bg-slate-800'
              }`}
            >
              {st.label}
            </button>
          ))}
        </div>
      </div>

      {/* Trades Table */}
      <div className="rounded-2xl border border-slate-800 bg-slate-900/60 overflow-hidden shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead className="bg-slate-950/60 text-slate-400 font-semibold border-b border-slate-800 uppercase tracking-wider text-[11px]">
              <tr>
                <th className="py-3.5 px-4">Trade ID / Source</th>
                <th className="py-3.5 px-4">Product</th>
                <th className="py-3.5 px-4">Counterparty</th>
                <th className="py-3.5 px-4">Economics</th>
                <th className="py-3.5 px-4">Dates</th>
                <th className="py-3.5 px-4">Processing Status</th>
                <th className="py-3.5 px-4 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60">
              {filteredTrades.length > 0 ? (
                filteredTrades.map((t) => {
                  const cp = t.parties.find((p) => p.partyRole === 'COUNTERPARTY');
                  return (
                    <tr
                      key={t.tradeId}
                      onClick={() => setSelectedTradeId(t.tradeId)}
                      className="hover:bg-slate-800/40 transition-colors cursor-pointer group"
                    >
                      <td className="py-3.5 px-4">
                        <div className="font-bold text-slate-200 group-hover:text-indigo-400 transition-colors">
                          {t.tradeId}
                          <span className="ml-1.5 text-[10px] px-1.5 py-0.2 rounded bg-slate-800 text-slate-400 font-mono">
                            v{t.tradeVersion}
                          </span>
                        </div>
                        <div className="text-[11px] text-slate-500 font-mono mt-0.5">
                          {t.sourceSystemTradeId}
                        </div>
                      </td>

                      <td className="py-3.5 px-4">
                        <span className="font-medium text-slate-300">{t.productType}</span>
                        <div className="text-[11px] text-slate-500">
                          {t.fxOptionEconomics?.optionType ? `${t.fxOptionEconomics.buySell} ${t.fxOptionEconomics.optionType}` : 'Standard'}
                        </div>
                      </td>

                      <td className="py-3.5 px-4">
                        <div className="font-medium text-slate-200">{cp?.legalEntityName || cp?.partyName || cp?.partyId}</div>
                        <div className="text-[11px] text-slate-500 font-mono">{cp?.partyId}</div>
                      </td>

                      <td className="py-3.5 px-4">
                        {t.fxOptionEconomics ? (
                          <div>
                            <div className="font-semibold text-slate-200">
                              {t.fxOptionEconomics.notionalCurrency}{' '}
                              {t.fxOptionEconomics.notionalAmount?.toLocaleString() ?? 'N/A'}
                            </div>
                            <div className="text-[11px] text-slate-400">
                              {t.fxOptionEconomics.underlier} @ Strike: <span className={t.fxOptionEconomics.strike ? 'font-mono' : 'text-rose-400 font-bold'}>{t.fxOptionEconomics.strike ?? 'MISSING'}</span>
                            </div>
                          </div>
                        ) : (
                          <span className="text-slate-500 italic">No Economics</span>
                        )}
                      </td>

                      <td className="py-3.5 px-4">
                        <div className="text-slate-300">Trade: {t.tradeDate}</div>
                        <div className="text-[11px] text-slate-500">Maturity: {t.maturityDate}</div>
                      </td>

                      <td className="py-3.5 px-4">
                        <StatusBadge status={t.status} />
                      </td>

                      <td className="py-3.5 px-4 text-right">
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            setSelectedTradeId(t.tradeId);
                          }}
                          className="p-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white transition-colors"
                          title="Inspect trade details"
                        >
                          <Eye size={15} />
                        </button>
                      </td>
                    </tr>
                  );
                })
              ) : (
                <tr>
                  <td colSpan={7} className="py-12 text-center text-slate-500">
                    No trades match current filters.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Trade Detail Modal */}
      <TradeDetailModal />
    </div>
  );
};
