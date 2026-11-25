import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { DEFAULT_MODULE_ORDER, DEFAULT_SETTINGS } from '../shared/defaults.ts';
import type {
  AppSettings,
  BrandingConfig,
  DesignTokens,
  InterfaceDensity,
  ModuleId,
  ThemeMode,
} from '../shared/types.ts';

export const APP_STATE_STORAGE_KEY = 'printshop_pos_global_settings_v1';

export function mergeSettingsWithDefaults(
  raw: Partial<AppSettings> | null | undefined,
): AppSettings {
  if (!raw || typeof raw !== 'object') {
    return { ...DEFAULT_SETTINGS, updatedAt: '' };
  }

  const validModules = new Set<ModuleId>(DEFAULT_MODULE_ORDER);
  const incomingOrder = Array.isArray(raw.moduleOrder)
    ? raw.moduleOrder.filter((m): m is ModuleId => validModules.has(m))
    : [];
  for (const mod of DEFAULT_MODULE_ORDER) {
    if (!incomingOrder.includes(mod)) {
      incomingOrder.push(mod);
    }
  }

  const hiddenModules = Array.isArray(raw.hiddenModules)
    ? raw.hiddenModules.filter(
        (m): m is ModuleId => validModules.has(m) && m !== 'pos' && m !== 'settings',
      )
    : DEFAULT_SETTINGS.hiddenModules;

  return {
    themeMode:
      raw.themeMode === 'light' || raw.themeMode === 'dark' || raw.themeMode === 'system'
        ? raw.themeMode
        : DEFAULT_SETTINGS.themeMode,
    density:
      raw.density === 'comfortable' || raw.density === 'compact' || raw.density === 'touch'
        ? raw.density
        : DEFAULT_SETTINGS.density,
    defaultLandingModule:
      raw.defaultLandingModule && validModules.has(raw.defaultLandingModule)
        ? raw.defaultLandingModule
        : DEFAULT_SETTINGS.defaultLandingModule,
    moduleOrder: incomingOrder,
    hiddenModules,
    shellPreferences: {
      sidebarCollapsed:
        typeof raw.shellPreferences?.sidebarCollapsed === 'boolean'
          ? raw.shellPreferences.sidebarCollapsed
          : Boolean(DEFAULT_SETTINGS.shellPreferences?.sidebarCollapsed),
    },
    tokens: {
      ...DEFAULT_SETTINGS.tokens,
      ...(raw.tokens && typeof raw.tokens === 'object' ? raw.tokens : {}),
    },
    branding: {
      ...DEFAULT_SETTINGS.branding,
      ...(raw.branding && typeof raw.branding === 'object' ? raw.branding : {}),
    },
    posLayout: {
      ...DEFAULT_SETTINGS.posLayout,
      ...(raw.posLayout && typeof raw.posLayout === 'object' ? raw.posLayout : {}),
    },
    dashboardWidgets: (() => {
      const base =
        Array.isArray(raw.dashboardWidgets) && raw.dashboardWidgets.length > 0
          ? raw.dashboardWidgets.map((w) => ({ ...w }))
          : DEFAULT_SETTINGS.dashboardWidgets.map((w) => ({ ...w }));
      const existingIds = new Set(base.map((w) => w.id));
      for (const defWidget of DEFAULT_SETTINGS.dashboardWidgets) {
        if (!existingIds.has(defWidget.id)) {
          base.push({ ...defWidget, order: base.length });
        }
      }
      return base;
    })(),
    receiptTemplate: {
      ...DEFAULT_SETTINGS.receiptTemplate,
      ...(raw.receiptTemplate && typeof raw.receiptTemplate === 'object'
        ? raw.receiptTemplate
        : {}),
    },
    paymentMethods:
      Array.isArray(raw.paymentMethods) && raw.paymentMethods.length > 0
        ? raw.paymentMethods
        : DEFAULT_SETTINGS.paymentMethods,
    taxAndCurrency: {
      ...DEFAULT_SETTINGS.taxAndCurrency,
      ...(raw.taxAndCurrency && typeof raw.taxAndCurrency === 'object'
        ? raw.taxAndCurrency
        : {}),
    },
    demoModeActive:
      typeof raw.demoModeActive === 'boolean' ? raw.demoModeActive : DEFAULT_SETTINGS.demoModeActive,
    updatedAt: typeof raw.updatedAt === 'string' && raw.updatedAt ? raw.updatedAt : '',
  };
}

