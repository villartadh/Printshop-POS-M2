import test from 'node:test';
import assert from 'node:assert/strict';
import {DEFAULT_SETTINGS,DEMO_SERVICES} from '../src/shared/defaults.ts';
import {calculateServicePrice,calculateCartItemTotals,calculateOrderTotals,getDefaultServiceSelection} from '../src/shared/pricingEngine.ts';
import {verifyCatalogPricing} from '../src/shared/authoritativePricing.ts';
const settings=structuredClone(DEFAULT_SETTINGS);
const service=DEMO_SERVICES[0];
const lookup=id=>id===service.id?service:undefined;
function fixture(config=getDefaultServiceSelection(service),s=service,settingsArg=settings){
 const tax=settingsArg.taxAndCurrency,calc=calculateServicePrice(s,config,tax);
 let item={id:'line',serviceId:s.id,sku:s.sku,serviceName:s.name,pricingMethod:s.pricingMethod,unitLabel:s.unitLabel,config,unitPrice:calc.unitPrice,effectiveQuantity:calc.effectiveQuantity,lineSubtotal:calc.lineSubtotal,costTotal:calc.estimatedCost,displaySummary:calc.displaySummary,requiresProductionJob:s.requiresProductionJob,consumables:s.consumables,discountType:'none',discountValue:0};
 item={...item,...calculateCartItemTotals(item,tax)};
 return {items:[item],orderDiscountType:'none',orderDiscountValue:0,...calculateOrderTotals([item],'none',0,tax),payments:[]};
}
const verify=(t,role='cashier')=>verifyCatalogPricing(t,lookup,settings,role);
test('catalog price accepted',()=>assert.equal(verify(fixture()).totalAmount,3));
test('forged price rejected even with consistent totals',()=>{let t=fixture();Object.assign(t.items[0],{unitPrice:1,lineSubtotal:1,lineTotal:1});Object.assign(t,{subtotal:1,totalAmount:1});assert.throws(()=>verify(t),/Price/);});
test('catalog inventory binding restored',()=>{let t=fixture();t.items[0].consumables=[];assert.deepEqual(verify(t).items[0].consumables,service.consumables);});
test('unknown or archived service rejected',()=>{let t=fixture();assert.throws(()=>verifyCatalogPricing(t,()=>({...service,isArchived:true}),settings,'cashier'));t.items[0].serviceId='missing';assert.throws(()=>verify(t));});
test('bulk tier pages and copies',()=>{let c=getDefaultServiceSelection(service);c.pages=25;c.copies=2;assert.equal(verify(fixture(c)).totalAmount,125);});
test('unknown attribute rejected',()=>{let t=fixture();t.items[0].config.selectedAttributes.paperSize='bogus';assert.throws(()=>verify(t),/option/);});
test('invalid quantity rejected',()=>{let t=fixture();t.items[0].config.pages=0;assert.throws(()=>verify(t),/configuration/);});
test('forged tax rejected',()=>{let t=fixture();t.taxAmount=1;assert.throws(()=>verify(t),/tax/);});
test('custom item manager-only',()=>{let t=fixture();Object.assign(t.items[0],{serviceId:'custom',isCustomItem:true});assert.throws(()=>verify(t),/manager/);assert.equal(verify(t,'manager').totalAmount,3);});
test('payment method spoofing rejected',()=>{let t=fixture();t.payments=[{methodId:'gcash',methodType:'cash',referenceNumber:'ref',verificationStatus:'verified'}];assert.throws(()=>verify(t),/payment method/);});
test('area pricing and non-repeating design fee',()=>{const s={...service,id:'banner',basePrice:25,pricingMethod:'per_sqft',allowCustomDimensions:true,allowPagesAndCopies:false,bulkTiers:[],attributeGroups:[],defaultDesignFee:10};let c=getDefaultServiceSelection(s);Object.assign(c,{width:3,height:4,quantity:2});assert.equal(verifyCatalogPricing(fixture(c,s),()=>s,settings,'cashier').totalAmount,610);});
test('zero rush rate respected',()=>{const s={...service,rushFeePercent:0};let c=getDefaultServiceSelection(s);c.isRush=true;assert.equal(calculateServicePrice(s,c,settings.taxAndCurrency).lineSubtotal,3);});
