import Database from 'better-sqlite3';
import fs from 'fs';
import path from 'path';
import {
  DEFAULT_SETTINGS,
  DEFAULT_SUPPLY_CATEGORIES,
  DEMO_CATEGORIES,
  DEMO_CUSTOMERS,
  DEMO_EXPENSES,
  DEMO_INVENTORY,
  DEMO_PRINT_JOBS,
  DEMO_SERVICES,
  DEMO_STOCK_MOVEMENTS,
  DEMO_TRANSACTIONS,
} from '../shared/defaults.ts';

const DATA_DIR = path.resolve(process.env.POS_DATA_DIR || 'data');
if (!fs.existsSync(DATA_DIR)) {
  fs.mkdirSync(DATA_DIR, { recursive: true });
}

const DB_PATH = path.join(DATA_DIR, 'printshop-pos.db');
export const db = new Database(DB_PATH);

db.pragma('journal_mode = WAL');
db.pragma('foreign_keys = ON');

export function initDatabase() {
  const schemaVersion=Number(db.pragma('user_version',{simple:true}));
  if(schemaVersion!==0&&schemaVersion!==1&&schemaVersion!==2)throw new Error('Unsupported Printshop POS database schema version: '+schemaVersion);
  if(schemaVersion>=1){
    const currentTables=["transaction_request_identities","inventory_mutation_results","applied_mutations","users","sessions","app_settings","categories","services","supply_categories","inventory_items","stock_movements","customers","transactions","order_conversions","print_jobs","expenses","cashier_shifts","transaction_reversals","drawer_movement_requests","balance_collection_requests","balance_collections","audit_logs","inventory_alert_epochs","inventory_alert_reads"];
const criticalColumns={"transactions":["id","client_mutation_id","receipt_snapshot_json","payments_json","balance_due"],"inventory_items":["id","reserved_quantity","archived_at","archive_reason","minimum_threshold"],"stock_movements":["id","inventory_item_id","purchase_unit_cost","quantity_before","quantity_delta"],"transaction_request_identities":["mutation_id","actor_id","transaction_id","request_digest"],"balance_collection_requests":["mutation_id","actor_id","customer_id","amount","outstanding_after"],"drawer_movement_requests":["mutation_id","actor_id","shift_id","movement_type","amount","reason","movement_id","expected_after"],"inventory_mutation_results":["id","actor_id","request_json","result_json"],"balance_collections":["id","transaction_id","customer_id","shift_id","amount"],"inventory_alert_epochs":["item_id","epoch"],"inventory_alert_reads":["user_id","item_id","epoch"]};
const requiredTriggers=['inventory_alert_insert','inventory_alert_transition'];
    const tables=new Set((db.prepare("SELECT name FROM sqlite_master WHERE type='table'").all() as {name:string}[]).map(r=>r.name));
    if(schemaVersion===2){
      currentTables.push('monetary_request_cancellations');
      Object.assign(criticalColumns,{monetary_request_cancellations:['id','kind','actor_id','mutation_id','reason','created_at'],expenses:['id','client_mutation_id','request_digest','actor_id','voided_at','voided_by','void_reason']});
    }
    if(currentTables.some(t=>!tables.has(t)))throw new Error('Current database schema incomplete. Restore a verified backup; do not recreate missing tables.');
    for(const [table,columns] of Object.entries(criticalColumns)){
      const found=new Set((db.prepare('PRAGMA table_info('+table+')').all() as {name:string}[]).map(r=>r.name));
      if(columns.some(c=>!found.has(c)))throw new Error('Current database schema missing required columns: '+table);
    }
    const triggers=new Set((db.prepare("SELECT name FROM sqlite_master WHERE type='trigger'").all() as {name:string}[]).map(r=>r.name));
    if(requiredTriggers.some(t=>!triggers.has(t)))throw new Error('Current database schema missing required inventory alert triggers.');
  }
  db.exec(`
    CREATE TABLE IF NOT EXISTS transaction_request_identities (
      mutation_id TEXT PRIMARY KEY,
      actor_id TEXT NOT NULL,
      transaction_id TEXT NOT NULL,
      request_digest TEXT NOT NULL
    );
    CREATE TABLE IF NOT EXISTS inventory_mutation_results (
      id TEXT PRIMARY KEY, actor_id TEXT NOT NULL, request_json TEXT NOT NULL, result_json TEXT NOT NULL
    );
    CREATE TABLE IF NOT EXISTS applied_mutations (
      id TEXT PRIMARY KEY,
      applied_at TEXT NOT NULL
    );
    CREATE TABLE IF NOT EXISTS users (
      id TEXT PRIMARY KEY,
      username TEXT UNIQUE NOT NULL,
      full_name TEXT NOT NULL,
      email TEXT NOT NULL DEFAULT '',
      role TEXT NOT NULL CHECK(role IN ('admin', 'manager', 'cashier', 'operator')),
      password_hash TEXT NOT NULL,
      pin_hash TEXT NOT NULL DEFAULT '',
      is_active INTEGER NOT NULL DEFAULT 1,
      preferred_landing_module TEXT DEFAULT 'pos',
      created_at TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS sessions (
      id TEXT PRIMARY KEY,
      user_id TEXT NOT NULL,
      device_id TEXT NOT NULL,
      device_name TEXT NOT NULL,
      expires_at TEXT NOT NULL,
      created_at TEXT NOT NULL,
      last_seen_at TEXT NOT NULL,
      FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS app_settings (
      key TEXT PRIMARY KEY,
      value_json TEXT NOT NULL,
      updated_at TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS categories (
      id TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      sort_order INTEGER NOT NULL DEFAULT 0,
      is_demo INTEGER NOT NULL DEFAULT 0
    );

    CREATE TABLE IF NOT EXISTS services (
      id TEXT PRIMARY KEY,
      sku TEXT NOT NULL,
      name TEXT NOT NULL,
      category_id TEXT NOT NULL,
      description TEXT NOT NULL DEFAULT '',
      icon_name TEXT NOT NULL DEFAULT 'Printer',
      pricing_method TEXT NOT NULL,
      base_price REAL NOT NULL DEFAULT 0,
      unit_label TEXT NOT NULL DEFAULT 'pc',
      cost_estimate REAL NOT NULL DEFAULT 0,
      requires_configuration INTEGER NOT NULL DEFAULT 1,
      requires_production_job INTEGER NOT NULL DEFAULT 0,
      allow_custom_dimensions INTEGER NOT NULL DEFAULT 0,
      allow_pages_and_copies INTEGER NOT NULL DEFAULT 0,
      allow_rush_fee INTEGER NOT NULL DEFAULT 0,
      rush_fee_percent REAL NOT NULL DEFAULT 25,
      default_design_fee REAL NOT NULL DEFAULT 0,
      bulk_tiers_json TEXT NOT NULL DEFAULT '[]',
      formula_expression TEXT NOT NULL DEFAULT '',
      attribute_groups_json TEXT NOT NULL DEFAULT '[]',
      consumables_json TEXT NOT NULL DEFAULT '[]',
      is_favorite INTEGER NOT NULL DEFAULT 0,
      is_archived INTEGER NOT NULL DEFAULT 0,
      is_demo INTEGER NOT NULL DEFAULT 0,
      updated_at TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS supply_categories (
      id TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      description TEXT NOT NULL DEFAULT '',
      default_unit TEXT NOT NULL DEFAULT 'pc',
      sort_order INTEGER NOT NULL DEFAULT 0,
      is_demo INTEGER NOT NULL DEFAULT 0
    );

    CREATE TABLE IF NOT EXISTS inventory_items (
      id TEXT PRIMARY KEY,
      sku TEXT NOT NULL,
      barcode TEXT NOT NULL DEFAULT '',
      name TEXT NOT NULL,
      category_id TEXT NOT NULL DEFAULT '',
      category TEXT NOT NULL,
      item_type TEXT NOT NULL CHECK(item_type IN ('consumable', 'finished_product')),
      unit TEXT NOT NULL,
      quantity_on_hand REAL NOT NULL DEFAULT 0,
      reserved_quantity REAL NOT NULL DEFAULT 0,
      reorder_level REAL NOT NULL DEFAULT 0,
      reorder_quantity REAL NOT NULL DEFAULT 0,
      unit_cost REAL NOT NULL DEFAULT 0,
      supplier TEXT NOT NULL DEFAULT '',
      supplier_contact TEXT NOT NULL DEFAULT '',
      supplier_sku TEXT NOT NULL DEFAULT '',
      lead_time_days INTEGER NOT NULL DEFAULT 0,
      storage_location TEXT NOT NULL DEFAULT '',
      last_restocked_at TEXT,
      is_demo INTEGER NOT NULL DEFAULT 0,
      created_at TEXT NOT NULL DEFAULT '',
      updated_at TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS stock_movements (
      id TEXT PRIMARY KEY,
      inventory_item_id TEXT NOT NULL,
      item_name TEXT NOT NULL,
      movement_type TEXT NOT NULL,
      quantity_before REAL NOT NULL DEFAULT 0,
      quantity_delta REAL NOT NULL,
      balance_after REAL NOT NULL,
      unit_cost_at_movement REAL NOT NULL DEFAULT 0,
      reference_type TEXT NOT NULL DEFAULT 'manual',
      reference TEXT NOT NULL DEFAULT '',
      performed_by_id TEXT NOT NULL DEFAULT '',
      performed_by TEXT NOT NULL,
      note TEXT NOT NULL DEFAULT '',
      occurred_at TEXT NOT NULL DEFAULT '',
      created_at TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS customers (
      id TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      phone TEXT NOT NULL DEFAULT '',
      email TEXT NOT NULL DEFAULT '',
      address TEXT NOT NULL DEFAULT '',
      customer_group TEXT NOT NULL DEFAULT 'regular',
      discount_percent REAL NOT NULL DEFAULT 0,
      loyalty_points INTEGER NOT NULL DEFAULT 0,
      outstanding_balance REAL NOT NULL DEFAULT 0,
      total_spent REAL NOT NULL DEFAULT 0,
      notes TEXT NOT NULL DEFAULT '',
      is_demo INTEGER NOT NULL DEFAULT 0,
      created_at TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS transactions (
      id TEXT PRIMARY KEY,
      receipt_number TEXT NOT NULL,
      client_mutation_id TEXT UNIQUE NOT NULL,
      type TEXT NOT NULL CHECK(type IN ('sale', 'quotation', 'held')),
      customer_id TEXT,
      customer_name TEXT NOT NULL DEFAULT 'Walk-in Customer',
      cashier_id TEXT NOT NULL,
      cashier_name TEXT NOT NULL,
      shift_id TEXT,
      items_json TEXT NOT NULL,
      subtotal REAL NOT NULL,
      item_discounts_total REAL NOT NULL DEFAULT 0,
      order_discount_type TEXT NOT NULL DEFAULT 'none',
      order_discount_value REAL NOT NULL DEFAULT 0,
      order_discount_amount REAL NOT NULL DEFAULT 0,
      tax_amount REAL NOT NULL DEFAULT 0,
      total_amount REAL NOT NULL,
      amount_paid REAL NOT NULL DEFAULT 0,
      change_amount REAL NOT NULL DEFAULT 0,
      balance_due REAL NOT NULL DEFAULT 0,
      payment_status TEXT NOT NULL,
      payments_json TEXT NOT NULL DEFAULT '[]',
      notes TEXT NOT NULL DEFAULT '',
      sync_status TEXT NOT NULL DEFAULT 'synced',
      is_demo INTEGER NOT NULL DEFAULT 0,
      created_at TEXT NOT NULL,
      void_reason TEXT,
      refund_reason TEXT
    );

    CREATE TABLE IF NOT EXISTS order_conversions (
      source_id TEXT PRIMARY KEY, sale_id TEXT NOT NULL UNIQUE, converted_at TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS print_jobs (
      id TEXT PRIMARY KEY,
      job_order_number TEXT NOT NULL,
      transaction_id TEXT,
      customer_id TEXT,
      customer_name TEXT NOT NULL,
      customer_phone TEXT NOT NULL DEFAULT '',
      title TEXT NOT NULL,
      specifications TEXT NOT NULL DEFAULT '',
      attachment_names_json TEXT NOT NULL DEFAULT '[]',
      instructions TEXT NOT NULL DEFAULT '',
      assigned_operator TEXT NOT NULL DEFAULT '',
      production_status TEXT NOT NULL,
      payment_status TEXT NOT NULL,
      total_amount REAL NOT NULL DEFAULT 0,
      deposit_amount REAL NOT NULL DEFAULT 0,
      remaining_balance REAL NOT NULL DEFAULT 0,
      estimated_completion TEXT NOT NULL,
      deadline TEXT NOT NULL,
      history_json TEXT NOT NULL DEFAULT '[]',
      is_demo INTEGER NOT NULL DEFAULT 0,
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS expenses (
      id TEXT PRIMARY KEY,
      category TEXT NOT NULL,
      description TEXT NOT NULL,
      amount REAL NOT NULL,
      vendor TEXT NOT NULL DEFAULT '',
      recorded_by TEXT NOT NULL,
      expense_date TEXT NOT NULL,
      is_demo INTEGER NOT NULL DEFAULT 0,
      created_at TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS cashier_shifts (
      id TEXT PRIMARY KEY,
      cashier_id TEXT NOT NULL,
      cashier_name TEXT NOT NULL,
      status TEXT NOT NULL CHECK(status IN ('open', 'closed')),
      opening_cash REAL NOT NULL DEFAULT 0,
      cash_sales REAL NOT NULL DEFAULT 0,
      electronic_sales REAL NOT NULL DEFAULT 0,
      refunds_total REAL NOT NULL DEFAULT 0,
      cash_movements_json TEXT NOT NULL DEFAULT '[]',
      expected_cash REAL NOT NULL DEFAULT 0,
      actual_cash REAL,
      discrepancy REAL,
      reconciliation_note TEXT NOT NULL DEFAULT '',
      started_at TEXT NOT NULL,
      ended_at TEXT
    );

    CREATE TABLE IF NOT EXISTS transaction_reversals (
      transaction_id TEXT PRIMARY KEY, kind TEXT NOT NULL, reason TEXT NOT NULL,
      actor_id TEXT NOT NULL, shift_id TEXT, cash_amount REAL NOT NULL,
      electronic_amount REAL NOT NULL, cancelled_receivable REAL NOT NULL, created_at TEXT NOT NULL
    );
    CREATE TABLE IF NOT EXISTS drawer_movement_requests (
      mutation_id TEXT PRIMARY KEY, actor_id TEXT NOT NULL, shift_id TEXT NOT NULL,
      movement_type TEXT NOT NULL, amount REAL NOT NULL, reason TEXT NOT NULL,
      movement_id TEXT NOT NULL, expected_after REAL NOT NULL, created_at TEXT NOT NULL
    );
    CREATE TABLE IF NOT EXISTS balance_collection_requests (
      mutation_id TEXT PRIMARY KEY, actor_id TEXT NOT NULL, customer_id TEXT NOT NULL,
      amount REAL NOT NULL, outstanding_after REAL NOT NULL, created_at TEXT NOT NULL
    );
    CREATE TABLE IF NOT EXISTS monetary_request_cancellations (
      id TEXT PRIMARY KEY, kind TEXT NOT NULL, actor_id TEXT NOT NULL,
      mutation_id TEXT NOT NULL, reason TEXT NOT NULL, created_at TEXT NOT NULL,
      UNIQUE(kind, actor_id, mutation_id)
    );
    CREATE TABLE IF NOT EXISTS balance_collections (
      id TEXT PRIMARY KEY, transaction_id TEXT NOT NULL, customer_id TEXT NOT NULL,
      shift_id TEXT NOT NULL, amount REAL NOT NULL, actor_id TEXT NOT NULL, created_at TEXT NOT NULL
    );
    CREATE TABLE IF NOT EXISTS audit_logs (
      id TEXT PRIMARY KEY,
      actor_id TEXT NOT NULL,
      actor_name TEXT NOT NULL,
      action TEXT NOT NULL,
      entity_type TEXT NOT NULL,
      entity_id TEXT NOT NULL,
      summary TEXT NOT NULL,
      created_at TEXT NOT NULL
    );
  `);

  // Safe idempotent column migrations for existing databases
  const ensureColumn = (table: string, column: string, definition: string) => {
    const cols = db.prepare(`PRAGMA table_info(${table})`).all() as Array<{ name: string }>;
    if (!cols.some((c) => c.name === column)) {
      db.exec(`ALTER TABLE ${table} ADD COLUMN ${column} ${definition}`);
    }
  };

  // Additive migration: preserve all existing expense rows, including legacy backups.
  ensureColumn('expenses', 'client_mutation_id', 'TEXT');
  ensureColumn('expenses', 'request_digest', 'TEXT');
  ensureColumn('expenses', 'actor_id', 'TEXT');
  ensureColumn('expenses', 'voided_at', 'TEXT');
  ensureColumn('expenses', 'voided_by', "TEXT NOT NULL DEFAULT ''");
  ensureColumn('expenses', 'void_reason', "TEXT NOT NULL DEFAULT ''");
  ensureColumn('transactions', 'receipt_snapshot_json', 'TEXT');
  ensureColumn('inventory_items', 'barcode', "TEXT NOT NULL DEFAULT ''");
  ensureColumn('inventory_items', 'category_id', "TEXT NOT NULL DEFAULT ''");
  ensureColumn('inventory_items', 'reserved_quantity', 'REAL NOT NULL DEFAULT 0');
  ensureColumn('inventory_items', 'archived_at', 'TEXT');
  ensureColumn('inventory_items', 'archive_reason', "TEXT NOT NULL DEFAULT ''");
  ensureColumn('inventory_items', 'minimum_threshold', 'REAL NOT NULL DEFAULT 0');
  ensureColumn('inventory_items', 'reorder_quantity', 'REAL NOT NULL DEFAULT 0');
  ensureColumn('inventory_items', 'supplier_contact', "TEXT NOT NULL DEFAULT ''");
  ensureColumn('inventory_items', 'supplier_sku', "TEXT NOT NULL DEFAULT ''");
  ensureColumn('inventory_items', 'lead_time_days', 'INTEGER NOT NULL DEFAULT 0');
  ensureColumn('inventory_items', 'storage_location', "TEXT NOT NULL DEFAULT ''");
  ensureColumn('inventory_items', 'last_restocked_at', 'TEXT');
  ensureColumn('inventory_items', 'created_at', "TEXT NOT NULL DEFAULT ''");

  ensureColumn('stock_movements', 'quantity_before', 'REAL NOT NULL DEFAULT 0');
  ensureColumn('stock_movements', 'purchase_unit_cost', 'REAL');
  ensureColumn('stock_movements', 'unit_cost_at_movement', 'REAL NOT NULL DEFAULT 0');
  ensureColumn('stock_movements', 'reference_type', "TEXT NOT NULL DEFAULT 'manual'");
  ensureColumn('stock_movements', 'performed_by_id', "TEXT NOT NULL DEFAULT ''");
  ensureColumn('stock_movements', 'occurred_at', "TEXT NOT NULL DEFAULT ''");

  db.exec(`
    CREATE UNIQUE INDEX IF NOT EXISTS idx_expense_mutation ON expenses(client_mutation_id) WHERE client_mutation_id IS NOT NULL;
    CREATE INDEX IF NOT EXISTS idx_sessions_user_expiry ON sessions(user_id, expires_at);
    CREATE INDEX IF NOT EXISTS idx_transactions_created ON transactions(created_at DESC);
    CREATE INDEX IF NOT EXISTS idx_transactions_customer ON transactions(customer_id, created_at);
    CREATE INDEX IF NOT EXISTS idx_jobs_transaction ON print_jobs(transaction_id);
    CREATE INDEX IF NOT EXISTS idx_jobs_created ON print_jobs(created_at DESC);
    CREATE INDEX IF NOT EXISTS idx_stock_created ON stock_movements(created_at DESC);
    CREATE INDEX IF NOT EXISTS idx_stock_item_created ON stock_movements(inventory_item_id, created_at DESC, id DESC);
    CREATE INDEX IF NOT EXISTS idx_audit_created ON audit_logs(created_at DESC);
    CREATE INDEX IF NOT EXISTS idx_shifts_cashier_status ON cashier_shifts(cashier_id, status);
  `);
  db.exec(`
    CREATE TABLE IF NOT EXISTS inventory_alert_epochs (
      item_id TEXT PRIMARY KEY REFERENCES inventory_items(id) ON DELETE CASCADE,
      epoch TEXT NOT NULL
    );
    CREATE TABLE IF NOT EXISTS inventory_alert_reads (
      user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      item_id TEXT NOT NULL REFERENCES inventory_items(id) ON DELETE CASCADE,
      epoch TEXT NOT NULL,
      PRIMARY KEY(user_id,item_id)
    );
    INSERT OR IGNORE INTO inventory_alert_epochs(item_id,epoch)
      SELECT id,lower(hex(randomblob(16))) FROM inventory_items;
    CREATE TRIGGER IF NOT EXISTS inventory_alert_insert AFTER INSERT ON inventory_items
    BEGIN
      INSERT OR REPLACE INTO inventory_alert_epochs VALUES(NEW.id,lower(hex(randomblob(16))));
    END;
    CREATE TRIGGER IF NOT EXISTS inventory_alert_transition AFTER UPDATE ON inventory_items
    WHEN (CASE WHEN OLD.archived_at IS NOT NULL THEN 'none' WHEN ROUND(MAX(0,OLD.quantity_on_hand-MAX(0,OLD.reserved_quantity)),2)<=0 THEN 'out' WHEN ROUND(MAX(0,OLD.quantity_on_hand-MAX(0,OLD.reserved_quantity)),2)>OLD.reorder_level THEN 'none' WHEN OLD.minimum_threshold>0 AND ROUND(MAX(0,OLD.quantity_on_hand-MAX(0,OLD.reserved_quantity)),2)<OLD.minimum_threshold THEN 'critical' ELSE 'reorder' END) <> (CASE WHEN NEW.archived_at IS NOT NULL THEN 'none' WHEN ROUND(MAX(0,NEW.quantity_on_hand-MAX(0,NEW.reserved_quantity)),2)<=0 THEN 'out' WHEN ROUND(MAX(0,NEW.quantity_on_hand-MAX(0,NEW.reserved_quantity)),2)>NEW.reorder_level THEN 'none' WHEN NEW.minimum_threshold>0 AND ROUND(MAX(0,NEW.quantity_on_hand-MAX(0,NEW.reserved_quantity)),2)<NEW.minimum_threshold THEN 'critical' ELSE 'reorder' END)
      OR OLD.reorder_level <> NEW.reorder_level OR OLD.minimum_threshold <> NEW.minimum_threshold
    BEGIN
      INSERT OR REPLACE INTO inventory_alert_epochs VALUES(NEW.id,lower(hex(randomblob(16))));
      DELETE FROM inventory_alert_reads WHERE item_id=NEW.id;
    END;
  `);
  // Ensure default supply categories exist
  const supplyCatCount = (
    db.prepare('SELECT COUNT(*) as count FROM supply_categories').get() as { count: number }
  ).count;
  if (supplyCatCount === 0) {
    const insertSupCat = db.prepare(`
      INSERT OR IGNORE INTO supply_categories (id, name, description, default_unit, sort_order, is_demo)
      VALUES (?, ?, ?, ?, ?, ?)
    `);
    for (const sc of DEFAULT_SUPPLY_CATEGORIES) {
      insertSupCat.run(sc.id, sc.name, sc.description, sc.defaultUnit, sc.sortOrder, sc.isDemo ? 1 : 0);
    }
  }

  // Ensure default settings exist
  const settingsRow = db.prepare('SELECT value_json FROM app_settings WHERE key = ?').get('app_settings') as
    | { value_json: string }
    | undefined;
  if (!settingsRow) {
    db.prepare('INSERT INTO app_settings (key, value_json, updated_at) VALUES (?, ?, ?)').run(
      'app_settings',
      JSON.stringify(DEFAULT_SETTINGS),
      new Date().toISOString(),
    );
  }

  // Seed initial demonstration dataset only if catalog is empty on first boot
  const serviceCount = (db.prepare('SELECT COUNT(*) as count FROM services').get() as { count: number }).count;
  if (serviceCount === 0 && process.env.SEED_DEMO === 'true') {
    seedDemoDataset();
  }
  // Stamp only after all additive migrations and initialization finish successfully.
  db.pragma('user_version = 2');
}

