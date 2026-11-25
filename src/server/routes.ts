import { attemptGate, validPassword, validPin, removesLastAdmin } from '../shared/securityPolicy.ts';
import { validateSyncEnvelope } from '../shared/offlinePolicy.ts';
import {validatePurchaseCost,weightedPurchaseCost} from '../shared/purchaseCost.ts';
import { recordOrderConversion } from './orderConversion.ts';
import { validateJobPatch } from '../shared/jobWorkflow.ts';
import { reverseSale, collectBalance } from './reversal.ts';
import crypto from 'crypto';
import express from 'express';
import {
  clearDemoDataset,
  db,
  seedDemoDataset,
  writeAuditLog,
} from './db.ts';
import {
  createSessionForUser,
  hashSecret,
  requireAuth,
  requireRole,
  sanitizeUser,
  verifySecret,
  type AuthenticatedRequest,
} from './auth.ts';
import type {
  AppSettings,
  BulkInventoryUpdatePayload,
  CartItem,
  Customer,
  ExpenseRecord,
  InventoryItem,
  PrintJob,
  PrintService,
  ProductionStatus,
  ServiceCategory,
  StockMovement,
  StockMovementType,
  SupplyCategory,
  Transaction,
} from '../shared/types.ts';
import { verifyCatalogPricing } from '../shared/authoritativePricing.ts';
import { validateTransaction } from '../shared/transactionValidation.ts';
import { DEFAULT_SETTINGS } from '../shared/defaults.ts';
import { validateSettings } from '../shared/settingsValidation.ts';
import {
  computeSignedStockDelta,
  inferReferenceType,
  validateInventoryItemInput,
  validateStockMovementInput,
} from '../shared/inventoryModel.ts';

export const apiRouter = express.Router();

function genId(prefix: string) {
  return `${prefix}_${Date.now()}_${crypto.randomBytes(4).toString('hex')}`;
}

function parseJsonSafe<T>(raw: string | null | undefined, fallback: T): T {
  if (!raw) return fallback;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return fallback;
  }
}

function mapInventoryItemRow(i: Record<string, unknown>): InventoryItem {
  const quantityOnHand = Number(i.quantity_on_hand) || 0;
  const reorderLevel = Number(i.reorder_level) || 0;
  const supplier = String(i.supplier || '');
  const supplierContact = String(i.supplier_contact || '');
  const supplierSku = String(i.supplier_sku || '');
  const leadTimeDays = Number(i.lead_time_days) || 0;
  const supplierPayload = supplier
    ? {
        name: supplier,
        contact: supplierContact,
        supplierSku,
        leadTimeDays,
      }
    : undefined;

  return {
    id: String(i.id),
    sku: String(i.sku),
    barcode: String(i.barcode || ''),
    name: String(i.name),
    categoryId: i.category_id ? String(i.category_id) : undefined,
    category: String(i.category),
    itemType: i.item_type as InventoryItem['itemType'],
    unit: String(i.unit),
    quantityOnHand,
    currentStock: quantityOnHand,
    reservedQuantity: Number(i.reserved_quantity) || 0,
    reorderLevel,
    reorderThreshold: reorderLevel,
    minimumThreshold: Number(i.minimum_threshold) || 0,
    reorderQuantity: Number(i.reorder_quantity) || Math.max(reorderLevel * 2, 0),
    unitCost: Number(i.unit_cost) || 0,
    supplier,
    supplierContact,
    supplierSku,
    leadTimeDays,
    supplierDetails: supplierPayload,
    supplierInfo: supplierPayload,
    storageLocation: String(i.storage_location || ''),
    lastRestockedAt: i.last_restocked_at ? String(i.last_restocked_at) : null,
    isDemo: i.is_demo === 1,
    createdAt: i.created_at ? String(i.created_at) : String(i.updated_at),
    updatedAt: String(i.updated_at),
  };
}

function mapStockMovementRow(m: Record<string, unknown>): StockMovement {
  const delta = Number(m.quantity_delta) || 0;
  const balanceAfter = Number(m.balance_after) || 0;
  const quantityBefore =
    m.quantity_before !== undefined && m.quantity_before !== null
      ? Number(m.quantity_before) || 0
      : Math.max(0, Number((balanceAfter - delta).toFixed(2)));
  const movementType = (String(m.movement_type || 'adjustment') as StockMovementType);
  const createdAt = String(m.created_at);

  return {
    id: String(m.id),
    inventoryItemId: String(m.inventory_item_id),
    itemName: String(m.item_name),
    movementType,
    quantityBefore,
    quantityDelta: delta,
    balanceAfter,
    unitCostAtMovement: Number(m.unit_cost_at_movement) || 0,
    purchaseUnitCost: m.purchase_unit_cost == null ? undefined : Number(m.purchase_unit_cost),
    referenceType: (m.reference_type
      ? String(m.reference_type)
      : inferReferenceType(movementType)) as StockMovement['referenceType'],
    reference: String(m.reference || ''),
    performedById: String(m.performed_by_id || ''),
    performedBy: String(m.performed_by),
    note: String(m.note || ''),
    occurredAt: m.occurred_at ? String(m.occurred_at) : createdAt,
    createdAt,
  };
}

function mapServiceRow(row: Record<string, unknown>): PrintService {
  return {
    id: String(row.id),
    sku: String(row.sku),
    name: String(row.name),
    categoryId: String(row.category_id),
    description: String(row.description || ''),
    iconName: String(row.icon_name || 'Printer'),
    pricingMethod: row.pricing_method as PrintService['pricingMethod'],
    basePrice: Number(row.base_price) || 0,
    unitLabel: String(row.unit_label || 'pc'),
    costEstimate: Number(row.cost_estimate) || 0,
    requiresConfiguration: row.requires_configuration === 1,
    requiresProductionJob: row.requires_production_job === 1,
    allowCustomDimensions: row.allow_custom_dimensions === 1,
    allowPagesAndCopies: row.allow_pages_and_copies === 1,
    allowRushFee: row.allow_rush_fee === 1,
    rushFeePercent: Number(row.rush_fee_percent) || 0,
    defaultDesignFee: Number(row.default_design_fee) || 0,
    bulkTiers: parseJsonSafe(row.bulk_tiers_json as string, []),
    formulaExpression: String(row.formula_expression || ''),
    attributeGroups: parseJsonSafe(row.attribute_groups_json as string, []),
    consumables: parseJsonSafe(row.consumables_json as string, []),
    isFavorite: row.is_favorite === 1,
    isArchived: row.is_archived === 1,
    isDemo: row.is_demo === 1,
    updatedAt: String(row.updated_at),
  };
}

function mapTransactionRow(row: Record<string, unknown>): Transaction {
  return {
    receiptSnapshot: row.receipt_snapshot_json ? parseJsonSafe(String(row.receipt_snapshot_json), undefined) : undefined,
    id: String(row.id),
    receiptNumber: String(row.receipt_number),
    clientMutationId: String(row.client_mutation_id),
    type: row.type as Transaction['type'],
    customerId: row.customer_id ? String(row.customer_id) : null,
    customerName: String(row.customer_name || 'Walk-in Customer'),
    cashierId: String(row.cashier_id),
    cashierName: String(row.cashier_name),
    shiftId: row.shift_id ? String(row.shift_id) : null,
    items: parseJsonSafe(row.items_json as string, []),
    subtotal: Number(row.subtotal) || 0,
    itemDiscountsTotal: Number(row.item_discounts_total) || 0,
    orderDiscountType: (row.order_discount_type as Transaction['orderDiscountType']) || 'none',
    orderDiscountValue: Number(row.order_discount_value) || 0,
    orderDiscountAmount: Number(row.order_discount_amount) || 0,
    taxAmount: Number(row.tax_amount) || 0,
    totalAmount: Number(row.total_amount) || 0,
    amountPaid: Number(row.amount_paid) || 0,
    changeAmount: Number(row.change_amount) || 0,
    balanceDue: Number(row.balance_due) || 0,
    paymentStatus: row.payment_status as Transaction['paymentStatus'],
    payments: parseJsonSafe(row.payments_json as string, []),
    notes: String(row.notes || ''),
    syncStatus: (row.sync_status as Transaction['syncStatus']) || 'synced',
    isDemo: row.is_demo === 1,
    createdAt: String(row.created_at),
    voidReason: row.void_reason ? String(row.void_reason) : undefined,
    refundReason: row.refund_reason ? String(row.refund_reason) : undefined,
    convertedSaleId: (db.prepare("SELECT sale_id FROM order_conversions WHERE source_id=?").get(row.id) as {sale_id:string} | undefined)?.sale_id,
  };
}

function mapPrintJobRow(row: Record<string, unknown>): PrintJob {
  return {
    id: String(row.id),
    jobOrderNumber: String(row.job_order_number),
    transactionId: row.transaction_id ? String(row.transaction_id) : null,
    customerId: row.customer_id ? String(row.customer_id) : null,
    customerName: String(row.customer_name),
    customerPhone: String(row.customer_phone || ''),
    title: String(row.title),
    specifications: String(row.specifications || ''),
    attachmentNames: parseJsonSafe(row.attachment_names_json as string, []),
    instructions: String(row.instructions || ''),
    assignedOperator: String(row.assigned_operator || ''),
    productionStatus: row.production_status as ProductionStatus,
    paymentStatus: row.payment_status as PrintJob['paymentStatus'],
    totalAmount: Number(row.total_amount) || 0,
    depositAmount: Number(row.deposit_amount) || 0,
    remainingBalance: Number(row.remaining_balance) || 0,
    estimatedCompletion: String(row.estimated_completion),
    deadline: String(row.deadline),
    history: parseJsonSafe(row.history_json as string, []),
    isDemo: row.is_demo === 1,
    createdAt: String(row.created_at),
    updatedAt: String(row.updated_at),
  };
}

// ---------- AUTH ROUTES ----------
const credentialGate = attemptGate();
const networkCredentialGate = attemptGate();
apiRouter.use(['/auth/pin-login', '/auth/setup-admin'], (_req, res, next) => {
  if (res.locals.networkAccess) return res.status(403).json({error:'This action requires the local POS console.'});
  next();
});
apiRouter.use(['/auth/login', '/auth/pin-login', '/auth/setup-admin'], (req, res, next) => {
  if (req.method !== 'POST') return next();
  const retry = (res.locals.networkAccess ? networkCredentialGate : credentialGate)();
  if (retry) { res.setHeader('Retry-After', String(retry)); return res.status(429).json({error:'Too many authentication attempts. Try again shortly.'}); }
  next();
});

apiRouter.get('/auth/status', (_req, res) => {
  if (res.locals.networkAccess) return res.json({needsSetup:false,quickUsers:[]});
  const adminCount = (
    db.prepare("SELECT COUNT(*) as c FROM users WHERE role = 'admin' AND is_active = 1").get() as {
      c: number;
    }
  ).c;
  const users = db
    .prepare('SELECT id, username, full_name, role, pin_hash FROM users WHERE is_active = 1 ORDER BY created_at ASC')
    .all() as Array<{ id: string; username: string; full_name: string; role: string; pin_hash: string }>;

  res.json({
    needsSetup: adminCount === 0,
    quickUsers: users.map((u) => ({
      id: u.id,
      username: u.username,
      fullName: u.full_name,
      role: u.role,
      hasPin: Boolean(u.pin_hash),
    })),
  });
});

