export type InventoryItemType = 'consumable' | 'finished_product';
export type StockStatus = 'in_stock' | 'low_stock' | 'out_of_stock';
export type InventorySortField =
  | 'name'
  | 'sku'
  | 'category'
  | 'quantityOnHand'
  | 'currentStock'
  | 'reorderLevel'
  | 'reorderThreshold'
  | 'unitCost'
  | 'status'
  | 'sortOrder'
  | 'updatedAt';
export type InventorySortDirection = 'asc' | 'desc';

export interface InventorySortConfig {
  field: InventorySortField;
  direction: InventorySortDirection;
}

export interface InventorySupplierDetails {
  name: string;
  contact?: string;
  supplierSku?: string;
  leadTimeDays?: number;
}

export type InventorySupplierInfo = InventorySupplierDetails;

export interface SupplyCategory {
  id: string;
  name: string;
  description: string;
  /** Default unit of measurement for items in this category (e.g., sheet, roll, bottle) */
  defaultUnit: string;
  /** Display sort priority (lower numbers appear first) */
  sortOrder: number;
  isDemo?: boolean;
}

export interface InventoryItem {
  id: string;
  sku: string;
  barcode?: string;
  name: string;
  categoryId?: string;
  category: string;
  itemType: InventoryItemType;
  /** Unit of measurement (e.g., sheet, pc, sq ft, bottle, roll) */
  unit: string;
  /** Current physical stock count on hand (canonical DB-mapped field) */
  quantityOnHand: number;
  /** Alias for quantityOnHand representing current stock count */
  currentStock?: number;
  /** Stock reserved/allocated to active print jobs */
  reservedQuantity?: number;
  /** Reorder threshold at or below which low-stock warning triggers (canonical DB-mapped field) */
  reorderLevel: number;
  /** Alias for reorderLevel representing reorder threshold */
  reorderThreshold?: number;
  /** Critical available-stock threshold; zero disables the separate alert. */
  minimumThreshold?: number;
  archivedAt?: string;
  archiveReason?: string;
  /** Recommended restock quantity when ordering from supplier */
  reorderQuantity?: number;
  unitCost: number;
  /** Primary supplier display name (flat column-compatible field) */
  supplier: string;
  /** Optional phone, email, or account rep for supplier */
  supplierContact?: string;
  /** Supplier's own catalog SKU or part code */
  supplierSku?: string;
  leadTimeDays?: number;
  /** Structured supplier details mirrored from flat supplier fields */
  supplierDetails?: InventorySupplierDetails;
  /** Alias for supplierDetails for backward compatibility */
  supplierInfo?: InventorySupplierInfo;
  storageLocation?: string;
  /** Display/catalog sort priority (lower numbers appear first) */
  sortOrder?: number;
  lastRestockedAt?: string | null;
  isDemo?: boolean;
  createdAt?: string;
  updatedAt: string;
}

export type StockMovementType =
  | 'initial_stock'
  | 'purchase'
  | 'sale_deduction'
  | 'job_usage'
  | 'adjustment'
  | 'wastage'
  | 'damaged'
  | 'return';

export type StockMovementReferenceType =
  | 'sale'
  | 'print_job'
  | 'purchase_order'
  | 'manual'
  | 'return'
  | 'system';

export interface StockMovement {
  id: string;
  inventoryItemId: string;
  itemName: string;
  movementType: StockMovementType;
  /** Stock count immediately prior to this movement */
  quantityBefore?: number;
  /** Signed quantity change (+ for additions, - for deductions/usage/wastage) */
  quantityDelta: number;
  /** Stock count immediately after this movement */
  balanceAfter: number;
  unitCostAtMovement?: number;
  /** Explicit supplier price; absent on legacy or unpriced receipts. */
  purchaseUnitCost?: number;
  referenceType?: StockMovementReferenceType;
  reference: string;
  performedById?: string;
  performedBy: string;
  note: string;
  /** Effective date stamp when the stock event occurred */
  occurredAt?: string;
  /** Record creation ISO date stamp */
  createdAt: string;
}

export interface StockAdjustmentPayload {
  movementType: StockMovementType;
  purchaseUnitCost?: number;
  quantityDelta: number;
  note: string;
  reference?: string;
  referenceType?: StockMovementReferenceType;
  occurredAt?: string;
}

export interface InventoryStockFilter {
  search?: string;
  categoryId?: string;
  stockStatus?: StockStatus | 'all';
  itemType?: InventoryItemType | 'all';
  sortBy?: InventorySortField;
  sortDir?: InventorySortDirection;
}

export interface BulkInventoryUpdatePayload {
  ids: string[];
  categoryId?: string;
  category?: string;
  supplier?: string;
  supplierContact?: string;
  supplierSku?: string;
  leadTimeDays?: number;
}
