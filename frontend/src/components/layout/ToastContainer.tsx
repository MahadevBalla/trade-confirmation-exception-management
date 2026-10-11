import React from 'react';
import { useApp } from '../../context/useApp';
import { CheckCircle2, AlertCircle, Info, XCircle, X } from 'lucide-react';

export const ToastContainer: React.FC = () => {
  const { toasts, removeToast } = useApp();

  if (toasts.length === 0) return null;

  return (
    <div className="fixed bottom-5 right-5 z-50 flex flex-col gap-2 max-w-sm w-full pointer-events-none">
      {toasts.map((toast) => {
        let Icon = Info;
        let colorClasses = 'border-blue-500/30 bg-slate-900/95 text-blue-400';

        if (toast.type === 'success') {
          Icon = CheckCircle2;
          colorClasses = 'border-emerald-500/30 bg-slate-900/95 text-emerald-400';
        } else if (toast.type === 'warning') {
          Icon = AlertCircle;
          colorClasses = 'border-amber-500/30 bg-slate-900/95 text-amber-400';
        } else if (toast.type === 'error') {
          Icon = XCircle;
          colorClasses = 'border-rose-500/30 bg-slate-900/95 text-rose-400';
        }

        return (
          <div
            key={toast.id}
            className={`pointer-events-auto p-3.5 rounded-xl border shadow-xl flex items-start gap-3 backdrop-blur transition-all animate-in slide-in-from-bottom-2 ${colorClasses}`}
          >
            <Icon size={18} className="shrink-0 mt-0.5" />
            <div className="flex-1">
              <h4 className="text-xs font-semibold text-slate-200">{toast.title}</h4>
              <p className="text-xs text-slate-400 mt-0.5 leading-snug">{toast.message}</p>
            </div>
            <button
              onClick={() => removeToast(toast.id)}
              className="text-slate-500 hover:text-slate-300 p-0.5 rounded transition-colors"
            >
              <X size={14} />
            </button>
          </div>
        );
      })}
    </div>
  );
};
