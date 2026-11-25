import React, { useEffect, useId, useMemo, useState } from 'react';
import { DEFAULT_SUPPLY_CATEGORIES } from '../../shared/defaults.ts';
import {
  extractSupplierInfo,
  getInventoryStockStatus,
  resolveSupplyCategory,
  validateInventoryItemInput,
} from '../../shared/inventoryModel.ts';
import type {
  InventoryItem,
  InventoryItemType,
  InventorySupplierInfo,
  SupplyCategory,
} from '../../types/inventory.ts';
import { Button, Input, Select, StatusIndicator } from '../ui/Primitives.tsx';
import { Modal } from '../ui/Overlays.tsx';

export interface InventoryItemFormValues {
  name: string;
  sku: string;
  barcode?: string;
  categoryId?: string;
  category: string;
  itemType: InventoryItemType;
  unit: string;
  quantityOnHand: number;
  reorderLevel: number;
  reorderQuantity: number;
  unitCost: number;
  supplier: string;
  supplierContact: string;
  supplierSku: string;
  leadTimeDays: number;
  supplierInfo?: InventorySupplierInfo;
  storageLocation: string;
}

export interface InventoryItemDialogProps {
  open: boolean;
  onClose: () => void;
  item?: InventoryItem | null;
  supplyCategories?: SupplyCategory[];
  currencySymbol?: string;
  onSave: (
    values: InventoryItemFormValues,
    existingItem?: InventoryItem | null,
  ) => Promise<void> | void;
}