apiRouter.post('/auth/setup-admin', (req, res) => {
  const { username, fullName, email, password, pin, deviceId, deviceName } = req.body || {};
  if (!username || !fullName || !password || !validPassword(password)) {
    return res
      .status(400)
      .json({ error: 'Username, full name, and a password of 8 to 128 characters are required.' });
  }
  if (pin && !validPin(pin)) {
    return res.status(400).json({ error: 'PIN must be 4 to 6 digits.' });
  }

  const existingAdmin = db
    .prepare("SELECT id FROM users WHERE role = 'admin' AND is_active = 1 LIMIT 1")
    .get();
  if (existingAdmin) {
    return res.status(409).json({ error: 'An administrator account already exists. Please sign in.' });
  }

  const userId = genId('usr');
  const now = new Date().toISOString();
  const pwHash = hashSecret(String(password));
  const pinHash = pin ? hashSecret(String(pin)) : '';

  db.prepare(`
    INSERT INTO users (id, username, full_name, email, role, password_hash, pin_hash, is_active, preferred_landing_module, created_at)
    VALUES (?, ?, ?, ?, 'admin', ?, ?, 1, 'pos', ?)
  `).run(
    userId,
    String(username).trim().toLowerCase(),
    String(fullName).trim(),
    String(email || '').trim(),
    pwHash,
    pinHash,
    now,
  );

  const createdUser = db.prepare('SELECT * FROM users WHERE id = ?').get(userId) as never;
  const session = createSessionForUser(
    createdUser,
    String(deviceId || 'web-browser'),
    String(deviceName || 'Web Terminal'),
  );
  res.status(201).json(session);
});

apiRouter.post('/auth/login', (req, res) => {
  const { username, password, deviceId, deviceName } = req.body || {};
  if (typeof username !== 'string' || !username.trim() || username.length > 254 || typeof password !== 'string' || !password || password.length > 128) {
    return res.status(400).json({ error: 'Username and password are required.' });
  }
  const normalized = String(username).trim().toLowerCase();
  const userRow = db
    .prepare('SELECT * FROM users WHERE (LOWER(username) = ? OR LOWER(email) = ?) AND is_active = 1')
    .get(normalized, normalized) as { password_hash: string } | undefined;

  if (!userRow || !verifySecret(String(password), userRow.password_hash)) {
    return res.status(401).json({ error: 'Invalid username or password.' });
  }

  const session = createSessionForUser(
    userRow as never,
    String(deviceId || 'web-browser'),
    String(deviceName || 'Web Terminal'),
  );
  res.json(session);
});

apiRouter.post('/auth/pin-login', (req, res) => {
  const { userId, pin, deviceId, deviceName } = req.body || {};
  if (typeof userId !== 'string' || userId.length > 128 || !validPin(pin)) {
    return res.status(400).json({ error: 'User and PIN are required.' });
  }
  const userRow = db
    .prepare('SELECT * FROM users WHERE id = ? AND is_active = 1')
    .get(String(userId)) as { pin_hash: string } | undefined;

  if (!userRow || !userRow.pin_hash || !verifySecret(String(pin), userRow.pin_hash)) {
    return res.status(401).json({ error: 'Invalid cashier PIN.' });
  }

  const session = createSessionForUser(
    userRow as never,
    String(deviceId || 'web-browser'),
    String(deviceName || 'Web Terminal'),
  );
  res.json(session);
});

apiRouter.post('/auth/logout', requireAuth, (req: AuthenticatedRequest, res) => {
  if (req.sessionId) {
    db.prepare('DELETE FROM sessions WHERE id = ?').run(req.sessionId);
  }
  res.json({ ok: true });
});

apiRouter.get('/auth/sessions', requireAuth, (req: AuthenticatedRequest, res) => {
  const rows = db
    .prepare(`
      SELECT s.id, s.user_id, u.full_name as user_name, u.role, s.device_id, s.device_name, s.created_at, s.last_seen_at
      FROM sessions s
      JOIN users u ON u.id = s.user_id
      WHERE s.expires_at > ? AND (? = 'admin' OR s.user_id = ?)
      ORDER BY s.last_seen_at DESC
    `)
    .all(new Date().toISOString(), req.authUser!.role, req.authUser!.id) as Array<Record<string, unknown>>;

  res.json(
    rows.map((r) => ({
      id: String(r.id),
      userId: String(r.user_id),
      userName: String(r.user_name),
      role: r.role,
      deviceId: String(r.device_id),
      deviceName: String(r.device_name),
      createdAt: String(r.created_at),
      lastSeenAt: String(r.last_seen_at),
      isCurrent: r.id === req.sessionId,
    })),
  );
});

apiRouter.delete('/auth/sessions/:id', requireAuth, (req: AuthenticatedRequest, res) => {
  const targetId = String(req.params.id);
  const existing = db.prepare('SELECT user_id FROM sessions WHERE id = ?').get(targetId) as
    | { user_id: string }
    | undefined;
  if (!existing) {
    return res.status(404).json({ error: 'Session not found.' });
  }
  if (existing.user_id !== req.authUser?.id && req.authUser?.role !== 'admin') {
    return res.status(403).json({ error: 'Only admins can revoke another user session.' });
  }
  db.prepare('DELETE FROM sessions WHERE id = ?').run(targetId);
  res.json({ ok: true });
});

apiRouter.post('/auth/users', requireAuth, requireRole(['admin']), (req: AuthenticatedRequest, res) => {
  const { username, fullName, email, role, password, pin } = req.body || {};
  if (!username || !fullName || !password || !validPassword(password)) {
    return res.status(400).json({ error: 'Username, full name, and 8–128 character password required.' });
  }
  if (!['admin', 'manager', 'cashier', 'operator'].includes(role)) {
    return res.status(400).json({ error: 'Invalid user role.' });
  }
  if (pin && !validPin(pin)) {
    return res.status(400).json({ error: 'PIN must be 4 to 6 digits.' });
  }
  const normalized = String(username).trim().toLowerCase();
  const exists = db.prepare('SELECT id FROM users WHERE LOWER(username) = ?').get(normalized);
  if (exists) {
    return res.status(409).json({ error: 'Username already exists.' });
  }

  const id = genId('usr');
  const now = new Date().toISOString();
  db.prepare(`
    INSERT INTO users (id, username, full_name, email, role, password_hash, pin_hash, is_active, created_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?)
  `).run(
    id,
    normalized,
    String(fullName).trim(),
    String(email || '').trim(),
    role,
    hashSecret(String(password)),
    pin ? hashSecret(String(pin)) : '',
    now,
  );
  writeAuditLog(
    req.authUser!.id,
    req.authUser!.fullName,
    'USER_CREATED',
    'user',
    id,
    `Created ${role} account ${normalized}`,
  );
  const created = db.prepare('SELECT * FROM users WHERE id = ?').get(id) as never;
  res.status(201).json(sanitizeUser(created));
});

apiRouter.patch('/auth/users/:id', requireAuth, requireRole(['admin']), (req: AuthenticatedRequest, res) => {
  const id = String(req.params.id);
  const { fullName, email, role, isActive, newPassword, newPin } = req.body || {};
  const existing = db.prepare('SELECT * FROM users WHERE id = ?').get(id) as Record<string, unknown> | undefined;
  if (!existing) return res.status(404).json({ error: 'User not found.' });

  if (role !== undefined && !['admin','manager','cashier','operator'].includes(role)) return res.status(400).json({error:'Invalid role.'});
  if (newPassword !== undefined && newPassword !== '' && !validPassword(newPassword)) return res.status(400).json({error:'Password must contain 8–128 characters.'});
  if (newPin !== undefined && newPin !== '' && !validPin(newPin)) return res.status(400).json({error:'PIN must be 4 to 6 digits.'});
  const nextActive = typeof isActive === 'boolean' ? Number(isActive) : existing.is_active;
  const adminCount = (db.prepare("SELECT COUNT(*) AS c FROM users WHERE role='admin' AND is_active=1").get() as {c:number}).c;
  if (removesLastAdmin(existing.role, existing.is_active, role ?? existing.role, nextActive, adminCount)) return res.status(409).json({error:'Cannot disable or demote the last active administrator.'});
  const nextPasswordHash =
    validPassword(newPassword)
      ? hashSecret(String(newPassword))
      : String(existing.password_hash);
  const nextPinHash =
    newPin !== undefined
      ? newPin
        ? hashSecret(String(newPin))
        : ''
      : String(existing.pin_hash);

  db.prepare(`
    UPDATE users
    SET full_name = ?, email = ?, role = ?, is_active = ?, password_hash = ?, pin_hash = ?
    WHERE id = ?
  `).run(
    fullName ?? existing.full_name,
    email ?? existing.email,
    role ?? existing.role,
    typeof isActive === 'boolean' ? (isActive ? 1 : 0) : existing.is_active,
    nextPasswordHash,
    nextPinHash,
    id,
  );
  if (nextPasswordHash !== existing.password_hash || nextPinHash !== existing.pin_hash || (role ?? existing.role) !== existing.role || nextActive !== existing.is_active) db.prepare('DELETE FROM sessions WHERE user_id = ?').run(id);
  writeAuditLog(
    req.authUser!.id,
    req.authUser!.fullName,
    'USER_UPDATED',
    'user',
    id,
    `Updated account settings for ${existing.username}`,
  );
  const updated = db.prepare('SELECT * FROM users WHERE id = ?').get(id) as never;
  res.json(sanitizeUser(updated));
});

// ---------- BOOTSTRAP ----------

