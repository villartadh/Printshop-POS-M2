import React, { useEffect, useState } from 'react';
import {
  ChevronDown,
  ChevronUp,
  Download,
  Eye,
  EyeOff,
  Plus,
  RefreshCw,
  RotateCcw,
  Trash2,
} from 'lucide-react';
import { useApp } from '../../context/AppContext.tsx';
import { apiRequest, getMemoryToken } from '../../services/apiClient.ts';
import type {
  AppSettings,
  AuthorizedSessionRecord,
  ModuleId,
  UserRole,
} from '../../shared/types.ts';
import { Button, Card, Input, Select, StatusIndicator } from '../../components/ui/Primitives.tsx';
import { Modal } from '../../components/ui/Overlays.tsx';

const MODULE_LABELS: Record<ModuleId, string> = {
  dashboard: 'Dashboard',
  pos: 'POS Terminal',
  jobs: 'Print Jobs',
  services: 'Services & Pricing',
  inventory: 'Inventory',
  customers: 'Customers',
  finance: 'Expenses & Reports',
  shifts: 'Cashier Shifts',
  devices: 'Devices & Logs',
  settings: 'Settings',
};

export const SettingsModule: React.FC = () => {
  const {
    settings,
    users,
    outbox,
    isOnline,
    isSyncing,
    lastSyncedAt,
    session,
    saveSettings,
    setThemeMode,
    setDensity,
    resetSettingsToDefaults,
    syncNow,
    seedDemo,
    clearDemo,
    refreshBootstrap,
    notify,
  } = useApp();

  const isAdmin = session?.user.role === 'admin';
  const canConfigure = isAdmin || session?.user.role === 'manager';

  const [tab, setTab] = useState<
    'branding' | 'appearance' | 'pos_tax' | 'users' | 'sync_data'
  >('branding');
  const [draft, setDraft] = useState<AppSettings>(settings);
  const [sessionsList, setSessionsList] = useState<AuthorizedSessionRecord[]>([]);

  // New user modal
  const [addUserOpen, setAddUserOpen] = useState(false);
  const [newUsername, setNewUsername] = useState('');
  const [newFullName, setNewFullName] = useState('');
  const [newEmail, setNewEmail] = useState('');
  const [newRole, setNewRole] = useState<UserRole>('cashier');
  const [newPassword, setNewPassword] = useState('');
  const [newPin, setNewPin] = useState('');

  useEffect(() => {
    setDraft(settings);
  }, [settings]);

  const loadSessions = async () => {
    try {
      const list = await apiRequest<AuthorizedSessionRecord[]>('/api/auth/sessions');
      setSessionsList(list);
    } catch {
      // Offline fallback
    }
  };

  useEffect(() => {
    if (tab === 'users') void loadSessions();
  }, [tab]);

  const handleSave = async () => {
    if (!canConfigure) {
      notify('Only managers or administrators can save store settings.', 'error');
      return;
    }
    await saveSettings(draft);
  };

  const handleLogoUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (file.size > 512000) {
      notify('Logo image must be under 500 KB.', 'error');
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      setDraft((prev) => ({
        ...prev,
        branding: { ...prev.branding, logoDataUrl: String(reader.result || '') },
      }));
    };
    reader.readAsDataURL(file);
  };

  const moveModule = (mod: ModuleId, dir: -1 | 1) => {
    const order = [...draft.moduleOrder];
    const idx = order.indexOf(mod);
    const swap = idx + dir;
    if (idx < 0 || swap < 0 || swap >= order.length) return;
    const temp = order[idx];
    order[idx] = order[swap];
    order[swap] = temp;
    setDraft({ ...draft, moduleOrder: order });
  };

  const toggleModuleVisibility = (mod: ModuleId) => {
    if (mod === 'settings' || mod === 'pos') return; // Keep core POS & Settings reachable
    const hidden = new Set(draft.hiddenModules);
    if (hidden.has(mod)) hidden.delete(mod);
    else hidden.add(mod);
    setDraft({ ...draft, hiddenModules: Array.from(hidden) });
  };

  const handleExportBackup = async () => {
    const res = await fetch('/api/backup/export', {
      headers: { Authorization: `Bearer ${getMemoryToken()}` },
    });
    if (!res.ok) {
      notify('Backup export requires administrator permission.', 'error');
      return;
    }
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `printshop-pos-backup-${new Date().toISOString().slice(0, 10)}.json`;
    a.click();
    URL.revokeObjectURL(url);
    notify('Backup exported.', 'success');
  };

  const handleExportConfig = () => {
    const blob = new Blob([JSON.stringify(draft, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'printshop-pos-theme-config.json';
    a.click();
    URL.revokeObjectURL(url);
  };

  const handleImportConfig = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      try {
        const parsed = JSON.parse(String(reader.result || '{}')) as AppSettings;
        if (parsed.branding && parsed.tokens) {
          setDraft(parsed);
          notify('Configuration loaded into editor. Click Save Settings to apply.', 'info');
        }
      } catch {
        notify('Invalid configuration JSON file.', 'error');
      }
    };
    reader.readAsText(file);
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold text-[var(--pos-text)]">Settings & Customization</h1>
          <p className="text-xs text-[var(--pos-text-muted)]">
            Customize branding, design tokens, layout density, tax rules, users, and offline sync
          </p>
        </div>
        {canConfigure ? (
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => {
                resetSettingsToDefaults();
                notify('Default settings restored locally. Click Save Changes to sync.', 'info');
              }}
            >
              <RotateCcw className="w-3.5 h-3.5" />
              Reset Defaults
            </Button>
            <Button size="sm" onClick={() => void handleSave()}>
              Save Changes
            </Button>
          </div>
        ) : (
          <Button
            size="sm"
            onClick={() => {
              setThemeMode(draft.themeMode);
              setDensity(draft.density);
              notify('Display theme and density saved for this device.', 'success');
            }}
          >
            Save Display Preferences
          </Button>
        )}
      </div>

      {/* Tabs */}
      <div className="flex flex-wrap items-center gap-1 p-1 bg-slate-100 dark:bg-slate-800 rounded-lg">
        {[
          { id: 'branding', label: 'Branding & Profile' },
          { id: 'appearance', label: 'Theme & Navigation' },
          { id: 'pos_tax', label: 'POS, Tax & Receipts' },
          { id: 'users', label: 'Users & Devices' },
          { id: 'sync_data', label: 'Offline Sync & Data' },
        ].map((t) => (
          <button
            key={t.id}
            type="button"
            onClick={() => setTab(t.id as typeof tab)}
            className={`px-3 py-1.5 text-xs font-medium rounded-md cursor-pointer ${
              tab === t.id
                ? 'bg-[var(--pos-surface)] text-[var(--pos-text)] shadow-xs'
                : 'text-[var(--pos-text-muted)]'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'branding' && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <Card className="space-y-3.5">
            <h2 className="text-sm font-semibold">Application & Store Profile</h2>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <Input
                label="Application Name"
                value={draft.branding.appName}
                onChange={(e) =>
                  setDraft({ ...draft, branding: { ...draft.branding, appName: e.target.value } })
                }
              />
              <Input
                label="Business Name"
                value={draft.branding.businessName}
                onChange={(e) =>
                  setDraft({
                    ...draft,
                    branding: { ...draft.branding, businessName: e.target.value },
                  })
                }
              />
            </div>
            <Input
              label="Tagline"
              value={draft.branding.tagline}
              onChange={(e) =>
                setDraft({ ...draft, branding: { ...draft.branding, tagline: e.target.value } })
              }
            />
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <Input
                label="Branch / Store ID"
                value={draft.branding.storeId}
                onChange={(e) =>
                  setDraft({ ...draft, branding: { ...draft.branding, storeId: e.target.value } })
                }
              />
              <Input
                label="Phone"
                value={draft.branding.phone}
                onChange={(e) =>
                  setDraft({ ...draft, branding: { ...draft.branding, phone: e.target.value } })
                }
              />
            </div>
            <Input
              label="Business Address"
              value={draft.branding.address}
              onChange={(e) =>
                setDraft({ ...draft, branding: { ...draft.branding, address: e.target.value } })
              }
            />
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <Input
                label="Email"
                value={draft.branding.email}
                onChange={(e) =>
                  setDraft({ ...draft, branding: { ...draft.branding, email: e.target.value } })
                }
              />
              <Input
                label={`${draft.branding.taxIdLabel} Registration #`}
                value={draft.branding.taxRegistrationNumber}
                onChange={(e) =>
                  setDraft({
                    ...draft,
                    branding: { ...draft.branding, taxRegistrationNumber: e.target.value },
                  })
                }
              />
            </div>
          </Card>

          <Card className="space-y-3.5">
            <h2 className="text-sm font-semibold">Logo & Receipt Branding</h2>
            <div className="flex items-center gap-4">
              {draft.branding.logoDataUrl ? (
                <img
                  src={draft.branding.logoDataUrl}
                  alt="Business logo"
                  className="h-14 w-14 rounded-lg object-contain border border-[var(--pos-border)] p-1 bg-white"
                  referrerPolicy="no-referrer"
                />
              ) : (
                <div className="h-14 w-14 rounded-lg border border-dashed border-[var(--pos-border)] flex items-center justify-center text-[11px] text-[var(--pos-text-muted)]">
                  No Logo
                </div>
              )}
              <div className="space-y-1.5">
                <input
                  type="file"
                  accept="image/*"
                  onChange={handleLogoUpload}
                  className="text-xs text-[var(--pos-text-muted)]"
                />
                {draft.branding.logoDataUrl && (
                  <button
                    type="button"
                    onClick={() =>
                      setDraft({ ...draft, branding: { ...draft.branding, logoDataUrl: '' } })
                    }
                    className="text-xs text-[#DC2626] hover:underline cursor-pointer block"
                  >
                    Remove logo
                  </button>
                )}
              </div>
            </div>
            <Input
              label="Receipt Header Message"
              value={draft.branding.receiptHeader}
              onChange={(e) =>
                setDraft({
                  ...draft,
                  branding: { ...draft.branding, receiptHeader: e.target.value },
                })
              }
            />
            <Input
              label="Receipt Footer Disclaimer"
              value={draft.branding.receiptFooter}
              onChange={(e) =>
                setDraft({
                  ...draft,
                  branding: { ...draft.branding, receiptFooter: e.target.value },
                })
              }
            />
            <div className="flex flex-wrap gap-4 text-xs pt-1">
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={draft.branding.showLogoOnReceipt}
                  onChange={(e) =>
                    setDraft({
                      ...draft,
                      branding: { ...draft.branding, showLogoOnReceipt: e.target.checked },
                    })
                  }
                  className="h-4 w-4 accent-[var(--pos-primary)]"
                />
                Show Logo on Receipts
              </label>
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={draft.branding.showQrOnReceipt}
                  onChange={(e) =>
                    setDraft({
                      ...draft,
                      branding: { ...draft.branding, showQrOnReceipt: e.target.checked },
                    })
                  }
                  className="h-4 w-4 accent-[var(--pos-primary)]"
                />
                Show Document Reference (text)
              </label>
            </div>
          </Card>
        </div>
      )}

      {tab === 'appearance' && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <Card className="space-y-3.5">
            <h2 className="text-sm font-semibold">Design Tokens & Theme</h2>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <Select
                label="Theme Mode"
                value={draft.themeMode}
                onChange={(e) =>
                  setDraft({ ...draft, themeMode: e.target.value as AppSettings['themeMode'] })
                }
                options={[
                  { value: 'light', label: 'Light Mode' },
                  { value: 'dark', label: 'Dark Mode' },
                  { value: 'system', label: 'System Theme' },
                ]}
              />
              <Select
                label="Interface Density"
                value={draft.density}
                onChange={(e) =>
                  setDraft({ ...draft, density: e.target.value as AppSettings['density'] })
                }
                options={[
                  { value: 'comfortable', label: 'Comfortable' },
                  { value: 'compact', label: 'Compact' },
                  { value: 'touch', label: 'Touch-Friendly' },
                ]}
              />
              <Select
                label="Default Landing"
                value={draft.defaultLandingModule}
                onChange={(e) =>
                  setDraft({ ...draft, defaultLandingModule: e.target.value as ModuleId })
                }
                options={Object.entries(MODULE_LABELS).map(([k, v]) => ({ value: k, label: v }))}
              />
            </div>

            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
              {(
                [
                  { key: 'primary', label: 'Primary' },
                  { key: 'navBackground', label: 'Navigation' },
                  { key: 'appBackground', label: 'Background' },
                  { key: 'surface', label: 'Surface' },
                ] as const
              ).map((c) => (
                <div key={c.key}>
                  <label className="block text-xs font-medium text-[var(--pos-text-muted)] mb-1">
                    {c.label}
                  </label>
                  <div className="flex items-center gap-2 border border-[var(--pos-border)] rounded-lg px-2 py-1.5">
                    <input
                      type="color"
                      value={draft.tokens[c.key]}
                      onChange={(e) =>
                        setDraft({
                          ...draft,
                          tokens: { ...draft.tokens, [c.key]: e.target.value },
                        })
                      }
                      className="h-6 w-6 rounded cursor-pointer border-0 bg-transparent"
                    />
                    <span className="text-[11px] font-mono-num">{draft.tokens[c.key]}</span>
                  </div>
                </div>
              ))}
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <Select
                label="Font Family"
                value={draft.tokens.fontFamily}
                onChange={(e) =>
                  setDraft({
                    ...draft,
                    tokens: { ...draft.tokens, fontFamily: e.target.value },
                  })
                }
                options={[
                  { value: 'Plus Jakarta Sans', label: 'Plus Jakarta Sans' },
                  { value: 'DM Sans', label: 'DM Sans' },
                  { value: 'Inter', label: 'Inter' },
                  { value: 'Segoe UI', label: 'Segoe UI' },
                  { value: 'Roboto', label: 'Roboto' },
                ]}
              />
              <Select
                label="Font Scale"
                value={String(draft.tokens.fontSizeScale)}
                onChange={(e) =>
                  setDraft({
                    ...draft,
                    tokens: { ...draft.tokens, fontSizeScale: Number(e.target.value) },
                  })
                }
                options={[
                  { value: '0.9', label: '90% (Compact)' },
                  { value: '1', label: '100% (Standard)' },
                  { value: '1.1', label: '110% (Large)' },
                  { value: '1.2', label: '120% (Accessible)' },
                ]}
              />
              <Input
                label="Corner Radius (px)"
                type="number"
                min="2"
                max="20"
                value={draft.tokens.borderRadiusPx}
                onChange={(e) =>
                  setDraft({
                    ...draft,
                    tokens: { ...draft.tokens, borderRadiusPx: Number(e.target.value) || 10 },
                  })
                }
              />
            </div>

            <div className="flex flex-wrap gap-2 pt-2">
              <Button variant="outline" size="sm" onClick={handleExportConfig}>
                <Download className="w-3.5 h-3.5" />
                Export Config JSON
              </Button>
              <label className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-[var(--pos-border)] text-xs font-medium cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-800">
                Import Config JSON
                <input type="file" accept=".json" onChange={handleImportConfig} className="hidden" />
              </label>
            </div>
          </Card>

          <Card className="space-y-3">
            <h2 className="text-sm font-semibold">Reorder & Toggle Navigation Modules</h2>
            <div className="space-y-1.5">
              {draft.moduleOrder.map((mod, idx) => {
                const isHidden = draft.hiddenModules.includes(mod);
                return (
                  <div
                    key={mod}
                    className="flex items-center justify-between px-3 py-2 rounded-lg border border-[var(--pos-border)] text-sm"
                  >
                    <span className={isHidden ? 'text-[var(--pos-text-muted)] line-through' : ''}>
                      {MODULE_LABELS[mod]}
                    </span>
                    <div className="flex items-center gap-1">
                      <button
                        type="button"
                        disabled={idx === 0}
                        onClick={() => moveModule(mod, -1)}
                        className="p-1 rounded hover:bg-slate-100 dark:hover:bg-slate-800 disabled:opacity-30 cursor-pointer"
                      >
                        <ChevronUp className="w-4 h-4" />
                      </button>
                      <button
                        type="button"
                        disabled={idx === draft.moduleOrder.length - 1}
                        onClick={() => moveModule(mod, 1)}
                        className="p-1 rounded hover:bg-slate-100 dark:hover:bg-slate-800 disabled:opacity-30 cursor-pointer"
                      >
                        <ChevronDown className="w-4 h-4" />
                      </button>
                      <button
                        type="button"
                        disabled={mod === 'pos' || mod === 'settings'}
                        onClick={() => toggleModuleVisibility(mod)}
                        className="p-1 rounded hover:bg-slate-100 dark:hover:bg-slate-800 disabled:opacity-30 cursor-pointer"
                      >
                        {isHidden ? (
                          <EyeOff className="w-4 h-4 text-[var(--pos-text-muted)]" />
                        ) : (
                          <Eye className="w-4 h-4 text-[var(--pos-primary)]" />
                        )}
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          </Card>
        </div>
      )}

      {tab === 'pos_tax' && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <Card className="space-y-3.5">
            <h2 className="text-sm font-semibold">POS Workspace & Card Preferences</h2>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <Select
                label="Default View"
                value={draft.posLayout.defaultView}
                onChange={(e) =>
                  setDraft({
                    ...draft,
                    posLayout: {
                      ...draft.posLayout,
                      defaultView: e.target.value as 'grid' | 'list',
                    },
                  })
                }
                options={[
                  { value: 'grid', label: 'Card Grid' },
                  { value: 'list', label: 'Compact List' },
                ]}
              />
              <Select
                label="Desktop Grid Columns"
                value={String(draft.posLayout.gridColumnsDesktop)}
                onChange={(e) =>
                  setDraft({
                    ...draft,
                    posLayout: {
                      ...draft.posLayout,
                      gridColumnsDesktop: Number(e.target.value),
                    },
                  })
                }
                options={[
                  { value: '2', label: '2 Columns' },
                  { value: '3', label: '3 Columns' },
                  { value: '4', label: '4 Columns' },
                ]}
              />
            </div>
            <div className="grid grid-cols-2 gap-2.5 text-xs">
              {[
                { key: 'showPriceOnCard', label: 'Show Price on Cards' },
                { key: 'showCategoryOnCard', label: 'Show Category & SKU' },
                { key: 'showUnitOnCard', label: 'Show Unit Label' },
                { key: 'showQuickItemsBar', label: 'Favorites / Recent Tabs' },
              ].map((item) => (
                <label
                  key={item.key}
                  className="flex items-center gap-2 p-2.5 rounded-lg border border-[var(--pos-border)] cursor-pointer"
                >
                  <input
                    type="checkbox"
                    checked={Boolean(draft.posLayout[item.key as keyof typeof draft.posLayout])}
                    onChange={(e) =>
                      setDraft({
                        ...draft,
                        posLayout: { ...draft.posLayout, [item.key]: e.target.checked },
                      })
                    }
                    className="h-4 w-4 accent-[var(--pos-primary)]"
                  />
                  <span>{item.label}</span>
                </label>
              ))}
            </div>
          </Card>

          <Card className="space-y-3.5">
            <h2 className="text-sm font-semibold">Currency, Tax & Receipt Defaults</h2>
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
              <Input
                label="Currency Symbol"
                value={draft.taxAndCurrency.currencySymbol}
                onChange={(e) =>
                  setDraft({
                    ...draft,
                    taxAndCurrency: { ...draft.taxAndCurrency, currencySymbol: e.target.value },
                  })
                }
              />
              <Input
                label="Currency Code"
                value={draft.taxAndCurrency.currencyCode}
                onChange={(e) =>
                  setDraft({
                    ...draft,
                    taxAndCurrency: { ...draft.taxAndCurrency, currencyCode: e.target.value },
                  })
                }
              />
              <Select
                label="Default Paper Size"
                value={draft.receiptTemplate.defaultPaperSize}
                onChange={(e) =>
                  setDraft({
                    ...draft,
                    receiptTemplate: {
                      ...draft.receiptTemplate,
                      defaultPaperSize: e.target.value as AppSettings['receiptTemplate']['defaultPaperSize'],
                    },
                  })
                }
                options={[
                  { value: '58mm', label: '58mm Thermal' },
                  { value: '80mm', label: '80mm Thermal' },
                  { value: 'A4', label: 'A4' },
                  { value: 'Letter', label: 'Letter' },
                ]}
              />
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 items-end">
              <label className="flex items-center gap-2 text-xs p-2.5 rounded-lg border border-[var(--pos-border)] cursor-pointer">
                <input
                  type="checkbox"
                  checked={draft.taxAndCurrency.taxEnabled}
                  onChange={(e) =>
                    setDraft({
                      ...draft,
                      taxAndCurrency: { ...draft.taxAndCurrency, taxEnabled: e.target.checked },
                    })
                  }
                  className="h-4 w-4 accent-[var(--pos-primary)]"
                />
                Enable Sales Tax
              </label>
              <Input
                label="Tax Name"
                value={draft.taxAndCurrency.taxName}
                onChange={(e) =>
                  setDraft({
                    ...draft,
                    taxAndCurrency: { ...draft.taxAndCurrency, taxName: e.target.value },
                  })
                }
              />
              <Input
                label="Tax Rate (%)"
                type="number"
                min="0"
                step="0.5"
                value={draft.taxAndCurrency.taxRatePercent}
                onChange={(e) =>
                  setDraft({
                    ...draft,
                    taxAndCurrency: {
                      ...draft.taxAndCurrency,
                      taxRatePercent: Number(e.target.value) || 0,
                    },
                  })
                }
              />
            </div>
          </Card>
        </div>
      )}

      {tab === 'users' && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <Card className="space-y-3">
            <div className="flex items-center justify-between">
              <h2 className="text-sm font-semibold">Staff Accounts & Roles</h2>
              {isAdmin && (
                <Button size="sm" onClick={() => setAddUserOpen(true)}>
                  <Plus className="w-3.5 h-3.5" />
                  Add User
                </Button>
              )}
            </div>
            <div className="divide-y divide-[var(--pos-border)]">
              {users.map((u) => (
                <div key={u.id} className="py-2.5 flex items-center justify-between text-xs">
                  <div>
                    <div className="font-semibold text-sm text-[var(--pos-text)]">
                      {u.fullName}
                    </div>
                    <div className="text-[var(--pos-text-muted)]">
                      @{u.username} · {u.role.toUpperCase()} · {u.hasPin ? 'PIN enabled' : 'Password only'}
                    </div>
                  </div>
                  <StatusIndicator
                    tone={u.isActive ? 'success' : 'neutral'}
                    label={u.isActive ? 'Active' : 'Disabled'}
                  />
                </div>
              ))}
            </div>
          </Card>

          <Card className="space-y-3">
            <h2 className="text-sm font-semibold">Authorized Devices & Active Sessions</h2>
            <div className="divide-y divide-[var(--pos-border)]">
              {sessionsList.length === 0 ? (
                <p className="text-xs text-[var(--pos-text-muted)] py-3">
                  No active device sessions loaded.
                </p>
              ) : (
                sessionsList.map((s) => (
                  <div key={s.id} className="py-2.5 flex items-center justify-between text-xs gap-2">
                    <div>
                      <div className="font-semibold text-[var(--pos-text)]">
                        {s.deviceName} {s.isCurrent ? '(This Device)' : ''}
                      </div>
                      <div className="text-[var(--pos-text-muted)]">
                        {s.userName} · Last active {new Date(s.lastSeenAt).toLocaleString()}
                      </div>
                    </div>
                    {!s.isCurrent && isAdmin && (
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={async () => {
                          await apiRequest(`/api/auth/sessions/${s.id}`, { method: 'DELETE' });
                          await loadSessions();
                        }}
                      >
                        Revoke
                      </Button>
                    )}
                  </div>
                ))
              )}
            </div>
          </Card>
        </div>
      )}

      {tab === 'sync_data' && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <Card className="space-y-3">
            <h2 className="text-sm font-semibold">Offline Queue & Synchronization</h2>
            <div className="p-3 rounded-lg bg-slate-50 dark:bg-slate-900/50 border border-[var(--pos-border)] space-y-1.5 text-xs">
              <div className="flex justify-between">
                <span className="text-[var(--pos-text-muted)]">Connectivity</span>
                <StatusIndicator
                  tone={isOnline ? 'success' : 'warning'}
                  label={isOnline ? 'Online' : 'Offline Mode'}
                />
              </div>
              <div className="flex justify-between">
                <span className="text-[var(--pos-text-muted)]">Pending Offline Mutations</span>
                <span className="font-mono-num font-bold">{outbox.length}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-[var(--pos-text-muted)]">Last Synced</span>
                <span className="font-mono-num">
                  {lastSyncedAt ? new Date(lastSyncedAt).toLocaleString() : 'Not yet synced'}
                </span>
              </div>
            </div>
            {outbox.filter(m => m.status === 'conflict' || m.status === 'failed').map(m => (
              <div key={m.id} className="border rounded p-2 text-xs text-amber-700">
                {m.type} · {m.clientMutationId}: {m.errorMessage || 'Requires reconciliation'}. Preserved locally; do not repeat payment.
              </div>
            ))}
            <Button
              variant="outline"
              size="sm"
              loading={isSyncing}
              disabled={!isOnline}
              onClick={() => void syncNow()}
            >
              <RefreshCw className="w-3.5 h-3.5" />
              Synchronize Now
            </Button>
          </Card>

          <Card className="space-y-3">
            <h2 className="text-sm font-semibold">Demonstration Data & Database Backup</h2>
            <p className="text-xs text-[var(--pos-text-muted)]">
              Sample services, customers, and starter transactions are isolated as demonstration records and can be cleared anytime before production use.
            </p>
            <div className="flex flex-wrap gap-2">
              {canConfigure && (
                <>
                  <Button variant="outline" size="sm" onClick={() => void seedDemo()}>
                    Load Sample Demo Data
                  </Button>
                  <Button
                    variant="danger"
                    size="sm"
                    onClick={async () => {
                      await clearDemo();
                      await refreshBootstrap();
                    }}
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                    Clear Demo Data
                  </Button>
                </>
              )}
              {isAdmin && (
                <Button variant="secondary" size="sm" onClick={() => void handleExportBackup()}>
                  <Download className="w-3.5 h-3.5" />
                  Export Business Data JSON (partial)
                </Button>
              )}
            </div>
          </Card>
        </div>
      )}

      {/* Add User Modal */}
      <Modal
        open={addUserOpen}
        onClose={() => setAddUserOpen(false)}
        title="Create Staff Account"
        maxWidth="sm"
      >
        <form
          onSubmit={async (e) => {
            e.preventDefault();
            await apiRequest('/api/auth/users', {
              method: 'POST',
              body: JSON.stringify({
                username: newUsername,
                fullName: newFullName,
                email: newEmail,
                role: newRole,
                password: newPassword,
                pin: newPin,
              }),
            });
            setNewUsername('');
            setNewFullName('');
            setNewEmail('');
            setNewPassword('');
            setNewPin('');
            setAddUserOpen(false);
            await refreshBootstrap();
            notify('Staff account created.', 'success');
          }}
          className="space-y-3"
        >
          <Input
            label="Full Name"
            value={newFullName}
            onChange={(e) => setNewFullName(e.target.value)}
            required
          />
          <Input
            label="Username"
            value={newUsername}
            onChange={(e) => setNewUsername(e.target.value)}
            required
          />
          <Input
            label="Email"
            type="email"
            value={newEmail}
            onChange={(e) => setNewEmail(e.target.value)}
          />
          <Select
            label="Role"
            value={newRole}
            onChange={(e) => setNewRole(e.target.value as UserRole)}
            options={[
              { value: 'cashier', label: 'Cashier' },
              { value: 'operator', label: 'Production Operator' },
              { value: 'manager', label: 'Manager' },
              { value: 'admin', label: 'Administrator' },
            ]}
          />
          <Input
            label="Password (8–128 chars)"
            type="password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            required
          />
          <Input
            label="Quick PIN (4–6 digits, optional)"
            type="password"
            maxLength={6}
            value={newPin}
            onChange={(e) => setNewPin(e.target.value.replace(/\D/g, ''))}
          />
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="secondary" type="button" onClick={() => setAddUserOpen(false)}>
              Cancel
            </Button>
            <Button type="submit">Create Account</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
