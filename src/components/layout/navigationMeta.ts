import type React from 'react';
import {
  BarChart3,
  ClipboardList,
  Clock,
  Home,
  LayoutDashboard,
  Package,
  Printer,
  Settings,
  ShoppingCart,
  Tag,
  Users,
} from 'lucide-react';
import type { ModuleId } from '../../shared/types.ts';

export interface ModuleMeta {
  label: string;
  shortLabel: string;
  description: string;
  icon: React.FC<{ className?: string }>;
}

export const MODULE_META: Record<ModuleId, ModuleMeta> = {
  dashboard: {
    label: 'Dashboard',
    shortLabel: 'Home',
    description: 'Store revenue, print job queue, and daily overview',
    icon: LayoutDashboard,
  },
  pos: {
    label: 'POS Terminal',
    shortLabel: 'POS',
    description: 'Counter checkout, dynamic pricing, and order cart',
    icon: ShoppingCart,
  },
  jobs: {
    label: 'Print Jobs',
    shortLabel: 'Orders',
    description: 'Production tracking, specifications, and pickups',
    icon: ClipboardList,
  },
  services: {
    label: 'Services & Pricing',
    shortLabel: 'Products',
    description: 'Print catalog, bulk tiers, and material bindings',
    icon: Tag,
  },
  inventory: {
    label: 'Inventory Management',
    shortLabel: 'Stock',
    description: 'Consumables, stock counts, reorder levels, and movements',
    icon: Package,
  },
  customers: {
    label: 'Customers',
    shortLabel: 'Clients',
    description: 'Customer accounts, loyalty points, and balances',
    icon: Users,
  },
  finance: {
    label: 'Expenses & Reports',
    shortLabel: 'Reports',
    description: 'Sales ledger, expenses, and financial analytics',
    icon: BarChart3,
  },
  shifts: {
    label: 'Cashier Shifts',
    shortLabel: 'Shifts',
    description: 'Drawer reconciliation, cash movements, and shifts',
    icon: Clock,
  },
  devices: {
    label: 'Printers & Devices',
    shortLabel: 'Devices',
    description: 'Receipt printers, USB/Serial pairing, and logs',
    icon: Printer,
  },
  settings: {
    label: 'Settings',
    shortLabel: 'Settings',
    description: 'Branding, design tokens, tax rules, and staff',
    icon: Settings,
  },
};

export interface MobileTabMeta {
  id: Extract<ModuleId, 'dashboard' | 'services' | 'pos' | 'jobs'>;
  label: string;
  icon: React.FC<{ className?: string }>;
  primary?: boolean;
}

export const MOBILE_CORE_TABS: MobileTabMeta[] = [
  { id: 'dashboard', label: 'Home', icon: Home },
  { id: 'services', label: 'Products', icon: Package },
  { id: 'pos', label: 'POS', icon: ShoppingCart, primary: true },
  { id: 'jobs', label: 'Orders', icon: ClipboardList },
];
