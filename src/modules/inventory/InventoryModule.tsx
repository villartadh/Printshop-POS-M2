import React, { useMemo, useState } from 'react';
import { AlertTriangle, ArrowUpDown, Edit2, Plus, RefreshCw, Search, Trash2, X } from 'lucide-react';
import { useApp } from '../../context/AppContext.tsx';
import { formatCurrency } from '../../shared/pricingEngine.ts';
import {
  extractSupplierInfo,
  filterInventoryItems,
  getAvailableStock,
  getInventoryStockStatus,
  getSuggestedRestockQuantity,
  STOCK_MOVEMENT_LABELS,
} from '../../shared/inventoryModel.ts';
import type {
  InventoryItem,
  InventoryItemType,
  InventorySortDirection,
  InventorySortField,
  StockMovementType,
  StockStatus,
} from '../../types/inventory.ts';
import { Button, Card, Input, Select, StatusIndicator } from '../../components/ui/Primitives.tsx';
import { DataTable } from '../../components/ui/DataTable.tsx';
import { LoadingState, Modal } from '../../components/ui/Overlays.tsx';
import {
  InventoryItemDialog,
  type InventoryItemFormValues,
} from '../../components/inventory/InventoryItemDialog.tsx';
import { InventoryTable } from '../../components/inventory/InventoryTable.tsx';

type InventoryTab = 'items' | 'low_stock' | 'movements';
type InventorySortKey = Extract<
  InventorySortField,
  'name' | 'quantityOnHand' | 'reorderLevel' | 'unitCost' | 'status'
>;

const STOCK_STATUS_RANK: Record<StockStatus, number> = {
  out_of_stock: 0,
  low_stock: 1,
  in_stock: 2,
};