apiRouter.get('/bootstrap', requireAuth, (_req, res) => {
  const settingsRow = db.prepare('SELECT value_json FROM app_settings WHERE key = ?').get('app_settings') as
    | { value_json: string }
    | undefined;
  const settings: AppSettings = settingsRow
    ? parseJsonSafe(settingsRow.value_json, DEFAULT_SETTINGS)
    : DEFAULT_SETTINGS;

  const categories = (
    db.prepare('SELECT * FROM categories ORDER BY sort_order ASC, name ASC').all() as Array<
      Record<string, unknown>
    >
  ).map((c) => ({
    id: String(c.id),
    name: String(c.name),
    sortOrder: Number(c.sort_order) || 0,
    isDemo: c.is_demo === 1,
  }));

  const services = (
    db.prepare('SELECT * FROM services ORDER BY is_favorite DESC, name ASC').all() as Array<
      Record<string, unknown>
    >
  ).map(mapServiceRow);

  const supplyCategories: SupplyCategory[] = (
    db.prepare('SELECT * FROM supply_categories ORDER BY sort_order ASC, name ASC').all() as Array<
      Record<string, unknown>
    >
  ).map((sc) => ({
    id: String(sc.id),
    name: String(sc.name),
    description: String(sc.description || ''),
    defaultUnit: String(sc.default_unit || 'pc'),
    sortOrder: Number(sc.sort_order) || 0,
    isDemo: sc.is_demo === 1,
  }));

  const inventory = (
    db.prepare('SELECT * FROM inventory_items ORDER BY name ASC').all() as Array<
      Record<string, unknown>
    >
  ).map(mapInventoryItemRow);

  const stockMovements = (
    db.prepare('SELECT * FROM stock_movements ORDER BY created_at DESC LIMIT 200').all() as Array<
      Record<string, unknown>
    >
  ).map(mapStockMovementRow);

  const customers = (
    db.prepare('SELECT * FROM customers ORDER BY name ASC').all() as Array<Record<string, unknown>>
  ).map((c) => ({
    id: String(c.id),
    name: String(c.name),
    phone: String(c.phone || ''),
    email: String(c.email || ''),
    address: String(c.address || ''),
    group: c.customer_group as Customer['group'],
    discountPercent: Number(c.discount_percent) || 0,
    loyaltyPoints: Number(c.loyalty_points) || 0,
    outstandingBalance: Number(c.outstanding_balance) || 0,
    totalSpent: Number(c.total_spent) || 0,
    notes: String(c.notes || ''),
    isDemo: c.is_demo === 1,
    createdAt: String(c.created_at),
  }));

  const transactions = (
    db.prepare('SELECT * FROM transactions ORDER BY created_at DESC LIMIT 300').all() as Array<
      Record<string, unknown>
    >
  ).map(mapTransactionRow);

  const printJobs = (
    db.prepare('SELECT * FROM print_jobs ORDER BY created_at DESC LIMIT 200').all() as Array<
      Record<string, unknown>
    >
  ).map(mapPrintJobRow);

  const expenses = (
    db.prepare('SELECT * FROM expenses ORDER BY expense_date DESC, created_at DESC LIMIT 200').all() as Array<
      Record<string, unknown>
    >
  ).map((e) => ({
    id: String(e.id),
    category: e.category as ExpenseRecord['category'],
    description: String(e.description),
    amount: Number(e.amount) || 0,
    vendor: String(e.vendor || ''),
    recordedBy: String(e.recorded_by),
    expenseDate: String(e.expense_date),
    isDemo: e.is_demo === 1,
    createdAt: String(e.created_at),
  }));

  const shifts = (
    db.prepare('SELECT * FROM cashier_shifts ORDER BY started_at DESC LIMIT 50').all() as Array<
      Record<string, unknown>
    >
  ).map((s) => ({
    id: String(s.id),
    cashierId: String(s.cashier_id),
    cashierName: String(s.cashier_name),
    status: s.status as 'open' | 'closed',
    openingCash: Number(s.opening_cash) || 0,
    cashSales: Number(s.cash_sales) || 0,
    electronicSales: Number(s.electronic_sales) || 0,
    refundsTotal: Number(s.refunds_total) || 0,
    cashMovements: parseJsonSafe(s.cash_movements_json as string, []),
    expectedCash: Number(s.expected_cash) || 0,
    actualCash: s.actual_cash !== null ? Number(s.actual_cash) : null,
    discrepancy: s.discrepancy !== null ? Number(s.discrepancy) : null,
    reconciliationNote: String(s.reconciliation_note || ''),
    startedAt: String(s.started_at),
    endedAt: s.ended_at ? String(s.ended_at) : null,
  }));

  const users = (
    db.prepare('SELECT * FROM users ORDER BY created_at ASC').all() as Array<never>
  ).map(sanitizeUser);

  const auditLogs = (
    db.prepare('SELECT * FROM audit_logs ORDER BY created_at DESC LIMIT 100').all() as Array<
      Record<string, unknown>
    >
  ).map((a) => ({
    id: String(a.id),
    actorId: String(a.actor_id),
    actorName: String(a.actor_name),
    action: String(a.action),
    entityType: String(a.entity_type),
    entityId: String(a.entity_id),
    summary: String(a.summary),
    createdAt: String(a.created_at),
  }));

  res.json({
    settings,
    categories,
    supplyCategories,
    services,
    inventory,
    stockMovements,
    customers,
    transactions,
    printJobs,
    expenses,
    shifts,
    users,
    auditLogs,
    serverTime: new Date().toISOString(),
  });
});

// ---------- DATE-RANGE FINANCIAL REPORTS ----------
apiRouter.get('/reports/finance', requireAuth, (req, res) => {
  const from = req.query.from;
  const to = req.query.to;
  const valid = (v: unknown): v is string => typeof v === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(v) &&
    Number.isFinite(Date.parse(v)) && new Date(v).toISOString().slice(0,10) === v;
  if (!valid(from) || !valid(to) || from > to) return res.status(400).json({error:'Valid reporting date range required.'});
  const start = new Date(from+'T00:00:00+08:00').toISOString();
  const end = new Date(Date.parse(to+'T00:00:00+08:00') + 86400000).toISOString();
  const result = db.transaction(() => {
    const transactions = (db.prepare("SELECT * FROM transactions WHERE type = 'sale' AND created_at >= ? AND created_at < ? ORDER BY created_at DESC, id DESC").all(start,end) as Record<string,unknown>[]).map(mapTransactionRow);
    const expenses = (db.prepare('SELECT * FROM expenses WHERE expense_date >= ? AND expense_date <= ? ORDER BY expense_date DESC, created_at DESC').all(from,to) as Record<string,unknown>[]).map(e=>({
      id:String(e.id), category:e.category as ExpenseRecord['category'], description:String(e.description),
      amount:Number(e.amount)||0, vendor:String(e.vendor||''), recordedBy:String(e.recorded_by),
      expenseDate:String(e.expense_date), isDemo:e.is_demo===1, createdAt:String(e.created_at),
    }));
    return {from,to,transactions,expenses,generatedAt:new Date().toISOString()};
  })();
  res.json(result);
});

// ---------- SETTINGS ----------

apiRouter.put(
  '/settings',
  requireAuth,
  requireRole(['admin', 'manager']),
  (req: AuthenticatedRequest, res) => {
    const incoming = req.body as AppSettings;
    const invalid = validateSettings(incoming);
    if (invalid) return res.status(400).json({ error: invalid });
    const updated = db.transaction(() => {
      const row = db.prepare('SELECT value_json FROM app_settings WHERE key = ?').get('app_settings') as { value_json: string } | undefined;
      const current = row ? parseJsonSafe(row.value_json, DEFAULT_SETTINGS) : DEFAULT_SETTINGS;
      if (incoming.updatedAt !== current.updatedAt) return null;
      const next: AppSettings = {
        ...incoming,
        updatedAt: new Date(Math.max(Date.now(), (Date.parse(current.updatedAt) || 0) + 1)).toISOString(),
      };
      db.prepare('INSERT OR REPLACE INTO app_settings (key, value_json, updated_at) VALUES (?, ?, ?)')
        .run('app_settings', JSON.stringify(next), next.updatedAt);
      writeAuditLog(req.authUser!.id, req.authUser!.fullName, 'SETTINGS_UPDATED', 'app_settings', 'app_settings',
        'Updated business branding, layout, and system settings');
      return next;
    })();
    if (!updated) return res.status(409).json({ error: 'Settings changed on another terminal. Reload settings and review your changes before saving again.' });
    res.json(updated);
  },
);

// ---------- CATEGORIES & SERVICES ----------

apiRouter.post(
  '/categories',
  requireAuth,
  requireRole(['admin', 'manager']),
  (req: AuthenticatedRequest, res) => {
    const { name, sortOrder } = req.body || {};
    if (!name || !String(name).trim()) {
      return res.status(400).json({ error: 'Category name is required.' });
    }
    const cat: ServiceCategory = {
      id: genId('cat'),
      name: String(name).trim(),
      sortOrder: Number(sortOrder) || 10,
      isDemo: false,
    };
    db.prepare('INSERT INTO categories (id, name, sort_order, is_demo) VALUES (?, ?, ?, 0)').run(
      cat.id,
      cat.name,
      cat.sortOrder,
    );
    res.status(201).json(cat);
  },
);

apiRouter.delete(
  '/categories/:id',
  requireAuth,
  requireRole(['admin', 'manager']),
  (req: AuthenticatedRequest, res) => {
    db.prepare('DELETE FROM categories WHERE id = ?').run(String(req.params.id));
    res.json({ ok: true });
  },
);

apiRouter.post(
  '/services',
  requireAuth,
  requireRole(['admin', 'manager']),
  (req: AuthenticatedRequest, res) => {
    const s = req.body as Partial<PrintService>;
    if (!s.name || !s.categoryId || !s.pricingMethod) {
      return res.status(400).json({ error: 'Service name, category, and pricing method are required.' });
    }
    const id = s.id || genId('srv');
    const now = new Date().toISOString();
    db.prepare(`
      INSERT INTO services (
        id, sku, name, category_id, description, icon_name, pricing_method, base_price, unit_label,
        cost_estimate, requires_configuration, requires_production_job, allow_custom_dimensions,
        allow_pages_and_copies, allow_rush_fee, rush_fee_percent, default_design_fee,
        bulk_tiers_json, formula_expression, attribute_groups_json, consumables_json,
        is_favorite, is_archived, is_demo, updated_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
    `).run(
      id,
      s.sku || `SRV-${Date.now().toString().slice(-4)}`,
      String(s.name).trim(),
      s.categoryId,
      s.description || '',
      s.iconName || 'Printer',
      s.pricingMethod,
      Number(s.basePrice) || 0,
      s.unitLabel || 'pc',
      Number(s.costEstimate) || 0,
      s.requiresConfiguration ? 1 : 0,
      s.requiresProductionJob ? 1 : 0,
      s.allowCustomDimensions ? 1 : 0,
      s.allowPagesAndCopies ? 1 : 0,
      s.allowRushFee ? 1 : 0,
      Number(s.rushFeePercent ?? 25) || 0,
      Number(s.defaultDesignFee) || 0,
      JSON.stringify(s.bulkTiers || []),
      s.formulaExpression || '',
      JSON.stringify(s.attributeGroups || []),
      JSON.stringify(s.consumables || []),
      s.isFavorite ? 1 : 0,
      s.isArchived ? 1 : 0,
      now,
    );
    writeAuditLog(
      req.authUser!.id,
      req.authUser!.fullName,
      'SERVICE_CREATED',
      'service',
      id,
      `Created printing service ${s.name}`,
    );
    const created = db.prepare('SELECT * FROM services WHERE id = ?').get(id) as Record<string, unknown>;
    res.status(201).json(mapServiceRow(created));
  },
);