export function loadPersistedAppSettings(): AppSettings {
  if (typeof window === 'undefined' || !window.localStorage) {
    return mergeSettingsWithDefaults(null);
  }
  try {
    const raw = window.localStorage.getItem(APP_STATE_STORAGE_KEY);
    if (!raw) return mergeSettingsWithDefaults(null);
    return mergeSettingsWithDefaults(JSON.parse(raw) as Partial<AppSettings>);
  } catch {
    return mergeSettingsWithDefaults(null);
  }
}

export function savePersistedAppSettings(settings: AppSettings): boolean {
  if (typeof window === 'undefined' || !window.localStorage) return false;
  try {
    window.localStorage.setItem(APP_STATE_STORAGE_KEY, JSON.stringify(settings));
    return true;
  } catch {
    return false;
  }
}

export function applySettingsToDocument(settings: AppSettings, systemPrefersDark: boolean) {
  if (typeof document === 'undefined') return;
  const root = document.documentElement;
  const { tokens, themeMode, density, branding } = settings;

  const shouldBeDark = themeMode === 'dark' || (themeMode === 'system' && systemPrefersDark);
  root.classList.toggle('dark', shouldBeDark);
  root.setAttribute('data-density', density);

  root.style.setProperty('--pos-primary', tokens.primary);
  root.style.setProperty('--pos-primary-hover', tokens.primaryHover);
  root.style.setProperty('--pos-nav-bg', tokens.navBackground);
  root.style.setProperty('--pos-success', tokens.success);
  root.style.setProperty('--pos-warning', tokens.warning);
  root.style.setProperty('--pos-error', tokens.error);
  root.style.setProperty('--pos-info', tokens.info);
  root.style.setProperty('--pos-radius', `${tokens.borderRadiusPx}px`);
  root.style.setProperty('--pos-scale', String(tokens.fontSizeScale || 1));
  root.style.setProperty(
    '--pos-font',
    `"${tokens.fontFamily}", "DM Sans", -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif`,
  );

  if (!shouldBeDark) {
    root.style.setProperty('--pos-bg', tokens.appBackground);
    root.style.setProperty('--pos-surface', tokens.surface);
    root.style.setProperty('--pos-text', tokens.primaryText);
    root.style.setProperty('--pos-text-muted', tokens.secondaryText);
    root.style.setProperty('--pos-border', tokens.border);
  } else {
    root.style.removeProperty('--pos-bg');
    root.style.removeProperty('--pos-surface');
    root.style.removeProperty('--pos-text');
    root.style.removeProperty('--pos-text-muted');
    root.style.removeProperty('--pos-border');
  }

  const metaThemeColor = document.querySelector('meta[name="theme-color"]');
  if (metaThemeColor) {
    metaThemeColor.setAttribute('content', tokens.primary);
  }

  document.title = `${branding.appName || 'Printshop POS'} — ${branding.businessName || branding.tagline}`;
}

export interface AppStateContextValue {
  settings: AppSettings;
  hasLocalCustomizations: boolean;
  lastLocalSavedAt: string | null;
  setSettingsState: (next: AppSettings | ((prev: AppSettings) => AppSettings)) => AppSettings;
  updateBranding: (patch: Partial<BrandingConfig>) => AppSettings;
  updateTokens: (patch: Partial<DesignTokens>) => AppSettings;
  setThemeMode: (mode: ThemeMode) => AppSettings;
  setDensity: (density: InterfaceDensity) => AppSettings;
  setSidebarCollapsed: (collapsed: boolean) => AppSettings;
  resetSettingsToDefaults: () => AppSettings;
}

const AppStateContext = createContext<AppStateContextValue | null>(null);

export function useAppState(): AppStateContextValue {
  const ctx = useContext(AppStateContext);
  if (!ctx) {
    throw new Error('useAppState must be used inside AppStateProvider');
  }
  return ctx;
}