export const InventoryItemDialog: React.FC<InventoryItemDialogProps> = ({
  open,
  onClose,
  item = null,
  supplyCategories = DEFAULT_SUPPLY_CATEGORIES,
  currencySymbol = '₱',
  onSave,
}) => {
  const formId = useId();
  const isEditing = Boolean(item);

  const [name, setName] = useState('');
  const [sku, setSku] = useState('');
  const [barcode, setBarcode] = useState('');
  const [selectedCategoryId, setSelectedCategoryId] = useState('sup-cat-paper');
  const [categoryName, setCategoryName] = useState('Paper');
  const [itemType, setItemType] = useState<InventoryItemType>('consumable');
  const [unit, setUnit] = useState('sheet');

  const [quantityOnHand, setQuantityOnHand] = useState('100');
  const [reorderLevel, setReorderLevel] = useState('50');
  const [reorderQuantity, setReorderQuantity] = useState('200');
  const [unitCost, setUnitCost] = useState('1.50');
  const [storageLocation, setStorageLocation] = useState('');

  const [supplier, setSupplier] = useState('');
  const [supplierContact, setSupplierContact] = useState('');
  const [supplierSku, setSupplierSku] = useState('');
  const [leadTimeDays, setLeadTimeDays] = useState('0');

  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    if (!open) return;

    setSubmitError(null);
    setFieldErrors({});
    setSubmitting(false);

    if (item) {
      const matched = resolveSupplyCategory(
        supplyCategories,
        item.categoryId || item.category,
      );
      const sup = extractSupplierInfo(item);
      setName(item.name);
      setSku(item.sku);
      setBarcode(item.barcode || '');
      setSelectedCategoryId(matched?.id || 'custom');
      setCategoryName(item.category);
      setItemType(item.itemType);
      setUnit(item.unit);
      setQuantityOnHand(String(item.quantityOnHand));
      setReorderLevel(String(item.reorderLevel));
      setReorderQuantity(
        String(item.reorderQuantity ?? Math.max(item.reorderLevel * 2, 0)),
      );
      setUnitCost(String(item.unitCost));
      setStorageLocation(item.storageLocation || '');
      setSupplier(sup.name);
      setSupplierContact(sup.contact || '');
      setSupplierSku(sup.supplierSku || '');
      setLeadTimeDays(String(sup.leadTimeDays ?? 0));
    } else {
      const firstCat = supplyCategories[0];
      setName('');
      setSku('');
      setBarcode('');
      setSelectedCategoryId(firstCat?.id || 'sup-cat-paper');
      setCategoryName(firstCat?.name || 'Paper');
      setItemType('consumable');
      setUnit(firstCat?.defaultUnit || 'sheet');
      setQuantityOnHand('100');
      setReorderLevel('50');
      setReorderQuantity('200');
      setUnitCost('1.50');
      setStorageLocation('');
      setSupplier('');
      setSupplierContact('');
      setSupplierSku('');
      setLeadTimeDays('0');
    }
  }, [open, item, supplyCategories]);

  const handleSelectSupplyCategory = (val: string) => {
    if (val === 'custom') {
      setSelectedCategoryId('custom');
      return;
    }
    setSelectedCategoryId(val);
    const matched = resolveSupplyCategory(supplyCategories, val);
    if (matched) {
      setCategoryName(matched.name);
      if (!isEditing) {
        setUnit(matched.defaultUnit);
      }
    }
  };

  const parsedQty = Math.max(0, Number(quantityOnHand) || 0);
  const parsedReorder = Math.max(0, Number(reorderLevel) || 0);
  const stockDeltaFromExisting =
    item !== null ? Number((parsedQty - item.quantityOnHand).toFixed(2)) : 0;

  const previewStatus = useMemo(
    () =>
      getInventoryStockStatus({
        quantityOnHand: parsedQty,
        reorderLevel: parsedReorder,
      }),
    [parsedQty, parsedReorder],
  );

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitError(null);
    const nextErrors: Record<string, string> = {};

    const numQty = Number(quantityOnHand);
    const numReorder = Number(reorderLevel);
    const numReorderQty = Number(reorderQuantity);
    const numCost = Number(unitCost);
    const numLeadTime = Number(leadTimeDays);

    if (!name.trim()) nextErrors.name = 'Item name is required.';
    if (!unit.trim()) nextErrors.unit = 'Unit is required.';
    if (selectedCategoryId === 'custom' && !categoryName.trim()) {
      nextErrors.categoryName = 'Category name is required.';
    }
    if (Number.isNaN(numQty) || numQty < 0) {
      nextErrors.quantityOnHand = 'Current stock cannot be negative.';
    }
    if (Number.isNaN(numReorder) || numReorder < 0) {
      nextErrors.reorderLevel = 'Reorder threshold cannot be negative.';
    }
    if (Number.isNaN(numCost) || numCost < 0) {
      nextErrors.unitCost = 'Unit cost cannot be negative.';
    }

    const validation = validateInventoryItemInput({
      name,
      unit,
      quantityOnHand: numQty,
      reorderLevel: numReorder,
      unitCost: numCost,
    });

    if (!validation.valid || Object.keys(nextErrors).length > 0) {
      setFieldErrors(nextErrors);
      if (validation.valid === false) {
        setSubmitError(validation.error);
      }
      return;
    }

    setFieldErrors({});
    setSubmitting(true);

    try {
      const resolvedCatId =
        selectedCategoryId === 'custom' ? undefined : selectedCategoryId;
      const trimmedSupplier = supplier.trim();
      const trimmedContact = supplierContact.trim();
      const trimmedSupplierSku = supplierSku.trim();
      const safeLeadTime = Math.max(0, Math.round(Number.isNaN(numLeadTime) ? 0 : numLeadTime));

      const supplierInfo: InventorySupplierInfo | undefined = trimmedSupplier
        ? {
            name: trimmedSupplier,
            contact: trimmedContact,
            supplierSku: trimmedSupplierSku,
            leadTimeDays: safeLeadTime,
          }
        : undefined;

      const payload: InventoryItemFormValues = {
        name: name.trim(),
        sku: sku.trim() || `MAT-${Date.now().toString().slice(-4)}`,
        barcode: barcode.trim(),
        categoryId: resolvedCatId,
        category: categoryName.trim() || 'Supplies',
        itemType,
        unit: unit.trim(),
        quantityOnHand: Number(numQty.toFixed(2)),
        reorderLevel: Number(numReorder.toFixed(2)),
        reorderQuantity: Math.max(
          0,
          Number((Number.isNaN(numReorderQty) ? 0 : numReorderQty).toFixed(2)),
        ),
        unitCost: Number(numCost.toFixed(2)),
        supplier: trimmedSupplier,
        supplierContact: trimmedContact,
        supplierSku: trimmedSupplierSku,
        leadTimeDays: safeLeadTime,
        supplierInfo,
        storageLocation: storageLocation.trim(),
      };

      await onSave(payload, item);
      onClose();
    } catch (err) {
      setSubmitError(
        err instanceof Error ? err.message : 'Unable to save inventory item.',
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      open={open}
      onClose={() => {
        if (!submitting) onClose();
      }}
      title={isEditing ? `Edit Inventory Item · ${item?.name}` : 'Add Inventory Item'}
      subtitle={
        isEditing
          ? `SKU ${item?.sku} · Update stock counts, reorder threshold, and supplier details`
          : 'Create a tracked material or finished product record'
      }
      maxWidth="lg"
      footer={
        <>
          <Button
            variant="secondary"
            type="button"
            disabled={submitting}
            onClick={onClose}
          >
            Cancel
          </Button>
          <Button type="submit" form={formId} loading={submitting}>
            {isEditing ? 'Save Changes' : 'Save Item'}
          </Button>
        </>
      }
    >
      <form id={formId} onSubmit={handleSubmit} className="space-y-5">
        {submitError && (
          <div
            role="alert"
            style={{ borderRadius: 'var(--pos-radius)' }}
            className="px-3.5 py-2.5 text-xs font-medium text-[var(--pos-error)] bg-[var(--pos-error)]/10 border border-[var(--pos-error)]/25"
          >
            {submitError}
          </div>
        )}

        {/* Section 1: Item Identity & Classification */}
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <h3 className="text-xs font-semibold text-[var(--pos-text)]">
              Item Details
            </h3>
            <StatusIndicator
              tone={
                previewStatus === 'out_of_stock'
                  ? 'error'
                  : previewStatus === 'low_stock'
                    ? 'warning'
                    : 'success'
              }
              label={
                previewStatus === 'out_of_stock'
                  ? 'Out of Stock'
                  : previewStatus === 'low_stock'
                    ? 'At / Below Reorder'
                    : 'Healthy Stock'
              }
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Input
              id={`${formId}-name`}
              label="Item Name"
              placeholder="e.g. Glossy Sticker Paper A4"
              value={name}
              onChange={(e) => setName(e.target.value)}
              error={fieldErrors.name}
              required
            />
            <div className="grid grid-cols-2 gap-2.5">
              <Input
                id={`${formId}-sku`}
                label="SKU"
                placeholder="Auto if blank"
                value={sku}
                onChange={(e) => setSku(e.target.value)}
              />
              <Input
                id={`${formId}-barcode`}
                label="Barcode (Optional)"
                placeholder="EAN / UPC"
                value={barcode}
                onChange={(e) => setBarcode(e.target.value)}
              />
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <Select
              id={`${formId}-type`}
              label="Item Type"
              value={itemType}
              onChange={(e) => setItemType(e.target.value as InventoryItemType)}
              options={[
                { value: 'consumable', label: 'Consumable Material' },
                { value: 'finished_product', label: 'Finished Product' },
              ]}
            />
            <Select
              id={`${formId}-category`}
              label="Supply Category"
              value={selectedCategoryId}
              onChange={(e) => handleSelectSupplyCategory(e.target.value)}
              options={[
                ...supplyCategories.map((sc) => ({
                  value: sc.id,
                  label: `${sc.name} (${sc.defaultUnit})`,
                })),
                { value: 'custom', label: 'Custom Category' },
              ]}
            />
            <Input
              id={`${formId}-unit`}
              label="Stock Unit (sheet, pc, sq ft)"
              value={unit}
              onChange={(e) => setUnit(e.target.value)}
              error={fieldErrors.unit}
              required
            />
          </div>

          {selectedCategoryId === 'custom' && (
            <Input
              id={`${formId}-custom-category`}
              label="Custom Category Name"
              value={categoryName}
              onChange={(e) => setCategoryName(e.target.value)}
              error={fieldErrors.categoryName}
              required
            />
          )}
        </div>

        {/* Section 2: Stock Counts & Reorder Thresholds */}
        <div className="pt-4 border-t border-[var(--pos-border)] space-y-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h3 className="text-xs font-semibold text-[var(--pos-text)]">
              Stock Counts & Reorder Threshold
            </h3>
            {isEditing && stockDeltaFromExisting !== 0 && (
              <span className="text-[11px] font-mono-num text-[var(--pos-primary)]">
                Stock change: {stockDeltaFromExisting > 0 ? `+${stockDeltaFromExisting}` : stockDeltaFromExisting} {unit} (logged automatically)
              </span>
            )}
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            <Input
              id={`${formId}-qty`}
              label={`Current Stock (${unit || 'units'})`}
              type="number"
              min="0"
              step="0.1"
              className="font-mono-num"
              value={quantityOnHand}
              onChange={(e) => setQuantityOnHand(e.target.value)}
              error={fieldErrors.quantityOnHand}
              required
            />
            <Input
              id={`${formId}-reorder`}
              label="Reorder Threshold"
              type="number"
              min="0"
              step="0.1"
              className="font-mono-num"
              value={reorderLevel}
              onChange={(e) => setReorderLevel(e.target.value)}
              error={fieldErrors.reorderLevel}
              required
            />
            <Input
              id={`${formId}-reorder-qty`}
              label="Suggested Order Qty"
              type="number"
              min="0"
              step="0.1"
              className="font-mono-num"
              value={reorderQuantity}
              onChange={(e) => setReorderQuantity(e.target.value)}
            />
            <Input
              id={`${formId}-cost`}
              label={`Unit Cost (${currencySymbol})`}
              type="number"
              min="0"
              step="0.01"
              className="font-mono-num"
              value={unitCost}
              onChange={(e) => setUnitCost(e.target.value)}
              error={fieldErrors.unitCost}
            />
          </div>

          <Input
            id={`${formId}-location`}
            label="Storage Location (Rack / Shelf)"
            placeholder="e.g. Rack B · Shelf 3"
            value={storageLocation}
            onChange={(e) => setStorageLocation(e.target.value)}
          />
        </div>

        {/* Section 3: Supplier Information */}
        <div className="pt-4 border-t border-[var(--pos-border)] space-y-3">
          <h3 className="text-xs font-semibold text-[var(--pos-text)]">
            Supplier Information
          </h3>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Input
              id={`${formId}-supplier`}
              label="Supplier Name"
              placeholder="e.g. Metro Paper & Vinyl Supply"
              value={supplier}
              onChange={(e) => setSupplier(e.target.value)}
            />
            <Input
              id={`${formId}-supplier-contact`}
              label="Supplier Contact (Phone / Email)"
              placeholder="e.g. +63 917 555 0142"
              value={supplierContact}
              onChange={(e) => setSupplierContact(e.target.value)}
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Input
              id={`${formId}-supplier-sku`}
              label="Supplier SKU / Code"
              placeholder="Vendor part number"
              value={supplierSku}
              onChange={(e) => setSupplierSku(e.target.value)}
            />
            <Input
              id={`${formId}-lead-time`}
              label="Lead Time (Days)"
              type="number"
              min="0"
              step="1"
              className="font-mono-num"
              value={leadTimeDays}
              onChange={(e) => setLeadTimeDays(e.target.value)}
            />
          </div>
        </div>
      </form>
    </Modal>
  );
};