apiRouter.put(
  '/services/:id',
  requireAuth,
  requireRole(['admin', 'manager']),
  (req: AuthenticatedRequest, res) => {
    const id = String(req.params.id);
    const s = req.body as Partial<PrintService>;
    const existing = db.prepare('SELECT id FROM services WHERE id = ?').get(id);
    if (!existing) return res.status(404).json({ error: 'Service not found.' });

    const now = new Date().toISOString();
    db.prepare(`
      UPDATE services SET
        sku = ?, name = ?, category_id = ?, description = ?, icon_name = ?, pricing_method = ?,
        base_price = ?, unit_label = ?, cost_estimate = ?, requires_configuration = ?,
        requires_production_job = ?, allow_custom_dimensions = ?, allow_pages_and_copies = ?,
        allow_rush_fee = ?, rush_fee_percent = ?, default_design_fee = ?, bulk_tiers_json = ?,
        formula_expression = ?, attribute_groups_json = ?, consumables_json = ?, is_favorite = ?,
        is_archived = ?, updated_at = ?
      WHERE id = ?
    `).run(
      s.sku || 'SRV',
      String(s.name || '').trim(),
      s.categoryId || 'cat-print',
      s.description || '',
      s.iconName || 'Printer',
      s.pricingMethod || 'fixed',
      Number(s.basePrice) || 0,
      s.unitLabel || 'pc',
      Number(s.costEstimate) || 0,
      s.requiresConfiguration ? 1 : 0,
      s.requiresProductionJob ? 1 : 0,
      s.allowCustomDimensions ? 1 : 0,
      s.allowPagesAndCopies ? 1 : 0,
      s.allowRushFee ? 1 : 0,
      Number(s.rushFeePercent ?? 25) || 0,
      Number(s.defaultDesignFee) || 0,
      JSON.stringify(s.bulkTiers || []),
      s.formulaExpression || '',
      JSON.stringify(s.attributeGroups || []),
      JSON.stringify(s.consumables || []),
      s.isFavorite ? 1 : 0,
      s.isArchived ? 1 : 0,
      now,
      id,
    );
    const updated = db.prepare('SELECT * FROM services WHERE id = ?').get(id) as Record<string, unknown>;
    res.json(mapServiceRow(updated));
  },
);

apiRouter.delete(
  '/services/:id',
  requireAuth,
  requireRole(['admin', 'manager']),
  (req: AuthenticatedRequest, res) => {
    const id = String(req.params.id);
    db.prepare('DELETE FROM services WHERE id = ?').run(id);
    writeAuditLog(
      req.authUser!.id,
      req.authUser!.fullName,
      'SERVICE_DELETED',
      'service',
      id,
      `Deleted service ${id}`,
    );
    res.json({ ok: true });
  },
);

// ---------- TRANSACTIONS (IDEMPOTENT) ----------

function applyTransactionCreation(
  payload: Transaction,
  actorId: string,
  actorName: string,
  actorRole: string,
): { transaction: Transaction; duplicate: boolean } {
  validateTransaction(payload);
  const existing = db
    .prepare('SELECT * FROM transactions WHERE client_mutation_id = ?')
    .get(payload.clientMutationId) as Record<string, unknown> | undefined;

  if (existing) {
    if (existing.cashier_id !== actorId) throw Object.assign(new Error("Mutation belongs to another cashier."), {statusCode:409});
    return { transaction: mapTransactionRow(existing), duplicate: true };
  }

  const settingsRow = db.prepare('SELECT value_json FROM app_settings WHERE key = ?').get('app_settings') as { value_json: string } | undefined;
  const pricingSettings = settingsRow ? parseJsonSafe(settingsRow.value_json, DEFAULT_SETTINGS) : DEFAULT_SETTINGS;
  payload = verifyCatalogPricing(payload, id => {
    const row = db.prepare('SELECT * FROM services WHERE id = ?').get(id) as Record<string, unknown> | undefined;
    return row ? mapServiceRow(row) : undefined;
  }, pricingSettings, actorRole);
  const txId = payload.id || genId('txn');
  const receiptNumber =
    payload.receiptNumber ||
    `${payload.type === 'quotation' ? 'QT' : payload.type === 'held' ? 'HLD' : 'OR'}-${new Date()
      .toISOString()
      .slice(2, 10)
      .replace(/-/g, '')}-${Math.floor(1000 + Math.random() * 9000)}`;
  const now = payload.createdAt || new Date().toISOString();

  const runAtomic = db.transaction(() => {
    if (payload.sourceOrderId) {
      recordOrderConversion(db, payload, txId);
    }
    if (payload.type === 'sale' && (!payload.shiftId || !db.prepare("SELECT id FROM cashier_shifts WHERE id = ? AND cashier_id = ? AND status = 'open'").get(payload.shiftId, actorId))) throw Object.assign(new Error('Sale requires an open shift belonging to the cashier.'), { statusCode: 409 });
    if (payload.type === 'sale' && payload.balanceDue > 0 && !payload.customerId) throw Object.assign(new Error('Credit sales require a registered customer.'), {statusCode:400});
    if (payload.customerId && !db.prepare('SELECT id FROM customers WHERE id = ?').get(payload.customerId)) throw Object.assign(new Error('Customer not found.'), { statusCode: 400 });
    if (db.prepare('SELECT id FROM transactions WHERE receipt_number = ?').get(receiptNumber)) throw Object.assign(new Error('Receipt number already exists.'), { statusCode: 409 });
    db.prepare(`
      INSERT INTO transactions (
        id, receipt_number, client_mutation_id, type, customer_id, customer_name, cashier_id,
        cashier_name, shift_id, items_json, subtotal, item_discounts_total, order_discount_type,
        order_discount_value, order_discount_amount, tax_amount, total_amount, amount_paid,
        change_amount, balance_due, payment_status, payments_json, notes, sync_status, is_demo, created_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'synced', 0, ?)
    `).run(
      txId,
      receiptNumber,
      payload.clientMutationId,
      payload.type,
      payload.customerId || null,
      payload.customerName || 'Walk-in Customer',
      actorId,
      actorName,
      payload.shiftId || null,
      JSON.stringify(payload.items || []),
      Number(payload.subtotal) || 0,
      Number(payload.itemDiscountsTotal) || 0,
      payload.orderDiscountType || 'none',
      Number(payload.orderDiscountValue) || 0,
      Number(payload.orderDiscountAmount) || 0,
      Number(payload.taxAmount) || 0,
      Number(payload.totalAmount) || 0,
      Number(payload.amountPaid) || 0,
      Number(payload.changeAmount) || 0,
      Number(payload.balanceDue) || 0,
      payload.paymentStatus,
      JSON.stringify(payload.payments || []),
      payload.notes || '',
      now,
    );

    db.prepare('UPDATE transactions SET receipt_snapshot_json = ? WHERE id = ?').run(JSON.stringify({branding:pricingSettings.branding,taxAndCurrency:pricingSettings.taxAndCurrency}),txId);

    // If this is a real completed or partial sale (not held or quotation), deduct inventory consumables, update customer, and update shift
    if (payload.type === 'sale') {
      const items = (payload.items || []) as CartItem[];

      // Deduct consumables
      for (const item of items) {
        for (const binding of item.consumables || []) {
          const deductQty = Number((binding.quantityPerUnit * item.effectiveQuantity).toFixed(2));
          if (deductQty <= 0) continue;
          const invRow = db
            .prepare('SELECT id, name, quantity_on_hand, reserved_quantity, unit_cost FROM inventory_items WHERE id = ?')
            .get(binding.inventoryItemId) as
            | { id: string; name: string; quantity_on_hand: number; reserved_quantity: number; unit_cost: number }
            | undefined;
          if (!invRow) throw Object.assign(new Error("Configured consumable not found."), { statusCode: 409 });
          if (invRow) {
            const qtyBefore = Number(invRow.quantity_on_hand) || 0;
            if (!Number.isFinite(deductQty) || deductQty > Number((qtyBefore - Math.max(0,Number(invRow.reserved_quantity)||0)).toFixed(2))) throw Object.assign(new Error(`Insufficient stock: ${invRow.name}`), { statusCode: 409 });
            const nextQty = Number((qtyBefore - deductQty).toFixed(2));
            db.prepare('UPDATE inventory_items SET quantity_on_hand = ?, updated_at = ? WHERE id = ?').run(
              nextQty,
              now,
              invRow.id,
            );
            db.prepare(`
              INSERT INTO stock_movements (
                id, inventory_item_id, item_name, movement_type, quantity_before, quantity_delta,
                balance_after, unit_cost_at_movement, reference_type, reference, performed_by_id,
                performed_by, note, occurred_at, created_at
              ) VALUES (?, ?, ?, 'sale_deduction', ?, ?, ?, ?, 'sale', ?, ?, ?, ?, ?, ?)
            `).run(
              genId('mov'),
              invRow.id,
              invRow.name,
              qtyBefore,
              -deductQty,
              nextQty,
              Number(invRow.unit_cost) || 0,
              receiptNumber,
              actorId,
              actorName,
              `Auto-deducted for ${item.serviceName}`,
              now,
              now,
            );
          }
        }
      }

      // Create production print job if any item requires production job
      const prodItems = items.filter((i) => i.requiresProductionJob);
      if (prodItems.length > 0) {
        const jobId = genId('job');
        const joNumber = `JO-${jobId.slice(4)}`;
        const custRow = payload.customerId
          ? (db.prepare('SELECT phone FROM customers WHERE id = ?').get(payload.customerId) as
              | { phone: string }
              | undefined)
          : undefined;
        const specs = prodItems.map((i) => `${i.serviceName} (${i.displaySummary})`).join('; ');
        db.prepare(`
          INSERT INTO print_jobs (
            id, job_order_number, transaction_id, customer_id, customer_name, customer_phone,
            title, specifications, attachment_names_json, instructions, assigned_operator,
            production_status, payment_status, total_amount, deposit_amount, remaining_balance,
            estimated_completion, deadline, history_json, is_demo, created_at, updated_at
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, '[]', ?, '', 'pending', ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
        `).run(
          jobId,
          joNumber,
          txId,
          payload.customerId || null,
          payload.customerName || 'Walk-in Customer',
          custRow?.phone || '',
          prodItems[0].serviceName + (prodItems.length > 1 ? ` +${prodItems.length - 1}` : ''),
          specs,
          payload.notes || '',
          payload.paymentStatus,
          Number(payload.totalAmount) || 0,
          Number(payload.amountPaid) || 0,
          Number(payload.balanceDue) || 0,
          new Date(Date.now() + 3600000 * 4).toISOString(),
          new Date(Date.now() + 3600000 * 24).toISOString(),
          JSON.stringify([
            {
              status: 'pending',
              changedBy: actorName,
              note: `Created from POS transaction ${receiptNumber}`,
              timestamp: now,
            },
          ]),
          now,
          now,
        );
      }

      // Update customer spend, points, and outstanding balance
      if (payload.customerId) {
        const pointsEarned = Math.floor(Number(payload.amountPaid) / 10);
        db.prepare(`
          UPDATE customers
          SET total_spent = total_spent + ?,
              loyalty_points = loyalty_points + ?,
              outstanding_balance = outstanding_balance + ?
          WHERE id = ?
        `).run(
          Number(payload.amountPaid) || 0,
          pointsEarned,
          Number(payload.balanceDue) || 0,
          payload.customerId,
        );
      }

      // Update active cashier shift if linked
      if (payload.shiftId) {
        const cashPaid = (payload.payments || [])
          .filter((p) => p.methodType === 'cash')
          .reduce((s, p) => s + Number(p.amount), 0);
        const netCashAdded = Math.max(0, cashPaid - (Number(payload.changeAmount) || 0));
        const electronicPaid = (payload.payments || [])
          .filter((p) => p.methodType !== 'cash' && p.methodType !== 'credit_balance')
          .reduce((s, p) => s + Number(p.amount), 0);

        db.prepare(`
          UPDATE cashier_shifts
          SET cash_sales = cash_sales + ?,
              electronic_sales = electronic_sales + ?,
              expected_cash = expected_cash + ?
          WHERE id = ? AND status = 'open'
        `).run(netCashAdded, electronicPaid, netCashAdded, payload.shiftId);
      }
    }

    writeAuditLog(
      actorId,
      actorName,
      `TXN_${payload.type.toUpperCase()}`,
      'transaction',
      txId,
      `${receiptNumber} · ${payload.customerName} · ${payload.totalAmount}`,
    );
  });

  runAtomic();
  const saved = db.prepare('SELECT * FROM transactions WHERE id = ?').get(txId) as Record<string, unknown>;
  return { transaction: mapTransactionRow(saved), duplicate: false };
}