export function seedDemoDataset() {
  const tx = db.transaction(() => {
    const insertCat = db.prepare(
      'INSERT OR IGNORE INTO categories (id, name, sort_order, is_demo) VALUES (?, ?, ?, 1)',
    );
    for (const c of DEMO_CATEGORIES) {
      insertCat.run(c.id, c.name, c.sortOrder);
    }

    const insertSupCat = db.prepare(`
      INSERT OR IGNORE INTO supply_categories (id, name, description, default_unit, sort_order, is_demo)
      VALUES (?, ?, ?, ?, ?, 1)
    `);
    for (const sc of DEFAULT_SUPPLY_CATEGORIES) {
      insertSupCat.run(sc.id, sc.name, sc.description, sc.defaultUnit, sc.sortOrder);
    }

    const insertInv = db.prepare(`
      INSERT OR IGNORE INTO inventory_items (
        id, sku, barcode, name, category_id, category, item_type, unit, quantity_on_hand, reserved_quantity,
        reorder_level, reorder_quantity, unit_cost, supplier, supplier_contact, supplier_sku,
        lead_time_days, storage_location, last_restocked_at, is_demo, created_at, updated_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)
    `);
    for (const inv of DEMO_INVENTORY) {
      insertInv.run(
        inv.id,
        inv.sku,
        inv.barcode || '',
        inv.name,
        inv.categoryId || '',
        inv.category,
        inv.itemType,
        inv.unit,
        inv.quantityOnHand,
        inv.reservedQuantity || 0,
        inv.reorderLevel,
        inv.reorderQuantity || 0,
        inv.unitCost,
        inv.supplier,
        inv.supplierContact || '',
        inv.supplierSku || '',
        inv.leadTimeDays || 0,
        inv.storageLocation || '',
        inv.lastRestockedAt || null,
        inv.createdAt || inv.updatedAt,
        inv.updatedAt,
      );
    }

    const insertMov = db.prepare(`
      INSERT OR IGNORE INTO stock_movements (
        id, inventory_item_id, item_name, movement_type, quantity_before, quantity_delta,
        balance_after, unit_cost_at_movement, reference_type, reference, performed_by_id,
        performed_by, note, occurred_at, created_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `);
    for (const m of DEMO_STOCK_MOVEMENTS) {
      insertMov.run(
        m.id,
        m.inventoryItemId,
        m.itemName,
        m.movementType,
        m.quantityBefore ?? Math.max(0, m.balanceAfter - m.quantityDelta),
        m.quantityDelta,
        m.balanceAfter,
        m.unitCostAtMovement ?? 0,
        m.referenceType || 'manual',
        m.reference,
        m.performedById || '',
        m.performedBy,
        m.note,
        m.occurredAt || m.createdAt,
        m.createdAt,
      );
    }

    const insertSrv = db.prepare(`
      INSERT OR IGNORE INTO services (
        id, sku, name, category_id, description, icon_name, pricing_method, base_price, unit_label,
        cost_estimate, requires_configuration, requires_production_job, allow_custom_dimensions,
        allow_pages_and_copies, allow_rush_fee, rush_fee_percent, default_design_fee,
        bulk_tiers_json, formula_expression, attribute_groups_json, consumables_json,
        is_favorite, is_archived, is_demo, updated_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?)
    `);
    for (const s of DEMO_SERVICES) {
      insertSrv.run(
        s.id,
        s.sku,
        s.name,
        s.categoryId,
        s.description,
        s.iconName,
        s.pricingMethod,
        s.basePrice,
        s.unitLabel,
        s.costEstimate,
        s.requiresConfiguration ? 1 : 0,
        s.requiresProductionJob ? 1 : 0,
        s.allowCustomDimensions ? 1 : 0,
        s.allowPagesAndCopies ? 1 : 0,
        s.allowRushFee ? 1 : 0,
        s.rushFeePercent,
        s.defaultDesignFee,
        JSON.stringify(s.bulkTiers),
        s.formulaExpression || '',
        JSON.stringify(s.attributeGroups),
        JSON.stringify(s.consumables),
        s.isFavorite ? 1 : 0,
        s.isArchived ? 1 : 0,
        s.updatedAt,
      );
    }

    const insertCust = db.prepare(`
      INSERT OR IGNORE INTO customers (
        id, name, phone, email, address, customer_group, discount_percent, loyalty_points,
        outstanding_balance, total_spent, notes, is_demo, created_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?)
    `);
    for (const cust of DEMO_CUSTOMERS) {
      insertCust.run(
        cust.id,
        cust.name,
        cust.phone,
        cust.email,
        cust.address,
        cust.group,
        cust.discountPercent,
        cust.loyaltyPoints,
        cust.outstandingBalance,
        cust.totalSpent,
        cust.notes,
        cust.createdAt,
      );
    }

    const insertTxn = db.prepare(`
      INSERT OR IGNORE INTO transactions (
        id, receipt_number, client_mutation_id, type, customer_id, customer_name, cashier_id,
        cashier_name, shift_id, items_json, subtotal, item_discounts_total, order_discount_type,
        order_discount_value, order_discount_amount, tax_amount, total_amount, amount_paid,
        change_amount, balance_due, payment_status, payments_json, notes, sync_status, is_demo, created_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?)
    `);
    for (const t of DEMO_TRANSACTIONS) {
      insertTxn.run(
        t.id,
        t.receiptNumber,
        t.clientMutationId,
        t.type,
        t.customerId,
        t.customerName,
        t.cashierId,
        t.cashierName,
        t.shiftId,
        JSON.stringify(t.items),
        t.subtotal,
        t.itemDiscountsTotal,
        t.orderDiscountType,
        t.orderDiscountValue,
        t.orderDiscountAmount,
        t.taxAmount,
        t.totalAmount,
        t.amountPaid,
        t.changeAmount,
        t.balanceDue,
        t.paymentStatus,
        JSON.stringify(t.payments),
        t.notes,
        t.syncStatus,
        t.createdAt,
      );
    }

    const insertJob = db.prepare(`
      INSERT OR IGNORE INTO print_jobs (
        id, job_order_number, transaction_id, customer_id, customer_name, customer_phone,
        title, specifications, attachment_names_json, instructions, assigned_operator,
        production_status, payment_status, total_amount, deposit_amount, remaining_balance,
        estimated_completion, deadline, history_json, is_demo, created_at, updated_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)
    `);
    for (const j of DEMO_PRINT_JOBS) {
      insertJob.run(
        j.id,
        j.jobOrderNumber,
        j.transactionId,
        j.customerId,
        j.customerName,
        j.customerPhone,
        j.title,
        j.specifications,
        JSON.stringify(j.attachmentNames),
        j.instructions,
        j.assignedOperator,
        j.productionStatus,
        j.paymentStatus,
        j.totalAmount,
        j.depositAmount,
        j.remainingBalance,
        j.estimatedCompletion,
        j.deadline,
        JSON.stringify(j.history),
        j.createdAt,
        j.updatedAt,
      );
    }

    const insertExp = db.prepare(`
      INSERT OR IGNORE INTO expenses (
        id, category, description, amount, vendor, recorded_by, expense_date, is_demo, created_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?)
    `);
    for (const e of DEMO_EXPENSES) {
      insertExp.run(
        e.id,
        e.category,
        e.description,
        e.amount,
        e.vendor,
        e.recordedBy,
        e.expenseDate,
        e.createdAt,
      );
    }
  });
  tx();
}

