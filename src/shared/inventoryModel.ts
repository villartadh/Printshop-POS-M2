import type {
  InventoryItem,
  InventoryStockFilter,
  InventorySupplierInfo,
  StockMovement,
  StockMovementReferenceType,
  StockMovementType,
  StockStatus,
  SupplyCategory,
} from '../types/inventory.ts';

export const STOCK_MOVEMENT_LABELS: Record<StockMovementType, string> = {
  initial_stock: 'Initial Stock',
  purchase: 'Purchase / Restock',
  sale_deduction: 'POS Sale Deduction',
  job_usage: 'Print Job Usage',
  adjustment: 'Manual Adjustment',
  wastage: 'Production Wastage',
  damaged: 'Damaged Stock',
  return: 'Stock Return',
};

export function getAvailableStock(
  item: Pick<InventoryItem, 'quantityOnHand' | 'reservedQuantity'>,
): number {
  const onHand = Number(item.quantityOnHand) || 0;
  const reserved = Math.max(0, Number(item.reservedQuantity) || 0);
  return Number(Math.max(0, onHand - reserved).toFixed(2));
}

export function getInventoryStockStatus(
  item: Pick<InventoryItem, 'quantityOnHand' | 'reorderLevel'>,
): StockStatus {
  const onHand = Number(item.quantityOnHand) || 0;
  const reorder = Number(item.reorderLevel) || 0;
  if (onHand <= 0) return 'out_of_stock';
  if (onHand <= reorder) return 'low_stock';
  return 'in_stock';
}

export function isLowStockItem(
  item: Pick<InventoryItem, 'quantityOnHand' | 'reorderLevel'>,
): boolean {
  return (Number(item.quantityOnHand) || 0) <= (Number(item.reorderLevel) || 0);
}

export function getSuggestedRestockQuantity(
  item: Pick<InventoryItem, 'quantityOnHand' | 'reorderLevel' | 'reorderQuantity'>,
): number {
  const onHand = Number(item.quantityOnHand) || 0;
  const reorderLevel = Number(item.reorderLevel) || 0;
  if (onHand > reorderLevel) return 0;
  if (item.reorderQuantity && item.reorderQuantity > 0) {
    return item.reorderQuantity;
  }
  return Math.max(1, Number((reorderLevel * 2 - onHand).toFixed(2)));
}

export function extractSupplierInfo(item: InventoryItem): InventorySupplierInfo {
  const details = item.supplierDetails ?? item.supplierInfo;
  if (details && details.name) {
    return {
      name: details.name,
      contact: details.contact ?? item.supplierContact ?? '',
      supplierSku: details.supplierSku ?? item.supplierSku ?? '',
      leadTimeDays: details.leadTimeDays ?? item.leadTimeDays ?? 0,
    };
  }
  return {
    name: item.supplier || '',
    contact: item.supplierContact || '',
    supplierSku: item.supplierSku || '',
    leadTimeDays: item.leadTimeDays ?? 0,
  };
}

export function calculateInventoryValuation(items: InventoryItem[]): number {
  return Number(
    items
      .reduce(
        (sum, item) =>
          sum + Math.max(0, Number(item.quantityOnHand) || 0) * (Number(item.unitCost) || 0),
        0,
      )
      .toFixed(2),
  );
}

export function computeSignedStockDelta(
  movementType: StockMovementType,
  rawDelta: number,
): number {
  const num = Number(rawDelta) || 0;
  if (
    movementType === 'wastage' ||
    movementType === 'damaged' ||
    movementType === 'sale_deduction' ||
    movementType === 'job_usage'
  ) {
    return -Math.abs(num);
  }
  if (
    movementType === 'purchase' ||
    movementType === 'initial_stock' ||
    movementType === 'return'
  ) {
    return Math.abs(num);
  }
  return num;
}

export function inferReferenceType(
  movementType: StockMovementType,
): StockMovementReferenceType {
  switch (movementType) {
    case 'sale_deduction':
      return 'sale';
    case 'job_usage':
      return 'print_job';
    case 'purchase':
      return 'purchase_order';
    case 'return':
      return 'return';
    case 'initial_stock':
      return 'system';
    default:
      return 'manual';
  }
}

export function resolveSupplyCategory(
  categories: SupplyCategory[],
  categoryIdOrName?: string,
): SupplyCategory | undefined {
  if (!categoryIdOrName) return undefined;
  const target = categoryIdOrName.trim().toLowerCase();
  return categories.find(
    (c) => c.id.toLowerCase() === target || c.name.toLowerCase() === target,
  );
}

export function filterInventoryItems(
  items: InventoryItem[],
  filter: InventoryStockFilter,
  categories: SupplyCategory[] = [],
): InventoryItem[] {
  const q = (filter.search || '').trim().toLowerCase();
  const targetCat = filter.categoryId && filter.categoryId !== 'all'
    ? resolveSupplyCategory(categories, filter.categoryId)
    : undefined;

  return items.filter((item) => {
    if (filter.stockStatus && filter.stockStatus !== 'all') {
      if (getInventoryStockStatus(item) !== filter.stockStatus) return false;
    }
    if (filter.itemType && filter.itemType !== 'all') {
      if (item.itemType !== filter.itemType) return false;
    }
    if (filter.categoryId && filter.categoryId !== 'all') {
      const matchesId = item.categoryId === filter.categoryId;
      const matchesName =
        targetCat && item.category.toLowerCase() === targetCat.name.toLowerCase();
      const matchesRaw = item.category.toLowerCase() === filter.categoryId.toLowerCase();
      if (!matchesId && !matchesName && !matchesRaw) return false;
    }
    if (q) {
      const supplierName = (item.supplierInfo?.name || item.supplier || '').toLowerCase();
      const hit =
        item.name.toLowerCase().includes(q) ||
        item.sku.toLowerCase().includes(q) ||
        item.category.toLowerCase().includes(q) ||
        supplierName.includes(q) ||
        (item.storageLocation || '').toLowerCase().includes(q);
      if (!hit) return false;
    }
    return true;
  });
}