apiRouter.post('/transactions', requireAuth, (req: AuthenticatedRequest, res) => {
  const payload = req.body as Transaction;
  if (!payload || !payload.clientMutationId || !Array.isArray(payload.items) || payload.items.length === 0) {
    return res.status(400).json({ error: 'Transaction items and clientMutationId are required.' });
  }
  const result = applyTransactionCreation(payload, req.authUser!.id, req.authUser!.fullName, req.authUser!.role);
  res.status(result.duplicate ? 200 : 201).json(result);
});

apiRouter.delete('/transactions/:id/held', requireAuth, (req: AuthenticatedRequest, res) => {
  db.prepare("DELETE FROM transactions WHERE id = ? AND type = 'held' AND NOT EXISTS (SELECT 1 FROM order_conversions WHERE source_id=transactions.id)").run(String(req.params.id));
  res.json({ ok: true });
});

for (const action of ['void', 'refund'] as const) {
  apiRouter.post(`/transactions/:id/${action}`, requireAuth, requireRole(['admin', 'manager']), (req: AuthenticatedRequest, res) => {
    const id = String(req.params.id);
    const reason = typeof req.body?.reason === 'string' ? req.body.reason.trim() : '';
    const actor = req.authUser!;
    const kind = action === 'void' ? 'voided' : 'refunded';
    const updated = db.transaction(() => {
      const result = reverseSale(db,id,kind,reason,actor.id,actor.fullName);
      writeAuditLog(actor.id,actor.fullName,`TXN_${kind.toUpperCase()}`,'transaction',id,`${reason}; cash ${result.cash}; electronic ${result.electronic}; cancelled receivable ${result.due}`);
      return db.prepare('SELECT * FROM transactions WHERE id=?').get(id) as Record<string,unknown>;
    })();
    res.json(mapTransactionRow(updated));
  });
}

// ---------- PRINT JOBS ----------

apiRouter.post('/print-jobs', requireAuth, (req: AuthenticatedRequest, res) => {
  const j = req.body as Partial<PrintJob>;
  if (!j.title || !j.customerName) {
    return res.status(400).json({ error: 'Job title and customer name are required.' });
  }
  const id = genId('job');
  const joNumber = `JO-${id.slice(4)}`;
  const now = new Date().toISOString();
  const total = Number(j.totalAmount);
  if (!Number.isFinite(total) || total < 0 || Number(j.depositAmount || 0) !== 0 || j.transactionId || j.paymentStatus || j.productionStatus) return res.status(400).json({error:'Manual jobs are unpaid quotations only. Record payments through POS.'});
  if (j.customerId && !db.prepare('SELECT id FROM customers WHERE id=?').get(j.customerId)) return res.status(400).json({error:'Customer not found.'});
  const deposit = 0;
  const remaining = total;
  const paymentStatus = total === 0 ? 'paid' : 'unpaid';

  db.prepare(`
    INSERT INTO print_jobs (
      id, job_order_number, transaction_id, customer_id, customer_name, customer_phone,
      title, specifications, attachment_names_json, instructions, assigned_operator,
      production_status, payment_status, total_amount, deposit_amount, remaining_balance,
      estimated_completion, deadline, history_json, is_demo, created_at, updated_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
  `).run(
    id,
    joNumber,
    j.transactionId || null,
    j.customerId || null,
    j.customerName,
    j.customerPhone || '',
    j.title,
    j.specifications || '',
    JSON.stringify(j.attachmentNames || []),
    j.instructions || '',
    j.assignedOperator || req.authUser!.fullName,
    'quotation',
    paymentStatus,
    total,
    deposit,
    remaining,
    j.estimatedCompletion || new Date(Date.now() + 3600000 * 4).toISOString(),
    j.deadline || new Date(Date.now() + 3600000 * 24).toISOString(),
    JSON.stringify([
      {
        status: 'quotation',
        changedBy: req.authUser!.fullName,
        note: 'Job order logged',
        timestamp: now,
      },
    ]),
    now,
    now,
  );
  const saved = db.prepare('SELECT * FROM print_jobs WHERE id = ?').get(id) as Record<string, unknown>;
  res.status(201).json(mapPrintJobRow(saved));
});

apiRouter.patch('/print-jobs/:id', requireAuth, (req: AuthenticatedRequest, res) => {
  const id = String(req.params.id);
  const existing = db.prepare('SELECT * FROM print_jobs WHERE id = ?').get(id) as
    | Record<string, unknown>
    | undefined;
  if (!existing) return res.status(404).json({ error: 'Print job not found.' });

  const patch = req.body || {};
  const { productionStatus, assignedOperator, instructions, note } = patch;
  const linkedSale = existing.transaction_id ? db.prepare("SELECT payment_status,balance_due FROM transactions WHERE id=? AND type='sale'").get(existing.transaction_id) as {payment_status:string;balance_due:number} | undefined : undefined;
  if (existing.transaction_id && !linkedSale) return res.status(409).json({error:'Linked sale not found. Reconcile the job before updating.'});
  try { validateJobPatch(existing.production_status as ProductionStatus, linkedSale?.payment_status ?? String(existing.payment_status), linkedSale?.balance_due ?? Number(existing.remaining_balance), Boolean(linkedSale), Number(existing.total_amount), patch); }
  catch (error) { return res.status(409).json({error: (error as Error).message}); }

  const now = new Date().toISOString();
  const history = parseJsonSafe<Array<Record<string, unknown>>>(existing.history_json as string, []);
  if (productionStatus && productionStatus !== existing.production_status) {
    history.push({
      status: productionStatus,
      changedBy: req.authUser!.fullName,
      note: note || `Moved to ${String(productionStatus).replace(/_/g, ' ')}`,
      timestamp: now,
    });
  }

  const total = Number(existing.total_amount) || 0;
  const nextDeposit = Number(existing.deposit_amount);
  const nextRemaining = Math.max(0, total - nextDeposit);

  db.prepare(`
    UPDATE print_jobs
    SET production_status = ?, payment_status = ?, assigned_operator = ?, instructions = ?,
        deposit_amount = ?, remaining_balance = ?, history_json = ?, updated_at = ?
    WHERE id = ?
  `).run(
    productionStatus ?? existing.production_status,
    existing.payment_status,
    assignedOperator ?? existing.assigned_operator,
    instructions ?? existing.instructions,
    nextDeposit,
    nextRemaining,
    JSON.stringify(history),
    now,
    id,
  );

  const updated = db.prepare('SELECT * FROM print_jobs WHERE id = ?').get(id) as Record<string, unknown>;
  res.json(mapPrintJobRow(updated));
});

// ---------- INVENTORY ----------

apiRouter.get('/inventory', requireAuth, (_req, res) => {
  const supplyCategories: SupplyCategory[] = (
    db.prepare('SELECT * FROM supply_categories ORDER BY sort_order ASC, name ASC').all() as Array<
      Record<string, unknown>
    >
  ).map((sc) => ({
    id: String(sc.id),
    name: String(sc.name),
    description: String(sc.description || ''),
    defaultUnit: String(sc.default_unit || 'pc'),
    sortOrder: Number(sc.sort_order) || 0,
    isDemo: sc.is_demo === 1,
  }));

  const items = (
    db.prepare('SELECT * FROM inventory_items ORDER BY name ASC').all() as Array<
      Record<string, unknown>
    >
  ).map(mapInventoryItemRow);

  const stockMovements = (
    db.prepare('SELECT * FROM stock_movements ORDER BY created_at DESC LIMIT 200').all() as Array<
      Record<string, unknown>
    >
  ).map(mapStockMovementRow);

  res.json({
    items,
    stockMovements,
    supplyCategories,
    serverTime: new Date().toISOString(),
  });
});

apiRouter.post(
  '/inventory',
  requireAuth,
  requireRole(['admin', 'manager']),
  (req: AuthenticatedRequest, res) => {
    const i = req.body as Partial<InventoryItem>;
    const validation = validateInventoryItemInput(i);
    if (validation.valid === false) {
      return res.status(400).json({ error: validation.error });
    }
    const id = genId('inv');
    const now = new Date().toISOString();
    const initialQty = Math.max(0, Number(i.quantityOnHand ?? i.currentStock) || 0);
    const reorderLevel = Math.max(0, Number(i.reorderLevel ?? i.reorderThreshold) || 0);
    const reorderQuantity =
      i.reorderQuantity !== undefined
        ? Math.max(0, Number(i.reorderQuantity) || 0)
        : Math.max(reorderLevel * 2, 0);
    const unitCost = Math.max(0, Number(i.unitCost) || 0);
    if (Number(i.reservedQuantity ?? 0) > initialQty) return res.status(400).json({error:'Reserved stock cannot exceed stock on hand.'});
    const itemName = i.name!.trim();

    const supplierDetails = i.supplierDetails ?? i.supplierInfo;
    const supplierName = (i.supplier ?? supplierDetails?.name ?? '').trim();
    const supplierContact = (i.supplierContact ?? supplierDetails?.contact ?? '').trim();
    const supplierSku = (i.supplierSku ?? supplierDetails?.supplierSku ?? '').trim();
    const leadTimeDays = Math.max(0, Number(i.leadTimeDays ?? supplierDetails?.leadTimeDays) || 0);

    const run = db.transaction(() => {
      db.prepare(`
        INSERT INTO inventory_items (
          id, sku, barcode, name, category_id, category, item_type, unit, quantity_on_hand, reserved_quantity,
          reorder_level, minimum_threshold, reorder_quantity, unit_cost, supplier, supplier_contact, supplier_sku,
          lead_time_days, storage_location, last_restocked_at, is_demo, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
      `).run(
        id,
        (i.sku || '').trim() || `MAT-${Date.now().toString().slice(-4)}`,
        (i.barcode || '').trim(),
        itemName,
        (i.categoryId || '').trim(),
        (i.category || 'Supplies').trim(),
        i.itemType === 'finished_product' ? 'finished_product' : 'consumable',
        i.unit!.trim(),
        initialQty,
        Math.max(0, Number(i.reservedQuantity) || 0),
        reorderLevel,
        i.minimumThreshold ?? 0,
        reorderQuantity,
        unitCost,
        supplierName,
        supplierContact,
        supplierSku,
        leadTimeDays,
        (i.storageLocation || '').trim(),
        initialQty > 0 ? now : null,
        now,
        now,
      );

      if (initialQty > 0) {
        db.prepare(`
          INSERT INTO stock_movements (
            id, inventory_item_id, item_name, movement_type, quantity_before, quantity_delta,
            balance_after, unit_cost_at_movement, reference_type, reference, performed_by_id,
            performed_by, note, occurred_at, created_at
          ) VALUES (?, ?, ?, 'initial_stock', 0, ?, ?, ?, 'system', 'INIT', ?, ?, 'Initial opening stock balance', ?, ?)
        `).run(
          genId('mov'),
          id,
          itemName,
          initialQty,
          initialQty,
          unitCost,
          req.authUser!.id,
          req.authUser!.fullName,
          now,
          now,
        );
      }
    });
    run();

    const created = db.prepare('SELECT * FROM inventory_items WHERE id = ?').get(id) as Record<string, unknown>;
    res.status(201).json(mapInventoryItemRow(created));
  },
);

