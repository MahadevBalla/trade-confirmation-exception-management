import React from 'react';
import { useApp } from '../../context/useApp';
import { CATEGORY_METADATA } from '../../data/referenceData';
import type { ExceptionCategory } from '../../types/tcem';

export const CategoryBreakdown: React.FC = () => {
  const { exceptions, setActiveTab, setSelectedExceptionId } = useApp();

  const categories: ExceptionCategory[] = [
    'MISSING_ECONOMIC_DATA',
    'MISSING_STATIC_REFERENCE_DATA',
    'TEMPLATE_ISSUE',
    'BUSINESS_RULE_FAILURE',
    'TECHNICAL_PROCESSING_FAILURE',
    'COUNTERPARTY_MATCHING_DISPUTE',
  ];

  const counts: Record<ExceptionCategory, number> = {
    MISSING_ECONOMIC_DATA: 0,
    MISSING_STATIC_REFERENCE_DATA: 0,
    TEMPLATE_ISSUE: 0,
    BUSINESS_RULE_FAILURE: 0,
    TECHNICAL_PROCESSING_FAILURE: 0,
    COUNTERPARTY_MATCHING_DISPUTE: 0,
    LEGAL_DOCUMENTATION_GAP: 0,
    FALSE_POSITIVE_CONFIRMABLE_EVENT: 0,
  };

  exceptions.forEach((exc) => {
    if (counts[exc.category] !== undefined) {
      counts[exc.category] += 1;
    }
  });

  const maxCount = Math.max(...Object.values(counts), 1);

  return (
    <div className="rounded-2xl border border-slate-800 bg-slate-900/60 p-5 flex flex-col justify-between">
      <div className="flex items-center justify-between mb-4">
        <div>
          <h3 className="text-sm font-semibold text-white">Exceptions by Root Cause Category</h3>
          <p className="text-xs text-slate-400">Spec §12 Taxonomy &amp; Automated Classification</p>
        </div>
        <button
          onClick={() => {
            setSelectedExceptionId(null);
            setActiveTab('exceptions');
          }}
          className="text-xs text-indigo-400 hover:text-indigo-300 font-medium"
        >
          View all ({exceptions.length}) &rarr;
        </button>
      </div>

      <div className="space-y-3">
        {categories.map((cat) => {
          const count = counts[cat];
          const meta = CATEGORY_METADATA[cat];
          const pct = Math.round((count / maxCount) * 100);

          return (
            <div
              key={cat}
              onClick={() => {
                setSelectedExceptionId(null);
                setActiveTab('exceptions');
              }}
              className="group cursor-pointer p-2 rounded-xl hover:bg-slate-800/40 transition-colors"
            >
              <div className="flex items-center justify-between text-xs mb-1.5">
                <span className="font-medium text-slate-300 group-hover:text-white transition-colors">
                  {meta.label}
                </span>
                <div className="flex items-center gap-2">
                  <span className="text-[11px] text-slate-400 px-1.5 py-0.5 rounded bg-slate-800 border border-slate-700">
                    Queue: {meta.defaultOwner}
                  </span>
                  <span className="font-bold text-slate-200">{count}</span>
                </div>
              </div>

              {/* Progress Bar */}
              <div className="h-2 w-full bg-slate-800/80 rounded-full overflow-hidden">
                <div
                  style={{ width: `${Math.max(pct, count > 0 ? 10 : 0)}%` }}
                  className={`h-full rounded-full transition-all duration-300 ${
                    count === 0 ? 'bg-slate-700' : 'bg-indigo-500'
                  }`}
                />
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
