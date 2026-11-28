import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  Briefcase,
  Lock,
  Maximize2,
  Minimize2,
  Monitor,
  Moon,
  Printer,
  RefreshCw,
  Search,
  Smartphone,
  Sun,
  Tablet,
  WifiOff,
} from 'lucide-react';
import { useAppState } from '../../context/AppStateContext.tsx';
import { useApp } from '../../context/AppContext.tsx';
import { detectHardwareCapabilities } from '../../services/hardwareService.ts';
import type { InterfaceDensity, ModuleId, PlatformPreviewMode } from '../../shared/types.ts';
import { PWAInstallButton } from '../ui/PWAInstallButton.tsx';
import { MODULE_META } from './navigationMeta.ts';
import { NotificationCenter } from './NotificationCenter.tsx';
import { SyncRecovery } from './SyncRecovery.tsx';

interface TopUtilityBarProps {
  layoutTier: 'desktop' | 'tablet' | 'mobile';
  windowWidth: number;
  activeModule: ModuleId;
  onOpenSearch: () => void;
  onOpenDevices: () => void;
}

const DENSITY_OPTIONS: Array<{ id: InterfaceDensity; label: string; title: string }> = [
  { id: 'compact', label: 'Compact', title: 'Compact UI density' },
  { id: 'comfortable', label: 'Std', title: 'Comfortable standard UI density' },
  { id: 'touch', label: 'Touch', title: 'Touch-friendly large hit targets' },
];

