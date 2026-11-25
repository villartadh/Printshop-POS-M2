export type UserRole = 'admin' | 'manager' | 'cashier' | 'operator';

export interface UserAccount {
  id: string;
  username: string;
  fullName: string;
  email: string;
  role: UserRole;
  isActive: boolean;
  hasPin: boolean;
  preferredLandingModule?: ModuleId;
  createdAt: string;
}

export interface AuthSession {
  token: string;
  user: UserAccount;
  deviceId: string;
  deviceName: string;
  expiresAt: string;
}

export interface AuthorizedSessionRecord {
  id: string;
  userId: string;
  userName: string;
  role: UserRole;
  deviceId: string;
  deviceName: string;
  createdAt: string;
  lastSeenAt: string;
  isCurrent?: boolean;
}

export type ModuleId =
  | 'dashboard'
  | 'pos'
  | 'jobs'
  | 'services'
  | 'inventory'
  | 'customers'
  | 'finance'
  | 'shifts'
  | 'devices'
  | 'settings';

export type ThemeMode = 'light' | 'dark' | 'system';
export type InterfaceDensity = 'comfortable' | 'compact' | 'touch';
export type PlatformPreviewMode = 'auto' | 'windows' | 'web' | 'tablet' | 'mobile';

export interface DesignTokens {
  primary: string;
  primaryHover: string;
  navBackground: string;
  appBackground: string;
  surface: string;
  primaryText: string;
  secondaryText: string;
  border: string;
  success: string;
  warning: string;
  error: string;
  info: string;
  borderRadiusPx: number;
  fontFamily: string;
  fontSizeScale: number; // 0.9 | 1 | 1.1 | 1.2
  sidebarWidthPx: number;
}

export interface BrandingConfig {
  appName: string;
  tagline: string;
  businessName: string;
  storeId: string;
  logoDataUrl: string;
  address: string;
  phone: string;
  email: string;
  taxIdLabel: string;
  taxRegistrationNumber: string;
  receiptHeader: string;
  receiptFooter: string;
  showLogoOnReceipt: boolean;
  showQrOnReceipt: boolean;
}

export interface DashboardWidgetConfig {
  id: string;
  label: string;
  visible: boolean;
  order: number;
}

export interface PosLayoutConfig {
  defaultView: 'grid' | 'list';
  gridColumnsDesktop: number;
  showPriceOnCard: boolean;
  showCategoryOnCard: boolean;
  showUnitOnCard: boolean;
  showQuickItemsBar: boolean;
  sortBy: 'name' | 'price_asc' | 'price_desc' | 'popular';
  cartPanelWidthPx: number;
}

export interface ReceiptTemplateConfig {
  defaultPaperSize: '58mm' | '80mm' | 'A4' | 'Letter' | 'Custom';
  customWidthMm: number;
  defaultDocumentType:
    | 'sales_receipt'
    | 'invoice'
    | 'quotation'
    | 'job_order_slip'
    | 'payment_acknowledgment';
  includeCustomerCopy: boolean;
  includeInternalCopy: boolean;
}

export interface PaymentMethodConfig {
  id: string;
  name: string;
  type: 'cash' | 'ewallet' | 'bank_transfer' | 'card' | 'credit_balance' | 'custom';
  enabled: boolean;
  requiresReference: boolean;
  requiresVerification: boolean;
}

export interface TaxAndCurrencyConfig {
  currencyCode: string;
  currencySymbol: string;
  decimalPlaces: number;
  taxEnabled: boolean;
  taxName: string;
  taxRatePercent: number;
  taxInclusive: boolean;
  roundingMode: 'none' | 'nearest_0_25' | 'nearest_1';
}

export interface ShellPreferences {
  sidebarCollapsed: boolean;
}

export interface AppSettings {
  themeMode: ThemeMode;
  density: InterfaceDensity;
  defaultLandingModule: ModuleId;
  moduleOrder: ModuleId[];
  hiddenModules: ModuleId[];
  shellPreferences?: ShellPreferences;
  tokens: DesignTokens;
  branding: BrandingConfig;
  posLayout: PosLayoutConfig;
  dashboardWidgets: DashboardWidgetConfig[];
  receiptTemplate: ReceiptTemplateConfig;
  paymentMethods: PaymentMethodConfig[];
  taxAndCurrency: TaxAndCurrencyConfig;
  demoModeActive: boolean;
  updatedAt: string;
}

