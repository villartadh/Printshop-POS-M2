import { resolveLayoutTier } from '../../shared/layoutPolicy.ts';
import React, { useEffect, useState } from 'react';
import { useAppState } from '../../context/AppStateContext.tsx';
import { useApp } from '../../context/AppContext.tsx';
import { ErrorState, LoadingState } from '../ui/Overlays.tsx';
import { DesktopSidebar } from './DesktopSidebar.tsx';
import { TopUtilityBar } from './TopUtilityBar.tsx';
import { MobileBottomNav } from './MobileBottomNav.tsx';
import { GlobalSearchModal } from './GlobalSearchModal.tsx';
import { DashboardModule } from '../../modules/dashboard/DashboardModule.tsx';
import { PosModule } from '../../modules/pos/PosModule.tsx';
import { PrintJobsModule } from '../../modules/jobs/PrintJobsModule.tsx';
import { ServicesModule } from '../../modules/services/ServicesModule.tsx';
import { InventoryModule } from '../../modules/inventory/InventoryModule.tsx';
import { CustomersModule } from '../../modules/customers/CustomersModule.tsx';
import { ExpensesReportsModule } from '../../modules/finance/ExpensesReportsModule.tsx';
import { ShiftsModule } from '../../modules/shifts/ShiftsModule.tsx';
import { DevicesModule } from '../../modules/devices/DevicesModule.tsx';
import { SettingsModule } from '../../modules/settings/SettingsModule.tsx';

export const AppShell: React.FC = () => {
  const { settings } = useAppState();
  const {
    activeModule,
    session,
    setActiveModule,
    platformPreview,
    fullScreenCashier,
    setIsLocked,
    loadingData,
    dataError,
    categories,
    services,
    refreshBootstrap,
    toasts,
    dismissToast,
  } = useApp();

  const [moreSheetOpen, setMoreSheetOpen] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [windowWidth, setWindowWidth] = useState(
    typeof window !== 'undefined' ? window.innerWidth : 1280,
  );

  useEffect(() => {
    const onResize = () => setWindowWidth(window.innerWidth);
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);

  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        setSearchOpen((prev) => !prev);
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, []);

  const layoutTier = resolveLayoutTier(windowWidth, platformPreview);
  const cashierFocus = fullScreenCashier && layoutTier === 'desktop' && activeModule === 'pos';

  const visibleModules = settings.moduleOrder.filter(
    (m) => !settings.hiddenModules.includes(m),
  );

  const renderModule = () => {
    if (loadingData && categories.length === 0 && services.length === 0) {
      return <LoadingState rows={6} />;
    }
    if (dataError) {
      return <ErrorState message={dataError} onRetry={() => void refreshBootstrap()} />;
    }
    switch (activeModule) {
      case 'dashboard':
        return <DashboardModule />;
      case 'pos':
        return <PosModule layoutTier={layoutTier} />;
      case 'jobs':
        return <PrintJobsModule />;
      case 'services':
        return <ServicesModule />;
      case 'inventory':
        return <InventoryModule />;
      case 'customers':
        return <CustomersModule />;
      case 'finance':
        return <ExpensesReportsModule />;
      case 'shifts':
        return <ShiftsModule />;
      case 'devices':
        return <DevicesModule />;
      case 'settings':
        return <SettingsModule />;
      default:
        return <PosModule layoutTier={layoutTier} />;
    }
  };

  const previewFrameStyle: React.CSSProperties =
    platformPreview === 'mobile'
      ? {
          maxWidth: '412px',
          margin: '0 auto',
          borderLeft: '1px solid var(--pos-border)',
          borderRight: '1px solid var(--pos-border)',
        }
      : platformPreview === 'tablet'
        ? {
            maxWidth: '900px',
            margin: '0 auto',
            borderLeft: '1px solid var(--pos-border)',
            borderRight: '1px solid var(--pos-border)',
          }
        : {};

  const showSidebar = !cashierFocus && layoutTier !== 'mobile';

  return (
    <div
      style={previewFrameStyle}
      data-layout-tier={layoutTier}
      className="min-h-dvh h-dvh flex bg-[var(--pos-bg)] text-[var(--pos-text)] overflow-hidden relative"
    >
      {showSidebar && (
        <DesktopSidebar
          layoutTier={layoutTier}
          visibleModules={visibleModules}
          activeModule={activeModule}
          onSelectModule={setActiveModule}
          onLockTerminal={() => setIsLocked(true)}
        />
      )}

      <div className="flex-1 min-w-0 flex flex-col h-full">
        <TopUtilityBar
          layoutTier={layoutTier}
          windowWidth={windowWidth}
          activeModule={activeModule}
          onOpenSearch={() => setSearchOpen(true)}
          onOpenDevices={() => setActiveModule('devices')}
        />

        <main
          className={`flex-1 min-h-0 overflow-y-auto p-3 sm:p-5 ${
            layoutTier === 'mobile' ? activeModule === 'pos' ? 'pos-mobile-content pos-mobile-checkout' : 'pos-mobile-content' : ''
          }`}
        >
          {!loadingData && !dataError && (session?.user.role === 'admin' || session?.user.role === 'manager') && !String(settings.branding.businessName ?? '').trim() && (
            <section aria-label="Business setup" className="mb-3 rounded-lg border border-amber-400 bg-[var(--pos-surface)] p-3 text-sm">
              <p className="font-semibold">Complete your business profile</p>
              <p className="mt-1 text-[var(--pos-text-muted)]">Enter your actual shop details before issuing customer documents. Configure services, payment methods and receipts using the existing modules.</p>
              <button type="button" onClick={() => setActiveModule('settings')} className="mt-2 rounded-md bg-[var(--pos-primary)] px-3 py-2 text-white">Configure business profile</button>
            </section>
          )}
          {renderModule()}
        </main>
      </div>

      {layoutTier === 'mobile' && !cashierFocus && (
        <MobileBottomNav
          visibleModules={visibleModules}
          activeModule={activeModule}
          moreSheetOpen={moreSheetOpen}
          onSetMoreSheetOpen={setMoreSheetOpen}
          onSelectModule={setActiveModule}
          onLockTerminal={() => setIsLocked(true)}
        />
      )}

      <GlobalSearchModal
        open={searchOpen}
        onClose={() => setSearchOpen(false)}
        onNavigate={setActiveModule}
      />

      {toasts.length > 0 && (
        <div className="fixed top-16 right-4 z-50 space-y-2 max-w-xs w-full pointer-events-none">
          {toasts.map((t) => (
            <div
              key={t.id}
              onClick={() => dismissToast(t.id)}
              style={{ borderRadius: 'var(--pos-radius)' }}
              className={`pointer-events-auto px-3.5 py-2.5 shadow-lg border text-xs font-medium cursor-pointer ${
                t.tone === 'error'
                  ? 'bg-[#DC2626] text-white border-[#B91C1C]'
                  : t.tone === 'success'
                    ? 'bg-[#16A34A] text-white border-[#15803D]'
                    : 'bg-[var(--pos-nav-bg)] text-white border-white/15'
              }`}
            >
              {t.text}
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
