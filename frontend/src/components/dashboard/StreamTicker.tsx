import React from 'react';
import { useApp } from '../../context/useApp';
import { ExternalLink } from 'lucide-react';

export const StreamTicker: React.FC = () => {
  const { streamEvents, setSelectedTradeId, setActiveTab } = useApp();

  const getTopicColor = (topic: string) => {
    switch (topic) {
      case 'trade.raw':
        return 'text-cyan-400 bg-cyan-500/10 border-cyan-500/30';
      case 'trade.accepted':
        return 'text-blue-400 bg-blue-500/10 border-blue-500/30';
      case 'trade.enriched':
        return 'text-indigo-400 bg-indigo-500/10 border-indigo-500/30';
      case 'stp.validated':
      case 'confirmation.generated':
        return 'text-emerald-400 bg-emerald-500/10 border-emerald-500/30';
      case 'exception.created':
      case 'exception.classified':
        return 'text-rose-400 bg-rose-500/10 border-rose-500/30';
      case 'deadletter.trade':
        return 'text-red-400 bg-red-500/10 border-red-500/30';
      case 'manual.review.required':
        return 'text-amber-400 bg-amber-500/10 border-amber-500/30';
      default:
        return 'text-slate-400 bg-slate-500/10 border-slate-500/30';
    }
  };

  return (
    <div className="rounded-2xl border border-slate-800 bg-slate-900/60 p-5">
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2">
          <div className="relative flex h-2.5 w-2.5">
            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
            <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-emerald-500"></span>
          </div>
          <div>
            <h3 className="text-sm font-semibold text-white">Live Kafka Topic Stream</h3>
            <p className="text-xs text-slate-400">Real-time event stream across microservice boundaries</p>
          </div>
        </div>

        <span className="text-[11px] font-mono text-slate-400">
          Showing last {streamEvents.length} events
        </span>
      </div>

      <div className="overflow-x-auto">
        <div className="flex gap-3 pb-2 pt-1">
          {streamEvents.map((ev) => (
            <div
              key={ev.id}
              onClick={() => {
                setSelectedTradeId(ev.tradeId);
                setActiveTab('trades');
              }}
              className="shrink-0 p-3 rounded-xl border border-slate-800 bg-slate-950/60 hover:bg-slate-800/60 transition-colors cursor-pointer w-60 flex flex-col justify-between group"
            >
              <div>
                <div className="flex items-center justify-between mb-1.5">
                  <span
                    className={`text-[10px] font-mono px-2 py-0.5 rounded-md border font-semibold ${getTopicColor(
                      ev.topic
                    )}`}
                  >
                    {ev.topic}
                  </span>
                  <span className="text-[10px] text-slate-500 font-mono">{ev.timestamp}</span>
                </div>
                <div className="flex items-center justify-between mt-2">
                  <span className="text-xs font-bold text-slate-200 group-hover:text-indigo-300 transition-colors">
                    {ev.tradeId}
                  </span>
                  <ExternalLink size={12} className="text-slate-500 group-hover:text-slate-300" />
                </div>
              </div>
              <div className="mt-2 pt-2 border-t border-slate-800/80 flex items-center justify-between text-[11px] text-slate-400">
                <span className="truncate max-w-[120px]">{ev.service}</span>
                <span className="font-semibold text-slate-300">{ev.status}</span>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};
