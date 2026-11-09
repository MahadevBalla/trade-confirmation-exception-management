import { AppProvider } from './context/AppContext';
import { useApp } from './context/useApp';
import { Navbar } from './components/layout/Navbar';
import { Sidebar } from './components/layout/Sidebar';
import { ToastContainer } from './components/layout/ToastContainer';
import { DashboardView } from './components/dashboard/DashboardView';
import { TradeList } from './components/trades/TradeList';
import { ExceptionQueue } from './components/exceptions/ExceptionQueue';
import { ManualReviewQueue } from './components/manualReview/ManualReviewQueue';
import { WorkItemList } from './components/workitems/WorkItemList';
import { DeadLetterQueue } from './components/dlq/DeadLetterQueue';
import { AuditTrailView } from './components/audit/AuditTrailView';
import { ServiceTopology } from './components/topology/ServiceTopology';

const MainContent: React.FC = () => {
  const { activeTab, dataMode, connection } = useApp();

  return (
    <main className="min-w-0 flex-1 overflow-y-auto p-3 sm:p-6 bg-slate-950/40">
      {dataMode === 'live' && connection.status !== 'connected' && (
        <div className={`mb-4 rounded-xl border px-4 py-3 text-xs ${
          connection.status === 'connecting' ? 'border-slate-700 bg-slate-800/60 text-slate-300'
            : connection.status === 'offline' ? 'border-red-500/40 bg-red-500/10 text-red-200'
              : 'border-amber-500/40 bg-amber-500/10 text-amber-200'
        }`}>
          {connection.status === 'connecting' && 'Loading live data from the service APIs…'}
          {connection.status === 'offline' && `All service APIs are unavailable. ${connection.lastUpdated ? `Last successful refresh: ${connection.lastUpdated}.` : 'No live data has loaded yet.'}`}
          {connection.status === 'partial' && `Some live data may be out of date. Unavailable sources: ${connection.unavailable.join(', ')}.`}
        </div>
      )}
      {activeTab === 'dashboard' && <DashboardView />}
      {activeTab === 'trades' && <TradeList />}
      {activeTab === 'exceptions' && <ExceptionQueue />}
      {activeTab === 'manualReview' && <ManualReviewQueue />}
      {activeTab === 'workitems' && <WorkItemList />}
      {activeTab === 'dlq' && <DeadLetterQueue />}
      {activeTab === 'audit' && <AuditTrailView />}
      {activeTab === 'topology' && <ServiceTopology />}
    </main>
  );
};

export function App() {
  return (
    <AppProvider>
      <div className="min-h-screen bg-[#0b0f19] text-slate-100 flex flex-col font-sans selection:bg-indigo-500 selection:text-white">
        <Navbar />
        <div className="flex flex-1 min-h-0 flex-col overflow-hidden lg:flex-row">
          <Sidebar />
          <MainContent />
        </div>
        <ToastContainer />
      </div>
    </AppProvider>
  );
}

export default App;
