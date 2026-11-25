import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { useAppState } from './AppStateContext.tsx';
import type {
  AppSettings,
  AuditLogEntry,
  AuthSession,
  BrandingConfig,
  BulkInventoryUpdatePayload,
  CashierShift,
  Customer,
  DesignTokens,
  ExpenseRecord,
  InterfaceDensity,
  InventoryItem,
  ModuleId,
  OfflineMutation,
  PlatformPreviewMode,
  PrintJob,
  PrintService,
  ServiceCategory,
  StockAdjustmentPayload,
  StockMovement,
  SupplyCategory,
  ThemeMode,
  Transaction,
  UserAccount,
} from '../shared/types.ts';
import { DEFAULT_SUPPLY_CATEGORIES } from '../shared/defaults.ts';
import {
  calculateInventoryValuation,
  computeSignedStockDelta,
  inferReferenceType,
  isLowStockItem,
  normalizeInventoryItem,
  normalizeStockMovement,
  validateInventoryItemInput,
  validateStockMovementInput,
} from '../shared/inventoryModel.ts';
import { apiRequest, getDeviceDescriptor, setMemoryToken } from '../services/apiClient.ts';
import {
  appendDiagnosticLog,
  enqueueOfflineMutation,
  getOutboxMutations,
  loadOfflineSnapshot,
  removeOutboxMutation,
  saveOfflineSnapshot,
  updateOutboxMutation,
} from '../services/offlineDb.ts';
import {validatePurchaseCost,weightedPurchaseCost} from '../shared/purchaseCost.ts';
import { pendingBatch, mutationOwner, validateSyncResults } from '../shared/offlinePolicy.ts';
import { useOnlineStatus } from '../hooks/usePWAInstall.ts';

export interface QuickUserInfo {
  id: string;
  username: string;
  fullName: string;
  role: UserAccount['role'];
  hasPin: boolean;
}

export interface ToastMessage {
  id: string;
  tone: 'success' | 'error' | 'info';
  text: string;
}

export interface InventoryStateSlice {
  items: InventoryItem[];
  stockMovements: StockMovement[];
  supplyCategories: SupplyCategory[];
  lowStockItems: InventoryItem[];
  totalValuation: number;
  loading: boolean;
  error: string | null;
  lastLoadedAt: string | null;
}

interface BootstrapPayload {
  settings: AppSettings;
  categories: ServiceCategory[];
  supplyCategories?: SupplyCategory[];
  services: PrintService[];
  inventory: InventoryItem[];
  stockMovements: StockMovement[];
  customers: Customer[];
  transactions: Transaction[];
  printJobs: PrintJob[];
  expenses: ExpenseRecord[];
  shifts: CashierShift[];
  users: UserAccount[];
  auditLogs: AuditLogEntry[];
  serverTime: string;
}

interface AppContextValue {
  // Auth & shell
  authChecked: boolean;
  needsSetup: boolean;
  quickUsers: QuickUserInfo[];
  session: AuthSession | null;
  isLocked: boolean;
  setIsLocked: (locked: boolean) => void;
  activeModule: ModuleId;
  setActiveModule: (mod: ModuleId) => void;
  platformPreview: PlatformPreviewMode;
  setPlatformPreview: (mode: PlatformPreviewMode) => void;
  fullScreenCashier: boolean;
  setFullScreenCashier: (val: boolean) => void;

  // Connectivity & Sync
  isOnline: boolean;
  isSyncing: boolean;
  pendingSyncCount: number;
  lastSyncedAt: string | null;
  outbox: OfflineMutation[];
  syncNow: () => Promise<void>;

  // Data stores
  loadingData: boolean;
  dataError: string | null;
  settings: AppSettings;
  categories: ServiceCategory[];
  supplyCategories: SupplyCategory[];
  services: PrintService[];
  inventory: InventoryItem[];
  inventoryState: InventoryStateSlice;
  stockMovements: StockMovement[];
  customers: Customer[];
  transactions: Transaction[];
  printJobs: PrintJob[];
  expenses: ExpenseRecord[];
  shifts: CashierShift[];
  activeShift: CashierShift | null;
  users: UserAccount[];
  auditLogs: AuditLogEntry[];

  // Toasts
  toasts: ToastMessage[];
  notify: (text: string, tone?: ToastMessage['tone']) => void;
  dismissToast: (id: string) => void;

  // Auth Actions
  refreshAuthStatus: () => Promise<void>;
  setupAdmin: (payload: {
    username: string;
    fullName: string;
    email: string;
    password: string;
    pin: string;
  }) => Promise<void>;
  loginWithPassword: (username: string, password: string) => Promise<void>;
  loginWithPin: (userId: string, pin: string) => Promise<void>;
  logout: () => Promise<void>;

