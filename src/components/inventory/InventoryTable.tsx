import React, { useCallback, useEffect, useId, useMemo, useRef, useState } from 'react';
import {
  AlertTriangle,
  ArrowUpDown,
  CheckSquare,
  Edit2,
  Search,
  Trash2,
  X,
} from 'lucide-react';
import type {
  BulkInventoryUpdatePayload,
  InventoryItem,
  InventorySortDirection,
  InventorySortField,
  SupplyCategory,
} from '../../types/inventory.ts';
import type { TaxAndCurrencyConfig } from '../../shared/types.ts';
import { DEFAULT_SETTINGS, DEFAULT_SUPPLY_CATEGORIES } from '../../shared/defaults.ts';
import { formatCurrency } from '../../shared/pricingEngine.ts';
import {
  extractSupplierInfo,
  filterInventoryItems,
  getAvailableStock,
  getInventoryStockStatus,
  getSuggestedRestockQuantity,
  resolveSupplyCategory,
} from '../../shared/inventoryModel.ts';
import { Button, Input, Select, StatusIndicator } from '../ui/Primitives.tsx';
import { DataTable, type DataTableColumn } from '../ui/DataTable.tsx';

export interface InventoryTableProps {
  items: InventoryItem[];
  supplyCategories?: SupplyCategory[];
  taxAndCurrency?: TaxAndCurrencyConfig;
  canEdit?: boolean;
  onEdit: (item: InventoryItem) => void;
  onDelete?: (item: InventoryItem) => void;
  onAdjustStock?: (item: InventoryItem) => void;
  onBulkUpdate?: (payload: BulkInventoryUpdatePayload) => Promise<InventoryItem[] | void> | void;
  onRowClick?: (item: InventoryItem) => void;
  sortKey?: InventorySortField;
  sortDir?: InventorySortDirection;
  onSortChange?: (key: string) => void;
  searchQuery?: string;
  onSearchChange?: (value: string) => void;
  categoryFilter?: string;
  onCategoryChange?: (value: string) => void;
  showSearchAndCategory?: boolean;
  toolbarExtras?: React.ReactNode;
  totalCount?: number;
  emptyTitle?: string;
  emptyDescription?: string;
  emptyActionLabel?: string;
  onEmptyAction?: () => void;
}

