import React from 'react';
import { useApp } from '../../context/useApp';
import { Database, FlaskConical, Loader2, Unplug } from 'lucide-react';

const Banner: React.FC<{ tone: string; children: React.ReactNode }> = ({ tone, children }) => (
  <div className={`mb-4 flex flex-wrap items-center justify-between gap-3 rounded-xl border px-4 py-3 text-xs ${tone}`}>
    {children}
  </div>
);

/** States the data source plainly: simulated data, a backend that can't be reached, or an empty system. */
export const DataStatusBanner: React.FC = () => {
  const { dataMode, setDataMode, connection, trades, seedTrades } = useApp();

  if (dataMode === 'demo') {
    return (
      <Banner tone="border-amber-500/40 bg-amber-500/10 text-amber-200">
        <span className="flex items-center gap-2">
          <FlaskConical size={15} />
          <span>
            <strong>Demo data.</strong> This is an in-browser simulation. Nothing here comes from the backend.
          </span>
        </span>
        <button
          onClick={() => setDataMode('live')}
          className="rounded-lg bg-amber-500/20 px-3 py-1.5 font-semibold hover:bg-amber-500/30"
        >
          Switch to live data
        </button>
      </Banner>
    );
  }

  if (connection.status === 'connecting') {
    return (
      <Banner tone="border-slate-700 bg-slate-800/60 text-slate-300">
        <span className="flex items-center gap-2">
          <Loader2 size={15} className="animate-spin" />
          Loading data from the backend...
        </span>
      </Banner>
    );
  }

  if (connection.status === 'offline') {
    return (
      <Banner tone="border-red-500/40 bg-red-500/10 text-red-200">
        <span className="flex items-center gap-2">
          <Unplug size={15} />
          <span>
            <strong>Backend unreachable.</strong>
            {connection.lastUpdated
              ? ` Showing data from ${connection.lastUpdated}. Retrying automatically.`
              : ' No data could be loaded. Retrying automatically.'}
          </span>
        </span>
        <button
          onClick={() => setDataMode('demo')}
          className="rounded-lg bg-red-500/20 px-3 py-1.5 font-semibold hover:bg-red-500/30"
        >
          Switch to demo data
        </button>
      </Banner>
    );
  }

  if (connection.status === 'partial') {
    return (
      <Banner tone="border-amber-500/40 bg-amber-500/10 text-amber-200">
        <span className="flex items-center gap-2">
          <Unplug size={15} />
          <span>
            <strong>Some data is out of date.</strong> Could not refresh: {connection.unavailable.join(', ')}.
          </span>
        </span>
      </Banner>
    );
  }

  if (trades.length === 0) {
    return (
      <Banner tone="border-indigo-500/40 bg-indigo-500/10 text-indigo-200">
        <span className="flex items-center gap-2">
          <Database size={15} />
          <span>
            <strong>No trades yet.</strong> Load the seed data, or publish a trade with the scenario buttons.
          </span>
        </span>
        <button
          onClick={seedTrades}
          className="rounded-lg bg-indigo-600 px-3 py-1.5 font-semibold text-white hover:bg-indigo-500"
        >
          Load 500 seed trades
        </button>
      </Banner>
    );
  }

  return null;
};
