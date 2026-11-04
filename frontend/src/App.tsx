import { AppProvider } from './context/AppProvider';
import { useApp } from './context/useApp';
import { Navbar } from './components/layout/Navbar';
import { Sidebar } from './components/layout/Sidebar';
import { ToastContainer } from './components/layout/ToastContainer';
import { DataStatusBanner } from './components/layout/DataStatusBanner';
import { DashboardView } from './components/dashboard/DashboardView';
import { TradeList } from './components/trades/TradeList';
import { ExceptionQueue } from './components/exceptions/ExceptionQueue';
import { ManualReviewQueue } from './components/manualReview/ManualReviewQueue';
import { WorkItemList } from './components/workitems/WorkItemList';
import { DeadLetterQueue } from './components/dlq/DeadLetterQueue';
import { AuditTrailView } from './components/audit/AuditTrailView';
import { ServiceTopology } from './components/topology/ServiceTopology';

const MainContent: React.FC = () => {
  const { activeTab } = useApp();

  return (
    <main className="min-w-0 flex-1 overflow-y-auto p-3 sm:p-6 bg-slate-950/40">
      <DataStatusBanner />
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