export type PricingMethod =
  | 'per_page'
  | 'per_sheet'
  | 'per_copy'
  | 'per_piece'
  | 'per_set'
  | 'per_sqft'
  | 'per_linear_ft'
  | 'per_meter'
  | 'per_hour'
  | 'fixed'
  | 'tiered_bulk'
  | 'formula';

export interface BulkTier {
  minQty: number;
  unitPrice: number;
}

export interface ServiceAttributeOption {
  id: string;
  label: string;
  priceDelta: number;
  priceMultiplier?: number;
}

export interface ServiceAttributeGroup {
  key:
    | 'paperSize'
    | 'paperType'
    | 'paperThickness'
    | 'colorMode'
    | 'sides'
    | 'finishing'
    | 'materialType';
  label: string;
  options: ServiceAttributeOption[];
  defaultOptionId: string;
}

export interface MaterialConsumptionBinding {
  inventoryItemId: string;
  quantityPerUnit: number; // consumed per page/sheet/piece/sqft
}

export interface ServiceCategory {
  id: string;
  name: string;
  sortOrder: number;
  isDemo?: boolean;
}

export interface PrintService {
  id: string;
  sku: string;
  name: string;
  categoryId: string;
  categoryName?: string;
  description: string;
  iconName: string;
  pricingMethod: PricingMethod;
  basePrice: number;
  unitLabel: string;
  costEstimate: number;
  requiresConfiguration: boolean;
  requiresProductionJob: boolean;
  allowCustomDimensions: boolean;
  allowPagesAndCopies: boolean;
  allowRushFee: boolean;
  rushFeePercent: number;
  defaultDesignFee: number;
  bulkTiers: BulkTier[];
  formulaExpression?: string; // e.g., "base * areaSqFt * copies + finishing + designFee"
  attributeGroups: ServiceAttributeGroup[];
  consumables: MaterialConsumptionBinding[];
  isFavorite: boolean;
  isArchived: boolean;
  isDemo?: boolean;
  updatedAt: string;
}

export interface ServiceConfigSelection {
  quantity: number;
  pages: number;
  copies: number;
  width: number;
  height: number;
  dimensionUnit: 'ft' | 'in' | 'cm' | 'm';
  selectedAttributes: Record<string, string>; // group.key -> option.id
  isRush: boolean;
  designFee: number;
  laborFee: number;
  notes: string;
}

export interface CartItem {
  id: string;
  serviceId: string;
  sku: string;
  serviceName: string;
  pricingMethod: PricingMethod;
  unitLabel: string;
  unitPrice: number;
  effectiveQuantity: number;
  displaySummary: string;
  config: ServiceConfigSelection;
  discountType: 'none' | 'percent' | 'fixed';
  discountValue: number;
  lineSubtotal: number;
  lineDiscount: number;
  lineTotal: number;
  costTotal: number;
  requiresProductionJob: boolean;
  consumables: MaterialConsumptionBinding[];
  isCustomItem?: boolean;
}

export type CustomerGroup = 'regular' | 'vip' | 'corporate' | 'student' | 'reseller';

export interface Customer {
  id: string;
  name: string;
  phone: string;
  email: string;
  address: string;
  group: CustomerGroup;
  discountPercent: number;
  loyaltyPoints: number;
  outstandingBalance: number;
  totalSpent: number;
  notes: string;
  isDemo?: boolean;
  createdAt: string;
}

export interface PaymentEntry {
  id: string;
  methodId: string;
  methodName: string;
  methodType: PaymentMethodConfig['type'];
  amount: number;
  referenceNumber: string;
  verificationStatus: 'verified' | 'pending_verification';
  paidAt: string;
}

export type ProductionStatus =
  | 'quotation'
  | 'awaiting_approval'
  | 'pending'
  | 'in_production'
  | 'quality_checking'
  | 'ready_for_pickup'
  | 'completed'
  | 'cancelled';

export type PaymentStatus = 'unpaid' | 'partial' | 'paid' | 'refunded' | 'voided';

