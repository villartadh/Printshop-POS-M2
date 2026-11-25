import type {
  CartItem,
  PrintService,
  ServiceConfigSelection,
  TaxAndCurrencyConfig,
} from './types.ts';

export interface PricingCalculationResult {
  unitPrice: number;
  effectiveQuantity: number;
  areaSqFt: number;
  linearFt: number;
  linearMeters: number;
  baseSubtotal: number;
  attributesDelta: number;
  rushAmount: number;
  extraFees: number;
  lineSubtotal: number;
  estimatedCost: number;
  displaySummary: string;
}

export function roundAmount(value: number, config: TaxAndCurrencyConfig): number {
  const factor = Math.pow(10, config.decimalPlaces);
  let rounded = Math.round((value + Number.EPSILON) * factor) / factor;
  if (config.roundingMode === 'nearest_0_25') {
    rounded = Math.round(rounded * 4) / 4;
  } else if (config.roundingMode === 'nearest_1') {
    rounded = Math.round(rounded);
  }
  return rounded;
}

export function formatCurrency(amount: number, config: TaxAndCurrencyConfig): string {
  const safe = Number.isFinite(amount) ? amount : 0;
  return `${config.currencySymbol}${safe.toLocaleString('en-PH', {
    minimumFractionDigits: config.decimalPlaces,
    maximumFractionDigits: config.decimalPlaces,
  })}`;
}

export function convertToFeet(value: number, unit: ServiceConfigSelection['dimensionUnit']): number {
  if (!Number.isFinite(value) || value <= 0) return 0;
  switch (unit) {
    case 'ft':
      return value;
    case 'in':
      return value / 12;
    case 'cm':
      return value / 30.48;
    case 'm':
      return value * 3.28084;
    default:
      return value;
  }
}

export function convertToMeters(value: number, unit: ServiceConfigSelection['dimensionUnit']): number {
  return convertToFeet(value, unit) * 0.3048;
}

export function evaluateFormulaExpression(
  expression: string | undefined,
  vars: Record<string, number>,
): number | null {
  if (!expression || !expression.trim()) return null;
  // Strictly sanitize to math expressions with known variable identifiers only
  const allowedPattern = /^[a-zA-Z0-9_+\-*/().\s]+$/;
  if (!allowedPattern.test(expression)) return null;

  let normalized = expression;
  const sortedKeys = Object.keys(vars).sort((a, b) => b.length - a.length);
  for (const key of sortedKeys) {
    const val = Number.isFinite(vars[key]) ? vars[key] : 0;
    normalized = normalized.replace(new RegExp(`\\b${key}\\b`, 'g'), String(val));
  }

  // After replacing known variables, only numbers and arithmetic operators may remain
  if (!/^[0-9+\-*/().\s]+$/.test(normalized)) return null;
  try {
    const fn = new Function(`"use strict"; return (${normalized});`);
    const result = Number(fn());
    return Number.isFinite(result) && result >= 0 ? result : null;
  } catch {
    return null;
  }
}

export function getDefaultServiceSelection(service: PrintService): ServiceConfigSelection {
  const selectedAttributes: Record<string, string> = {};
  for (const group of service.attributeGroups) {
    if (group.defaultOptionId) {
      selectedAttributes[group.key] = group.defaultOptionId;
    } else if (group.options.length > 0) {
      selectedAttributes[group.key] = group.options[0].id;
    }
  }
  return {
    quantity: 1,
    pages: 1,
    copies: 1,
    width: service.allowCustomDimensions ? 3 : 1,
    height: service.allowCustomDimensions ? 2 : 1,
    dimensionUnit: 'ft',
    selectedAttributes,
    isRush: false,
    designFee: service.defaultDesignFee || 0,
    laborFee: 0,
    notes: '',
  };
}

