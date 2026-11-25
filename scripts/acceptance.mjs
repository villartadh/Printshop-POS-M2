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
  const password=randomUUID();let token,shift,customer,stock,service,paid,partial;
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
  await check('authoritative catalog checkout, tender/change and duplicate', async () => {
    const sale = makeOrder('sale',50);
    await write('/transactions',{...sale,items:[{...sale.items[0],unitPrice:1}]},409);
    paid = (await write('/transactions',sale)).transaction;
    assert.equal(paid.totalAmount,40);
    assert.equal(paid.changeAmount,10);
    assert.equal((await write('/transactions',sale,200)).duplicate,true);
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
    await write('/customers/'+customer.id+'/pay-balance',{amount:25},200);
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
  await check('cashier shift close',async()=>{const x=verifyResponse(await request(`/api/shifts/${shift.id}/close`,{method:'POST',auth:true,body:{actualCash:540}}),200);assert.equal(x.discrepancy,0);});
  await check('database and session persist across restart',async()=>{await stop();await start();const x=verifyResponse(await request('/api/bootstrap',{auth:true}),200);assert.ok(x.shifts.some(s=>s.id===shift.id&&s.status==='closed'));});
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
    verifyResponse(await request('/api/transactions/'+paid.id+'/refund',
      {method:'POST',body:{reason:'Forbidden'},headers}),403);
  });
  await check('logout revokes session',async()=>{verifyResponse(await request('/api/auth/logout',{method:'POST',auth:true}),200);verifyResponse(await request('/api/bootstrap',{auth:true}),401);});
  await stop();report.status='PASS';
 }catch(e){report.error=String(e.message);console.error(report.status,report.error);process.exitCode=1;}
 finally{if(child){if(child.exitCode===null&&child.signalCode===null&&!spawnFailed)child.kill('SIGKILL');await exitPromise;}if(sandbox)fs.rmSync(sandbox,{recursive:true,force:true,maxRetries:5,retryDelay:200});report.finishedAt=new Date().toISOString();const file=path.join(evidence,`acceptance-${randomUUID()}.json`);fs.writeFileSync(file,JSON.stringify(report,null,2),{flag:'wx'});console.log('Acceptance evidence:',file);}
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url))await main();