export const InventoryTable: React.FC<InventoryTableProps> = ({
  items,
  supplyCategories = DEFAULT_SUPPLY_CATEGORIES,
  taxAndCurrency = DEFAULT_SETTINGS.taxAndCurrency,
  canEdit = true,
  onEdit,
  onDelete,
  onAdjustStock,
  onBulkUpdate,
  onRowClick,
  sortKey = 'name',
  sortDir = 'asc',
  onSortChange,
  searchQuery,
  onSearchChange,
  categoryFilter,
  onCategoryChange,
  showSearchAndCategory = true,
  toolbarExtras,
  totalCount,
  emptyTitle,
  emptyDescription,
  emptyActionLabel,
  onEmptyAction,
}) => {
  const supplierDatalistId = useId();
  const headerCheckboxRef = useRef<HTMLInputElement>(null);

  const [internalSearch, setInternalSearch] = useState('');
  const [internalCategory, setInternalCategory] = useState('all');
  const [selectedIds, setSelectedIds] = useState<Set<string>>(() => new Set());
  const [bulkCategoryId, setBulkCategoryId] = useState('');
  const [bulkSupplier, setBulkSupplier] = useState('');
  const [bulkSubmitting, setBulkSubmitting] = useState(false);
  const [bulkError, setBulkError] = useState<string | null>(null);

  const isSearchControlled = searchQuery !== undefined && onSearchChange !== undefined;
  const isCategoryControlled = categoryFilter !== undefined && onCategoryChange !== undefined;

  const activeSearch = isSearchControlled ? searchQuery : internalSearch;
  const activeCategory = isCategoryControlled ? categoryFilter : internalCategory;

  const setActiveSearch = (val: string) => {
    if (isSearchControlled) {
      onSearchChange(val);
    } else {
      setInternalSearch(val);
    }
  };

  const setActiveCategory = (val: string) => {
    if (isCategoryControlled) {
      onCategoryChange(val);
    } else {
      setInternalCategory(val);
    }
  };

  const categoryOptions = useMemo(() => {
    const opts: Array<{ value: string; label: string }> = [
      { value: 'all', label: 'All Categories' },
    ];
    const seen = new Set<string>();
    for (const sc of supplyCategories) {
      opts.push({ value: sc.id, label: sc.name });
      seen.add(sc.name.trim().toLowerCase());
    }
    for (const item of items) {
      const cat = (item.category || '').trim();
      if (cat && !seen.has(cat.toLowerCase())) {
        seen.add(cat.toLowerCase());
        opts.push({ value: cat, label: cat });
      }
    }
    return opts;
  }, [supplyCategories, items]);

  const displayedItems = useMemo(() => {
    if (isSearchControlled && isCategoryControlled) {
      return items;
    }
    return filterInventoryItems(
      items,
      {
        search: isSearchControlled ? '' : activeSearch,
        categoryId: isCategoryControlled ? 'all' : activeCategory,
      },
      supplyCategories,
    );
  }, [
    items,
    isSearchControlled,
    isCategoryControlled,
    activeSearch,
    activeCategory,
    supplyCategories,
  ]);

  const hasToolbarFilters = activeSearch.trim().length > 0 || activeCategory !== 'all';

  useEffect(() => {
    const visibleSet = new Set(displayedItems.map((item) => item.id));
    setSelectedIds((prev) => {
      if (prev.size === 0) return prev;
      let changed = false;
      const next = new Set<string>();
      for (const id of prev) {
        if (visibleSet.has(id)) {
          next.add(id);
        } else {
          changed = true;
        }
      }
      return changed ? next : prev;
    });
  }, [displayedItems]);

  const allVisibleSelected =
    displayedItems.length > 0 && displayedItems.every((item) => selectedIds.has(item.id));
  const someVisibleSelected =
    displayedItems.some((item) => selectedIds.has(item.id)) && !allVisibleSelected;

  useEffect(() => {
    if (headerCheckboxRef.current) {
      headerCheckboxRef.current.indeterminate = someVisibleSelected;
    }
  }, [someVisibleSelected]);

  const toggleSelectAll = useCallback(() => {
    setBulkError(null);
    if (allVisibleSelected) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(displayedItems.map((item) => item.id)));
    }
  }, [allVisibleSelected, displayedItems]);

  const toggleSelectItem = useCallback((id: string) => {
    setBulkError(null);
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }, []);

  const clearSelection = () => {
    setSelectedIds(new Set());
    setBulkCategoryId('');
    setBulkSupplier('');
    setBulkError(null);
  };

  const { supplierSuggestions, knownSupplierContacts } = useMemo(() => {
    const contactsMap = new Map<string, { name: string; contact: string }>();
    for (const item of items) {
      const sup = extractSupplierInfo(item);
      const name = sup.name.trim();
      if (!name) continue;
      const key = name.toLowerCase();
      const existing = contactsMap.get(key);
      if (!existing) {
        contactsMap.set(key, { name, contact: (sup.contact || '').trim() });
      } else if (!existing.contact && sup.contact) {
        existing.contact = sup.contact.trim();
      }
    }
    const suggestions = Array.from(contactsMap.values())
      .map((entry) => entry.name)
      .sort((a, b) => a.localeCompare(b));
    return { supplierSuggestions: suggestions, knownSupplierContacts: contactsMap };
  }, [items]);

  const handleApplyBulkUpdate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!onBulkUpdate || selectedIds.size === 0) return;

    const trimmedSupplier = bulkSupplier.trim();
    if (!bulkCategoryId && !trimmedSupplier) {
      setBulkError('Choose a supply category or enter a supplier name to apply.');
      return;
    }

    const payload: BulkInventoryUpdatePayload = {
      ids: Array.from(selectedIds),
    };

    if (bulkCategoryId) {
      const matchedCategory = resolveSupplyCategory(supplyCategories, bulkCategoryId);
      payload.categoryId = matchedCategory?.id ?? bulkCategoryId;
      payload.category = matchedCategory?.name ?? bulkCategoryId;
    }

    if (trimmedSupplier) {
      payload.supplier = trimmedSupplier;
      const known = knownSupplierContacts.get(trimmedSupplier.toLowerCase());
      if (known?.contact) {
        payload.supplierContact = known.contact;
      }
    }

    setBulkSubmitting(true);
    setBulkError(null);
    try {
      await onBulkUpdate(payload);
      clearSelection();
    } catch (err) {
      setBulkError(
        err instanceof Error ? err.message : 'Unable to apply bulk updates to selected items.',
      );
    } finally {
      setBulkSubmitting(false);
    }
  };

  const columns = useMemo<DataTableColumn<InventoryItem>[]>(() => {
    const baseColumns: DataTableColumn<InventoryItem>[] = [
      {
        key: 'name',
        label: 'Item Name',
        sortable: Boolean(onSortChange),
        render: (item) => (
          <div className="min-w-[180px]">
            <div className="text-sm font-semibold text-[var(--pos-text)] leading-snug">
              {item.name}
            </div>
            <div className="text-[11px] text-[var(--pos-text-muted)] mt-0.5 flex flex-wrap items-center gap-1">
              <span className="font-mono-num">{item.sku}</span>
              <span aria-hidden="true">·</span>
              <span>{item.category}</span>
              <span aria-hidden="true">·</span>
              <span>
                {item.itemType === 'finished_product' ? 'Finished' : 'Consumable'}
              </span>
              {item.storageLocation && (
                <>
                  <span aria-hidden="true">·</span>
                  <span>{item.storageLocation}</span>
                </>
              )}
            </div>
          </div>
        ),
      },
      {
        key: 'supplier',
        label: 'Supplier',
        hideOnMobile: true,
        render: (item) => {
          const sup = extractSupplierInfo(item);
          return (
            <div className="min-w-[130px]">
              <div className="text-xs font-medium text-[var(--pos-text)]">
                {sup.name || 'Unassigned'}
              </div>
              {(sup.contact || sup.supplierSku) && (
                <div className="text-[11px] text-[var(--pos-text-muted)] mt-0.5">
                  {[sup.supplierSku, sup.contact].filter(Boolean).join(' · ')}
                </div>
              )}
            </div>
          );
        },
      },
      {
        key: 'quantityOnHand',
        label: 'Current Stock',
        align: 'right',
        sortable: Boolean(onSortChange),
        render: (item) => {
          const stockCount = item.currentStock ?? item.quantityOnHand;
          const threshold = item.reorderThreshold ?? item.reorderLevel;
          const status = getInventoryStockStatus({
            quantityOnHand: stockCount,
            reorderLevel: threshold,
          });
          const avail = getAvailableStock({
            quantityOnHand: stockCount,
            reservedQuantity: item.reservedQuantity,
          });
          const scaleMax = Math.max(threshold * 2, stockCount, 1);
          const pct = Math.min(
            100,
            Math.max(0, Math.round((stockCount / scaleMax) * 100)),
          );
          const meterColor =
            status === 'out_of_stock'
              ? 'bg-[var(--pos-error)]'
              : status === 'low_stock'
                ? 'bg-[var(--pos-warning)]'
                : 'bg-[var(--pos-success)]';

          return (
            <div className="inline-flex flex-col items-end min-w-[96px]">
              <div
                className={`font-mono-num font-semibold text-sm ${
                  status === 'out_of_stock'
                    ? 'text-[var(--pos-error)]'
                    : status === 'low_stock'
                      ? 'text-[var(--pos-warning)]'
                      : 'text-[var(--pos-text)]'
                }`}
              >
                {stockCount} {item.unit}
              </div>
              <div className="w-20 h-1 rounded-full bg-[var(--pos-border)] overflow-hidden mt-1">
                <div
                  className={`h-full rounded-full ${meterColor}`}
                  style={{ width: `${pct}%` }}
                />
              </div>
              {(item.reservedQuantity || 0) > 0 && (
                <div className="text-[11px] text-[var(--pos-text-muted)] font-mono-num mt-0.5">
                  Avail {avail}
                </div>
              )}
            </div>
          );
        },
      },
      {
        key: 'reorderLevel',
        label: 'Reorder Threshold',
        align: 'right',
        sortable: Boolean(onSortChange),
        render: (item) => {
          const stockCount = item.currentStock ?? item.quantityOnHand;
          const threshold = item.reorderThreshold ?? item.reorderLevel;
          const suggested = getSuggestedRestockQuantity({
            quantityOnHand: stockCount,
            reorderLevel: threshold,
            reorderQuantity: item.reorderQuantity,
          });
          return (
            <div className="inline-flex flex-col items-end">
              <div className="font-mono-num text-sm font-medium text-[var(--pos-text)]">
                {threshold} {item.unit}
              </div>
              <div className="text-[11px] text-[var(--pos-text-muted)] font-mono-num mt-0.5">
                {suggested > 0
                  ? `Restock +${suggested}`
                  : item.reorderQuantity
                    ? `Order ${item.reorderQuantity}`
                    : 'Threshold'}
              </div>
            </div>
          );
        },
      },
      {
        key: 'unitCost',
        label: 'Unit Cost',
        align: 'right',
        hideOnMobile: true,
        sortable: Boolean(onSortChange),
        render: (item) => (
          <span className="font-mono-num text-sm text-[var(--pos-text)]">
            {formatCurrency(item.unitCost, taxAndCurrency)}
          </span>
        ),
      },
      {
        key: 'status',
        label: 'Status',
        sortable: Boolean(onSortChange),
        render: (item) => {
          const stockCount = item.currentStock ?? item.quantityOnHand;
          const threshold = item.reorderThreshold ?? item.reorderLevel;
          const isLowStock = stockCount <= threshold;
          const isOutOfStock = stockCount <= 0;

          if (!isLowStock) {
            return <StatusIndicator tone="success" label="In Stock" />;
          }

          return (
            <div className="inline-flex flex-col items-start gap-0.5">
              <span
                style={{ borderRadius: 'calc(var(--pos-radius) - 4px)' }}
                className={`inline-flex items-center gap-1.5 px-2 py-0.5 text-xs font-semibold whitespace-nowrap border ${
                  isOutOfStock
                    ? 'text-[var(--pos-error)] bg-[var(--pos-error)]/12 border-[var(--pos-error)]/30'
                    : 'text-[var(--pos-warning)] bg-[var(--pos-warning)]/12 border-[var(--pos-warning)]/35'
                }`}
              >
                <AlertTriangle className="w-3.5 h-3.5 shrink-0" />
                <span>Low Stock</span>
              </span>
              {isOutOfStock && (
                <span className="text-[11px] font-medium text-[var(--pos-error)]">
                  Out of stock
                </span>
              )}
            </div>
          );
        },
      },
      {
        key: 'action',
        label: 'Actions',
        align: 'right',
        render: (item) => (
          <div
            className="inline-flex items-center justify-end gap-1.5"
            onClick={(e) => e.stopPropagation()}
          >
            {canEdit && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => onEdit(item)}
                title={`Edit ${item.name}`}
                aria-label={`Edit ${item.name}`}
              >
                <Edit2 className="w-3.5 h-3.5" />
                Edit
              </Button>
            )}
            {onAdjustStock && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => onAdjustStock(item)}
                aria-label={`Adjust stock for ${item.name}`}
              >
                <ArrowUpDown className="w-3.5 h-3.5" />
                Stock
              </Button>
            )}
            {canEdit && onDelete && (
              <Button
                variant="ghost"
                size="sm"
                className="text-[var(--pos-error)] hover:bg-[var(--pos-error)]/10 hover:text-[var(--pos-error)]"
                onClick={() => onDelete(item)}
                title={`Delete ${item.name}`}
                aria-label={`Delete ${item.name}`}
              >
                <Trash2 className="w-3.5 h-3.5" />
                Delete
              </Button>
            )}
          </div>
        ),
      },
    ];

    if (!canEdit) {
      return baseColumns;
    }

    const selectionColumn: DataTableColumn<InventoryItem> = {
      key: 'select',
      label: (
        <label className="flex items-center justify-center cursor-pointer">
          <input
            ref={headerCheckboxRef}
            type="checkbox"
            checked={allVisibleSelected}
            onChange={toggleSelectAll}
            aria-label="Select all visible inventory items"
            className="h-4 w-4 rounded border-[var(--pos-border)] accent-[var(--pos-primary)] cursor-pointer"
          />
        </label>
      ),
      stopRowClick: true,
      headerClassName: 'w-11 px-3',
      cellClassName: 'w-11 px-3',
      render: (item) => {
        const checked = selectedIds.has(item.id);
        return (
          <label className="flex items-center justify-center py-1 cursor-pointer">
            <input
              type="checkbox"
              checked={checked}
              onChange={() => toggleSelectItem(item.id)}
              aria-label={`Select ${item.name}`}
              className="h-4 w-4 rounded border-[var(--pos-border)] accent-[var(--pos-primary)] cursor-pointer"
            />
          </label>
        );
      },
    };

    return [selectionColumn, ...baseColumns];
  }, [
    allVisibleSelected,
    canEdit,
    onAdjustStock,
    onDelete,
    onEdit,
    onSortChange,
    selectedIds,
    taxAndCurrency,
    toggleSelectAll,
    toggleSelectItem,
  ]);

  const getRowHighlightClass = (item: InventoryItem): string => {
    const stockCount = item.currentStock ?? item.quantityOnHand;
    const threshold = item.reorderThreshold ?? item.reorderLevel;
    const isSelected = selectedIds.has(item.id);
    const selectedRing = isSelected ? 'ring-1 ring-inset ring-[var(--pos-primary)]/45' : '';

    if (stockCount <= 0) {
      return `bg-[var(--pos-error)]/[0.07] hover:bg-[var(--pos-error)]/[0.12] ${selectedRing}`;
    }
    if (stockCount <= threshold) {
      return `bg-[var(--pos-warning)]/[0.08] hover:bg-[var(--pos-warning)]/[0.14] ${selectedRing}`;
    }
    if (isSelected) {
      return 'bg-[var(--pos-primary)]/[0.07] hover:bg-[var(--pos-primary)]/[0.12]';
    }
    return '';
  };

  const resolvedTotal = totalCount ?? items.length;
  const resolvedEmptyTitle =
    emptyTitle ??
    (hasToolbarFilters
      ? 'No inventory items match your search'
      : 'No inventory items recorded yet');
  const resolvedEmptyDesc =
    emptyDescription ??
    (hasToolbarFilters
      ? 'Adjust your search query or supply category filter to view matching items.'
      : 'Add paper stock, inks, tarpaulin rolls, or finished supplies to start tracking counts.');
  const resolvedEmptyActionLabel =
    emptyActionLabel ?? (hasToolbarFilters ? 'Clear Filters' : undefined);
  const resolvedEmptyAction =
    onEmptyAction ??
    (hasToolbarFilters
      ? () => {
          setActiveSearch('');
          setActiveCategory('all');
        }
      : undefined);

  return (
    <div className="space-y-3">
      {showSearchAndCategory && (
        <div className="flex flex-col gap-2">
          <div className="flex flex-wrap items-center justify-between gap-2.5">
            <div className="flex flex-wrap items-center gap-2 flex-1 min-w-0">
              <div className="relative flex-1 min-w-[220px] sm:max-w-xs">
                <Input
                  aria-label="Search inventory items"
                  leftIcon={<Search className="w-3.5 h-3.5" />}
                  placeholder="Search name, SKU, supplier, rack..."
                  value={activeSearch}
                  onChange={(e) => setActiveSearch(e.target.value)}
                  className={activeSearch ? 'pr-8' : ''}
                />
                {activeSearch && (
                  <button
                    type="button"
                    onClick={() => setActiveSearch('')}
                    aria-label="Clear search"
                    className="absolute right-2.5 top-1/2 -translate-y-1/2 text-[var(--pos-text-muted)] hover:text-[var(--pos-text)] cursor-pointer p-0.5"
                  >
                    <X className="w-3.5 h-3.5" />
                  </button>
                )}
              </div>

              <div className="w-full sm:w-48">
                <Select
                  aria-label="Filter by supply category"
                  value={activeCategory}
                  onChange={(e) => setActiveCategory(e.target.value)}
                  options={categoryOptions}
                />
              </div>

              {toolbarExtras}
            </div>

            <div className="text-xs text-[var(--pos-text-muted)] font-mono-num whitespace-nowrap shrink-0">
              Showing {displayedItems.length} of {resolvedTotal} item(s)
            </div>
          </div>
        </div>
      )}

      {canEdit && selectedIds.size > 0 && (
        <div
          role="region"
          aria-label="Bulk inventory actions"
          style={{ borderRadius: 'var(--pos-radius)' }}
          className="px-3.5 py-2.5 border border-[var(--pos-primary)]/35 bg-[var(--pos-primary)]/[0.06] flex flex-col gap-2"
        >
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex flex-wrap items-center gap-2">
              <span className="inline-flex items-center gap-1.5 text-xs font-semibold text-[var(--pos-primary)]">
                <CheckSquare className="w-4 h-4 shrink-0" />
                <span className="font-mono-num">
                  {selectedIds.size} {selectedIds.size === 1 ? 'item' : 'items'} selected
                </span>
              </span>
              {!allVisibleSelected && displayedItems.length > selectedIds.size && (
                <button
                  type="button"
                  onClick={toggleSelectAll}
                  className="text-xs font-medium text-[var(--pos-primary)] hover:underline cursor-pointer"
                >
                  Select all {displayedItems.length}
                </button>
              )}
              <Button
                type="button"
                variant="ghost"
                size="sm"
                disabled={bulkSubmitting}
                onClick={clearSelection}
              >
                <X className="w-3.5 h-3.5" />
                Clear
              </Button>
            </div>

            <form
              onSubmit={handleApplyBulkUpdate}
              className="flex flex-wrap items-center gap-2 flex-1 justify-end min-w-[260px]"
            >
              <div className="w-full sm:w-48">
                <Select
                  aria-label="Bulk update supply category"
                  value={bulkCategoryId}
                  disabled={bulkSubmitting}
                  onChange={(e) => setBulkCategoryId(e.target.value)}
                  options={[
                    { value: '', label: 'Supply Category (No change)' },
                    ...supplyCategories.map((sc) => ({
                      value: sc.id,
                      label: sc.name,
                    })),
                  ]}
                />
              </div>

              <div className="w-full sm:w-52">
                <Input
                  aria-label="Bulk update supplier"
                  placeholder="Set supplier (optional)..."
                  value={bulkSupplier}
                  disabled={bulkSubmitting}
                  list={supplierDatalistId}
                  onChange={(e) => setBulkSupplier(e.target.value)}
                />
                <datalist id={supplierDatalistId}>
                  {supplierSuggestions.map((supName) => (
                    <option key={supName} value={supName} />
                  ))}
                </datalist>
              </div>

              <Button
                type="submit"
                size="sm"
                loading={bulkSubmitting}
                disabled={
                  bulkSubmitting ||
                  !onBulkUpdate ||
                  (!bulkCategoryId && !bulkSupplier.trim())
                }
              >
                Apply Updates
              </Button>
            </form>
          </div>

          {bulkError && (
            <div role="alert" className="text-xs font-medium text-[var(--pos-error)]">
              {bulkError}
            </div>
          )}
        </div>
      )}

      <DataTable
        rows={displayedItems}
        rowKey={(item) => item.id}
        columns={columns}
        sortKey={sortKey}
        sortDir={sortDir}
        onSortChange={onSortChange}
        onRowClick={onRowClick}
        rowClassName={getRowHighlightClass}
        emptyTitle={resolvedEmptyTitle}
        emptyDescription={resolvedEmptyDesc}
        emptyActionLabel={resolvedEmptyActionLabel}
        onEmptyAction={resolvedEmptyAction}
      />
    </div>
  );
};