apiRouter.patch(
  '/inventory/bulk',
  requireAuth,
  requireRole(['admin', 'manager']),
  (req: AuthenticatedRequest, res) => {
    const body = (req.body || {}) as Partial<BulkInventoryUpdatePayload>;
    const rawIds = Array.isArray(body.ids) ? body.ids : [];
    const ids = Array.from(
      new Set(
        rawIds
          .filter((id): id is string => typeof id === 'string')
          .map((id) => id.trim())
          .filter(Boolean),
      ),
    );

    if (ids.length === 0) {
      return res.status(400).json({ error: 'Select at least one inventory item to update.' });
    }
    if (ids.length > 500) {
      return res.status(400).json({ error: 'Cannot bulk-update more than 500 items at once.' });
    }

    const hasCategoryUpdate = body.categoryId !== undefined || body.category !== undefined;
    const hasSupplierUpdate =
      body.supplier !== undefined ||
      body.supplierContact !== undefined ||
      body.supplierSku !== undefined ||
      body.leadTimeDays !== undefined;

    if (!hasCategoryUpdate && !hasSupplierUpdate) {
      return res.status(400).json({
        error: 'Provide a supply category or supplier update to apply.',
      });
    }

    let resolvedCategoryId: string | undefined;
    let resolvedCategoryName: string | undefined;

    if (hasCategoryUpdate) {
      const incomingCatId =
        typeof body.categoryId === 'string' ? body.categoryId.trim() : '';
      const incomingCatName =
        typeof body.category === 'string' ? body.category.trim() : '';

      if (incomingCatId) {
        const catRow = db
          .prepare('SELECT id, name FROM supply_categories WHERE id = ?')
          .get(incomingCatId) as { id: string; name: string } | undefined;
        if (!catRow && !incomingCatName) {
          return res.status(400).json({ error: 'Selected supply category was not found.' });
        }
        resolvedCategoryId = catRow ? catRow.id : incomingCatId;
        resolvedCategoryName = incomingCatName || (catRow ? catRow.name : 'Supplies');
      } else {
        if (!incomingCatName) {
          return res.status(400).json({ error: 'Category name cannot be empty.' });
        }
        resolvedCategoryId = '';
        resolvedCategoryName = incomingCatName;
      }
    }

    const placeholders = ids.map(() => '?').join(', ');
    const existingRows = db
      .prepare(`SELECT * FROM inventory_items WHERE id IN (${placeholders})`)
      .all(...ids) as Array<Record<string, unknown>>;

    if (existingRows.length === 0) {
      return res.status(404).json({ error: 'No matching inventory items found.' });
    }

    const now = new Date().toISOString();
    const incomingSupplier =
      body.supplier !== undefined ? String(body.supplier).trim() : undefined;
    const incomingSupplierContact =
      body.supplierContact !== undefined ? String(body.supplierContact).trim() : undefined;
    const incomingSupplierSku =
      body.supplierSku !== undefined ? String(body.supplierSku).trim() : undefined;
    const incomingLeadTime =
      body.leadTimeDays !== undefined && Number.isFinite(Number(body.leadTimeDays))
        ? Math.max(0, Math.round(Number(body.leadTimeDays)))
        : undefined;

    const updateStmt = db.prepare(`
      UPDATE inventory_items
      SET category_id = ?, category = ?, supplier = ?, supplier_contact = ?,
          supplier_sku = ?, lead_time_days = ?, updated_at = ?
      WHERE id = ?
    `);

    const run = db.transaction(() => {
      for (const existing of existingRows) {
        const nextCategoryId = hasCategoryUpdate
          ? (resolvedCategoryId ?? '')
          : String(existing.category_id || '');
        const nextCategory = hasCategoryUpdate
          ? (resolvedCategoryName ?? String(existing.category || 'Supplies'))
          : String(existing.category || 'Supplies');

        const existingSupplier = String(existing.supplier || '');
        const nextSupplier =
          incomingSupplier !== undefined ? incomingSupplier : existingSupplier;
        const supplierChanged =
          incomingSupplier !== undefined &&
          incomingSupplier.toLowerCase() !== existingSupplier.toLowerCase();

        const nextSupplierContact =
          incomingSupplierContact !== undefined
            ? incomingSupplierContact
            : supplierChanged
              ? ''
              : String(existing.supplier_contact || '');
        const nextSupplierSku =
          incomingSupplierSku !== undefined
            ? incomingSupplierSku
            : supplierChanged
              ? ''
              : String(existing.supplier_sku || '');
        const nextLeadTime =
          incomingLeadTime !== undefined
            ? incomingLeadTime
            : supplierChanged
              ? 0
              : Number(existing.lead_time_days) || 0;

        updateStmt.run(
          nextCategoryId,
          nextCategory,
          nextSupplier,
          nextSupplierContact,
          nextSupplierSku,
          nextLeadTime,
          now,
          String(existing.id),
        );
      }

      const changedParts: string[] = [];
      if (hasCategoryUpdate && resolvedCategoryName) {
        changedParts.push(`category → ${resolvedCategoryName}`);
      }
      if (incomingSupplier !== undefined) {
        changedParts.push(`supplier → ${incomingSupplier || 'Unassigned'}`);
      }
      writeAuditLog(
        req.authUser!.id,
        req.authUser!.fullName,
        'INVENTORY_BULK_UPDATED',
        'inventory_item',
        ids.slice(0, 5).join(','),
        `Bulk updated ${existingRows.length} item(s) (${changedParts.join(', ')})`,
      );
    });
    run();

    const updatedRows = db
      .prepare(`SELECT * FROM inventory_items WHERE id IN (${placeholders}) ORDER BY name ASC`)
      .all(...ids) as Array<Record<string, unknown>>;

    res.json({
      items: updatedRows.map(mapInventoryItemRow),
      updatedCount: updatedRows.length,
    });
  },
);

apiRouter.patch(
  '/inventory/:id',
  requireAuth,
  requireRole(['admin', 'manager']),
  (req: AuthenticatedRequest, res) => {
    const id = String(req.params.id);
    const existing = db.prepare('SELECT * FROM inventory_items WHERE id = ?').get(id) as
      | Record<string, unknown>
      | undefined;
    if (!existing) {
      return res.status(404).json({ error: 'Inventory item not found.' });
    }
    const patch = req.body as Partial<InventoryItem>;
    const validation = validateInventoryItemInput({...mapInventoryItemRow(existing),...patch,reorderLevel:patch.reorderLevel ?? patch.reorderThreshold ?? Number(existing.reorder_level)});
    if (validation.valid === false) return res.status(400).json({error:validation.error});
    if (patch.reservedQuantity !== undefined && Number(patch.reservedQuantity)>Number(existing.quantity_on_hand)) return res.status(400).json({error:'Reserved stock cannot exceed stock on hand.'});
    const now = new Date().toISOString();

    const patchDetails = patch.supplierDetails ?? patch.supplierInfo;
    const nextSupplier =
      patch.supplier !== undefined
        ? String(patch.supplier).trim()
        : patchDetails?.name !== undefined
          ? String(patchDetails.name).trim()
          : String(existing.supplier || '');
    const nextSupplierContact =
      patch.supplierContact !== undefined
        ? String(patch.supplierContact).trim()
        : patchDetails?.contact !== undefined
          ? String(patchDetails.contact).trim()
          : String(existing.supplier_contact || '');
    const nextSupplierSku =
      patch.supplierSku !== undefined
        ? String(patch.supplierSku).trim()
        : patchDetails?.supplierSku !== undefined
          ? String(patchDetails.supplierSku).trim()
          : String(existing.supplier_sku || '');
    const nextLeadTime =
      patch.leadTimeDays !== undefined
        ? Math.max(0, Number(patch.leadTimeDays) || 0)
        : patchDetails?.leadTimeDays !== undefined
          ? Math.max(0, Number(patchDetails.leadTimeDays) || 0)
          : Number(existing.lead_time_days) || 0;

    const patchReorder = patch.reorderLevel ?? patch.reorderThreshold;

    db.prepare(`
      UPDATE inventory_items
      SET sku = ?, barcode = ?, name = ?, category_id = ?, category = ?, item_type = ?, unit = ?,
          reserved_quantity = ?, reorder_level = ?, minimum_threshold = ?, reorder_quantity = ?, unit_cost = ?,
          supplier = ?, supplier_contact = ?, supplier_sku = ?, lead_time_days = ?,
          storage_location = ?, updated_at = ?
      WHERE id = ?
    `).run(
      patch.sku !== undefined ? String(patch.sku).trim() : String(existing.sku),
      patch.barcode !== undefined ? String(patch.barcode).trim() : String(existing.barcode || ''),
      patch.name !== undefined ? String(patch.name).trim() : String(existing.name),
      patch.categoryId !== undefined
        ? String(patch.categoryId).trim()
        : String(existing.category_id || ''),
      patch.category !== undefined ? String(patch.category).trim() : String(existing.category),
      patch.itemType === 'finished_product' || patch.itemType === 'consumable'
        ? patch.itemType
        : String(existing.item_type),
      patch.unit !== undefined ? String(patch.unit).trim() : String(existing.unit),
      patch.reservedQuantity !== undefined
        ? Math.max(0, Number(patch.reservedQuantity) || 0)
        : Number(existing.reserved_quantity) || 0,
      patchReorder !== undefined
        ? Math.max(0, Number(patchReorder) || 0)
        : Number(existing.reorder_level) || 0,
      patch.minimumThreshold ?? Number(existing.minimum_threshold ?? 0),
      patch.reorderQuantity !== undefined
        ? Math.max(0, Number(patch.reorderQuantity) || 0)
        : Number(existing.reorder_quantity) || 0,
      patch.unitCost !== undefined
        ? Math.max(0, Number(patch.unitCost) || 0)
        : Number(existing.unit_cost) || 0,
      nextSupplier,
      nextSupplierContact,
      nextSupplierSku,
      nextLeadTime,
      patch.storageLocation !== undefined
        ? String(patch.storageLocation).trim()
        : String(existing.storage_location || ''),
      now,
      id,
    );

    const updated = db.prepare('SELECT * FROM inventory_items WHERE id = ?').get(id) as Record<string, unknown>;
    res.json(mapInventoryItemRow(updated));
  },
);