export function clearDemoDataset() {
  const tx = db.transaction(() => {
    db.prepare('DELETE FROM print_jobs WHERE is_demo = 1').run();
    db.prepare('DELETE FROM order_conversions WHERE source_id IN (SELECT id FROM transactions WHERE is_demo=1) OR sale_id IN (SELECT id FROM transactions WHERE is_demo=1)').run();
    db.prepare('DELETE FROM transactions WHERE is_demo = 1').run();
    db.prepare('DELETE FROM expenses WHERE is_demo = 1').run();
    db.prepare('DELETE FROM customers WHERE is_demo = 1').run();
    db.prepare('DELETE FROM services WHERE is_demo = 1').run();
    db.prepare('DELETE FROM inventory_items WHERE is_demo = 1').run();
    db.prepare('DELETE FROM categories WHERE is_demo = 1').run();
  });
  tx();
}

export function writeAuditLog(
  actorId: string,
  actorName: string,
  action: string,
  entityType: string,
  entityId: string,
  summary: string,
) {
  db.prepare(`
    INSERT INTO audit_logs (id, actor_id, actor_name, action, entity_type, entity_id, summary, created_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
  `).run(
    `aud_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
    actorId,
    actorName,
    action,
    entityType,
    entityId,
    summary,
    new Date().toISOString(),
  );
}