export const AppStateProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const initialRead = useMemo(() => {
    if (typeof window === 'undefined' || !window.localStorage) {
      return { settings: mergeSettingsWithDefaults(null), hasStored: false };
    }
    try {
      const raw = window.localStorage.getItem(APP_STATE_STORAGE_KEY);
      if (!raw) {
        return { settings: mergeSettingsWithDefaults(null), hasStored: false };
      }
      return {
        settings: mergeSettingsWithDefaults(JSON.parse(raw) as Partial<AppSettings>),
        hasStored: true,
      };
    } catch {
      return { settings: mergeSettingsWithDefaults(null), hasStored: false };
    }
  }, []);

  const [settings, setSettingsInternal] = useState<AppSettings>(initialRead.settings);
  const settingsRef = useRef(settings);
  settingsRef.current = settings;

  const [hasLocalCustomizations, setHasLocalCustomizations] = useState<boolean>(
    initialRead.hasStored,
  );
  const [lastLocalSavedAt, setLastLocalSavedAt] = useState<string | null>(
    initialRead.hasStored ? initialRead.settings.updatedAt || null : null,
  );

  const [systemPrefersDark, setSystemPrefersDark] = useState<boolean>(() =>
    typeof window !== 'undefined' &&
    Boolean(window.matchMedia?.('(prefers-color-scheme: dark)').matches),
  );

  // Watch OS color scheme changes
  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return;
    const mql = window.matchMedia('(prefers-color-scheme: dark)');
    const onChange = (e: MediaQueryListEvent) => setSystemPrefersDark(e.matches);
    mql.addEventListener('change', onChange);
    return () => mql.removeEventListener('change', onChange);
  }, []);

  // Apply theme, density, branding, and CSS custom properties to document
  useEffect(() => {
    applySettingsToDocument(settings, systemPrefersDark);
  }, [settings, systemPrefersDark]);

  // Cross-tab synchronization via localStorage 'storage' event
  useEffect(() => {
    if (typeof window === 'undefined') return;
    const onStorage = (e: StorageEvent) => {
      if (e.key === APP_STATE_STORAGE_KEY && e.newValue) {
        try {
          const parsed = mergeSettingsWithDefaults(JSON.parse(e.newValue) as Partial<AppSettings>);
          setSettingsInternal(parsed);
          setHasLocalCustomizations(true);
          setLastLocalSavedAt(parsed.updatedAt || new Date().toISOString());
        } catch {
          // Ignore corrupted cross-tab payload
        }
      }
    };
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, []);

  const setSettingsState = useCallback(
    (next: AppSettings | ((prev: AppSettings) => AppSettings)): AppSettings => {
      const resolved =
        typeof next === 'function' ? next(settingsRef.current) : next;
      const normalized = mergeSettingsWithDefaults({
        ...resolved,
        updatedAt: resolved.updatedAt || new Date().toISOString(),
      });
      settingsRef.current = normalized;
      setSettingsInternal(normalized);
      if (savePersistedAppSettings(normalized)) {
        setHasLocalCustomizations(true);
        setLastLocalSavedAt(normalized.updatedAt);
      }
      return normalized;
    },
    [],
  );

  const updateBranding = useCallback(
    (patch: Partial<BrandingConfig>) =>
      setSettingsState((prev) => ({
        ...prev,
        branding: { ...prev.branding, ...patch },
        updatedAt: new Date().toISOString(),
      })),
    [setSettingsState],
  );

  const updateTokens = useCallback(
    (patch: Partial<DesignTokens>) =>
      setSettingsState((prev) => ({
        ...prev,
        tokens: { ...prev.tokens, ...patch },
        updatedAt: new Date().toISOString(),
      })),
    [setSettingsState],
  );

  const setThemeMode = useCallback(
    (themeMode: ThemeMode) =>
      setSettingsState((prev) => ({
        ...prev,
        themeMode,
        updatedAt: new Date().toISOString(),
      })),
    [setSettingsState],
  );

  const setDensity = useCallback(
    (density: InterfaceDensity) =>
      setSettingsState((prev) => ({
        ...prev,
        density,
        updatedAt: new Date().toISOString(),
      })),
    [setSettingsState],
  );

  const setSidebarCollapsed = useCallback(
    (sidebarCollapsed: boolean) =>
      setSettingsState((prev) => ({
        ...prev,
        shellPreferences: {
          ...prev.shellPreferences,
          sidebarCollapsed,
        },
        updatedAt: new Date().toISOString(),
      })),
    [setSettingsState],
  );

  const resetSettingsToDefaults = useCallback(() => {
    const defaults = mergeSettingsWithDefaults({
      ...DEFAULT_SETTINGS,
      updatedAt: new Date().toISOString(),
    });
    return setSettingsState(defaults);
  }, [setSettingsState]);

  const value = useMemo<AppStateContextValue>(
    () => ({
      settings,
      hasLocalCustomizations,
      lastLocalSavedAt,
      setSettingsState,
      updateBranding,
      updateTokens,
      setThemeMode,
      setDensity,
      setSidebarCollapsed,
      resetSettingsToDefaults,
    }),
    [
      settings,
      hasLocalCustomizations,
      lastLocalSavedAt,
      setSettingsState,
      updateBranding,
      updateTokens,
      setThemeMode,
      setDensity,
      setSidebarCollapsed,
      resetSettingsToDefaults,
    ],
  );

  return <AppStateContext.Provider value={value}>{children}</AppStateContext.Provider>;
};