apiRouter.delete(
  '/inventory/:id',
  requireAuth,
  requireRole(['admin', 'manager']),
  (req: AuthenticatedRequest, res) => {
    const id = String(req.params.id);
    const existing = db.prepare('SELECT id, name, sku, quantity_on_hand FROM inventory_items WHERE id = ?').get(id) as
      | { id: string; name: string; sku: string; quantity_on_hand: number }
      | undefined;
    if (!existing) {
      return res.status(404).json({ error: 'Inventory item not found.' });
    }

    if (existing.quantity_on_hand !== 0 || db.prepare('SELECT 1 FROM stock_movements WHERE inventory_item_id = ? LIMIT 1').get(id)) {
      return res.status(409).json({error:'Inventory with stock or movement history cannot be deleted.'});
    }
    const linked = (db.prepare('SELECT consumables_json FROM services').all() as {consumables_json:string}[]).some(s=>parseJsonSafe<{inventoryItemId:string}[]>(s.consumables_json,[]).some(b=>b.inventoryItemId===id));
    if (linked) return res.status(409).json({error:'Inventory is linked to a service. Remove the binding first.'});
    const run = db.transaction(() => {
      db.prepare('DELETE FROM inventory_items WHERE id = ?').run(id);
      writeAuditLog(
        req.authUser!.id,
        req.authUser!.fullName,
        'INVENTORY_ITEM_DELETED',
        'inventory_item',
        id,
        `Deleted inventory item ${existing.name} (${existing.sku}) with stock ${existing.quantity_on_hand}`,
      );
    });
    run();

    res.json({ ok: true, id });
  },
);

function applyInventoryAdjustment(
  id: string,
  payload: {
    movementType?: StockMovementType;
    quantityDelta?: number;
    purchaseUnitCost?: number;
    note?: string;
    reference?: string;
    referenceType?: StockMovement['referenceType'];
    occurredAt?: string;
  },
  actorId: string,
  actorName: string,
) {
  const existing = db.prepare('SELECT * FROM inventory_items WHERE id = ?').get(id) as
    | Record<string, unknown>
    | undefined;
  if (!existing) {
    throw Object.assign(new Error('Inventory item not found.'), { statusCode: 404 });
  }

  const resolvedMovementType: StockMovementType = (payload.movementType ||
    'adjustment') as StockMovementType;
  const validation = validateStockMovementInput({
    movementType: resolvedMovementType,
    quantityDelta: payload.quantityDelta,
  });
  if (validation.valid === false) {
    throw Object.assign(new Error(validation.error), { statusCode: 400 });
  }

  const signedDelta = computeSignedStockDelta(
    resolvedMovementType,
    Number(payload.quantityDelta) || 0,
  );
  const qtyBefore = Number(existing.quantity_on_hand) || 0;
  const nextQty = Number((qtyBefore + signedDelta).toFixed(2));
  if (!Number.isFinite(nextQty) || nextQty < 0 || (signedDelta < 0 && nextQty < (Number(existing.reserved_quantity)||0))) throw Object.assign(new Error('Insufficient unreserved inventory quantity.'), { statusCode: 409 });
  const now = new Date().toISOString();
  const effectiveTimestamp =
    typeof payload.occurredAt === 'string' && payload.occurredAt ? payload.occurredAt : now;
  const restockedAt =
    signedDelta > 0 && (resolvedMovementType === 'purchase' || resolvedMovementType === 'return')
      ? effectiveTimestamp
      : existing.last_restocked_at
        ? String(existing.last_restocked_at)
        : null;

  const previousCost = Number(existing.unit_cost) || 0;
  const nextCost = weightedPurchaseCost(qtyBefore,previousCost,signedDelta,payload.purchaseUnitCost);
  const movementId = genId('mov');
  const resolvedRefType = payload.referenceType || inferReferenceType(resolvedMovementType);

  const run = db.transaction(() => {
    db.prepare(
      'UPDATE inventory_items SET quantity_on_hand = ?, unit_cost = ?, last_restocked_at = ?, updated_at = ? WHERE id = ?',
    ).run(nextQty, nextCost, restockedAt, now, id);
    db.prepare(`
      INSERT INTO stock_movements (
        id, inventory_item_id, item_name, movement_type, quantity_before, quantity_delta,
        balance_after, unit_cost_at_movement, purchase_unit_cost, reference_type, reference, performed_by_id,
        performed_by, note, occurred_at, created_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `).run(
      movementId,
      id,
      String(existing.name),
      resolvedMovementType,
      qtyBefore,
      signedDelta,
      nextQty,
      payload.purchaseUnitCost ?? previousCost,
      payload.purchaseUnitCost ?? null,
      resolvedRefType,
      payload.reference || '',
      actorId,
      actorName,
      payload.note || '',
      effectiveTimestamp,
      now,
    );
  });
  run();

  const updatedRow = db.prepare('SELECT * FROM inventory_items WHERE id = ?').get(id) as Record<
    string,
    unknown
  >;
  const movementRow = db.prepare('SELECT * FROM stock_movements WHERE id = ?').get(movementId) as Record<
    string,
    unknown
  >;

  return {
    id,
    quantityOnHand: nextQty,
    lastRestockedAt: restockedAt,
    updatedAt: now,
    item: mapInventoryItemRow(updatedRow),
    movement: mapStockMovementRow(movementRow),
  };
}

function applyInventoryOnce(id: string, payload: Parameters<typeof applyInventoryAdjustment>[1], actorId: string, actorName: string, mutationId: string, actorRole: string) {
  validatePurchaseCost(payload.purchaseUnitCost,payload.movementType || 'adjustment',actorRole);
  if (typeof mutationId !== 'string' || !mutationId || mutationId.length > 200) throw new Error('Invalid stock mutation ID');
  const { clientMutationId: _ignored, inventoryItemId: _item, ...fields } = payload as typeof payload & {clientMutationId?: string; inventoryItemId?: string};
  const request = JSON.stringify([id, Object.entries(fields).sort(([a],[b]) => a.localeCompare(b))]);
  return db.transaction(() => {
    const saved = db.prepare('SELECT * FROM inventory_mutation_results WHERE id = ?').get(mutationId) as {actor_id:string; request_json:string; result_json:string} | undefined;
    if (saved) {
      if (saved.actor_id !== actorId || saved.request_json !== request) throw new Error('Stock mutation identity conflict');
      return { duplicate: true, result: JSON.parse(saved.result_json) as ReturnType<typeof applyInventoryAdjustment> };
    }
    if (db.prepare('SELECT id FROM applied_mutations WHERE id = ?').get(mutationId)) throw new Error('Legacy stock mutation requires manual reconciliation');
    const result = applyInventoryAdjustment(id, fields, actorId, actorName);
    db.prepare('INSERT INTO inventory_mutation_results VALUES (?, ?, ?, ?)').run(mutationId, actorId, request, JSON.stringify(result));
    return {duplicate:false, result};
  })();
}

apiRouter.post('/inventory/:id/adjust', requireAuth, (req: AuthenticatedRequest, res) => {
  try {
    const result = applyInventoryOnce(
      String(req.params.id),
      req.body || {},
      req.authUser!.id,
      req.authUser!.fullName,
      req.body?.clientMutationId,
      req.authUser!.role,
    );
    res.json(result.result);
  } catch (err) {
    const status = (err as { statusCode?: number })?.statusCode || 400;
    res.status(status).json({ error: err instanceof Error ? err.message : 'Stock adjustment failed.' });
  }
});

// ---------- CUSTOMERS ----------

apiRouter.post('/customers', requireAuth, (req: AuthenticatedRequest, res) => {
  const c = req.body as Partial<Customer>;
  if (!c.name || !String(c.name).trim()) {
    return res.status(400).json({ error: 'Customer name is required.' });
  }
  const id = genId('cust');
  const now = new Date().toISOString();
  db.prepare(`
    INSERT INTO customers (
      id, name, phone, email, address, customer_group, discount_percent, loyalty_points,
      outstanding_balance, total_spent, notes, is_demo, created_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 0, ?, 0, ?)
  `).run(
    id,
    String(c.name).trim(),
    c.phone || '',
    c.email || '',
    c.address || '',
    c.group || 'regular',
    Number(c.discountPercent) || 0,
    Number(c.loyaltyPoints) || 0,
    c.notes || '',
    now,
  );
  res.status(201).json({
    id,
    name: String(c.name).trim(),
    phone: c.phone || '',
    email: c.email || '',
    address: c.address || '',
    group: c.group || 'regular',
    discountPercent: Number(c.discountPercent) || 0,
    loyaltyPoints: Number(c.loyaltyPoints) || 0,
    outstandingBalance: 0,
    totalSpent: 0,
    notes: c.notes || '',
    isDemo: false,
    createdAt: now,
  });
});

apiRouter.post('/customers/:id/pay-balance', requireAuth, (req: AuthenticatedRequest, res) => {
  const id = String(req.params.id);
  const amount = req.body?.amount;
  const actor = req.authUser!;
  const outstandingBalance = db.transaction(() => {
    const balance = collectBalance(db,id,amount,actor.id);
    writeAuditLog(actor.id,actor.fullName,'CUSTOMER_BALANCE_PAYMENT','customer',id,`Collected cash ${amount}`);
    return balance;
  })();
  res.json({id,outstandingBalance});
});

// ---------- EXPENSES ----------