export const TopUtilityBar: React.FC<TopUtilityBarProps> = ({
  layoutTier,
  windowWidth,
  activeModule,
  onOpenSearch,
  onOpenDevices,
}) => {
  const { settings, setThemeMode, setDensity } = useAppState();
  const {
    platformPreview,
    setPlatformPreview,
    fullScreenCashier,
    setFullScreenCashier,
    setIsLocked,
    isOnline,
    isSyncing,
    pendingSyncCount,
    syncError,
    refreshSyncQueue,
    outbox,
  } = useApp();

  const [printerMenuOpen, setPrinterMenuOpen] = useState(false);
  const [recoveryOpen,setRecoveryOpen]=useState(false);
  const conflicts=outbox.some(m=>m.status==='conflict'||m.status==='failed');
  const openRecovery=()=>{setRecoveryOpen(true);void refreshSyncQueue();};
  const printerPopoverRef = useRef<HTMLDivElement>(null);

  const caps = useMemo(() => detectHardwareCapabilities(), []);
  const printerReady = caps.browserPrintSupported;
  const directHardwareCount =
    Number(caps.webUsbSupported) + Number(caps.webSerialSupported) + Number(caps.webBluetoothSupported);

  useEffect(() => {
    if (!printerMenuOpen) return;
    const handleClickOutside = (e: MouseEvent) => {
      if (printerPopoverRef.current && !printerPopoverRef.current.contains(e.target as Node)) {
        setPrinterMenuOpen(false);
      }
    };
    const handleEsc = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setPrinterMenuOpen(false);
    };
    window.addEventListener('mousedown', handleClickOutside);
    window.addEventListener('keydown', handleEsc);
    return () => {
      window.removeEventListener('mousedown', handleClickOutside);
      window.removeEventListener('keydown', handleEsc);
    };
  }, [printerMenuOpen]);

  const isDark =
    settings.themeMode === 'dark' ||
    (settings.themeMode === 'system' &&
      typeof window !== 'undefined' &&
      window.matchMedia?.('(prefers-color-scheme: dark)').matches);

  const toggleQuickTheme = () => {
    setThemeMode(isDark ? 'light' : 'dark');
  };

  const moduleMeta = MODULE_META[activeModule];

  return (
    <header className="h-14 shrink-0 bg-[var(--pos-surface)] border-b border-[var(--pos-border)] px-3 sm:px-5 flex items-center justify-between gap-2.5 sm:gap-4">
      {/* Left Zone: Module Title & Mobile Brand */}
      <div className="flex items-center gap-2.5 min-w-0">
        {layoutTier === 'mobile' && (
          <div
            style={{
              backgroundColor: settings.tokens.primary,
              borderRadius: 'var(--pos-radius)',
            }}
            className="h-8 w-8 flex items-center justify-center text-white shrink-0 overflow-hidden"
          >
            {settings.branding.logoDataUrl ? (
              <img
                src={settings.branding.logoDataUrl}
                alt={settings.branding.appName || 'Printshop POS'}
                className="h-5 w-5 object-contain rounded"
                referrerPolicy="no-referrer"
              />
            ) : (
              <Printer className="w-4 h-4" />
            )}
          </div>
        )}
        <div className="min-w-0">
          <div className="flex items-baseline gap-2 min-w-0">
            <h1 className="text-sm sm:text-base font-bold text-[var(--pos-text)] truncate">
              {moduleMeta.label}
            </h1>
            {layoutTier === 'desktop' && (
              <span className="hidden lg:inline text-xs text-[var(--pos-text-muted)] truncate">
                · {settings.branding.businessName}
              </span>
            )}
          </div>
        </div>
      </div>

      {/* Center Zone: Global Search Trigger (Desktop/Tablet) */}
      {layoutTier !== 'mobile' && (
        <button
          type="button"
          onClick={onOpenSearch}
          style={{ borderRadius: 'var(--pos-radius)' }}
          className="flex-1 max-w-sm h-9 px-3 border border-[var(--pos-border)] bg-[var(--pos-bg)] text-xs text-[var(--pos-text-muted)] hover:text-[var(--pos-text)] hover:border-slate-300 dark:hover:border-slate-600 flex items-center justify-between gap-2 transition-colors cursor-pointer whitespace-nowrap min-w-[140px]"
          title="Global Search (Ctrl+K)"
        >
          <span className="flex items-center gap-2 min-w-0">
            <Search className="w-3.5 h-3.5 shrink-0" />
            <span className="truncate">Search services, customers, jobs…</span>
          </span>
          <kbd className="hidden sm:inline-block font-mono-num text-[10px] px-1.5 py-0.5 rounded border border-[var(--pos-border)] bg-[var(--pos-surface)] text-[var(--pos-text-muted)] shrink-0">
            ⌘K
          </kbd>
        </button>
      )}

      {/* Right Utility Zone */}
      <div className="flex items-center gap-1.5 sm:gap-2 shrink-0">
        {layoutTier === 'mobile' && (
          <button
            type="button"
            onClick={onOpenSearch}
            aria-label="Search store records"
            title="Search"
            className="h-10 w-10 rounded-lg border border-[var(--pos-border)] flex items-center justify-center text-[var(--pos-text-muted)] hover:text-[var(--pos-text)] cursor-pointer shrink-0"
          >
            <Search className="w-4 h-4" />
          </button>
        )}

        {(!isOnline||pendingSyncCount>0||syncError)&&<button type="button" aria-label="Offline Record Recovery" title="Offline Record Recovery" onClick={openRecovery} className={`inline-flex items-center gap-1.5 px-2 h-9 rounded-lg border border-[var(--pos-border)] text-xs font-medium shrink-0 ${conflicts||syncError?'text-[var(--pos-error)]':!isOnline?'text-[var(--pos-warning)]':'text-[var(--pos-primary)]'}`}>
          {isOnline?<RefreshCw className={`w-3.5 h-3.5 ${isSyncing?'animate-spin':''}`}/>:<WifiOff className="w-3.5 h-3.5"/>}
          <span className="hidden sm:inline">{conflicts?'Review':!isOnline?'Offline':'Sync'}</span><span>{pendingSyncCount||''}</span>
        </button>}
        {recoveryOpen&&<SyncRecovery onClose={()=>setRecoveryOpen(false)}/>}

        <NotificationCenter />

        {/* Printer & Hardware Readiness Indicator (Desktop/Tablet) */}
        {layoutTier !== 'mobile' && (
          <div className="relative" ref={printerPopoverRef}>
            <button
              type="button"
              onClick={() => setPrinterMenuOpen((v) => !v)}
              aria-expanded={printerMenuOpen}
              aria-label="Printer and peripheral readiness"
              title="Printer & Hardware Status"
              className="h-9 px-2.5 rounded-lg border border-[var(--pos-border)] flex items-center gap-1.5 text-xs font-medium text-[var(--pos-text-muted)] hover:text-[var(--pos-text)] hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors cursor-pointer whitespace-nowrap shrink-0"
            >
              <Printer
                className={`w-3.5 h-3.5 shrink-0 ${
                  printerReady ? 'text-[var(--pos-success)]' : 'text-[var(--pos-warning)]'
                }`}
              />
              <span className="hidden md:inline">
                {printerReady ? 'Printer Ready' : 'Check Printer'}
              </span>
            </button>

            {printerMenuOpen && (
              <div
                style={{ borderRadius: 'var(--pos-radius)' }}
                className="absolute right-0 mt-1.5 w-64 bg-[var(--pos-surface)] border border-[var(--pos-border)] shadow-lg p-3 z-40 space-y-2.5 text-xs"
              >
                <div className="flex items-center justify-between border-b border-[var(--pos-border)] pb-2">
                  <span className="font-semibold text-[var(--pos-text)]">Hardware Readiness</span>
                  <span className="text-[var(--pos-text-muted)] font-mono-num">
                    {settings.receiptTemplate.defaultPaperSize}
                  </span>
                </div>
                <div className="space-y-1.5">
                  <div className="flex items-center justify-between">
                    <span className="text-[var(--pos-text-muted)]">System Spooler Print</span>
                    <span
                      className={
                        printerReady
                          ? 'text-[var(--pos-success)] font-medium'
                          : 'text-[var(--pos-error)] font-medium'
                      }
                    >
                      {printerReady ? 'Ready' : 'Unavailable'}
                    </span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-[var(--pos-text-muted)]">Direct USB / Serial / BT</span>
                    <span className="font-mono-num text-[var(--pos-text)]">
                      {directHardwareCount}/3 APIs
                    </span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-[var(--pos-text-muted)]">Barcode Scanner</span>
                    <span className="text-[var(--pos-text)]">
                      {caps.barcodeDetectorSupported ? 'Native + Wedge' : 'Keyboard Wedge'}
                    </span>
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => {
                    setPrinterMenuOpen(false);
                    onOpenDevices();
                  }}
                  style={{ borderRadius: 'var(--pos-radius)' }}
                  className="w-full h-8 bg-[var(--pos-primary)] text-white font-medium hover:opacity-95 transition-opacity cursor-pointer"
                >
                  Open Printers & Devices
                </button>
              </div>
            )}
          </div>
        )}

        {/* Interface Density Switcher (Desktop/Web) */}
        {layoutTier === 'desktop' && (
          <div
            role="group"
            aria-label="Interface density"
            className="hidden xl:flex items-center border border-[var(--pos-border)] rounded-lg p-0.5 bg-[var(--pos-bg)] shrink-0"
          >
            {DENSITY_OPTIONS.map((opt) => {
              const active = settings.density === opt.id;
              return (
                <button
                  key={opt.id}
                  type="button"
                  onClick={() => setDensity(opt.id)}
                  title={opt.title}
                  aria-pressed={active}
                  className={`px-2 py-1 rounded-md text-[11px] font-medium transition-colors cursor-pointer whitespace-nowrap shrink-0 ${
                    active
                      ? 'bg-[var(--pos-surface)] text-[var(--pos-text)] shadow-xs'
                      : 'text-[var(--pos-text-muted)] hover:text-[var(--pos-text)]'
                  }`}
                >
                  {opt.label}
                </button>
              );
            })}
          </div>
        )}

        {/* Adaptive Platform Preview Switcher (Desktop Utility) */}
        {windowWidth >= 1536 && (
          <div className="hidden 2xl:flex items-center border border-[var(--pos-border)] rounded-lg p-0.5 bg-[var(--pos-bg)] shrink-0">
            {(
              [
                { id: 'auto', label: 'Auto', icon: Monitor },
                { id: 'windows', label: 'Win', icon: Briefcase },
                { id: 'tablet', label: 'Tab', icon: Tablet },
                { id: 'mobile', label: 'Mob', icon: Smartphone },
              ] as Array<{
                id: PlatformPreviewMode;
                label: string;
                icon: React.FC<{ className?: string }>;
              }>
            ).map((mode) => {
              const Icon = mode.icon;
              const active = platformPreview === mode.id;
              return (
                <button
                  key={mode.id}
                  type="button"
                  onClick={() => setPlatformPreview(mode.id)}
                  title={`Preview ${mode.label} layout`}
                  className={`px-2 py-1 rounded-md text-[11px] font-medium inline-flex items-center gap-1 cursor-pointer whitespace-nowrap shrink-0 ${
                    active
                      ? 'bg-[var(--pos-surface)] text-[var(--pos-text)] shadow-xs'
                      : 'text-[var(--pos-text-muted)] hover:text-[var(--pos-text)]'
                  }`}
                >
                  <Icon className="w-3 h-3 shrink-0" />
                  <span>{mode.label}</span>
                </button>
              );
            })}
          </div>
        )}

        <PWAInstallButton compact={layoutTier === 'mobile'} />

        <button
          type="button"
          onClick={toggleQuickTheme}
          title={isDark ? 'Switch to Light Mode' : 'Switch to Dark Mode'}
          aria-label={isDark ? 'Switch to Light Mode' : 'Switch to Dark Mode'}
          className="h-9 w-9 rounded-lg border border-[var(--pos-border)] flex items-center justify-center text-[var(--pos-text-muted)] hover:text-[var(--pos-text)] hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors cursor-pointer shrink-0"
        >
          {isDark ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
        </button>

        {layoutTier === 'desktop' && activeModule === 'pos' && (
          <button
            type="button"
            onClick={() => setFullScreenCashier(!fullScreenCashier)}
            title={fullScreenCashier ? 'Exit Full-Screen Cashier' : 'Full-Screen Cashier Mode'}
            aria-label={fullScreenCashier ? 'Exit Full-Screen Cashier' : 'Full-Screen Cashier Mode'}
            className="h-9 w-9 rounded-lg border border-[var(--pos-border)] flex items-center justify-center text-[var(--pos-text-muted)] hover:text-[var(--pos-text)] hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors cursor-pointer shrink-0"
          >
            {fullScreenCashier ? (
              <Minimize2 className="w-4 h-4" />
            ) : (
              <Maximize2 className="w-4 h-4" />
            )}
          </button>
        )}

        {layoutTier === 'mobile' && (
          <button
            type="button"
            onClick={() => setIsLocked(true)}
            title="Lock Terminal"
            aria-label="Lock Terminal"
            className="h-10 w-10 rounded-lg border border-[var(--pos-border)] flex items-center justify-center text-[var(--pos-text-muted)] cursor-pointer shrink-0"
          >
            <Lock className="w-4 h-4" />
          </button>
        )}
      </div>
    </header>
  );
};