export type ReceiptSnapshot = Pick<AppSettings, 'branding' | 'taxAndCurrency'>;

export interface Transaction {
  receiptSnapshot?: ReceiptSnapshot;
  id: string;
  receiptNumber: string;
  clientMutationId: string;
  sourceOrderId?: string;
  convertedSaleId?: string;
  type: 'sale' | 'quotation' | 'held';
  customerId: string | null;
  customerName: string;
  cashierId: string;
  cashierName: string;
  shiftId: string | null;
  items: CartItem[];
  subtotal: number;
  itemDiscountsTotal: number;
  orderDiscountType: 'none' | 'percent' | 'fixed';
  orderDiscountValue: number;
  orderDiscountAmount: number;
  taxAmount: number;
  totalAmount: number;
  amountPaid: number;
  changeAmount: number;
  balanceDue: number;
  paymentStatus: PaymentStatus;
  payments: PaymentEntry[];
  notes: string;
  syncStatus: 'synced' | 'pending_sync' | 'conflict';
  isDemo?: boolean;
  createdAt: string;
  voidReason?: string;
  refundReason?: string;
}

export interface JobStatusHistoryEntry {
  status: ProductionStatus;
  changedBy: string;
  note: string;
  timestamp: string;
}

export interface PrintJob {
  id: string;
  jobOrderNumber: string;
  transactionId: string | null;
  customerId: string | null;
  customerName: string;
  customerPhone: string;
  title: string;
  specifications: string;
  attachmentNames: string[];
  instructions: string;
  assignedOperator: string;
  productionStatus: ProductionStatus;
  paymentStatus: PaymentStatus;
  totalAmount: number;
  depositAmount: number;
  remainingBalance: number;
  estimatedCompletion: string;
  deadline: string;
  history: JobStatusHistoryEntry[];
  isDemo?: boolean;
  createdAt: string;
  updatedAt: string;
}

export type {
  BulkInventoryUpdatePayload,
  InventoryItem,
  InventoryItemType,
  InventorySortConfig,
  InventorySortDirection,
  InventorySortField,
  InventoryStockFilter,
  InventorySupplierDetails,
  InventorySupplierInfo,
  StockAdjustmentPayload,
  StockMovement,
  StockMovementReferenceType,
  StockMovementType,
  StockStatus,
  SupplyCategory,
} from '../types/inventory.ts';

export interface ExpenseRecord {
  id: string;
  category:
    | 'electricity'
    | 'ink_toner'
    | 'printing_materials'
    | 'maintenance'
    | 'equipment'
    | 'labor'
    | 'rent'
    | 'other';
  description: string;
  amount: number;
  vendor: string;
  recordedBy: string;
  expenseDate: string;
  isDemo?: boolean;
  createdAt: string;
}

export interface CashMovement {
  id: string;
  type: 'cash_in' | 'cash_out';
  amount: number;
  reason: string;
  timestamp: string;
}

export interface CashierShift {
  id: string;
  cashierId: string;
  cashierName: string;
  status: 'open' | 'closed';
  openingCash: number;
  cashSales: number;
  electronicSales: number;
  refundsTotal: number;
  cashMovements: CashMovement[];
  expectedCash: number;
  actualCash: number | null;
  discrepancy: number | null;
  reconciliationNote: string;
  startedAt: string;
  endedAt: string | null;
}

export interface AuditLogEntry {
  id: string;
  actorId: string;
  actorName: string;
  action: string;
  entityType: string;
  entityId: string;
  summary: string;
  createdAt: string;
}

export interface OfflineMutation {
  ownerId?: string;
  id: string;
  clientMutationId: string;
  type: 'CREATE_TRANSACTION' | 'UPDATE_JOB_STATUS' | 'CREATE_EXPENSE' | 'ADJUST_INVENTORY';
  endpoint: string;
  method: 'POST' | 'PUT' | 'PATCH';
  payload: unknown;
  status: 'pending' | 'syncing' | 'conflict' | 'failed';
  retryCount: number;
  errorMessage?: string;
  createdAt: string;
}

export interface DiagnosticLogEntry {
  id: string;
  level: 'info' | 'warn' | 'error';
  category: 'sync' | 'hardware' | 'auth' | 'storage';
  message: string;
  timestamp: string;
}