  // Domain Actions
  refreshBootstrap: () => Promise<void>;
  saveSettings: (next: AppSettings) => Promise<void>;
  setThemeMode: (mode: ThemeMode) => void;
  setDensity: (density: InterfaceDensity) => void;
  setSidebarCollapsed: (collapsed: boolean) => void;
  updateBranding: (patch: Partial<BrandingConfig>) => void;
  updateTokens: (patch: Partial<DesignTokens>) => void;
  resetSettingsToDefaults: () => void;
  submitTransaction: (txn: Transaction) => Promise<{ transaction: Transaction; queuedOffline: boolean }>;
  deleteHeldOrder: (id: string) => Promise<void>;
  voidTransaction: (id: string, reason: string) => Promise<void>;
  refundTransaction: (id: string, reason: string) => Promise<void>;
  createCategory: (name: string) => Promise<void>;
  deleteCategory: (id: string) => Promise<void>;
  saveService: (service: Partial<PrintService> & { id?: string }) => Promise<PrintService>;
  deleteService: (id: string) => Promise<void>;
  createPrintJob: (job: Partial<PrintJob>) => Promise<PrintJob>;
  updatePrintJob: (
    id: string,
    patch: {
      productionStatus?: PrintJob['productionStatus'];
      paymentStatus?: PrintJob['paymentStatus'];
      assignedOperator?: string;
      instructions?: string;
      depositAmount?: number;
      note?: string;
    },
  ) => Promise<PrintJob>;
  loadInventory: () => Promise<InventoryItem[]>;
  createInventoryItem: (item: Partial<InventoryItem>) => Promise<InventoryItem>;
  updateInventoryItem: (id: string, patch: Partial<InventoryItem>) => Promise<InventoryItem>;
  bulkUpdateInventoryItems: (payload: BulkInventoryUpdatePayload) => Promise<InventoryItem[]>;
  deleteInventoryItem: (id: string) => Promise<void>;
  trackStockChange: (id: string, payload: StockAdjustmentPayload) => Promise<StockMovement>;
  adjustInventory: (
    id: string,
    payload: StockAdjustmentPayload,
  ) => Promise<StockMovement>;
  getInventoryItemById: (id: string) => InventoryItem | undefined;
  getItemStockMovements: (inventoryItemId: string) => StockMovement[];
  createCustomer: (customer: Partial<Customer>) => Promise<Customer>;
  collectCustomerBalance: (id: string, amount: number) => Promise<void>;
  createExpense: (expense: Partial<ExpenseRecord>) => Promise<void>;
  deleteExpense: (id: string) => Promise<void>;
  startShift: (openingCash: number) => Promise<void>;
  recordCashMovement: (shiftId: string, type: 'cash_in' | 'cash_out', amount: number, reason: string) => Promise<void>;
  closeShift: (shiftId: string, actualCash: number, reconciliationNote: string) => Promise<void>;
  seedDemo: () => Promise<void>;
  clearDemo: () => Promise<void>;
}

const AppContext = createContext<AppContextValue | null>(null);

export function useApp() {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error('useApp must be used inside AppProvider');
  return ctx;
}