export function calculateServicePrice(
  service: PrintService,
  config: ServiceConfigSelection,
  taxCurrency: TaxAndCurrencyConfig,
): PricingCalculationResult {
  const qty = Math.max(1, Number(config.quantity) || 1);
  const pages = Math.max(1, Number(config.pages) || 1);
  const copies = Math.max(1, Number(config.copies) || 1);

  const widthFt = convertToFeet(config.width, config.dimensionUnit);
  const heightFt = convertToFeet(config.height, config.dimensionUnit);
  const areaSqFt = Math.max(0.1, Number((widthFt * heightFt).toFixed(4)));
  const linearFt = Math.max(0.1, Number(Math.max(widthFt, heightFt).toFixed(4)));
  const linearMeters = Math.max(
    0.1,
    Number(
      Math.max(
        convertToMeters(config.width, config.dimensionUnit),
        convertToMeters(config.height, config.dimensionUnit),
      ).toFixed(4),
    ),
  );

  // Attribute adjustments
  let attributesDelta = 0;
  let attributeMultiplier = 1;
  const chosenLabels: string[] = [];

  for (const group of service.attributeGroups) {
    const selectedId = config.selectedAttributes[group.key] || group.defaultOptionId;
    const option = group.options.find((o) => o.id === selectedId);
    if (option) {
      attributesDelta += Number(option.priceDelta) || 0;
      if (option.priceMultiplier && option.priceMultiplier > 0) {
        attributeMultiplier *= option.priceMultiplier;
      }
      chosenLabels.push(option.label);
    }
  }

  // Determine effective quantity and base unit price
  let effectiveQuantity = qty;
  if (service.pricingMethod === 'per_page') {
    effectiveQuantity = service.allowPagesAndCopies ? pages * copies : qty;
  } else if (service.pricingMethod === 'per_copy' || service.pricingMethod === 'per_sheet') {
    effectiveQuantity = service.allowPagesAndCopies ? pages * copies : qty;
  } else if (service.pricingMethod === 'per_sqft') {
    effectiveQuantity = Number((areaSqFt * qty).toFixed(2));
  } else if (service.pricingMethod === 'per_linear_ft') {
    effectiveQuantity = Number((linearFt * qty).toFixed(2));
  } else if (service.pricingMethod === 'per_meter') {
    effectiveQuantity = Number((linearMeters * qty).toFixed(2));
  } else if (service.pricingMethod === 'fixed') {
    effectiveQuantity = qty;
  }

  // Check bulk tiers
  let resolvedBasePrice = service.basePrice;
  if (
    (service.pricingMethod === 'tiered_bulk' || service.bulkTiers.length > 0) &&
    service.bulkTiers.length > 0
  ) {
    const sortedTiers = [...service.bulkTiers].sort((a, b) => b.minQty - a.minQty);
    const matchedTier = sortedTiers.find((t) => effectiveQuantity >= t.minQty);
    if (matchedTier) {
      resolvedBasePrice = matchedTier.unitPrice;
    }
  }

  let unitPrice = (resolvedBasePrice + attributesDelta) * attributeMultiplier;
  let baseSubtotal = unitPrice * effectiveQuantity;

  if (service.pricingMethod === 'formula') {
    const evaluated = evaluateFormulaExpression(service.formulaExpression, {
      base: resolvedBasePrice,
      qty,
      pages,
      copies,
      areaSqFt,
      linearFt,
      linearMeters,
      attrDelta: attributesDelta,
      attrMult: attributeMultiplier,
      designFee: Number(config.designFee) || 0,
      laborFee: Number(config.laborFee) || 0,
    });
    if (evaluated !== null) {
      baseSubtotal = evaluated;
      unitPrice = effectiveQuantity > 0 ? baseSubtotal / effectiveQuantity : baseSubtotal;
    }
  }

  const rushAmount =
    service.allowRushFee && config.isRush
      ? baseSubtotal * ((Number(service.rushFeePercent) || 0) / 100)
      : 0;
  const extraFees = (Number(config.designFee) || 0) + (Number(config.laborFee) || 0);
  const rawTotal = baseSubtotal + rushAmount + extraFees;

  const lineSubtotal = roundAmount(rawTotal, taxCurrency);
  const roundedUnitPrice = roundAmount(unitPrice, taxCurrency);
  const estimatedCost = roundAmount((service.costEstimate || 0) * effectiveQuantity, taxCurrency);

  const summaryParts: string[] = [];
  if (service.allowCustomDimensions) {
    summaryParts.push(`${config.width}×${config.height} ${config.dimensionUnit}`);
  }
  if (service.allowPagesAndCopies && (pages > 1 || copies > 1)) {
    summaryParts.push(`${pages}p × ${copies}c`);
  }
  if (chosenLabels.length > 0) {
    summaryParts.push(chosenLabels.join(' · '));
  }
  if (config.isRush) {
    summaryParts.push('Rush');
  }

  return {
    unitPrice: roundedUnitPrice,
    effectiveQuantity,
    areaSqFt,
    linearFt,
    linearMeters,
    baseSubtotal: roundAmount(baseSubtotal, taxCurrency),
    attributesDelta,
    rushAmount: roundAmount(rushAmount, taxCurrency),
    extraFees: roundAmount(extraFees, taxCurrency),
    lineSubtotal,
    estimatedCost,
    displaySummary: summaryParts.join(' · ') || `${effectiveQuantity} ${service.unitLabel}`,
  };
}