export const InventoryModule: React.FC = () => {
  const {
    session,
    settings,
    inventoryState,
    loadInventory,
    createInventoryItem,
    updateInventoryItem,
    bulkUpdateInventoryItems,
    deleteInventoryItem,
    trackStockChange,
    getInventoryItemById,
    getItemStockMovements,
    notify,
  } = useApp();

  const {
    items: inventory,
    stockMovements,
    supplyCategories,
    lowStockItems: lowStock,
    totalValuation,
    loading: loadingInventory,
    error: inventoryError,
    lastLoadedAt,
  } = inventoryState;

  const taxCurr = settings.taxAndCurrency;
  const canEditCatalog =
    session?.user.role === 'admin' || session?.user.role === 'manager';

  // View & Filter state
  const [tab, setTab] = useState<InventoryTab>('items');
  const [search, setSearch] = useState('');
  const [categoryFilter, setCategoryFilter] = useState<string>('all');
  const [statusFilter, setStatusFilter] = useState<StockStatus | 'all'>('all');
  const [typeFilter, setTypeFilter] = useState<InventoryItemType | 'all'>('all');
  const [sortKey, setSortKey] = useState<InventorySortKey>('name');
  const [sortDir, setSortDir] = useState<InventorySortDirection>('asc');

  // Modals
  const [itemModalOpen, setItemModalOpen] = useState(false);
  const [editingItem, setEditingItem] = useState<InventoryItem | null>(null);
  const [adjustTarget, setAdjustTarget] = useState<InventoryItem | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<InventoryItem | null>(null);
  const [detailItemId, setDetailItemId] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [deleting, setDeleting] = useState(false);

  // Stock adjustment form
  const [movementType, setMovementType] = useState<StockMovementType>('purchase');
  const [delta, setDelta] = useState('50');
  const [reference, setReference] = useState('');
  const [note, setNote] = useState('');

  const outOfStockCount = useMemo(
    () => inventory.filter((i) => getInventoryStockStatus(i) === 'out_of_stock').length,
    [inventory],
  );
  const consumableCount = useMemo(
    () => inventory.filter((i) => i.itemType === 'consumable').length,
    [inventory],
  );
  const finishedCount = inventory.length - consumableCount;

  const hasActiveFilters =
    search.trim().length > 0 ||
    categoryFilter !== 'all' ||
    statusFilter !== 'all' ||
    typeFilter !== 'all';

  const clearFilters = () => {
    setSearch('');
    setCategoryFilter('all');
    setStatusFilter('all');
    setTypeFilter('all');
  };

  const handleSortChange = (key: string) => {
    const typedKey = key as InventorySortKey;
    if (sortKey === typedKey) {
      setSortDir((prev) => (prev === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortKey(typedKey);
      setSortDir(typedKey === 'quantityOnHand' || typedKey === 'unitCost' ? 'desc' : 'asc');
    }
  };

  const visibleItems = useMemo(() => {
    const base = tab === 'low_stock' ? lowStock : inventory;
    const filtered = filterInventoryItems(
      base,
      {
        search,
        categoryId: categoryFilter,
        stockStatus: tab === 'low_stock' ? 'all' : statusFilter,
        itemType: typeFilter,
      },
      supplyCategories,
    );

    const dir = sortDir === 'asc' ? 1 : -1;
    return [...filtered].sort((a, b) => {
      switch (sortKey) {
        case 'quantityOnHand':
          return (a.quantityOnHand - b.quantityOnHand) * dir;
        case 'reorderLevel':
          return (a.reorderLevel - b.reorderLevel) * dir;
        case 'unitCost':
          return (a.unitCost - b.unitCost) * dir;
        case 'status': {
          const sa = STOCK_STATUS_RANK[getInventoryStockStatus(a)];
          const sb = STOCK_STATUS_RANK[getInventoryStockStatus(b)];
          if (sa !== sb) return (sa - sb) * dir;
          return a.name.localeCompare(b.name) * dir;
        }
        case 'name':
        default:
          return a.name.localeCompare(b.name) * dir;
      }
    });
  }, [
    tab,
    lowStock,
    inventory,
    search,
    categoryFilter,
    statusFilter,
    typeFilter,
    supplyCategories,
    sortKey,
    sortDir,
  ]);

  const filteredMovements = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return stockMovements;
    return stockMovements.filter(
      (m) =>
        m.itemName.toLowerCase().includes(q) ||
        m.performedBy.toLowerCase().includes(q) ||
        (m.reference || '').toLowerCase().includes(q) ||
        (m.note || '').toLowerCase().includes(q),
    );
  }, [stockMovements, search]);

  const detailItem = detailItemId ? getInventoryItemById(detailItemId) : undefined;
  const detailMovements = useMemo(
    () => (detailItemId ? getItemStockMovements(detailItemId) : []),
    [detailItemId, getItemStockMovements],
  );

  const openNewItemModal = () => {
    setEditingItem(null);
    setItemModalOpen(true);
  };

  const openEditItemModal = (item: InventoryItem) => {
    setEditingItem(item);
    setItemModalOpen(true);
  };

  const openAdjustModal = (item: InventoryItem) => {
    const suggested = getSuggestedRestockQuantity(item);
    setAdjustTarget(item);
    setMovementType('purchase');
    setDelta(String(suggested > 0 ? suggested : 50));
    setReference('');
    setNote('');
  };

  const handleSaveItem = async (
    values: InventoryItemFormValues,
    existing?: InventoryItem | null,
  ) => {
    if (existing) {
      await updateInventoryItem(existing.id, {
        name: values.name,
        sku: values.sku,
        barcode: values.barcode,
        categoryId: values.categoryId,
        category: values.category,
        itemType: values.itemType,
        unit: values.unit,
        reorderLevel: values.reorderLevel,
        reorderQuantity: values.reorderQuantity,
        unitCost: values.unitCost,
        supplier: values.supplier,
        supplierContact: values.supplierContact,
        supplierSku: values.supplierSku,
        leadTimeDays: values.leadTimeDays,
        supplierInfo: values.supplierInfo,
        storageLocation: values.storageLocation,
      });

      const stockDiff = Number((values.quantityOnHand - existing.quantityOnHand).toFixed(2));
      if (stockDiff !== 0) {
        await trackStockChange(existing.id, {
          movementType: 'adjustment',
          quantityDelta: stockDiff,
          reference: 'ITEM-EDIT',
          note: 'Stock count updated via Inventory Item editor',
        });
      }
    } else {
      await createInventoryItem(values);
    }
    setEditingItem(null);
  };

  const handleAdjust = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!adjustTarget) return;
    setSubmitting(true);
    try {
      await trackStockChange(adjustTarget.id, {
        movementType,
        quantityDelta: Number(delta) || 0,
        reference,
        note,
      });
      setAdjustTarget(null);
      setReference('');
      setNote('');
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Unable to update stock level.', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const handleConfirmDelete = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      const removedId = deleteTarget.id;
      await deleteInventoryItem(removedId);
      if (detailItemId === removedId) {
        setDetailItemId(null);
      }
      setDeleteTarget(null);
    } catch (err) {
      notify(err instanceof Error ? err.message : 'Unable to delete inventory item.', 'error');
    } finally {
      setDeleting(false);
    }
  };

  return (
    <div className="space-y-4">
      {/* Header Zone */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-xl font-bold text-[var(--pos-text)] tracking-tight">
            Inventory Management
          </h2>
          <p className="text-xs text-[var(--pos-text-muted)] mt-0.5">
            Monitor live stock counts, reorder thresholds, supplier records, and usage movements
          </p>
        </div>
        <div className="flex items-center gap-2 shrink-0">
          <Button
            variant="outline"
            size="sm"
            className="whitespace-nowrap shrink-0"
            disabled={loadingInventory}
            onClick={() => void loadInventory()}
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loadingInventory ? 'animate-spin' : ''}`} />
            {loadingInventory ? 'Refreshing...' : 'Refresh'}
          </Button>
          {canEditCatalog && (
            <Button size="sm" className="whitespace-nowrap shrink-0" onClick={openNewItemModal}>
              <Plus className="w-4 h-4" />
              Add Material / Product
            </Button>
          )}
        </div>
      </div>

      {inventoryError && (
        <div
          style={{ borderRadius: 'var(--pos-radius)' }}
          className="px-3.5 py-2.5 text-xs font-medium text-[var(--pos-error)] bg-[var(--pos-error)]/10 border border-[var(--pos-error)]/25 flex items-center justify-between gap-3"
        >
          <span>{inventoryError}</span>
          <Button variant="outline" size="sm" onClick={() => void loadInventory()}>
            Retry
          </Button>
        </div>
      )}

      {/* KPI Strip */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
        <Card>
          <div className="text-xs font-medium text-[var(--pos-text-muted)]">Total Tracked SKUs</div>
          <div className="text-2xl font-bold font-mono-num text-[var(--pos-text)] mt-1">
            {inventory.length}
          </div>
          <div className="text-[11px] text-[var(--pos-text-muted)] mt-1 font-mono-num">
            {consumableCount} consumables · {finishedCount} finished
          </div>
        </Card>
        <Card>
          <div className="text-xs font-medium text-[var(--pos-text-muted)]">
            Low & Out-of-Stock Alerts
          </div>
          <div
            className={`text-2xl font-bold font-mono-num mt-1 ${
              outOfStockCount > 0
                ? 'text-[var(--pos-error)]'
                : lowStock.length > 0
                  ? 'text-[var(--pos-warning)]'
                  : 'text-[var(--pos-success)]'
            }`}
          >
            {lowStock.length}
          </div>
          <div className="text-[11px] text-[var(--pos-text-muted)] mt-1 font-mono-num">
            {outOfStockCount} out of stock · {Math.max(0, lowStock.length - outOfStockCount)} at reorder
          </div>
        </Card>
        <Card>
          <div className="text-xs font-medium text-[var(--pos-text-muted)]">Inventory Valuation</div>
          <div className="text-2xl font-bold font-mono-num text-[var(--pos-text)] mt-1">
            {formatCurrency(totalValuation, taxCurr)}
          </div>
          <div className="text-[11px] text-[var(--pos-text-muted)] mt-1">
            {lastLoadedAt
              ? `Synced ${new Date(lastLoadedAt).toLocaleTimeString([], {
                  hour: '2-digit',
                  minute: '2-digit',
                })}`
              : 'Cost basis across on-hand stock'}
          </div>
        </Card>
      </div>

      {/* View Switcher */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div
          role="tablist"
          aria-label="Inventory views"
          style={{ borderRadius: 'var(--pos-radius)' }}
          className="inline-flex items-center gap-1 p-1 bg-[var(--pos-bg)] border border-[var(--pos-border)]"
        >
          <button
            type="button"
            role="tab"
            aria-selected={tab === 'items'}
            onClick={() => setTab('items')}
            className={`px-3 py-1.5 text-xs font-medium rounded-md cursor-pointer whitespace-nowrap shrink-0 transition-colors ${
              tab === 'items'
                ? 'bg-[var(--pos-surface)] text-[var(--pos-text)] shadow-xs border border-[var(--pos-border)]'
                : 'text-[var(--pos-text-muted)] hover:text-[var(--pos-text)]'
            }`}
          >
            All Inventory ({inventory.length})
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={tab === 'low_stock'}
            onClick={() => setTab('low_stock')}
            className={`px-3 py-1.5 text-xs font-medium rounded-md cursor-pointer whitespace-nowrap shrink-0 transition-colors ${
              tab === 'low_stock'
                ? 'bg-[var(--pos-surface)] text-[var(--pos-text)] shadow-xs border border-[var(--pos-border)]'
                : 'text-[var(--pos-text-muted)] hover:text-[var(--pos-text)]'
            }`}
          >
            Reorder Alerts ({lowStock.length})
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={tab === 'movements'}
            onClick={() => setTab('movements')}
            className={`px-3 py-1.5 text-xs font-medium rounded-md cursor-pointer whitespace-nowrap shrink-0 transition-colors ${
              tab === 'movements'
                ? 'bg-[var(--pos-surface)] text-[var(--pos-text)] shadow-xs border border-[var(--pos-border)]'
                : 'text-[var(--pos-text-muted)] hover:text-[var(--pos-text)]'
            }`}
          >
            Stock Movements ({stockMovements.length})
          </button>
        </div>

        {tab === 'movements' ? (
          <div className="relative w-full sm:w-64">
            <Input
              aria-label="Search stock movements"
              leftIcon={<Search className="w-3.5 h-3.5" />}
              placeholder="Search material, ref, staff..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className={search ? 'pr-8' : ''}
            />
            {search && (
              <button
                type="button"
                onClick={() => setSearch('')}
                aria-label="Clear search"
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-[var(--pos-text-muted)] hover:text-[var(--pos-text)] cursor-pointer p-0.5"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            )}
          </div>
        ) : (
          <span className="text-xs text-[var(--pos-text-muted)]">
            Click any row to inspect supplier details & movement history
          </span>
        )}
      </div>

      {/* Main Content Area */}
      {loadingInventory && inventory.length === 0 ? (
        <LoadingState rows={6} />
      ) : tab === 'movements' ? (
        <DataTable
          rows={filteredMovements}
          rowKey={(m) => m.id}
          emptyTitle={search ? 'No matching stock movements' : 'No stock movements logged yet'}
          emptyDescription={
            search
              ? 'Try clearing your search query to view all stock history.'
              : 'Restocks, print job deductions, and manual stock adjustments appear here.'
          }
          emptyActionLabel={search ? 'Clear Search' : undefined}
          onEmptyAction={search ? () => setSearch('') : undefined}
          columns={[
            {
              key: 'item',
              label: 'Material & Reference',
              render: (m) => (
                <div>
                  <div className="text-sm font-medium text-[var(--pos-text)]">{m.itemName}</div>
                  <div className="text-[11px] text-[var(--pos-text-muted)] mt-0.5">
                    {m.performedBy}
                    {m.reference ? ` · Ref: ${m.reference}` : ''}
                    {m.note ? ` · ${m.note}` : ''}
                  </div>
                </div>
              ),
            },
            {
              key: 'type',
              label: 'Movement Type',
              render: (m) => (
                <div>
                  <div className="text-xs font-medium text-[var(--pos-text)]">
                    {STOCK_MOVEMENT_LABELS[m.movementType] || m.movementType}
                  </div>
                  <div className="text-[11px] text-[var(--pos-text-muted)] mt-0.5 font-mono-num">
                    {new Date(m.occurredAt || m.createdAt).toLocaleString()}
                  </div>
                </div>
              ),
            },
            {
              key: 'delta',
              label: 'Quantity Change',
              align: 'right',
              render: (m) => (
                <span
                  className={`font-mono-num font-semibold ${
                    m.quantityDelta >= 0
                      ? 'text-[var(--pos-success)]'
                      : 'text-[var(--pos-error)]'
                  }`}
                >
                  {m.quantityDelta >= 0 ? `+${m.quantityDelta}` : m.quantityDelta}
                </span>
              ),
            },
            {
              key: 'after',
              label: 'Before → After',
              align: 'right',
              render: (m) => (
                <span className="font-mono-num text-xs text-[var(--pos-text-muted)]">
                  {m.quantityBefore !== undefined ? `${m.quantityBefore} → ` : ''}
                  <strong className="font-semibold text-[var(--pos-text)]">{m.balanceAfter}</strong>
                </span>
              ),
            },
          ]}
        />
      ) : (
        <InventoryTable
          items={visibleItems}
          supplyCategories={supplyCategories}
          taxAndCurrency={taxCurr}
          canEdit={canEditCatalog}
          onEdit={openEditItemModal}
          onDelete={(item) => setDeleteTarget(item)}
          onAdjustStock={openAdjustModal}
          onBulkUpdate={bulkUpdateInventoryItems}
          onRowClick={(item) => setDetailItemId(item.id)}
          sortKey={sortKey}
          sortDir={sortDir}
          onSortChange={handleSortChange}
          searchQuery={search}
          onSearchChange={setSearch}
          categoryFilter={categoryFilter}
          onCategoryChange={setCategoryFilter}
          totalCount={tab === 'low_stock' ? lowStock.length : inventory.length}
          toolbarExtras={
            <>
              {tab === 'items' && (
                <div className="w-1/2 sm:w-36 flex-1 sm:flex-initial">
                  <Select
                    aria-label="Filter by stock status"
                    value={statusFilter}
                    onChange={(e) => setStatusFilter(e.target.value as StockStatus | 'all')}
                    options={[
                      { value: 'all', label: 'All Statuses' },
                      { value: 'in_stock', label: 'In Stock' },
                      { value: 'low_stock', label: 'Low Stock' },
                      { value: 'out_of_stock', label: 'Out of Stock' },
                    ]}
                  />
                </div>
              )}
              <div className="w-1/2 sm:w-36 flex-1 sm:flex-initial">
                <Select
                  aria-label="Filter by item type"
                  value={typeFilter}
                  onChange={(e) => setTypeFilter(e.target.value as InventoryItemType | 'all')}
                  options={[
                    { value: 'all', label: 'All Types' },
                    { value: 'consumable', label: 'Consumables' },
                    { value: 'finished_product', label: 'Finished Goods' },
                  ]}
                />
              </div>
              {hasActiveFilters && (
                <Button variant="ghost" size="sm" onClick={clearFilters}>
                  Reset
                </Button>
              )}
            </>
          }
          emptyTitle={
            hasActiveFilters
              ? 'No inventory items match your search'
              : tab === 'low_stock'
                ? 'All stock levels are above reorder thresholds'
                : 'No inventory items recorded yet'
          }
          emptyDescription={
            hasActiveFilters
              ? 'Adjust your search terms or reset category and status filters.'
              : tab === 'low_stock'
                ? 'Items that drop to or below their reorder level will automatically appear here.'
                : 'Add paper stock, inks, tarpaulin rolls, or finished supplies to start tracking counts.'
          }
          emptyActionLabel={
            hasActiveFilters
              ? 'Clear Filters'
              : tab === 'items' && canEditCatalog
                ? 'Add First Inventory Item'
                : undefined
          }
          onEmptyAction={
            hasActiveFilters
              ? clearFilters
              : tab === 'items' && canEditCatalog
                ? openNewItemModal
                : undefined
          }
        />
      )}

      {/* Item Stock Details & Movement History Modal */}
      <Modal
        open={Boolean(detailItem)}
        onClose={() => setDetailItemId(null)}
        title={detailItem ? detailItem.name : ''}
        subtitle={
          detailItem
            ? `${detailItem.sku} · ${detailItem.category}${
                detailItem.storageLocation ? ` · ${detailItem.storageLocation}` : ''
              }`
            : ''
        }
        maxWidth="lg"
        footer={
          detailItem ? (
            <>
              {canEditCatalog && (
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => {
                    const current = detailItem;
                    setDetailItemId(null);
                    openEditItemModal(current);
                  }}
                >
                  <Edit2 className="w-3.5 h-3.5" />
                  Edit Item
                </Button>
              )}
              <Button
                size="sm"
                onClick={() => {
                  const current = detailItem;
                  setDetailItemId(null);
                  openAdjustModal(current);
                }}
              >
                <ArrowUpDown className="w-3.5 h-3.5" />
                Adjust Stock
              </Button>
            </>
          ) : undefined
        }
      >
        {detailItem && (() => {
          const sup = extractSupplierInfo(detailItem);
          const status = getInventoryStockStatus(detailItem);
          const avail = getAvailableStock(detailItem);
          const suggested = getSuggestedRestockQuantity(detailItem);

          return (
            <div className="space-y-4">
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                <div
                  style={{ borderRadius: 'var(--pos-radius)' }}
                  className="p-3 border border-[var(--pos-border)] bg-[var(--pos-bg)]"
                >
                  <div className="text-[11px] text-[var(--pos-text-muted)]">On Hand</div>
                  <div className="text-lg font-bold font-mono-num text-[var(--pos-text)] mt-0.5">
                    {detailItem.quantityOnHand} {detailItem.unit}
                  </div>
                </div>
                <div
                  style={{ borderRadius: 'var(--pos-radius)' }}
                  className="p-3 border border-[var(--pos-border)] bg-[var(--pos-bg)]"
                >
                  <div className="text-[11px] text-[var(--pos-text-muted)]">Available</div>
                  <div className="text-lg font-bold font-mono-num text-[var(--pos-text)] mt-0.5">
                    {avail} {detailItem.unit}
                  </div>
                </div>
                <div
                  style={{ borderRadius: 'var(--pos-radius)' }}
                  className="p-3 border border-[var(--pos-border)] bg-[var(--pos-bg)]"
                >
                  <div className="text-[11px] text-[var(--pos-text-muted)]">Reorder Level</div>
                  <div className="text-lg font-bold font-mono-num text-[var(--pos-text)] mt-0.5">
                    {detailItem.reorderLevel} {detailItem.unit}
                  </div>
                </div>
                <div
                  style={{ borderRadius: 'var(--pos-radius)' }}
                  className="p-3 border border-[var(--pos-border)] bg-[var(--pos-bg)]"
                >
                  <div className="text-[11px] text-[var(--pos-text-muted)]">Status</div>
                  <div className="mt-1.5">
                    <StatusIndicator
                      tone={
                        status === 'out_of_stock'
                          ? 'error'
                          : status === 'low_stock'
                            ? 'warning'
                            : 'success'
                      }
                      label={
                        status === 'out_of_stock'
                          ? 'Out of Stock'
                          : status === 'low_stock'
                            ? 'Low Stock'
                            : 'In Stock'
                      }
                    />
                  </div>
                </div>
              </div>

              <div
                style={{ borderRadius: 'var(--pos-radius)' }}
                className="p-3.5 border border-[var(--pos-border)] grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs"
              >
                <div>
                  <div className="text-[var(--pos-text-muted)]">Supplier</div>
                  <div className="font-medium text-[var(--pos-text)] mt-0.5">
                    {sup.name || 'No supplier set'}
                  </div>
                  {sup.contact && (
                    <div className="text-[var(--pos-text-muted)] mt-0.5">{sup.contact}</div>
                  )}
                </div>
                <div>
                  <div className="text-[var(--pos-text-muted)]">Unit Cost & Valuation</div>
                  <div className="font-mono-num font-medium text-[var(--pos-text)] mt-0.5">
                    {formatCurrency(detailItem.unitCost, taxCurr)} / {detailItem.unit}
                  </div>
                  <div className="font-mono-num text-[var(--pos-text-muted)] mt-0.5">
                    Total:{' '}
                    {formatCurrency(detailItem.quantityOnHand * detailItem.unitCost, taxCurr)}
                  </div>
                </div>
                <div>
                  <div className="text-[var(--pos-text-muted)]">Restock Guidance</div>
                  <div className="font-mono-num font-medium text-[var(--pos-text)] mt-0.5">
                    {suggested > 0
                      ? `Suggested +${suggested} ${detailItem.unit}`
                      : `Standard order ${detailItem.reorderQuantity || 0} ${detailItem.unit}`}
                  </div>
                  <div className="text-[var(--pos-text-muted)] mt-0.5">
                    {detailItem.lastRestockedAt
                      ? `Last restocked ${new Date(detailItem.lastRestockedAt).toLocaleDateString()}`
                      : 'No restock recorded'}
                  </div>
                </div>
              </div>

              <div>
                <div className="text-xs font-semibold text-[var(--pos-text)] mb-2">
                  Recent Stock Movements ({detailMovements.length})
                </div>
                <DataTable
                  rows={detailMovements.slice(0, 12)}
                  rowKey={(m) => m.id}
                  emptyTitle="No movement history for this item"
                  emptyDescription="Restocks, print job deductions, and manual counts will appear here."
                  columns={[
                    {
                      key: 'type',
                      label: 'Movement',
                      render: (m) => (
                        <div>
                          <div className="text-xs font-medium text-[var(--pos-text)]">
                            {STOCK_MOVEMENT_LABELS[m.movementType] || m.movementType}
                          </div>
                          <div className="text-[11px] text-[var(--pos-text-muted)]">
                            {m.performedBy}
                            {m.reference ? ` · ${m.reference}` : ''}
                            {m.note ? ` · ${m.note}` : ''}
                          </div>
                        </div>
                      ),
                    },
                    {
                      key: 'date',
                      label: 'Date',
                      render: (m) => (
                        <span className="font-mono-num text-xs text-[var(--pos-text-muted)]">
                          {new Date(m.occurredAt || m.createdAt).toLocaleString()}
                        </span>
                      ),
                    },
                    {
                      key: 'delta',
                      label: 'Delta',
                      align: 'right',
                      render: (m) => (
                        <span
                          className={`font-mono-num font-semibold text-xs ${
                            m.quantityDelta >= 0
                              ? 'text-[var(--pos-success)]'
                              : 'text-[var(--pos-error)]'
                          }`}
                        >
                          {m.quantityDelta >= 0 ? `+${m.quantityDelta}` : m.quantityDelta}
                        </span>
                      ),
                    },
                    {
                      key: 'balance',
                      label: 'Balance',
                      align: 'right',
                      render: (m) => (
                        <span className="font-mono-num text-xs font-semibold text-[var(--pos-text)]">
                          {m.balanceAfter} {detailItem.unit}
                        </span>
                      ),
                    },
                  ]}
                />
              </div>
            </div>
          );
        })()}
      </Modal>

      {/* Reusable Add / Edit InventoryItem Dialog */}
      <InventoryItemDialog
        open={itemModalOpen}
        onClose={() => {
          setItemModalOpen(false);
          setEditingItem(null);
        }}
        item={editingItem}
        supplyCategories={supplyCategories}
        currencySymbol={taxCurr.currencySymbol}
        onSave={handleSaveItem}
      />

      {/* Stock Adjustment Modal */}
      <Modal
        open={Boolean(adjustTarget)}
        onClose={() => setAdjustTarget(null)}
        title={adjustTarget ? `Adjust Stock · ${adjustTarget.name}` : ''}
        subtitle={
          adjustTarget
            ? `On hand: ${adjustTarget.quantityOnHand} ${adjustTarget.unit} · Reorder level: ${adjustTarget.reorderLevel} ${adjustTarget.unit}`
            : ''
        }
        maxWidth="sm"
      >
        <form onSubmit={handleAdjust} className="space-y-3.5">
          <Select
            label="Movement Type"
            value={movementType}
            onChange={(e) => setMovementType(e.target.value as StockMovementType)}
            options={[
              { value: 'purchase', label: 'Purchase / Restock (+)' },
              { value: 'job_usage', label: 'Print Job Usage (-)' },
              { value: 'adjustment', label: 'Manual Adjustment (+/-)' },
              { value: 'wastage', label: 'Production Wastage (-)' },
              { value: 'damaged', label: 'Damaged Stock (-)' },
              { value: 'return', label: 'Supplier / Customer Return (+)' },
            ]}
          />
          <Input
            label={`Quantity (${adjustTarget?.unit || 'units'})`}
            type="number"
            step="0.1"
            value={delta}
            onChange={(e) => setDelta(e.target.value)}
            required
          />
          <Input
            label="Reference # (PO / Job / Receipt)"
            placeholder="e.g. PO-2026-042 or JO-261124-102"
            value={reference}
            onChange={(e) => setReference(e.target.value)}
          />
          <Input
            label="Reason / Notes"
            placeholder="e.g. Monthly delivery or misprint wastage"
            value={note}
            onChange={(e) => setNote(e.target.value)}
          />
          <div className="flex justify-end gap-2 pt-2">
            <Button
              variant="secondary"
              type="button"
              disabled={submitting}
              onClick={() => setAdjustTarget(null)}
            >
              Cancel
            </Button>
            <Button type="submit" loading={submitting}>
              Apply Stock Update
            </Button>
          </div>
        </form>
      </Modal>

      {/* Delete Confirmation Dialog */}
      <Modal
        open={Boolean(deleteTarget)}
        onClose={() => {
          if (!deleting) setDeleteTarget(null);
        }}
        title="Delete Inventory Item"
        subtitle={
          deleteTarget ? `${deleteTarget.name} · ${deleteTarget.sku}` : ''
        }
        maxWidth="sm"
        footer={
          <>
            <Button
              variant="secondary"
              type="button"
              disabled={deleting}
              onClick={() => setDeleteTarget(null)}
            >
              Cancel
            </Button>
            <Button
              variant="danger"
              type="button"
              loading={deleting}
              onClick={() => void handleConfirmDelete()}
            >
              <Trash2 className="w-3.5 h-3.5" />
              Delete Item
            </Button>
          </>
        }
      >
        {deleteTarget && (
          <div className="space-y-3 text-sm text-[var(--pos-text)]">
            <p>
              Are you sure you want to permanently remove{' '}
              <strong className="font-semibold">{deleteTarget.name}</strong> from inventory?
            </p>
            {(deleteTarget.currentStock ?? deleteTarget.quantityOnHand) > 0 && (
              <div
                style={{ borderRadius: 'var(--pos-radius)' }}
                className="flex items-start gap-2.5 p-3 text-xs border border-[var(--pos-warning)]/35 bg-[var(--pos-warning)]/10 text-[var(--pos-text)]"
              >
                <AlertTriangle className="w-4 h-4 text-[var(--pos-warning)] shrink-0 mt-0.5" />
                <span>
                  This item currently has{' '}
                  <strong className="font-mono-num">
                    {deleteTarget.currentStock ?? deleteTarget.quantityOnHand} {deleteTarget.unit}
                  </strong>{' '}
                  on hand. Deleting it will also remove its associated stock movement history.
                </span>
              </div>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
};
