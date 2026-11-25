import React from 'react';
import { Lock, MoreHorizontal } from 'lucide-react';
import { useAppState } from '../../context/AppStateContext.tsx';
import { useApp } from '../../context/AppContext.tsx';
import type { InterfaceDensity, ModuleId, ThemeMode } from '../../shared/types.ts';
import { BottomSheet } from '../ui/Overlays.tsx';
import { MOBILE_CORE_TABS, MODULE_META } from './navigationMeta.ts';

interface MobileBottomNavProps {
  visibleModules: ModuleId[];
  activeModule: ModuleId;
  moreSheetOpen: boolean;
  onSetMoreSheetOpen: (open: boolean) => void;
  onSelectModule: (mod: ModuleId) => void;
  onLockTerminal: () => void;
}

export const MobileBottomNav: React.FC<MobileBottomNavProps> = ({
  visibleModules,
  activeModule,
  moreSheetOpen,
  onSetMoreSheetOpen,
  onSelectModule,
  onLockTerminal,
}) => {
  const { settings, setThemeMode, setDensity } = useAppState();
  const { session } = useApp();

  const isCoreTabActive = MOBILE_CORE_TABS.some((t) => t.id === activeModule);

  return (
    <>
      <nav
        aria-label="Mobile navigation"
        className="absolute bottom-0 left-0 right-0 z-30 pos-mobile-nav bg-[var(--pos-surface)]/95 backdrop-blur-md border-t border-[var(--pos-border)] flex items-center px-1"
      >
        {MOBILE_CORE_TABS.filter(tab => visibleModules.includes(tab.id)).map((tab) => {
          const Icon = tab.icon;
          const active = activeModule === tab.id;
          return (
            <button
              key={tab.id}
              type="button"
              onClick={() => onSelectModule(tab.id)}
              aria-label={tab.label}
              aria-current={active ? 'page' : undefined}
              className={`flex flex-1 min-w-0 flex-col items-center justify-center min-h-[44px] h-full gap-0.5 cursor-pointer whitespace-nowrap ${
                active ? 'text-[var(--pos-primary)]' : 'text-[var(--pos-text-muted)]'
              }`}
            >
              {tab.primary ? (
                <span
                  className={`h-10 w-10 rounded-full flex items-center justify-center transition-colors ${
                    active
                      ? 'bg-[var(--pos-primary)] text-white shadow-xs'
                      : 'bg-[var(--pos-primary)]/15 text-[var(--pos-primary)]'
                  }`}
                >
                  <Icon className="w-5 h-5" />
                </span>
              ) : (
                <Icon className="w-5 h-5 shrink-0" />
              )}
              {!tab.primary && (
                <span className="text-[10px] font-medium tracking-tight">{tab.label}</span>
              )}
            </button>
          );
        })}

        <button
          type="button"
          onClick={() => onSetMoreSheetOpen(true)}
          aria-label="More modules and preferences"
          className={`flex flex-1 min-w-0 flex-col items-center justify-center min-h-[44px] h-full gap-0.5 cursor-pointer whitespace-nowrap ${
            !isCoreTabActive || moreSheetOpen
              ? 'text-[var(--pos-primary)]'
              : 'text-[var(--pos-text-muted)]'
          }`}
        >
          <MoreHorizontal className="w-5 h-5 shrink-0" />
          <span className="text-[10px] font-medium tracking-tight">More</span>
        </button>
      </nav>

      <BottomSheet
        open={moreSheetOpen}
        onClose={() => onSetMoreSheetOpen(false)}
        title="All Modules & Display"
        subtitle={
          session
            ? `${session.user.fullName} · ${session.user.role} · ${settings.branding.businessName}`
            : settings.branding.businessName
        }
      >
        <div className="space-y-5">
          {/* Module Grid */}
          <div className="grid grid-cols-2 gap-2.5">
            {visibleModules.map((mod) => {
              const meta = MODULE_META[mod];
              const Icon = meta.icon;
              const active = activeModule === mod;
              return (
                <button
                  key={mod}
                  type="button"
                  onClick={() => {
                    onSelectModule(mod);
                    onSetMoreSheetOpen(false);
                  }}
                  style={{ borderRadius: 'var(--pos-radius)' }}
                  className={`min-h-[48px] px-3.5 py-2.5 border text-left flex items-center gap-2.5 cursor-pointer whitespace-nowrap transition-colors ${
                    active
                      ? 'border-[var(--pos-primary)] bg-[var(--pos-primary)]/10 text-[var(--pos-primary)] font-semibold'
                      : 'border-[var(--pos-border)] text-[var(--pos-text)] hover:bg-slate-50 dark:hover:bg-slate-800/60'
                  }`}
                >
                  <Icon className="w-4 h-4 shrink-0" />
                  <span className="text-xs font-medium truncate">{meta.label}</span>
                </button>
              );
            })}
          </div>

          {/* Automatic AppStateProvider Quick Appearance Preferences */}
          <div className="space-y-3 pt-2 border-t border-[var(--pos-border)]">
            <div>
              <div className="text-xs font-medium text-[var(--pos-text-muted)] mb-1.5">
                Theme Mode
              </div>
              <div className="grid grid-cols-3 gap-1 p-1 rounded-lg bg-[var(--pos-bg)] border border-[var(--pos-border)]">
                {(
                  [
                    { id: 'light', label: 'Light' },
                    { id: 'dark', label: 'Dark' },
                    { id: 'system', label: 'System' },
                  ] as Array<{ id: ThemeMode; label: string }>
                ).map((mode) => (
                  <button
                    key={mode.id}
                    type="button"
                    onClick={() => setThemeMode(mode.id)}
                    className={`min-h-[38px] text-xs font-medium rounded-md cursor-pointer transition-colors ${
                      settings.themeMode === mode.id
                        ? 'bg-[var(--pos-surface)] text-[var(--pos-text)] shadow-xs'
                        : 'text-[var(--pos-text-muted)]'
                    }`}
                  >
                    {mode.label}
                  </button>
                ))}
              </div>
            </div>

            <div>
              <div className="text-xs font-medium text-[var(--pos-text-muted)] mb-1.5">
                Interface Density
              </div>
              <div className="grid grid-cols-3 gap-1 p-1 rounded-lg bg-[var(--pos-bg)] border border-[var(--pos-border)]">
                {(
                  [
                    { id: 'compact', label: 'Compact' },
                    { id: 'comfortable', label: 'Standard' },
                    { id: 'touch', label: 'Touch' },
                  ] as Array<{ id: InterfaceDensity; label: string }>
                ).map((d) => (
                  <button
                    key={d.id}
                    type="button"
                    onClick={() => setDensity(d.id)}
                    className={`min-h-[38px] text-xs font-medium rounded-md cursor-pointer transition-colors ${
                      settings.density === d.id
                        ? 'bg-[var(--pos-surface)] text-[var(--pos-text)] shadow-xs'
                        : 'text-[var(--pos-text-muted)]'
                    }`}
                  >
                    {d.label}
                  </button>
                ))}
              </div>
            </div>
          </div>

          <button
            type="button"
            onClick={() => {
              onSetMoreSheetOpen(false);
              onLockTerminal();
            }}
            style={{ borderRadius: 'var(--pos-radius)' }}
            className="w-full min-h-[44px] border border-[var(--pos-border)] text-xs font-semibold text-[var(--pos-text)] flex items-center justify-center gap-2 hover:bg-slate-50 dark:hover:bg-slate-800 cursor-pointer"
          >
            <Lock className="w-4 h-4" />
            Lock Terminal / Switch Cashier
          </button>
        </div>
      </BottomSheet>
    </>
  );
};
