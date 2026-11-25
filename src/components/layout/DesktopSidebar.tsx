import React from 'react';
import { Lock, PanelLeftClose, PanelLeftOpen, Printer } from 'lucide-react';
import { useAppState } from '../../context/AppStateContext.tsx';
import { useApp } from '../../context/AppContext.tsx';
import type { ModuleId } from '../../shared/types.ts';
import { MODULE_META } from './navigationMeta.ts';

interface DesktopSidebarProps {
  layoutTier: 'desktop' | 'tablet' | 'mobile';
  visibleModules: ModuleId[];
  activeModule: ModuleId;
  onSelectModule: (mod: ModuleId) => void;
  onLockTerminal: () => void;
}

export const DesktopSidebar: React.FC<DesktopSidebarProps> = ({
  layoutTier,
  visibleModules,
  activeModule,
  onSelectModule,
  onLockTerminal,
}) => {
  const { settings, setSidebarCollapsed } = useAppState();
  const { session } = useApp();

  const isCollapsedPref = Boolean(settings.shellPreferences?.sidebarCollapsed);
  const railOnly = layoutTier === 'tablet' || isCollapsedPref;
  const isCompactDensity = settings.density === 'compact';
  const isTouchDensity = settings.density === 'touch';

  const expandedWidth = Math.max(200, Math.min(280, settings.tokens.sidebarWidthPx || 232));

  return (
    <aside
      aria-label="Primary navigation"
      style={{
        backgroundColor: settings.tokens.navBackground,
        width: railOnly ? '68px' : `${expandedWidth}px`,
      }}
      className="shrink-0 text-white flex flex-col justify-between border-r border-white/10 transition-[width] duration-150 select-none"
    >
      <div className="flex flex-col min-h-0 flex-1">
        {/* Brand Zone */}
        <div className="h-14 px-3.5 flex items-center justify-between gap-2 border-b border-white/10 shrink-0">
          <div className={`flex items-center gap-2.5 min-w-0 ${railOnly ? 'mx-auto' : ''}`}>
            <div
              style={{
                backgroundColor: settings.tokens.primary,
                borderRadius: 'var(--pos-radius)',
              }}
              className="h-9 w-9 flex items-center justify-center shrink-0 overflow-hidden"
            >
              {settings.branding.logoDataUrl ? (
                <img
                  src={settings.branding.logoDataUrl}
                  alt={settings.branding.appName || 'Printshop POS'}
                  className="h-6 w-6 object-contain rounded"
                  referrerPolicy="no-referrer"
                />
              ) : (
                <Printer className="w-5 h-5 text-white" />
              )}
            </div>
            {!railOnly && (
              <div className="min-w-0">
                <div className="text-sm font-bold truncate leading-tight">
                  {settings.branding.appName}
                </div>
                <div className="text-[11px] text-teal-200/80 truncate leading-tight mt-0.5">
                  {settings.branding.businessName}
                </div>
              </div>
            )}
          </div>

          {layoutTier === 'desktop' && !railOnly && (
            <button
              type="button"
              onClick={() => setSidebarCollapsed(true)}
              className="p-1.5 rounded-md text-slate-300 hover:bg-white/10 hover:text-white transition-colors cursor-pointer shrink-0 focus-visible:outline-2 focus-visible:outline-white"
              title="Collapse sidebar"
              aria-label="Collapse sidebar"
            >
              <PanelLeftClose className="w-4 h-4" />
            </button>
          )}
        </div>

        {/* Desktop Collapsed Expand Button */}
        {layoutTier === 'desktop' && railOnly && (
          <div className="flex justify-center py-2 border-b border-white/10 shrink-0">
            <button
              type="button"
              onClick={() => setSidebarCollapsed(false)}
              className="h-9 w-9 rounded-md flex items-center justify-center text-slate-300 hover:bg-white/10 hover:text-white transition-colors cursor-pointer focus-visible:outline-2 focus-visible:outline-white"
              title="Expand sidebar"
              aria-label="Expand sidebar"
            >
              <PanelLeftOpen className="w-4 h-4" />
            </button>
          </div>
        )}

        {/* Navigation Links */}
        <nav className="p-2 space-y-1 overflow-y-auto flex-1">
          {visibleModules.map((mod) => {
            const meta = MODULE_META[mod];
            const Icon = meta.icon;
            const active = activeModule === mod;
            const itemHeight = isTouchDensity
              ? 'min-h-[48px]'
              : isCompactDensity
                ? 'min-h-[36px]'
                : 'min-h-[42px]';

            return (
              <button
                key={mod}
                type="button"
                onClick={() => onSelectModule(mod)}
                title={meta.label}
                aria-label={meta.label}
                aria-current={active ? 'page' : undefined}
                style={{
                  borderRadius: 'var(--pos-radius)',
                  ...(active ? { backgroundColor: settings.tokens.primary } : {}),
                }}
                className={`w-full ${itemHeight} px-3 flex items-center gap-3 text-sm font-medium transition-colors cursor-pointer whitespace-nowrap focus-visible:outline-2 focus-visible:outline-white ${
                  railOnly ? 'justify-center px-0' : ''
                } ${
                  active
                    ? 'text-white shadow-xs'
                    : 'text-slate-300 hover:bg-white/10 hover:text-white'
                }`}
              >
                <Icon className="w-4 h-4 shrink-0" />
                {!railOnly && <span className="truncate">{meta.label}</span>}
              </button>
            );
          })}
        </nav>
      </div>

      {/* Operator Footer */}
      <div className="p-2.5 border-t border-white/10 space-y-1.5 shrink-0">
        {!railOnly && session && (
          <div className="px-2 py-1">
            <div className="text-xs font-semibold text-white truncate">
              {session.user.fullName}
            </div>
            <div className="text-[11px] text-slate-300 truncate capitalize">
              {session.user.role} · {settings.branding.storeId}
            </div>
          </div>
        )}
        <button
          type="button"
          onClick={onLockTerminal}
          title="Lock Terminal / Switch User"
          aria-label="Lock Terminal / Switch User"
          style={{ borderRadius: 'var(--pos-radius)' }}
          className={`w-full ${
            isTouchDensity ? 'min-h-[44px]' : 'min-h-[38px]'
          } px-3 flex items-center gap-2.5 text-xs font-medium text-slate-200 hover:bg-white/10 hover:text-white transition-colors cursor-pointer whitespace-nowrap focus-visible:outline-2 focus-visible:outline-white ${
            railOnly ? 'justify-center px-0' : ''
          }`}
        >
          <Lock className="w-4 h-4 shrink-0" />
          {!railOnly && <span className="truncate">Lock / Switch User</span>}
        </button>
      </div>
    </aside>
  );
};
