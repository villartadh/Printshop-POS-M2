import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawn} from 'node:child_process';
import {randomUUID} from 'node:crypto';
import net from 'node:net';
import { DatabaseSync } from 'node:sqlite';
import { snapshot } from './database-maintenance.mjs';
import { calculateServicePrice, calculateCartItemTotals, calculateOrderTotals } from '../src/shared/pricingEngine.ts';
import { DEFAULT_SETTINGS } from '../src/shared/defaults.ts';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
export function acceptanceEnv(port, instance){return {...Object.fromEntries(Object.entries(process.env).filter(([k])=>/^(PATH|SYSTEMROOT|WINDIR|TEMP|TMP|HOME|USERPROFILE|APPDATA|LOCALAPPDATA)$/i.test(k))),NODE_ENV:'production',HOST:'127.0.0.1',PORT:String(port),LDC_INSTANCE:instance,SEED_DEMO:'false'};}
export function verifyResponse(result,status){assert.equal(result.status,status,`Expected HTTP ${status}, got ${result.status}`);return result.body;}
export function assertInstance(body,instance){assert.equal(body?.status,'ok');assert.equal(body?.instance,instance);}
export async function freePort(){const s=net.createServer();await new Promise((resolve,reject)=>{s.once('error',reject);s.listen(0,'127.0.0.1',resolve);});const port=s.address().port;await new Promise(resolve=>s.close(resolve));return port;}
async function main(){
 const report={version:JSON.parse(fs.readFileSync(path.join(root,'package.json'))).version,startedAt:new Date().toISOString(),status:'BLOCKED',checks:[]};
 const evidence=path.join(root,'.ldc');fs.mkdirSync(evidence,{recursive:true});
 let sandbox,child,exitPromise,spawnFailed=false;
 const check=async(name,fn)=>{try{await fn();report.checks.push({name,status:'PASS'});console.log('PASS',name);}catch(e){report.checks.push({name,status:'FAIL',error:String(e.message)});throw e;}};
 try{
  for(const p of ['node_modules/tsx/package.json','node_modules/better-sqlite3/package.json','dist/index.html'])if(!fs.existsSync(path.join(root,p)))throw new Error('Missing prerequisite: '+p+'; run install and build first.');
  sandbox=fs.mkdtempSync(path.join(os.tmpdir(),'printshop-pos-acceptance-'));fs.cpSync(path.join(root,'dist'),path.join(sandbox,'dist'),{recursive:true});
  const port=await freePort(), instance=randomUUID(), base=`http://127.0.0.1:${port}`;
  const password=randomUUID();let token,shift,customer,stock,service,paid,partial,originalPaidRequest;
  const request=async(url,{method='GET',body,auth=false,headers={}}={})=>{const r=await fetch(base+url,{method,headers:{...(body!==undefined?{'Content-Type':'application/json'}:{}),...(auth?{Authorization:'Bearer '+token}:{}),...headers},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(5000)});const raw=await r.text();let data;try{data=JSON.parse(raw);}catch{data=raw;}return {status:r.status,body:data,headers:r.headers};};
  const start=async()=>{child=spawn(process.execPath,['--import',import.meta.resolve('tsx'),path.join(root,'scripts/start.ts')],{cwd:sandbox,env:acceptanceEnv(port,instance),stdio:'ignore'});spawnFailed=false;child.once('error',()=>{spawnFailed=true;});exitPromise=new Promise(resolve=>{child.once('exit',resolve);child.once('error',resolve);});for(let i=0;i<60;i++){if(spawnFailed||child.exitCode!==null||child.signalCode!==null)throw new Error('Isolated server exited during startup');try{assertInstance(verifyResponse(await request('/api/health'),200),instance);return;}catch{}await new Promise(r=>setTimeout(r,500));}throw new Error('Isolated server health timeout');};
  const stop=async()=>{if(!child)return;const p=child;if(p.exitCode===null&&p.signalCode===null&&!spawnFailed)p.kill();const timer=setTimeout(()=>p.kill('SIGKILL'),5000);try{await exitPromise;}finally{clearTimeout(timer);child=null;}};
  report.status='FAIL';
  await check('isolated production startup',start);
  await check('production HTML served',async()=>{const r=await request('/');assert.equal(r.status,200);assert.match(r.headers.get('content-type')||'',/text\/html/);});
  await check('anonymous bootstrap denied',async()=>verifyResponse(await request('/api/bootstrap'),401));
  await check('foreign origin denied',async()=>verifyResponse(await request('/api/health',{headers:{Origin:'https://example.invalid'}}),403));
  await check('fresh database needs setup',async()=>assert.equal(verifyResponse(await request('/api/auth/status'),200).needsSetup,true));
  await check('administrator creation',async()=>{const x=verifyResponse(await request('/api/auth/setup-admin',{method:'POST',body:{username:'acceptance',fullName:'Synthetic Test',password}}),201);assert.equal(x.user.role,'admin');assert.ok(x.token);token=x.token;});
  await check('duplicate administrator setup denied',async()=>verifyResponse(await request('/api/auth/setup-admin',{method:'POST',body:{username:'other',fullName:'Synthetic Other',password}}),409));
  await check('authenticated bootstrap, no demo sales',async()=>{const x=verifyResponse(await request('/api/bootstrap',{auth:true}),200);assert.deepEqual(x.transactions,[]);});
  await check('cashier shift start',async()=>{shift=verifyResponse(await request('/api/shifts/start',{method:'POST',auth:true,body:{openingCash:500}}),201);assert.equal(shift.expectedCash,500);});
  await check('duplicate shift denied',async()=>verifyResponse(await request('/api/shifts/start',{method:'POST',auth:true,body:{openingCash:500}}),409));
  const write = async (url, body, status = 201, method = 'POST') =>
    { const result = await request('/api'+url, {method, body, auth:true}); assert.equal(result.status,status,`${method} ${url} expected ${status}, got ${result.status}: ${result.body?.error || ""}`); return result.body; };
  const state = async () => verifyResponse(await request('/api/bootstrap', {auth:true}), 200);
  const makeOrder = (type, tender, extra = {}) => {
    const tax = DEFAULT_SETTINGS.taxAndCurrency;
    const config = {quantity:2, pages:1, copies:1, width:1, height:1, dimensionUnit:'in',
      designFee:0, laborFee:0, isRush:false, selectedAttributes:{}};
    const calc = calculateServicePrice(service, config, tax);
    const item = {id:randomUUID(), serviceId:service.id, serviceName:service.name, config,
      ...calc, discountType:'none', discountValue:0, consumables:service.consumables};
    Object.assign(item, calculateCartItemTotals(item, tax));
    const totals = calculateOrderTotals([item], 'none', 0, tax);
    const amountPaid = Math.min(totals.totalAmount, tender);
    return {id:randomUUID(), clientMutationId:randomUUID(), type, items:[item], ...totals,
      shiftId:shift.id, orderDiscountType:'none', orderDiscountValue:0,
      customerId:customer.id, customerName:customer.name, amountPaid,
      balanceDue:totals.totalAmount-amountPaid, changeAmount:tender-amountPaid,
      paymentStatus:amountPaid===totals.totalAmount?'paid':amountPaid?'partial':'unpaid',
      payments:tender?[{methodId:'cash',methodType:'cash',amount:tender,verificationStatus:'verified'}]:[],
      ...extra};
  };
  await check('fresh business identity contains no fictional registration details', async () => {
    const {branding,taxAndCurrency} = (await state()).settings;
    for (const key of ['businessName','storeId','address','phone','email','taxRegistrationNumber','receiptFooter']) assert.equal(branding[key],'',key);
    assert.equal(branding.showQrOnReceipt,false);
    assert.equal(taxAndCurrency.currencyCode,'PHP');
    assert.equal(taxAndCurrency.taxEnabled,false);
  });
  await check('settings validation preserves authoritative configuration on rejection', async () => {
    const original = (await state()).settings;
    const valid = structuredClone(original);
    valid.branding.businessName = 'Synthetic Acceptance Shop';
    const saved = await write('/settings',valid,200,'PUT');
    assert.equal(saved.branding.businessName,valid.branding.businessName);
    const invalidCases = [
      {branding:valid.branding,tokens:valid.tokens},
      {...valid,taxAndCurrency:{...valid.taxAndCurrency,taxRatePercent:-1}},
      {...valid,taxAndCurrency:{...valid.taxAndCurrency,decimalPlaces:3}},
      {...valid,paymentMethods:[valid.paymentMethods[0],valid.paymentMethods[0]]},
      {...valid,paymentMethods:valid.paymentMethods.map(m=>({...m,enabled:false}))},
      {...valid,hiddenModules:['pos']},
      {...valid,receiptTemplate:{...valid.receiptTemplate,defaultPaperSize:'unknown'}},
      {...valid,branding:{...valid.branding,businessName:123}},
      {...valid,posLayout:{...valid.posLayout,gridColumnsDesktop:0}},
      {...valid,dashboardWidgets:[null]},
    ];
    for (const payload of invalidCases) {
      await write('/settings',payload,400,'PUT');
      assert.deepEqual((await state()).settings,saved);
    }
    const stale = structuredClone(valid);
    stale.branding.businessName = 'Synthetic stale edit';
    await write('/settings',stale,409,'PUT');
    assert.deepEqual((await state()).settings,saved);
    const restored = await write('/settings',{...original,updatedAt:saved.updatedAt},200,'PUT');
    assert.notEqual(restored.updatedAt,saved.updatedAt);
    assert.deepEqual(restored.branding,original.branding);
  });
  await check('expense validation, Philippine business date and deletion audit', async () => {
    const fixture={description:'Synthetic expense',category:'other',amount:25};
    for (const invalid of [{amount:'Infinity'},{amount:-1},{amount:0},{amount:true},{description:' '},{category:'invalid'},{vendor:42},{expenseDate:'2026-02-30'},{expenseDate:'not-a-date'}]) {
      await write('/expenses',{...fixture,...invalid},400);
    }
    const date=()=>new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Manila',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
    const before=date();
    const expense=await write('/expenses',fixture);
    assert.ok([before,date()].includes(expense.expenseDate));
    assert.equal(expense.amount,25);
    assert.ok((await state()).expenses.some(e=>e.id===expense.id));
    await write('/expenses/'+expense.id,undefined,200,'DELETE');
    await write('/expenses/'+expense.id,undefined,404,'DELETE');
    const data=await state();
    assert.ok(!data.expenses.some(e=>e.id===expense.id));
    assert.ok(data.auditLogs.some(log=>log.entityId===expense.id&&log.action==='EXPENSE_DELETED'));
  });
  await check('synthetic catalog and customer setup', async () => {
    const category = await write('/categories',{name:'Acceptance Services'});
    stock = await write('/inventory',{name:'Acceptance Paper',unit:'sheet',quantityOnHand:20,unitCost:1});
    service = await write('/services',{name:'Acceptance Prints',categoryId:category.id,
      pricingMethod:'fixed',basePrice:20,requiresProductionJob:true,
      consumables:[{inventoryItemId:stock.id,quantityPerUnit:1}]});
    customer = await write('/customers',{name:'Acceptance Customer'});
  });
  await check('inventory numeric validation, reservations, rollback and movement preservation',async()=>{
    for(const patch of [{quantityOnHand:-1},{quantityOnHand:'Infinity'},{unitCost:true},{reservedQuantity:-1},{reservedQuantity:21},{name:42}]) {
      await write('/inventory',{name:'Invalid fixture',unit:'sheet',quantityOnHand:20,...patch},400);
    }
    const url='/inventory/'+stock.id;
    await write(url,{reservedQuantity:21},400,'PATCH');
    await write(url,{reservedQuantity:19},200,'PATCH');
    const before=await state();
    await write('/transactions',makeOrder('sale',50),409);
    const after=await state();
    assert.deepEqual(after.transactions,before.transactions);
    assert.deepEqual(after.stockMovements,before.stockMovements);
    assert.deepEqual(after.shifts,before.shifts);
    assert.deepEqual(after.customers,before.customers);
    assert.equal(after.inventory.find(i=>i.id===stock.id).quantityOnHand,20);
    const adjust=(payload,status=200)=>write(url+'/adjust',{clientMutationId:randomUUID(),...payload},status);
    for(const payload of [{quantityDelta:'Infinity'},{quantityDelta:true},{quantityDelta:0},{quantityDelta:0.001},{quantityDelta:1,movementType:'invalid'}]) await adjust(payload,400);
    await adjust({movementType:'wastage',quantityDelta:2},409);
    await write(url,undefined,409,'DELETE');
    assert.deepEqual((await state()).stockMovements,before.stockMovements);
    const purchase={clientMutationId:randomUUID(),movementType:'purchase',quantityDelta:2};
    await write(url+'/adjust',purchase,200);
    await write(url+'/adjust',purchase,200);
    assert.equal((await state()).inventory.find(i=>i.id===stock.id).quantityOnHand,22);
    await adjust({movementType:'wastage',quantityDelta:2});
    await write(url,{reservedQuantity:0},200,'PATCH');
    assert.equal((await state()).inventory.find(i=>i.id===stock.id).quantityOnHand,20);
    const empty=await write('/inventory',{name:'Unused empty fixture',unit:'sheet',quantityOnHand:0});
    await write('/inventory/'+empty.id,undefined,200,'DELETE');
    const history=await write('/inventory',{name:'Historical fixture',unit:'sheet',quantityOnHand:1});
    await write('/inventory/'+history.id+'/adjust',{clientMutationId:randomUUID(),movementType:'wastage',quantityDelta:1},200);
    await write('/inventory/'+history.id,undefined,409,'DELETE');
    assert.equal((await state()).stockMovements.filter(m=>m.inventoryItemId===history.id).length,2);
  });
  await check('purchase price and weighted average valuation are atomic and replay safe',async()=>{
    const item=await write('/inventory',{name:'Synthetic Purchase Cost',unit:'sheet',quantityOnHand:100,unitCost:2});
    const url='/inventory/'+item.id+'/adjust';
    const p={clientMutationId:randomUUID(),movementType:'purchase',quantityDelta:50,purchaseUnitCost:5};
    const before=await state();
    for(const patch of [{purchaseUnitCost:'5'},{purchaseUnitCost:-1},{movementType:'wastage'}])await write(url,{...p,...patch,clientMutationId:randomUUID()},400);
    assert.deepEqual((await state()).stockMovements,before.stockMovements);
    const result=await write(url,p,200);
    assert.equal(result.item.quantityOnHand,150);
    assert.equal(result.item.unitCost,3);
    assert.equal(result.movement.purchaseUnitCost,5);
    assert.equal(result.movement.unitCostAtMovement,5);
    assert.deepEqual(await write(url,p,200),result);
    await write(url,{...p,purchaseUnitCost:6},400);
    const current=await state();
    assert.equal(current.inventory.find(i=>i.id===item.id).unitCost,3);
    assert.equal(current.stockMovements.filter(m=>m.inventoryItemId===item.id).length,2);
    const later=await write(url,{clientMutationId:randomUUID(),movementType:'purchase',quantityDelta:10},200);
    assert.equal(later.item.unitCost,3);
    assert.equal(later.movement.purchaseUnitCost,undefined);
  });
  await check('authoritative catalog checkout, tender/change and duplicate', async () => {
    const sale = makeOrder('sale',50);
    originalPaidRequest=structuredClone(sale);
    await write('/transactions',{...sale,items:[{...sale.items[0],unitPrice:1}]},409);
    paid = (await write('/transactions',sale)).transaction;
    assert.equal(paid.totalAmount,40);
    assert.equal(paid.changeAmount,10);
    assert.equal((await write('/transactions',sale,200)).duplicate,true);
    await write('/transactions',{...sale,notes:'Changed after submission'},409);
    await write('/transactions',{...sale,payments:[{...sale.payments[0],amount:60}],changeAmount:20},409);
    const data = await state();
    assert.equal(data.inventory.find(i=>i.id===stock.id).quantityOnHand,18);
    assert.equal(data.shifts.find(s=>s.id===shift.id).expectedCash,540);
  });
  await check('cash refund preserves physical stock history and reconciles shift once', async () => {
    const url = '/transactions/'+paid.id+'/refund';
    assert.equal((await write(url,{reason:'Synthetic refund'},200)).paymentStatus,'refunded');
    await write(url,{reason:'Duplicate refund'},409);
    const data = await state();
    assert.equal(data.inventory.find(i=>i.id===stock.id).quantityOnHand,18);
    assert.equal(data.shifts.find(s=>s.id===shift.id).expectedCash,500);
  });
  await check('quotation conversion, partial balance and production release', async () => {
    const quote = (await write('/transactions',makeOrder('quotation',0))).transaction;
    const payload = makeOrder('sale',15,{sourceOrderId:quote.id});
    partial = (await write('/transactions',payload)).transaction;
    await write('/transactions',makeOrder('sale',15,{sourceOrderId:quote.id}),409);
    const data = await state();
    assert.equal(data.customers.find(c=>c.id===customer.id).outstandingBalance,25);
    const job = data.printJobs.find(j=>j.transactionId===partial.id);
    const url = '/print-jobs/'+job.id;
    for (const productionStatus of ['in_production','quality_checking','ready_for_pickup']) {
      await write(url,{productionStatus},200,'PATCH');
    }
    await write(url,{productionStatus:'completed'},409,'PATCH');
    const collectionUrl='/customers/'+customer.id+'/pay-balance';
    const payment={amount:25,clientMutationId:randomUUID()};
    await write(collectionUrl,{amount:25},400);
    const first=await write(collectionUrl,payment,200);
    assert.equal(first.duplicate,false);assert.equal(first.outstandingBalance,0);
    const accepted=await state();
    const replay=await write(collectionUrl,payment,200);
    assert.equal(replay.duplicate,true);assert.equal(replay.clientMutationId,payment.clientMutationId);
    await write(collectionUrl,{...payment,amount:24},409);
    const another=await write('/customers',{name:'Synthetic Other Collection Customer'});
    await write('/customers/'+another.id+'/pay-balance',payment,409);
    const after=await state();
    assert.deepEqual(after.shifts,accepted.shifts);
    assert.deepEqual(after.transactions,accepted.transactions);
    assert.deepEqual(after.printJobs,accepted.printJobs);
    assert.deepEqual(after.customers.find(c=>c.id===customer.id),accepted.customers.find(c=>c.id===customer.id));
    assert.equal((await write(url,{productionStatus:'completed'},200,'PATCH')).productionStatus,'completed');
    const settled = await state();
    assert.equal(settled.customers.find(c=>c.id===customer.id).outstandingBalance,0);
    assert.equal(settled.shifts.find(s=>s.id===shift.id).expectedCash,540);
  });
  await check('cash void retains audit history', async () => {
    const sale = (await write('/transactions',makeOrder('sale',0))).transaction;
    assert.equal((await write('/transactions/'+sale.id+'/void',{reason:'Synthetic void'},200)).paymentStatus,'voided');
    const data = await state();
    assert.ok(data.auditLogs.some(log=>log.entityId===sale.id && log.action==='TXN_VOIDED'));
    assert.equal(data.inventory.find(i=>i.id===stock.id).quantityOnHand,14);
  });
  await check('live backup and isolated recovery copy', async () => {
    const backup = path.join(sandbox,'backup.db'), recovery = path.join(sandbox,'recovery.db');
    snapshot(path.join(sandbox,'data','printshop-pos.db'),backup);
    snapshot(backup,recovery);
    const db = new DatabaseSync(recovery,{readOnly:true});
    try {
      assert.equal(db.prepare('SELECT payment_status FROM transactions WHERE id=?').get(partial.id).payment_status,'paid');
      assert.throws(()=>snapshot(backup,recovery),/refusing to overwrite/);
    } finally {db.close();}
  });
  await check('centavo precision rejects invalid tender and drawer values without financial side effects',async()=>{
    const before=await state();
    const sale=makeOrder('sale',50);
    for(const patch of [{amountPaid:39.99},{changeAmount:10.01},{payments:[{...sale.payments[0],amount:50.001}]}])await write('/transactions',{...sale,...patch,clientMutationId:randomUUID()},400);
    for(const value of [null,'10',true,0.001,-1]) {
      await write('/shifts/start',{openingCash:value},400);
      await write('/shifts/'+shift.id+'/movement',{type:'cash_in',amount:value,reason:'Invalid fixture'},400);
      await write('/shifts/'+shift.id+'/close',{actualCash:value},400);
    }
    const after=await state();
    assert.deepEqual(after.shifts,before.shifts);assert.deepEqual(after.customers,before.customers);assert.deepEqual(after.transactions,before.transactions);assert.deepEqual(after.stockMovements,before.stockMovements);
    const movementUrl='/shifts/'+shift.id+'/movement';
    const movement={type:'cash_in',amount:0.1,reason:'Centavo acceptance',clientMutationId:randomUUID()};
    await write(movementUrl,{type:'cash_in',amount:0.1,reason:'Missing reference'},400);
    const first=await write(movementUrl,movement,200);
    const accepted=await state();
    const replay=await write(movementUrl,movement,200);
    assert.equal(first.duplicate,false);assert.equal(replay.duplicate,true);assert.equal(replay.movementId,first.movementId);
    await write(movementUrl,{...movement,amount:0.2},409);
    await write(movementUrl,{...movement,reason:'Changed'},409);
    assert.deepEqual((await state()).shifts,accepted.shifts);
    await write('/shifts/'+shift.id+'/movement',{type:'cash_in',amount:0.2,reason:'Centavo acceptance',clientMutationId:randomUUID()},200);
    assert.equal((await state()).shifts.find(s=>s.id===shift.id).expectedCash,540.3);
    await write('/shifts/'+shift.id+'/movement',{type:'cash_out',amount:0.3,reason:'Centavo acceptance reversal',clientMutationId:randomUUID()},200);
    assert.equal((await state()).shifts.find(s=>s.id===shift.id).expectedCash,540);
  });
  await check('cashier shift close',async()=>{const x=verifyResponse(await request(`/api/shifts/${shift.id}/close`,{method:'POST',auth:true,body:{actualCash:540}}),200);assert.equal(x.discrepancy,0);});
  await check('database and session persist across restart',async()=>{await stop();await start();const x=verifyResponse(await request('/api/bootstrap',{auth:true}),200);assert.ok(x.shifts.some(s=>s.id===shift.id&&s.status==='closed'));});
  await check('historical receipt snapshot survives settings change, restart and duplicate submission',async()=>{
    const original=(await state()).settings;
    const before=(await state()).transactions.find(t=>t.id===paid.id);
    assert.ok(before.receiptSnapshot);
    assert.deepEqual(before.receiptSnapshot.branding,original.branding);
    const changed=await write('/settings',{...original,branding:{...original.branding,businessName:'Synthetic renamed historical shop'},taxAndCurrency:{...original.taxAndCurrency,currencySymbol:'USD',currencyCode:'USD'}},200,'PUT');
    try {
      await stop();await start();
      const after=(await state()).transactions.find(t=>t.id===paid.id);
      assert.deepEqual(after.receiptSnapshot,before.receiptSnapshot);
      const replay=await write('/transactions',{...originalPaidRequest,syncStatus:'pending_sync',receiptSnapshot:{branding:changed.branding,taxAndCurrency:changed.taxAndCurrency}},200);
      assert.equal(replay.duplicate,true);
      assert.deepEqual(replay.transaction.receiptSnapshot,before.receiptSnapshot);
    } finally {
      const current=(await state()).settings;
      await write('/settings',{...original,updatedAt:current.updatedAt},200,'PUT');
    }
  });
  await check('cashier controls and sanitized bootstrap',async()=>{
    const cashierPassword = randomUUID();
    await write('/auth/users',{username:'acceptance-cashier',fullName:'Synthetic Cashier',
      role:'cashier',password:cashierPassword});
    const cashier = verifyResponse(await request('/api/auth/login',{
      method:'POST',body:{username:'acceptance-cashier',password:cashierPassword}
    }),200);
    const headers = {Authorization:'Bearer '+cashier.token};
    const bootstrap = await request('/api/bootstrap',{headers});
    assert.equal(bootstrap.headers.get('cache-control'),'no-store');
    const userRows = verifyResponse(bootstrap,200).users;
    for (const user of userRows) {
      assert.ok(Object.keys(user).every(key=>['id','username','fullName','email','role',
        'isActive','hasPin','preferredLandingModule','createdAt'].includes(key)));
    }
    verifyResponse(await request('/api/categories',{method:'POST',body:{name:'Forbidden'},headers}),403);
    const costItem=(await state()).inventory.find(i=>i.name==='Synthetic Purchase Cost');
    const payload={inventoryItemId:costItem.id,clientMutationId:randomUUID(),movementType:'purchase',quantityDelta:1,purchaseUnitCost:1};
    verifyResponse(await request('/api/inventory/'+costItem.id+'/adjust',{method:'POST',body:payload,headers}),403);
    const rejected=verifyResponse(await request('/api/sync/batch',{method:'POST',headers,body:{mutations:[{id:payload.clientMutationId,clientMutationId:payload.clientMutationId,ownerId:cashier.user.id,type:'ADJUST_INVENTORY',payload}]}}),200);
    assert.equal(rejected.results[0].status,'error');
    assert.match(rejected.results[0].error,/Only administrators/);
    assert.equal((await state()).inventory.find(i=>i.id===costItem.id).quantityOnHand,160);
    verifyResponse(await request('/api/transactions/'+paid.id+'/refund',
      {method:'POST',body:{reason:'Forbidden'},headers}),403);
    const saved=(await write('/transactions',makeOrder('held',0))).transaction;
    verifyResponse(await request('/api/transactions/'+saved.id+'/held',{method:'DELETE',headers}),403);
    assert.ok((await state()).transactions.some(t=>t.id===saved.id));
    await write('/transactions/'+saved.id+'/held',undefined,200,'DELETE');
    assert.ok((await state()).auditLogs.some(a=>a.action==='HELD_ORDER_DELETED'&&a.entityId===saved.id));
    await write('/transactions/'+paid.id+'/held',undefined,409,'DELETE');
    const customerUrl='/api/customers';
    for(const body of [{name:'Forbidden VIP',group:'vip'},{name:'Forbidden Discount',discountPercent:50}])verifyResponse(await request(customerUrl,{method:'POST',headers,body}),403);
    for(const body of [{name:'Invalid Group',group:'bad'},{name:'Invalid Discount',discountPercent:101},{name:'Invalid Discount Type',discountPercent:'10'},{name:'Invalid Points',loyaltyPoints:100},{name:123}])await write('/customers',body,400);
    const regular=verifyResponse(await request(customerUrl,{method:'POST',headers,body:{name:'Cashier Regular Customer'}}),201);
    assert.equal(regular.group,'regular');assert.equal(regular.discountPercent,0);assert.equal(regular.loyaltyPoints,0);
    const privileged=await write('/customers',{name:'Manager Approved Customer',group:'vip',discountPercent:10});
    assert.equal(privileged.discountPercent,10);
    assert.deepEqual(userRows.filter(u=>u.id!==cashier.user.id).map(u=>u.email),userRows.filter(u=>u.id!==cashier.user.id).map(()=>''));
    assert.deepEqual(verifyResponse(await request('/api/bootstrap',{headers}),200).auditLogs,[]);
  });
  await check('complete financial history beyond bootstrap caps and Manila midnight boundary',async()=>{
    const db=new DatabaseSync(path.join(sandbox,'data','printshop-pos.db'));
    const dates=['2020-01-01T15:59:59.999Z','2020-01-01T16:00:00.000Z','2020-01-02T15:59:59.999Z','2020-01-02T16:00:00.000Z'];
    try {
      db.exec('BEGIN');
      const sale=db.prepare("INSERT INTO transactions (id,receipt_number,client_mutation_id,type,cashier_id,cashier_name,items_json,subtotal,total_amount,amount_paid,payment_status,created_at) VALUES (?,?,?,'sale',?,?,'[]',10,10,10,'paid',?)");
      const expense=db.prepare("INSERT INTO expenses (id,category,description,amount,recorded_by,expense_date,created_at) VALUES (?,'other','Synthetic report fixture',2,'Acceptance',?,?)");
      for(let i=0;i<305;i++) sale.run('report-sale-'+i,'REPORT-'+i,'report-mutation-'+i,'acceptance','Acceptance',dates[1+i%2]);
      sale.run('report-before','REPORT-BEFORE','report-before','acceptance','Acceptance',dates[0]);
      sale.run('report-after','REPORT-AFTER','report-after','acceptance','Acceptance',dates[3]);
      for(let i=0;i<205;i++)expense.run('report-expense-'+i,'2020-01-02',dates[1]);
      expense.run('report-expense-before','2020-01-01',dates[0]);
      expense.run('report-expense-after','2020-01-03',dates[3]);
      db.exec('COMMIT');
    } finally {db.close();}
    const url='/api/reports/finance?from=2020-01-02&to=2020-01-02';
    verifyResponse(await request(url),401);
    for(const query of ['from=2020-02-30&to=2020-03-01','from=2020-01-03&to=2020-01-02','from=2020-01-02']) verifyResponse(await request('/api/reports/finance?'+query,{auth:true}),400);
    const report=verifyResponse(await request(url,{auth:true}),200);
    assert.equal(report.transactions.length,305);
    assert.equal(report.expenses.length,205);
    assert.equal(report.transactions.reduce((sum,t)=>sum+t.totalAmount,0),3050);
    assert.equal(report.expenses.reduce((sum,e)=>sum+e.amount,0),410);
    assert.ok(report.transactions.every(t=>/^report-sale-/.test(t.id)));
    assert.ok(report.expenses.every(e=>e.expenseDate==='2020-01-02'));
    const bootstrap=await state();
    assert.equal(bootstrap.transactions.length,300);
    assert.equal(bootstrap.expenses.length,200);
  });
  await check('complete item history pagination beyond bootstrap limits',async()=>{
    const item=await write('/inventory',{name:'Synthetic Long History',unit:'sheet',quantityOnHand:0});
    const db=new DatabaseSync(path.join(sandbox,'data','printshop-pos.db'));
    try{
      const insert=db.prepare("INSERT INTO stock_movements(id,inventory_item_id,item_name,movement_type,quantity_delta,balance_after,performed_by,created_at,purchase_unit_cost) VALUES (?,?,?,'purchase',1,?,'Acceptance',?,?)");
      db.exec('BEGIN');
      for(let i=0;i<205;i++)insert.run('long-history-'+String(i).padStart(4,'0'),item.id,item.name,i+1,'2020-01-01T00:00:00.000Z',i%2===0?i:null);
      db.exec('COMMIT');
    }finally{db.close();}
    const url='/api/inventory/'+item.id+'/history';
    verifyResponse(await request(url),401);
    for(const q of ['limit=0','limit=101','offset=-1','snapshot=x'])verifyResponse(await request(url+'?'+q,{auth:true}),400);
    const first=verifyResponse(await request(url+'?limit=100',{auth:true}),200);
    assert.equal(first.total,205);assert.equal(first.movements.length,100);
    assert.equal(first.summary.count,103);assert.equal(first.summary.minimum,0);assert.equal(first.summary.maximum,204);
    assert.equal(first.purchasePoints.length,103);
    await write('/inventory/'+item.id+'/adjust',{clientMutationId:randomUUID(),movementType:'purchase',quantityDelta:1,purchaseUnitCost:3},200);
    const ids=first.movements.map(m=>m.id);
    for(const offset of [100,200]){
      const page=verifyResponse(await request(url+'?limit=100&offset='+offset+'&snapshot='+first.snapshot,{auth:true}),200);
      assert.equal(page.total,205);ids.push(...page.movements.map(m=>m.id));
    }
    assert.equal(new Set(ids).size,205);assert.equal(ids.length,205);
    const refreshed=verifyResponse(await request(url,{auth:true}),200);
    assert.equal(refreshed.total,206);
    assert.equal(refreshed.summary.count,104);
    assert.equal(refreshed.purchasePoints.at(-1).purchaseUnitCost,3);
  });
  await check('archive and restore preserve movements and prevent inactive stock operations',async()=>{
    const item=await write('/inventory',{name:'Synthetic Archive Fixture',unit:'sheet',quantityOnHand:2,unitCost:1});
    const url='/inventory/'+item.id;
    await write(url+'/archive',{reason:'Discontinued'},409);
    await write(url+'/adjust',{clientMutationId:randomUUID(),movementType:'wastage',quantityDelta:2},200);
    const before=verifyResponse(await request('/api/inventory/'+item.id+'/history',{auth:true}),200);
    await write(url+'/archive',{reason:''},400);
    const archived=await write(url+'/archive',{reason:'Discontinued'},200);
    assert.ok(archived.archivedAt);
    assert.equal((await state()).inventory.some(i=>i.id===item.id),false);
    const list=verifyResponse(await request('/api/inventory/archived',{auth:true}),200);
    assert.equal(list.items.find(i=>i.id===item.id).archiveReason,'Discontinued');
    await write(url,{name:'Forbidden edit'},409,'PATCH');
    await write(url+'/adjust',{clientMutationId:randomUUID(),movementType:'purchase',quantityDelta:1},409);
    await write(url,undefined,409,'DELETE');
    assert.deepEqual(verifyResponse(await request('/api/inventory/'+item.id+'/history',{auth:true}),200).movements,before.movements);
    const restored=await write(url+'/restore',{},200);
    assert.equal(restored.archivedAt,undefined);
    assert.ok((await state()).inventory.some(i=>i.id===item.id));
    assert.deepEqual(verifyResponse(await request('/api/inventory/'+item.id+'/history',{auth:true}),200).movements,before.movements);
  });
  await check('persistent alert acknowledgment rejects stale shortage episodes',async()=>{
    const item=await write('/inventory',{name:'Synthetic Alert Epoch',unit:'sheet',quantityOnHand:1,reorderLevel:5,minimumThreshold:2});
    const list=async()=>verifyResponse(await request('/api/inventory/alerts',{auth:true}),200).alerts;
    const find=async()=>(await list()).find(a=>a.itemId===item.id);
    const first=await find();assert.equal(first.read,false);
    await write('/inventory/alerts/read',{alerts:[first]},200);
    assert.equal((await find()).read,true);
    await write('/inventory/'+item.id+'/adjust',{clientMutationId:randomUUID(),movementType:'purchase',quantityDelta:10},200);
    assert.equal(await find(),undefined);
    await write('/inventory/'+item.id+'/adjust',{clientMutationId:randomUUID(),movementType:'wastage',quantityDelta:10},200);
    const renewed=await find();assert.notEqual(renewed.epoch,first.epoch);assert.equal(renewed.read,false);
    await write('/inventory/alerts/read',{alerts:[first]},409);
    await write('/inventory/alerts/read',{alerts:[renewed]},200);
    assert.equal((await find()).read,true);
    verifyResponse(await request('/api/inventory/alerts'),401);
  });
  await check('transaction mutation identity survives held-order deletion and quarantines legacy replay',async()=>{
    const held=makeOrder('held',0);
    const stored=(await write('/transactions',held)).transaction;
    await write('/transactions/'+stored.id+'/held',undefined,200,'DELETE');
    assert.equal((await state()).transactions.some(t=>t.id===stored.id),false);
    await write('/transactions',held,409);
    assert.equal((await state()).transactions.some(t=>t.id===stored.id),false);
    const db=new DatabaseSync(path.join(sandbox,'data','printshop-pos.db'));
    try{db.prepare('DELETE FROM transaction_request_identities WHERE mutation_id=?').run(originalPaidRequest.clientMutationId);}
    finally{db.close();}
    await write('/transactions',originalPaidRequest,409);
    const stateAfter=await state();
    // Historical test fixtures may push the original sale out of bootstrap; verify durable identity directly.
    const verify=new DatabaseSync(path.join(sandbox,'data','printshop-pos.db'),{readOnly:true});
    try{assert.equal(verify.prepare('SELECT COUNT(*) AS n FROM transactions WHERE client_mutation_id=?').get(originalPaidRequest.clientMutationId).n,1);}
    finally{verify.close();}
  });
  await check('complete versioned SQLite backup preserves financial recovery and business export excludes credentials',async()=>{
    const live=path.join(sandbox,'data','printshop-pos.db'),target=path.join(sandbox,'financial-complete.db');
    snapshot(live,target);
    const a=new DatabaseSync(live,{readOnly:true}),b=new DatabaseSync(target,{readOnly:true});
    try{
      assert.equal(b.prepare('PRAGMA user_version').get().user_version,1);
      for(const table of ["transaction_request_identities","inventory_mutation_results","applied_mutations","users","sessions","app_settings","categories","services","supply_categories","inventory_items","stock_movements","customers","transactions","order_conversions","print_jobs","expenses","cashier_shifts","transaction_reversals","drawer_movement_requests","balance_collection_requests","balance_collections","audit_logs","inventory_alert_epochs","inventory_alert_reads"]){
        const query='SELECT * FROM '+table+' ORDER BY rowid';
        assert.deepEqual(b.prepare(query).all(),a.prepare(query).all(),table);
      }
      for(const table of ['transaction_request_identities','balance_collection_requests','drawer_movement_requests','inventory_mutation_results','stock_movements','audit_logs'])assert.ok(b.prepare('SELECT COUNT(*) AS n FROM '+table).get().n>0,table);
    }finally{a.close();b.close();}
    const exported=verifyResponse(await request('/api/backup/export',{auth:true}),200);
    assert.equal(exported.restorable,false);assert.equal(exported.exportKind,'business-data-only');
    for(const table of ['transaction_request_identities','balance_collection_requests','drawer_movement_requests','inventory_mutation_results','stock_movements','cashier_shifts','transaction_reversals','balance_collections','audit_logs','inventory_alert_reads'])assert.ok(Array.isArray(exported[table]),table);
    assert.ok(!('users' in exported)&&!('sessions' in exported)&&!('session_secret' in exported));
    assert.ok(!JSON.stringify(exported).includes('password_hash')&&!JSON.stringify(exported).includes('pin_hash'));
  });
  await check('logout revokes session',async()=>{verifyResponse(await request('/api/auth/logout',{method:'POST',auth:true}),200);verifyResponse(await request('/api/bootstrap',{auth:true}),401);});
  await stop();report.status='PASS';
 }catch(e){report.error=String(e.message);console.error(report.status,report.error);process.exitCode=1;}
 finally{if(child){if(child.exitCode===null&&child.signalCode===null&&!spawnFailed)child.kill('SIGKILL');await exitPromise;}if(sandbox)fs.rmSync(sandbox,{recursive:true,force:true,maxRetries:5,retryDelay:200});report.finishedAt=new Date().toISOString();const file=path.join(evidence,`acceptance-${randomUUID()}.json`);fs.writeFileSync(file,JSON.stringify(report,null,2),{flag:'wx'});console.log('Acceptance evidence:',file);}
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url))await main();