export function normalizeInventoryItem(
  raw: Partial<InventoryItem> & { id: string },
): InventoryItem {
  const now = new Date().toISOString();
  const quantityOnHand = Math.max(0, Number(raw.quantityOnHand ?? raw.currentStock) || 0);
  const reorderLevel = Math.max(0, Number(raw.reorderLevel ?? raw.reorderThreshold) || 0);
  const details = raw.supplierDetails ?? raw.supplierInfo;
  const supplierName = (raw.supplier ?? details?.name ?? '').trim();
  const supplierContact = (raw.supplierContact ?? details?.contact ?? '').trim();
  const supplierSku = (raw.supplierSku ?? details?.supplierSku ?? '').trim();
  const leadTimeDays = Math.max(0, Number(raw.leadTimeDays ?? details?.leadTimeDays) || 0);
  const supplierPayload = supplierName
    ? {
        name: supplierName,
        contact: supplierContact,
        supplierSku,
        leadTimeDays,
      }
    : undefined;

  return {
    id: raw.id,
    sku: (raw.sku || '').trim() || `MAT-${Date.now().toString().slice(-4)}`,
    barcode: raw.barcode ? String(raw.barcode).trim() : '',
    name: (raw.name || '').trim(),
    categoryId: raw.categoryId ? String(raw.categoryId).trim() : undefined,
    category: (raw.category || 'Supplies').trim(),
    itemType: raw.itemType === 'finished_product' ? 'finished_product' : 'consumable',
    unit: (raw.unit || 'pc').trim(),
    quantityOnHand,
    currentStock: quantityOnHand,
    reservedQuantity: Math.max(0, Number(raw.reservedQuantity) || 0),
    reorderLevel,
    reorderThreshold: reorderLevel,
    reorderQuantity:
      raw.reorderQuantity !== undefined
        ? Math.max(0, Number(raw.reorderQuantity) || 0)
        : Math.max(reorderLevel * 2, 0),
    unitCost: Math.max(0, Number(raw.unitCost) || 0),
    supplier: supplierName,
    supplierContact,
    supplierSku,
    leadTimeDays,
    supplierDetails: supplierPayload,
    supplierInfo: supplierPayload,
    storageLocation: (raw.storageLocation || '').trim(),
    sortOrder: raw.sortOrder !== undefined ? Number(raw.sortOrder) || 0 : undefined,
    lastRestockedAt: raw.lastRestockedAt ?? null,
    isDemo: Boolean(raw.isDemo),
    createdAt: raw.createdAt || raw.updatedAt || now,
    updatedAt: raw.updatedAt || now,
  };
}

export function normalizeStockMovement(
  raw: Partial<StockMovement> & { id: string; inventoryItemId: string; itemName: string },
): StockMovement {
  const now = new Date().toISOString();
  const movementType: StockMovementType = raw.movementType || 'adjustment';
  const quantityDelta = Number(raw.quantityDelta) || 0;
  const balanceAfter = Math.max(0, Number(raw.balanceAfter) || 0);
  const quantityBefore =
    raw.quantityBefore !== undefined
      ? Math.max(0, Number(raw.quantityBefore) || 0)
      : Math.max(0, Number((balanceAfter - quantityDelta).toFixed(2)));
  const createdAt = raw.createdAt || now;

  return {
    id: raw.id,
    inventoryItemId: raw.inventoryItemId,
    itemName: raw.itemName,
    movementType,
    quantityBefore,
    quantityDelta,
    balanceAfter,
    unitCostAtMovement: Math.max(0, Number(raw.unitCostAtMovement) || 0),
    referenceType: raw.referenceType || inferReferenceType(movementType),
    reference: (raw.reference || '').trim(),
    performedById: raw.performedById || '',
    performedBy: raw.performedBy || 'System',
    note: (raw.note || '').trim(),
    occurredAt: raw.occurredAt || createdAt,
    createdAt,
  };
}

export function validateInventoryItemInput(
  input: Partial<InventoryItem>,
): { valid: true } | { valid: false; error: string } {
  if (!input.name || !input.name.trim()) {
    return { valid: false, error: 'Item name is required.' };
  }
  if (!input.unit || !input.unit.trim()) {
    return { valid: false, error: 'Stock unit (e.g., sheet, pc, sq ft) is required.' };
  }
  const stockVal = input.quantityOnHand ?? input.currentStock;
  if (stockVal !== undefined && Number(stockVal) < 0) {
    return { valid: false, error: 'Quantity on hand cannot be negative.' };
  }
  const thresholdVal = input.reorderLevel ?? input.reorderThreshold;
  if (thresholdVal !== undefined && Number(thresholdVal) < 0) {
    return { valid: false, error: 'Reorder level cannot be negative.' };
  }
  if (input.unitCost !== undefined && Number(input.unitCost) < 0) {
    return { valid: false, error: 'Unit cost cannot be negative.' };
  }
  return { valid: true };
}

export function validateStockMovementInput(input: {
  movementType?: StockMovementType;
  quantityDelta?: number;
}): { valid: true } | { valid: false; error: string } {
  const delta = Number(input.quantityDelta) || 0;
  if (delta === 0) {
    return { valid: false, error: 'Stock quantity change must be non-zero.' };
  }
  return { valid: true };
}