apiRouter.post('/expenses', requireAuth, (req: AuthenticatedRequest, res) => {
  const e = (req.body ?? {}) as Partial<ExpenseRecord>;
  const amount = Number(e.amount);
  const categories = ['electricity','ink_toner','printing_materials','maintenance','equipment','labor','rent','other'];
  if (typeof e.description !== 'string' || !e.description.trim() || !Number.isFinite(amount) || amount <= 0 ||
      (typeof e.amount !== 'number' && typeof e.amount !== 'string') ||
      (e.category !== undefined && !categories.includes(e.category)) ||
      (e.vendor !== undefined && typeof e.vendor !== 'string')) {
    return res.status(400).json({ error: 'Valid description, category and finite positive amount are required.' });
  }
  const id = genId('exp');
  const now = new Date().toISOString();
  const businessDate = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Manila', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(now));
  const expenseDate = e.expenseDate ?? businessDate;
  if (typeof expenseDate !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(expenseDate) ||
      !Number.isFinite(Date.parse(expenseDate)) || new Date(expenseDate).toISOString().slice(0,10) !== expenseDate) {
    return res.status(400).json({ error: 'Valid expense date is required (YYYY-MM-DD).' });
  }
  db.prepare(`
    INSERT INTO expenses (id, category, description, amount, vendor, recorded_by, expense_date, is_demo, created_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?)
  `).run(
    id,
    e.category || 'other',
    e.description,
    Number(e.amount),
    e.vendor || '',
    req.authUser!.fullName,
    expenseDate,
    now,
  );
  res.status(201).json({
    id,
    category: e.category || 'other',
    description: e.description,
    amount: Number(e.amount),
    vendor: e.vendor || '',
    recordedBy: req.authUser!.fullName,
    expenseDate,
    isDemo: false,
    createdAt: now,
  });
});

apiRouter.delete(
  '/expenses/:id',
  requireAuth,
  requireRole(['admin', 'manager']),
  (req: AuthenticatedRequest, res) => {
    const id = String(req.params.id);
    const removed = db.transaction(() => {
      const expense = db.prepare('SELECT amount, category FROM expenses WHERE id = ?').get(id) as { amount: number; category: string } | undefined;
      if (!expense) return false;
      db.prepare('DELETE FROM expenses WHERE id = ?').run(id);
      writeAuditLog(req.authUser!.id, req.authUser!.fullName, 'EXPENSE_DELETED', 'expense', id,
        `Deleted expense: ${expense.category}, amount ${expense.amount}`);
      return true;
    })();
    if (!removed) return res.status(404).json({ error: 'Expense not found.' });
    res.json({ ok: true });
  },
);

// ---------- CASHIER SHIFTS ----------

apiRouter.post('/shifts/start', requireAuth, (req: AuthenticatedRequest, res) => {
  const openingCash = Number(req.body?.openingCash);
  if (!Number.isFinite(openingCash) || openingCash < 0) return res.status(400).json({ error: 'Opening cash must be nonnegative.' });
  const openShift = db
    .prepare("SELECT id FROM cashier_shifts WHERE cashier_id = ? AND status = 'open'")
    .get(req.authUser!.id);
  if (openShift) {
    return res.status(409).json({ error: 'You already have an open cashier shift.' });
  }
  const id = genId('shf');
  const now = new Date().toISOString();
  db.prepare(`
    INSERT INTO cashier_shifts (
      id, cashier_id, cashier_name, status, opening_cash, cash_sales, electronic_sales,
      refunds_total, cash_movements_json, expected_cash, started_at
    ) VALUES (?, ?, ?, 'open', ?, 0, 0, 0, '[]', ?, ?)
  `).run(id, req.authUser!.id, req.authUser!.fullName, openingCash, openingCash, now);

  writeAuditLog(
    req.authUser!.id,
    req.authUser!.fullName,
    'SHIFT_STARTED',
    'shift',
    id,
    `Started shift with opening cash ${openingCash}`,
  );
  res.status(201).json({
    id,
    cashierId: req.authUser!.id,
    cashierName: req.authUser!.fullName,
    status: 'open',
    openingCash,
    cashSales: 0,
    electronicSales: 0,
    refundsTotal: 0,
    cashMovements: [],
    expectedCash: openingCash,
    actualCash: null,
    discrepancy: null,
    reconciliationNote: '',
    startedAt: now,
    endedAt: null,
  });
});

apiRouter.post('/shifts/:id/movement', requireAuth, (req: AuthenticatedRequest, res) => {
  const id = String(req.params.id);
  const { type, amount, reason } = req.body || {};
  const val = Number(amount) || 0;
  if (!['cash_in', 'cash_out'].includes(type) || !Number.isFinite(val) || val <= 0 || !reason) {
    return res.status(400).json({ error: 'Valid movement type, positive amount, and reason required.' });
  }
  const shift = db.prepare("SELECT * FROM cashier_shifts WHERE id = ? AND status = 'open'").get(id) as
    | Record<string, unknown>
    | undefined;
  if (!shift) return res.status(404).json({ error: 'Open shift not found.' });
  if (shift.cashier_id !== req.authUser!.id && !['admin','manager'].includes(req.authUser!.role)) return res.status(403).json({ error: 'Cannot manage another cashier’s shift.' });

  const movements = parseJsonSafe<Array<Record<string, unknown>>>(shift.cash_movements_json as string, []);
  movements.push({
    id: genId('cm'),
    type,
    amount: val,
    reason: String(reason),
    timestamp: new Date().toISOString(),
  });
  const delta = type === 'cash_in' ? val : -val;
  const expected = Number(((Number(shift.expected_cash) || 0) + delta).toFixed(2));
  if (expected < 0) return res.status(409).json({ error: 'Cash withdrawal exceeds expected drawer cash.' });

  db.prepare('UPDATE cashier_shifts SET cash_movements_json = ?, expected_cash = ? WHERE id = ?').run(
    JSON.stringify(movements),
    expected,
    id,
  );
  res.json({ id, cashMovements: movements, expectedCash: expected });
});

apiRouter.post('/shifts/:id/close', requireAuth, (req: AuthenticatedRequest, res) => {
  const id = String(req.params.id);
  const actualCash = Number(req.body?.actualCash);
  const reconciliationNote = String(req.body?.reconciliationNote || '');
  if (!Number.isFinite(actualCash) || actualCash < 0) {
    return res.status(400).json({ error: 'Actual cash count is required.' });
  }
  const shift = db.prepare("SELECT * FROM cashier_shifts WHERE id = ? AND status = 'open'").get(id) as
    | Record<string, unknown>
    | undefined;
  if (!shift) return res.status(404).json({ error: 'Open shift not found.' });
  if (shift.cashier_id !== req.authUser!.id && !['admin','manager'].includes(req.authUser!.role)) return res.status(403).json({ error: 'Cannot manage another cashier’s shift.' });

  const expected = Number(shift.expected_cash) || 0;
  const discrepancy = Number((actualCash - expected).toFixed(2));
  const now = new Date().toISOString();

  db.prepare(`
    UPDATE cashier_shifts
    SET status = 'closed', actual_cash = ?, discrepancy = ?, reconciliation_note = ?, ended_at = ?
    WHERE id = ?
  `).run(actualCash, discrepancy, reconciliationNote, now, id);

  writeAuditLog(
    req.authUser!.id,
    req.authUser!.fullName,
    'SHIFT_CLOSED',
    'shift',
    id,
    `Closed shift. Expected ${expected}, Actual ${actualCash}, Discrepancy ${discrepancy}`,
  );
  res.json({ id, status: 'closed', actualCash, discrepancy, endedAt: now });
});

// ---------- SYNC BATCH (OFFLINE QUEUE) ----------

apiRouter.post('/sync/batch', requireAuth, (req: AuthenticatedRequest, res) => {
  const mutations = Array.isArray(req.body?.mutations) ? req.body.mutations : [];
  const results: Array<{ clientMutationId: string; status: string; error?: string }> = [];

  if (mutations.length > 50) return res.status(400).json({error: "Maximum 50 mutations per batch"});
  for (const mut of mutations) {
    try {
      validateSyncEnvelope(mut, req.authUser!.id);
      if (mut.type === 'CREATE_TRANSACTION') {
        const out = applyTransactionCreation(
          mut.payload as Transaction,
          req.authUser!.id,
          req.authUser!.fullName,
          req.authUser!.role,
        );
        results.push({
          clientMutationId: mut.clientMutationId,
          status: out.duplicate ? 'duplicate' : 'applied',
        });
      } else if (mut.type === 'ADJUST_INVENTORY') {
        const adj = mut.payload as {
          inventoryItemId: string;
          movementType?: StockMovementType;
          quantityDelta?: number;
          purchaseUnitCost?: number;
          note?: string;
          reference?: string;
          referenceType?: StockMovement['referenceType'];
          occurredAt?: string;
        };
        const {duplicate} = applyInventoryOnce(adj.inventoryItemId, adj, req.authUser!.id, req.authUser!.fullName, mut.clientMutationId, req.authUser!.role);
        results.push({
          clientMutationId: mut.clientMutationId,
          status: duplicate ? 'duplicate' : 'applied',
        });
      } else {
        results.push({
          clientMutationId: mut.clientMutationId,
          status: 'error',
          error: 'Unsupported offline mutation',
        });
      }
    } catch (err) {
      results.push({
        clientMutationId: mut?.clientMutationId || "",
        status: 'error',
        error: err instanceof Error ? err.message : 'Sync failed',
      });
    }
  }

  res.json({ results, syncedAt: new Date().toISOString() });
});

// ---------- DEMO DATA & BACKUP ----------

apiRouter.post(
  '/demo/seed',
  requireAuth,
  requireRole(['admin', 'manager']),
  (req: AuthenticatedRequest, res) => {
    seedDemoDataset();
    writeAuditLog(
      req.authUser!.id,
      req.authUser!.fullName,
      'DEMO_SEEDED',
      'system',
      'demo',
      'Loaded demonstration catalog and sample records',
    );
    res.json({ ok: true });
  },
);

apiRouter.post(
  '/demo/clear',
  requireAuth,
  requireRole(['admin', 'manager']),
  (req: AuthenticatedRequest, res) => {
    clearDemoDataset();
    writeAuditLog(
      req.authUser!.id,
      req.authUser!.fullName,
      'DEMO_CLEARED',
      'system',
      'demo',
      'Cleared isolated demonstration records',
    );
    res.json({ ok: true });
  },
);

apiRouter.get(
  '/backup/export',
  requireAuth,
  requireRole(['admin']),
  (req: AuthenticatedRequest, res) => {
    const settingsRow = db.prepare('SELECT value_json FROM app_settings WHERE key = ?').get('app_settings') as
      | { value_json: string }
      | undefined;
    const backup = {
      exportedAt: new Date().toISOString(),
      schemaVersion: '1.0.0',
      exportedBy: req.authUser!.username,
      settings: settingsRow ? parseJsonSafe(settingsRow.value_json, DEFAULT_SETTINGS) : DEFAULT_SETTINGS,
      categories: db.prepare('SELECT * FROM categories').all(),
      services: db.prepare('SELECT * FROM services').all(),
      inventory_items: db.prepare('SELECT * FROM inventory_items').all(),
      customers: db.prepare('SELECT * FROM customers').all(),
      transactions: db.prepare('SELECT * FROM transactions').all(),
      print_jobs: db.prepare('SELECT * FROM print_jobs').all(),
      order_conversions: db.prepare('SELECT * FROM order_conversions').all(),
      expenses: db.prepare('SELECT * FROM expenses').all(),
    };
    writeAuditLog(
      req.authUser!.id,
      req.authUser!.fullName,
      'BACKUP_EXPORTED',
      'system',
      'backup',
      'Exported business configuration and ledger backup',
    );
    res.json(backup);
  },
);