export function calculateCartItemTotals(
  item: Omit<CartItem, 'lineDiscount' | 'lineTotal'>,
  taxCurrency: TaxAndCurrencyConfig,
): Pick<CartItem, 'lineDiscount' | 'lineTotal'> {
  let discount = 0;
  if (item.discountType === 'percent') {
    discount = item.lineSubtotal * (Math.min(100, Math.max(0, item.discountValue)) / 100);
  } else if (item.discountType === 'fixed') {
    discount = Math.min(item.lineSubtotal, Math.max(0, item.discountValue));
  }
  const lineDiscount = roundAmount(discount, taxCurrency);
  const lineTotal = roundAmount(Math.max(0, item.lineSubtotal - lineDiscount), taxCurrency);
  return { lineDiscount, lineTotal };
}

export function calculateOrderTotals(
  items: CartItem[],
  orderDiscountType: 'none' | 'percent' | 'fixed',
  orderDiscountValue: number,
  taxCurrency: TaxAndCurrencyConfig,
) {
  const subtotal = roundAmount(
    items.reduce((sum, item) => sum + item.lineSubtotal, 0),
    taxCurrency,
  );
  const itemDiscountsTotal = roundAmount(
    items.reduce((sum, item) => sum + item.lineDiscount, 0),
    taxCurrency,
  );
  const afterItemDiscounts = Math.max(0, subtotal - itemDiscountsTotal);

  let orderDiscountAmount = 0;
  if (orderDiscountType === 'percent') {
    orderDiscountAmount =
      afterItemDiscounts * (Math.min(100, Math.max(0, orderDiscountValue)) / 100);
  } else if (orderDiscountType === 'fixed') {
    orderDiscountAmount = Math.min(afterItemDiscounts, Math.max(0, orderDiscountValue));
  }
  orderDiscountAmount = roundAmount(orderDiscountAmount, taxCurrency);

  const taxableBase = Math.max(0, afterItemDiscounts - orderDiscountAmount);
  let taxAmount = 0;
  let totalAmount = taxableBase;

  if (taxCurrency.taxEnabled && taxCurrency.taxRatePercent > 0) {
    const rate = taxCurrency.taxRatePercent / 100;
    if (taxCurrency.taxInclusive) {
      taxAmount = roundAmount(taxableBase - taxableBase / (1 + rate), taxCurrency);
      totalAmount = roundAmount(taxableBase, taxCurrency);
    } else {
      taxAmount = roundAmount(taxableBase * rate, taxCurrency);
      totalAmount = roundAmount(taxableBase + taxAmount, taxCurrency);
    }
  } else {
    totalAmount = roundAmount(taxableBase, taxCurrency);
  }

  return {
    subtotal,
    itemDiscountsTotal,
    orderDiscountAmount,
    taxAmount,
    totalAmount,
  };
}
