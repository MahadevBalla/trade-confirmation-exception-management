import React from 'react';
import { useApp } from '../../context/useApp';
import {
  Network,
  Database,
  Radio,
  Server,
} from 'lucide-react';

export const ServiceTopology: React.FC = () => {
  const { metrics } = useApp();

  const services = [
    { name: 'ingestion-service', port: 8081, role: 'Schema validation & Raw persistence', spec: 'FR1–FR3', status: 'Healthy' },
    { name: 'enrichment-service', port: 8082, role: 'Reference data & Counterparty lookups', spec: 'FR4', status: 'Healthy' },
    { name: 'validation-service', port: 8083, role: 'STP rules engine & Confirmation generation', spec: 'FR5–FR6', status: 'Healthy' },
    { name: 'exception-service', port: 8084, role: 'Classification & Recommendation engine', spec: 'FR7–FR9', status: 'Healthy' },
    { name: 'workitem-service', port: 8085, role: 'Mock work-item tracker, DLQ & Replay', spec: 'FR10, FR12', status: 'Healthy' },
  ];

  const topics = [
    { name: 'trade.raw', producer: 'Producer / Loader', consumer: 'ingestion-service', count: metrics.totalTrades },
    { name: 'trade.accepted', producer: 'ingestion-service', consumer: 'enrichment-service', count: metrics.totalTrades },
    { name: 'trade.enriched', producer: 'enrichment-service', consumer: 'validation-service', count: metrics.totalTrades },
    { name: 'stp.validated', producer: 'validation-service', consumer: 'fan-out path', count: metrics.totalTrades },
    { name: 'confirmation.generated', producer: 'validation-service', consumer: 'workitem-service', count: metrics.stpPassedCount },
    { name: 'exception.created', producer: 'validation-service', consumer: 'exception-service', count: metrics.exceptionsCount },
    { name: 'exception.classified', producer: 'exception-service', consumer: 'workitem-service', count: metrics.exceptionsCount },
    { name: 'workitem.linked', producer: 'workitem-service', consumer: 'workitem-service / UI', count: metrics.activeWorkItemsCount },
    { name: 'manual.review.required', producer: 'exception-service', consumer: 'workitem-service / UI', count: 2 },
    { name: 'deadletter.trade', producer: 'any service on error', consumer: 'workitem-service (replay)', count: metrics.dlqCount },
  ];

  const databases = [
    { name: 'trades', desc: 'Canonical trades & version history' },
    { name: 'confirmations', desc: 'Generated confirmation documents & fingerprints' },
    { name: 'exceptions', desc: 'Classified exception records & recommendations' },
    { name: 'workitems', desc: 'Remediation work items & defect tickets' },
    { name: 'audit_events', desc: 'Append-only immutable audit transitions' },
    { name: 'dlq_entries', desc: 'Dead-letter envelopes with payloads' },
  ];

  return (
    <div className="space-y-6">
      {/* Banner */}
      <div className="p-5 rounded-2xl border border-indigo-500/30 bg-gradient-to-r from-indigo-950/30 via-slate-900 to-slate-900 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 text-indigo-400 font-bold text-base">
            <Network size={20} />
            <h2>Event-Driven Architecture &amp; Topic Topology (Spec §5)</h2>
          </div>
          <p className="text-xs text-slate-300 mt-1 max-w-2xl leading-relaxed">
            Real-time topology view of Apache Kafka (KRaft event streaming), Apache CouchDB (JSON document store), and the 5 decoupled Java Spring Boot microservices.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <span className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 text-xs font-semibold">
            <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"></span>
            Broker: KRaft Mode (Healthy)
          </span>
        </div>
      </div>

      {/* Visual Service Pipeline Flow */}
      <div className="p-6 rounded-2xl border border-slate-800 bg-slate-900/60 space-y-6">
        <h3 className="text-sm font-bold text-white flex items-center gap-2">
          <Server size={16} className="text-indigo-400" />
          <span>Microservice Pipeline Execution Flow</span>
        </h3>

        <div className="grid grid-cols-1 md:grid-cols-5 gap-3 relative">
          {services.map((svc, i) => (
            <div
              key={svc.name}
              className="p-4 rounded-xl border border-slate-800 bg-slate-950/60 flex flex-col justify-between space-y-3 relative group hover:border-indigo-500/40 transition-colors"
            >
              <div>
                <div className="flex items-center justify-between text-xs">
                  <span className="font-mono text-indigo-400 font-bold">{svc.name}</span>
                  <span className="text-[10px] px-1.5 py-0.2 rounded bg-slate-800 text-slate-400 font-mono">
                    :{svc.port}
                  </span>
                </div>
                <div className="text-[11px] text-slate-300 font-medium mt-1">{svc.role}</div>
                <p className="text-[10px] text-slate-500 mt-1">Implements: {svc.spec}</p>
              </div>

              <div className="pt-2 border-t border-slate-800/80 flex items-center justify-between text-[11px]">
                <span className="flex items-center gap-1.5 text-emerald-400 font-medium">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-400"></span>
                  {svc.status}
                </span>
                <span className="text-slate-500 font-mono">Step {i + 1}</span>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Kafka Topics Table */}
      <div className="rounded-2xl border border-slate-800 bg-slate-900/60 p-5 space-y-4">
        <div className="flex items-center justify-between">
          <h3 className="text-sm font-bold text-white flex items-center gap-2">
            <Radio size={16} className="text-cyan-400" />
            <span>Kafka Topics Inventory (docs/kafka-topics.md)</span>
          </h3>
          <span className="text-xs text-slate-400 font-mono">10 Configured Topics</span>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead className="bg-slate-950/60 text-slate-400 uppercase tracking-wider text-[11px] border-b border-slate-800">
              <tr>
                <th className="py-2.5 px-4">Topic Name</th>
                <th className="py-2.5 px-4">Producer Service</th>
                <th className="py-2.5 px-4">Consumer Service</th>
                <th className="py-2.5 px-4 text-right">Processed Messages</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60 font-mono text-[11px]">
              {topics.map((t) => (
                <tr key={t.name} className="hover:bg-slate-800/30 transition-colors">
                  <td className="py-2.5 px-4 text-indigo-300 font-bold">{t.name}</td>
                  <td className="py-2.5 px-4 text-slate-300">{t.producer}</td>
                  <td className="py-2.5 px-4 text-slate-400">{t.consumer}</td>
                  <td className="py-2.5 px-4 text-right text-emerald-400 font-bold">{t.count}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* CouchDB Document Stores */}
      <div className="rounded-2xl border border-slate-800 bg-slate-900/60 p-5 space-y-4">
        <h3 className="text-sm font-bold text-white flex items-center gap-2">
          <Database size={16} className="text-amber-400" />
          <span>Apache CouchDB Document Store Schemas (Port :5984)</span>
        </h3>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
          {databases.map((db) => (
            <div
              key={db.name}
              className="p-3.5 rounded-xl border border-slate-800 bg-slate-950/50 flex flex-col justify-between"
            >
              <div>
                <div className="font-mono text-xs font-bold text-amber-300 flex items-center gap-2">
                  <Database size={13} />
                  <span>/{db.name}</span>
                </div>
                <p className="text-xs text-slate-400 mt-1 leading-snug">{db.desc}</p>
              </div>
              <div className="mt-3 text-[10px] text-slate-500 font-mono">
                Storage: JSON Document API (Apache 2.0)
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};