export const AppProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const {
    settings,
    setSettingsState,
    updateBranding,
    updateTokens,
    setThemeMode,
    setDensity,
    setSidebarCollapsed,
    resetSettingsToDefaults,
  } = useAppState();

  const isOnline = useOnlineStatus();
  const [authChecked, setAuthChecked] = useState(false);
  const [needsSetup, setNeedsSetup] = useState(false);
  const [quickUsers, setQuickUsers] = useState<QuickUserInfo[]>([]);
  const [session, setSession] = useState<AuthSession | null>(null);
  const [isLocked, setIsLocked] = useState(false);

  const [activeModule, setActiveModule] = useState<ModuleId>('pos');
  const [platformPreview, setPlatformPreview] = useState<PlatformPreviewMode>('auto');
  const [fullScreenCashier, setFullScreenCashier] = useState(false);

  const [loadingData, setLoadingData] = useState(false);
  const [dataError, setDataError] = useState<string | null>(null);
  const [categories, setCategories] = useState<ServiceCategory[]>([]);
  const [supplyCategories, setSupplyCategories] = useState<SupplyCategory[]>(
    DEFAULT_SUPPLY_CATEGORIES,
  );
  const [services, setServices] = useState<PrintService[]>([]);
  const [inventory, setInventory] = useState<InventoryItem[]>([]);
  const [stockMovements, setStockMovements] = useState<StockMovement[]>([]);
  const [inventoryLoading, setInventoryLoading] = useState(false);
  const [inventoryError, setInventoryError] = useState<string | null>(null);
  const [inventoryLastLoadedAt, setInventoryLastLoadedAt] = useState<string | null>(null);
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [printJobs, setPrintJobs] = useState<PrintJob[]>([]);
  const [expenses, setExpenses] = useState<ExpenseRecord[]>([]);
  const [shifts, setShifts] = useState<CashierShift[]>([]);
  const [users, setUsers] = useState<UserAccount[]>([]);
  const [auditLogs, setAuditLogs] = useState<AuditLogEntry[]>([]);

  const syncLock = useRef(false);
  const [outbox, setOutbox] = useState<OfflineMutation[]>([]);
  const [isSyncing, setIsSyncing] = useState(false);
  const [lastSyncedAt, setLastSyncedAt] = useState<string | null>(null);
  const [toasts, setToasts] = useState<ToastMessage[]>([]);

  const notify = useCallback((text: string, tone: ToastMessage['tone'] = 'info') => {
    const id = `toast_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`;
    setToasts((prev) => [...prev.slice(-3), { id, tone, text }]);
    window.setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
    }, 3600);
  }, []);

  const dismissToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const applyBootstrapData = useCallback(
    (data: BootstrapPayload) => {
      setSettingsState((prev) => ({
        ...data.settings,
        themeMode: prev.themeMode,
        density: prev.density,
        shellPreferences: prev.shellPreferences ?? data.settings.shellPreferences,
      }));
      setCategories(data.categories);
      if (Array.isArray(data.supplyCategories) && data.supplyCategories.length > 0) {
        setSupplyCategories(data.supplyCategories);
      }
      setServices(data.services);
      setInventory((data.inventory || []).map((item) => normalizeInventoryItem(item)));
      setStockMovements(
        (data.stockMovements || []).map((mov) => normalizeStockMovement(mov)),
      );
      setInventoryLastLoadedAt(data.serverTime || new Date().toISOString());
      setInventoryError(null);
      setCustomers(data.customers);
      setTransactions(data.transactions);
      setPrintJobs(data.printJobs);
      setExpenses(data.expenses);
      setShifts(data.shifts);
      setUsers(data.users);
      setAuditLogs(data.auditLogs);
    },
    [setSettingsState],
  );

  const refreshBootstrap = useCallback(async () => {
    setLoadingData(true);
    setDataError(null);
    try {
      const data = await apiRequest<BootstrapPayload>('/api/bootstrap');
      applyBootstrapData(data);
      setLastSyncedAt(data.serverTime || new Date().toISOString());
      await saveOfflineSnapshot('bootstrap', data);
    } catch (err) {
      const cached = await loadOfflineSnapshot<BootstrapPayload>('bootstrap');
      if (cached) {
        applyBootstrapData(cached);
        notify('Working from cached offline store data.', 'info');
      } else {
        setDataError(err instanceof Error ? err.message : 'Unable to load store records.');
      }
    } finally {
      setLoadingData(false);
    }
  }, [applyBootstrapData, notify]);

  const refreshAuthStatus = useCallback(async () => {
    try {
      const status = await apiRequest<{ needsSetup: boolean; quickUsers: QuickUserInfo[] }>(
        '/api/auth/status',
      );
      setNeedsSetup(status.needsSetup);
      setQuickUsers(status.quickUsers);
    } catch {
      const cached = await loadOfflineSnapshot<BootstrapPayload>('bootstrap');
      if (cached?.users) {
        setQuickUsers(
          cached.users.map((u) => ({
            id: u.id,
            username: u.username,
            fullName: u.fullName,
            role: u.role,
            hasPin: u.hasPin,
          })),
        );
      }
    } finally {
      setAuthChecked(true);
    }
  }, []);

  useEffect(() => {
    void refreshAuthStatus();
    void getOutboxMutations().then(setOutbox).catch(() => notify("Offline queue unavailable. Check browser storage.", "error"));
  }, [refreshAuthStatus, notify]);

  const syncNow = useCallback(async () => {
    if (!session || !isOnline || syncLock.current) return;
    syncLock.current = true;
    setIsSyncing(true);
    try {
      const queue = pendingBatch(await getOutboxMutations(), session.user.id);
      if (!queue.length) return;
      const res = await apiRequest<{
        results: Array<{ clientMutationId: string; status: string; error?: string }>;
        syncedAt: string;
      }>('/api/sync/batch', {
        method: 'POST',
        body: JSON.stringify({ mutations: queue }),
      });

      const results = validateSyncResults(queue, res);
      let appliedCount = 0;
      let conflictCount = 0;
      for (const item of results) {
        const mut = queue.find((q) => q.clientMutationId === item.clientMutationId);
        if (!mut) continue;
        if (item.status === 'applied' || item.status === 'duplicate') {
          await removeOutboxMutation(mut.id);
          appliedCount++;
        } else {
          conflictCount++;
          await updateOutboxMutation({
            ...mut,
            status: 'conflict',
            retryCount: mut.retryCount + 1,
            errorMessage: item.error || 'Sync conflict',
          });
        }
      }
      setOutbox(await getOutboxMutations());
      setLastSyncedAt(res.syncedAt);
      if (appliedCount > 0) {
        notify(`Synchronized ${appliedCount} offline record(s).`, 'success');
      }
      if (conflictCount > 0) notify(`${conflictCount} offline record(s) need reconciliation. Records retained in the offline queue.`, 'error');
      await refreshBootstrap();
    } catch (err) {
      void appendDiagnosticLog({
        level: 'warn',
        category: 'sync',
        message: err instanceof Error ? err.message : 'Batch sync failed',
      });
    } finally {
      syncLock.current = false;
      setIsSyncing(false);
    }
  }, [isOnline, notify, refreshBootstrap, session]);

  useEffect(() => {
    if (!isOnline || !session || !pendingBatch(outbox, session.user.id).length) return;
    const timer = window.setInterval(() => void syncNow(), 15000);
    return () => window.clearInterval(timer);
  }, [isOnline, outbox, session, syncNow]);

  const completeSignIn = useCallback(
    async (authSession: AuthSession) => {
      setMemoryToken(authSession.token);
      setSession(authSession);
      setIsLocked(false);
      setNeedsSetup(false);
      await refreshBootstrap();
      setActiveModule(authSession.user.preferredLandingModule || settings.defaultLandingModule || 'pos');
    },
    [refreshBootstrap, settings.defaultLandingModule],
  );

  const setupAdmin = async (payload: {
    username: string;
    fullName: string;
    email: string;
    password: string;
    pin: string;
  }) => {
    const dev = getDeviceDescriptor();
    const res = await apiRequest<AuthSession>('/api/auth/setup-admin', {
      method: 'POST',
      body: JSON.stringify({ ...payload, ...dev }),
    });
    await completeSignIn(res);
    notify('Administrator account created and signed in.', 'success');
  };

  const loginWithPassword = async (username: string, password: string) => {
    const dev = getDeviceDescriptor();
    const res = await apiRequest<AuthSession>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password, ...dev }),
    });
    await completeSignIn(res);
    notify(`Signed in as ${res.user.fullName}.`, 'success');
  };

  const loginWithPin = async (userId: string, pin: string) => {
    const dev = getDeviceDescriptor();
    const res = await apiRequest<AuthSession>('/api/auth/pin-login', {
      method: 'POST',
      body: JSON.stringify({ userId, pin, ...dev }),
    });
    await completeSignIn(res);
    notify(`Switched cashier to ${res.user.fullName}.`, 'success');
  };

  const logout = async () => {
    try {
      await apiRequest('/api/auth/logout', { method: 'POST' });
    } catch {
      // Ignore network errors on sign-out
    }
    setMemoryToken('');
    setSession(null);
    setIsLocked(false);
    await refreshAuthStatus();
  };

  const saveSettings = async (next: AppSettings) => {
    if (!session || !['admin','manager'].includes(session.user.role)) {
      throw new Error('Only managers or administrators can save business settings.');
    }
    if (!isOnline) throw new Error('Connect to the store server before saving business settings.');
    const updated = await apiRequest<AppSettings>('/api/settings', {
      method: 'PUT',
      body: JSON.stringify(next),
    });
    setSettingsState(updated);
    notify('Settings saved and synced.', 'success');
  };

  const submitTransaction = async (txn: Transaction) => {
    if (!session || txn.cashierId !== session.user.id) throw new Error("Cashier session required.");
    if (!isOnline) {
      const offlineTxn: Transaction = { ...txn, syncStatus: 'pending_sync' };
      const mut: OfflineMutation = {
        id: txn.clientMutationId,
        ownerId: txn.cashierId,
        clientMutationId: txn.clientMutationId,
        type: 'CREATE_TRANSACTION',
        endpoint: '/api/transactions',
        method: 'POST',
        payload: offlineTxn,
        status: 'pending',
        retryCount: 0,
        createdAt: new Date().toISOString(),
      };
      await enqueueOfflineMutation(mut);
      setOutbox(await getOutboxMutations());
      setTransactions((prev) => [offlineTxn, ...prev]);
      notify('Offline mode: transaction queued safely for sync.', 'info');
      return { transaction: offlineTxn, queuedOffline: true };
    }

    try {
      const res = await apiRequest<{ transaction: Transaction; duplicate: boolean }>(
        '/api/transactions',
        {
          method: 'POST',
          body: JSON.stringify(txn),
        },
      );
      await refreshBootstrap();
      return { transaction: res.transaction, queuedOffline: false };
    } catch (err) {
      // Fallback to offline queue if network drops mid-checkout
      if (err instanceof TypeError) {
        const offlineTxn: Transaction = { ...txn, syncStatus: 'pending_sync' };
        const mut: OfflineMutation = {
          id: txn.clientMutationId,
        ownerId: txn.cashierId,
          clientMutationId: txn.clientMutationId,
          type: 'CREATE_TRANSACTION',
          endpoint: '/api/transactions',
          method: 'POST',
          payload: offlineTxn,
          status: 'pending',
          retryCount: 0,
          createdAt: new Date().toISOString(),
        };
        await enqueueOfflineMutation(mut);
        setOutbox(await getOutboxMutations());
        setTransactions((prev) => [offlineTxn, ...prev]);
        notify('Connection lost: transaction queued for automatic sync.', 'info');
        return { transaction: offlineTxn, queuedOffline: true };
      }
      throw err;
    }
  };

  const deleteHeldOrder = async (id: string) => {
    await apiRequest(`/api/transactions/${id}/held`, { method: 'DELETE' });
    setTransactions((prev) => prev.filter((t) => t.id !== id));
  };

  const voidTransaction = async (id: string, reason: string) => {
    const updated = await apiRequest<Transaction>(`/api/transactions/${id}/void`, {
      method: 'POST',
      body: JSON.stringify({ reason }),
    });
    setTransactions((prev) => prev.map((t) => (t.id === id ? updated : t)));
    await refreshBootstrap();
    notify('Transaction voided.', 'info');
  };

  const refundTransaction = async (id: string, reason: string) => {
    const updated = await apiRequest<Transaction>(`/api/transactions/${id}/refund`, {
      method: 'POST',
      body: JSON.stringify({ reason }),
    });
    await refreshBootstrap();
    setTransactions((prev) => prev.map((t) => (t.id === id ? updated : t)));
    notify('Refund recorded.', 'info');
  };

  const createCategory = async (name: string) => {
    const cat = await apiRequest<ServiceCategory>('/api/categories', {
      method: 'POST',
      body: JSON.stringify({ name, sortOrder: categories.length + 1 }),
    });
    setCategories((prev) => [...prev, cat]);
    notify(`Category "${name}" added.`, 'success');
  };

  const deleteCategory = async (id: string) => {
    await apiRequest(`/api/categories/${id}`, { method: 'DELETE' });
    setCategories((prev) => prev.filter((c) => c.id !== id));
  };

  const saveService = async (service: Partial<PrintService> & { id?: string }) => {
    const saved = service.id
      ? await apiRequest<PrintService>(`/api/services/${service.id}`, {
          method: 'PUT',
          body: JSON.stringify(service),
        })
      : await apiRequest<PrintService>('/api/services', {
          method: 'POST',
          body: JSON.stringify(service),
        });
    await refreshBootstrap();
    notify(`Service "${saved.name}" saved.`, 'success');
    return saved;
  };

  const deleteService = async (id: string) => {
    await apiRequest(`/api/services/${id}`, { method: 'DELETE' });
    setServices((prev) => prev.filter((s) => s.id !== id));
    notify('Service deleted.', 'info');
  };

  const createPrintJob = async (job: Partial<PrintJob>) => {
    const created = await apiRequest<PrintJob>('/api/print-jobs', {
      method: 'POST',
      body: JSON.stringify(job),
    });
    setPrintJobs((prev) => [created, ...prev]);
    notify(`Job Order ${created.jobOrderNumber} created.`, 'success');
    return created;
  };

  const updatePrintJob = async (
    id: string,
    patch: {
      productionStatus?: PrintJob['productionStatus'];
      paymentStatus?: PrintJob['paymentStatus'];
      assignedOperator?: string;
      instructions?: string;
      depositAmount?: number;
      note?: string;
    },
  ) => {
    const updated = await apiRequest<PrintJob>(`/api/print-jobs/${id}`, {
      method: 'PATCH',
      body: JSON.stringify(patch),
    });
    setPrintJobs((prev) => prev.map((j) => (j.id === id ? updated : j)));
    notify('Print job updated.', 'success');
    return updated;
  };

  const loadInventory = useCallback(async (): Promise<InventoryItem[]> => {
    setInventoryLoading(true);
    setInventoryError(null);
    try {
      const data = await apiRequest<{
        items: InventoryItem[];
        stockMovements: StockMovement[];
        supplyCategories?: SupplyCategory[];
        serverTime: string;
      }>('/api/inventory');

      const normalizedItems = (data.items || []).map((item) => normalizeInventoryItem(item));
      const normalizedMovements = (data.stockMovements || []).map((mov) =>
        normalizeStockMovement(mov),
      );
      setInventory(normalizedItems);
      setStockMovements(normalizedMovements);
      if (Array.isArray(data.supplyCategories) && data.supplyCategories.length > 0) {
        setSupplyCategories(data.supplyCategories);
      }
      setInventoryLastLoadedAt(data.serverTime || new Date().toISOString());

      const cached = await loadOfflineSnapshot<BootstrapPayload>('bootstrap');
      if (cached) {
        await saveOfflineSnapshot('bootstrap', {
          ...cached,
          inventory: normalizedItems,
          stockMovements: normalizedMovements,
          supplyCategories: data.supplyCategories ?? cached.supplyCategories,
        });
      }
      return normalizedItems;
    } catch (err) {
      const cached = await loadOfflineSnapshot<BootstrapPayload>('bootstrap');
      if (cached?.inventory) {
        const normalizedItems = cached.inventory.map((item) => normalizeInventoryItem(item));
        const normalizedMovements = (cached.stockMovements || []).map((mov) =>
          normalizeStockMovement(mov),
        );
        setInventory(normalizedItems);
        setStockMovements(normalizedMovements);
        return normalizedItems;
      }
      const message = err instanceof Error ? err.message : 'Unable to load inventory records.';
      setInventoryError(message);
      throw err;
    } finally {
      setInventoryLoading(false);
    }
  }, []);

  const createInventoryItem = async (item: Partial<InventoryItem>): Promise<InventoryItem> => {
    const validation = validateInventoryItemInput(item);
    if (validation.valid === false) {
      throw new Error(validation.error);
    }
    const created = await apiRequest<InventoryItem>('/api/inventory', {
      method: 'POST',
      body: JSON.stringify(item),
    });
    const normalized = normalizeInventoryItem(created);
    setInventory((prev) =>
      [...prev, normalized].sort((a, b) => a.name.localeCompare(b.name)),
    );
    if (normalized.quantityOnHand > 0) {
      await loadInventory();
    }
    notify(`Added "${normalized.name}" to inventory.`, 'success');
    return normalized;
  };

  const updateInventoryItem = async (
    id: string,
    patch: Partial<InventoryItem>,
  ): Promise<InventoryItem> => {
    const updated = await apiRequest<InventoryItem>(`/api/inventory/${id}`, {
      method: 'PATCH',
      body: JSON.stringify(patch),
    });
    const normalized = normalizeInventoryItem(updated);
    setInventory((prev) => prev.map((item) => (item.id === id ? normalized : item)));
    notify(`Updated "${normalized.name}".`, 'success');
    return normalized;
  };

  const bulkUpdateInventoryItems = async (
    payload: BulkInventoryUpdatePayload,
  ): Promise<InventoryItem[]> => {
    const res = await apiRequest<{ items: InventoryItem[]; updatedCount: number }>(
      '/api/inventory/bulk',
      {
        method: 'PATCH',
        body: JSON.stringify(payload),
      },
    );
    const normalizedList = (res.items || []).map((item) => normalizeInventoryItem(item));
    const updatedMap = new Map(normalizedList.map((item) => [item.id, item]));
    const nextItems = inventory.map((item) => updatedMap.get(item.id) ?? item);
    setInventory(nextItems);

    const cached = await loadOfflineSnapshot<BootstrapPayload>('bootstrap');
    if (cached) {
      await saveOfflineSnapshot('bootstrap', {
        ...cached,
        inventory: nextItems,
      });
    }

    notify(`Updated ${normalizedList.length} inventory item(s).`, 'success');
    return normalizedList;
  };

  const deleteInventoryItem = async (id: string): Promise<void> => {
    const target = inventory.find((item) => item.id === id);
    await apiRequest<{ ok: boolean; id: string }>(`/api/inventory/${id}`, {
      method: 'DELETE',
    });

    const nextItems = inventory.filter((item) => item.id !== id);
    const nextMovements = stockMovements.filter((mov) => mov.inventoryItemId !== id);
    setInventory(nextItems);
    setStockMovements(nextMovements);

    const cached = await loadOfflineSnapshot<BootstrapPayload>('bootstrap');
    if (cached) {
      await saveOfflineSnapshot('bootstrap', {
        ...cached,
        inventory: nextItems,
        stockMovements: nextMovements,
      });
    }

    notify(
      target ? `Deleted "${target.name}" from inventory.` : 'Inventory item deleted.',
      'info',
    );
  };

  const trackStockChange = async (
    id: string,
    payload: StockAdjustmentPayload,
  ): Promise<StockMovement> => {
    const validation = validateStockMovementInput({
      movementType: payload.movementType,
      quantityDelta: payload.quantityDelta,
    });
    if (validation.valid === false) {
      throw new Error(validation.error);
    }

    validatePurchaseCost(payload.purchaseUnitCost,payload.movementType,session?.user.role || '');
    const mutationId = `inv_adj_${crypto.randomUUID()}`;
    const applyOptimisticOffline = async (): Promise<StockMovement> => {
      if (!session) throw new Error("Cashier session required.");
      const target = inventory.find((item) => item.id === id);
      if (!target) {
        throw new Error('Inventory item not found.');
      }
      const signedDelta = computeSignedStockDelta(
        payload.movementType,
        payload.quantityDelta,
      );
      const qtyBefore = Number(target.quantityOnHand) || 0;
      const nextQty = Number((qtyBefore + signedDelta).toFixed(2));
      if (nextQty < 0 || (signedDelta < 0 && nextQty < (target.reservedQuantity ?? 0))) throw new Error("Insufficient unreserved stock.");
      const now = new Date().toISOString();
      const occurredAt = payload.occurredAt || now;
      const restockedAt =
        signedDelta > 0 &&
        (payload.movementType === 'purchase' || payload.movementType === 'return')
          ? occurredAt
          : target.lastRestockedAt ?? null;

      const updatedItem = normalizeInventoryItem({
        ...target,
        quantityOnHand: nextQty,
        unitCost: weightedPurchaseCost(qtyBefore,target.unitCost,signedDelta,payload.purchaseUnitCost),
        lastRestockedAt: restockedAt,
        updatedAt: now,
      });
      const movement = normalizeStockMovement({
        id: `mov_off_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`,
        inventoryItemId: target.id,
        itemName: target.name,
        movementType: payload.movementType,
        quantityBefore: qtyBefore,
        quantityDelta: signedDelta,
        balanceAfter: nextQty,
        unitCostAtMovement: payload.purchaseUnitCost ?? target.unitCost,
        purchaseUnitCost: payload.purchaseUnitCost,
        referenceType: payload.referenceType || inferReferenceType(payload.movementType),
        reference: payload.reference || '',
        performedById: session?.user.id || '',
        performedBy: session?.user.fullName || 'Offline Cashier',
        note: payload.note || '',
        occurredAt,
        createdAt: now,
      });

      const mut: OfflineMutation = {
        id: mutationId,
        ownerId: session.user.id,
        clientMutationId: mutationId,
        type: 'ADJUST_INVENTORY',
        endpoint: `/api/inventory/${id}/adjust`,
        method: 'POST',
        payload: { inventoryItemId: id, ...payload },
        status: 'pending',
        retryCount: 0,
        createdAt: now,
      };
      await enqueueOfflineMutation(mut);
      setOutbox(await getOutboxMutations());
      setInventory((prev) => prev.map((item) => (item.id === id ? updatedItem : item)));
      setStockMovements((prev) => [movement, ...prev]);
      notify('Offline mode: stock adjustment queued for sync.', 'info');
      return movement;
    };

    if (!isOnline) {
      return applyOptimisticOffline();
    }

    try {
      const res = await apiRequest<{
        id: string;
        quantityOnHand: number;
        lastRestockedAt: string | null;
        updatedAt: string;
        item?: InventoryItem;
        movement: StockMovement;
      }>(`/api/inventory/${id}/adjust`, {
        method: 'POST',
        body: JSON.stringify({...payload, clientMutationId: mutationId}),
      });

      const normalizedMovement = normalizeStockMovement(res.movement);
      setInventory((prev) =>
        prev.map((item) => {
          if (item.id !== id) return item;
          if (res.item) return normalizeInventoryItem(res.item);
          return normalizeInventoryItem({
            ...item,
            quantityOnHand: res.quantityOnHand,
            lastRestockedAt: res.lastRestockedAt,
            updatedAt: res.updatedAt,
          });
        }),
      );
      setStockMovements((prev) => [normalizedMovement, ...prev]);
      notify('Stock level updated.', 'success');
      return normalizedMovement;
    } catch (err) {
      if (err instanceof TypeError) {
        return applyOptimisticOffline();
      }
      throw err;
    }
  };

  const adjustInventory = trackStockChange;

  const getInventoryItemById = useCallback(
    (id: string) => inventory.find((item) => item.id === id),
    [inventory],
  );

  const getItemStockMovements = useCallback(
    (inventoryItemId: string) =>
      stockMovements.filter((mov) => mov.inventoryItemId === inventoryItemId),
    [stockMovements],
  );

  const createCustomer = async (customer: Partial<Customer>) => {
    const created = await apiRequest<Customer>('/api/customers', {
      method: 'POST',
      body: JSON.stringify(customer),
    });
    setCustomers((prev) => [...prev, created]);
    notify(`Customer "${created.name}" added.`, 'success');
    return created;
  };

  const collectCustomerBalance = async (id: string, amount: number) => {
    await apiRequest(`/api/customers/${id}/pay-balance`, {
      method: 'POST',
      body: JSON.stringify({ amount }),
    });
    await refreshBootstrap();
    notify('Customer balance payment recorded.', 'success');
  };

  const createExpense = async (expense: Partial<ExpenseRecord>) => {
    const created = await apiRequest<ExpenseRecord>('/api/expenses', {
      method: 'POST',
      body: JSON.stringify(expense),
    });
    setExpenses((prev) => [created, ...prev]);
    notify('Expense recorded.', 'success');
  };

  const deleteExpense = async (id: string) => {
    await apiRequest(`/api/expenses/${id}`, { method: 'DELETE' });
    setExpenses((prev) => prev.filter((e) => e.id !== id));
    notify('Expense removed.', 'info');
  };

  const startShift = async (openingCash: number) => {
    await apiRequest('/api/shifts/start', {
      method: 'POST',
      body: JSON.stringify({ openingCash }),
    });
    await refreshBootstrap();
    notify('Cashier shift started.', 'success');
  };

  const recordCashMovement = async (
    shiftId: string,
    type: 'cash_in' | 'cash_out',
    amount: number,
    reason: string,
  ) => {
    await apiRequest(`/api/shifts/${shiftId}/movement`, {
      method: 'POST',
      body: JSON.stringify({ type, amount, reason }),
    });
    await refreshBootstrap();
    notify(`Recorded ${type === 'cash_in' ? 'Cash In' : 'Cash Out'}.`, 'success');
  };

  const closeShift = async (shiftId: string, actualCash: number, reconciliationNote: string) => {
    await apiRequest(`/api/shifts/${shiftId}/close`, {
      method: 'POST',
      body: JSON.stringify({ actualCash, reconciliationNote }),
    });
    await refreshBootstrap();
    notify('Shift closed and reconciled.', 'success');
  };

  const seedDemo = async () => {
    await apiRequest('/api/demo/seed', { method: 'POST' });
    await refreshBootstrap();
    notify('Demonstration catalog and sample records loaded.', 'success');
  };

  const clearDemo = async () => {
    await apiRequest('/api/demo/clear', { method: 'POST' });
    await refreshBootstrap();
    notify('Demonstration records removed.', 'info');
  };

  const activeShift = useMemo(() => {
    if (!session) return null;
    return (
      shifts.find((s) => s.status === 'open' && s.cashierId === session.user.id) ||
      null
    );
  }, [session, shifts]);

  const inventoryState = useMemo<InventoryStateSlice>(
    () => ({
      items: inventory,
      stockMovements,
      supplyCategories,
      lowStockItems: inventory.filter((item) => isLowStockItem(item)),
      totalValuation: calculateInventoryValuation(inventory),
      loading: inventoryLoading,
      error: inventoryError,
      lastLoadedAt: inventoryLastLoadedAt,
    }),
    [
      inventory,
      inventoryError,
      inventoryLastLoadedAt,
      inventoryLoading,
      stockMovements,
      supplyCategories,
    ],
  );

  const value: AppContextValue = {
    authChecked,
    needsSetup,
    quickUsers,
    session,
    isLocked,
    setIsLocked,
    activeModule,
    setActiveModule,
    platformPreview,
    setPlatformPreview,
    fullScreenCashier,
    setFullScreenCashier,
    isOnline,
    isSyncing,
    pendingSyncCount: outbox.filter(m => session && mutationOwner(m) === session.user.id).length,
    lastSyncedAt,
    outbox: outbox.filter(m => session && mutationOwner(m) === session.user.id),
    syncNow,
    loadingData,
    dataError,
    settings,
    categories,
    supplyCategories,
    services,
    inventory,
    inventoryState,
    stockMovements,
    customers,
    transactions: [...transactions, ...outbox.filter(m => session && mutationOwner(m) === session.user.id && m.type === "CREATE_TRANSACTION" && !transactions.some(t => t.clientMutationId === m.clientMutationId)).map(m => ({...(m.payload as Transaction), syncStatus: (m.status === "conflict" || m.status === "failed" ? "conflict" : "pending_sync") as Transaction["syncStatus"]}))],
    printJobs,
    expenses,
    shifts,
    activeShift,
    users,
    auditLogs,
    toasts,
    notify,
    dismissToast,
    refreshAuthStatus,
    setupAdmin,
    loginWithPassword,
    loginWithPin,
    logout,
    refreshBootstrap,
    saveSettings,
    setThemeMode,
    setDensity,
    setSidebarCollapsed,
    updateBranding,
    updateTokens,
    resetSettingsToDefaults,
    submitTransaction,
    deleteHeldOrder,
    voidTransaction,
    refundTransaction,
    createCategory,
    deleteCategory,
    saveService,
    deleteService,
    createPrintJob,
    updatePrintJob,
    loadInventory,
    createInventoryItem,
    updateInventoryItem,
    bulkUpdateInventoryItems,
    deleteInventoryItem,
    trackStockChange,
    adjustInventory,
    getInventoryItemById,
    getItemStockMovements,
    createCustomer,
    collectCustomerBalance,
    createExpense,
    deleteExpense,
    startShift,
    recordCashMovement,
    closeShift,
    seedDemo,
    clearDemo,
  };

  return <AppContext.Provider value={value}>{children}</AppContext.Provider>;
};
