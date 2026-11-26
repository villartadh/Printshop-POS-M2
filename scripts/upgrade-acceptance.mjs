import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawn,execFileSync} from 'node:child_process';
import {createRequire} from 'node:module';
import {pathToFileURL} from 'node:url';
import {randomUUID,createHash} from 'node:crypto';
import {DatabaseSync} from 'node:sqlite';
import {setTimeout as delay} from 'node:timers/promises';
import {acceptanceEnv,assertInstance,freePort} from './acceptance.mjs';
import {snapshot,verify,restoreStoppedDatabase} from './database-maintenance.mjs';
import {calculateServicePrice,calculateCartItemTotals,calculateOrderTotals} from '../src/shared/pricingEngine.ts';
const root=path.resolve(import.meta.dirname,'..');
const baseline=path.resolve(process.argv[2]||'.baseline-v095');
const baselineSha='255518ed388bcee3d63ee211e17c199ba984547e';
const evidence=path.join(root,'.ldc','upgrade-'+randomUUID()+'.json');
const report={baselineSha,target:JSON.parse(fs.readFileSync(path.join(root,'package.json'))).version,status:'FAIL',checks:[]};
const sandbox=fs.mkdtempSync(path.join(os.tmpdir(),'pos-real-upgrade-')),data=path.join(sandbox,'data'),live=path.join(data,'printshop-pos.db');
const port=await freePort(),instance=randomUUID(),origin='http://127.0.0.1:'+port;
let child,exited,token;
const check=async(name,fn)=>{try{await fn();report.checks.push({name,status:'PASS'});console.log('PASS',name);}catch(e){report.checks.push({name,status:'FAIL',error:e.message});throw e;}};
const request=async(url,body,status=200,method=body===undefined?'GET':'POST')=>{
 const response=await fetch(origin+'/api'+url,{method,headers:{...(body!==undefined?{'Content-Type':'application/json'}:{}),...(token?{Authorization:'Bearer '+token}:{})},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(5000)});
 const result=await response.json();assert.equal(response.status,status,url+': '+(result.error||''));return result;
};
const start=async(source)=>{
 const require=createRequire(path.join(source,'package.json'));
 // Node's ESM --import requires a file URL for absolute Windows drive paths.
 const loader=pathToFileURL(require.resolve('tsx')).href;
 const stage=source===baseline?'baseline-v0.9.5':'candidate';
 let stderr='',spawnError=null;
 child=spawn(process.execPath,['--import',loader,path.join(source,'scripts/start.ts')],{cwd:sandbox,env:{...acceptanceEnv(port,instance),POS_DATA_DIR:data},stdio:['ignore','ignore','pipe']});
 child.stderr.setEncoding('utf8');
 child.stderr.on('data',chunk=>{stderr=(stderr+chunk).slice(-8192);});
 exited=new Promise(resolve=>{child.once('close',resolve);child.once('error',error=>{spawnError=error;resolve();});});
 const startupFailure=reason=>{
   // Synthetic isolated fixture only. Exclude local paths and possible credential/token lines from evidence.
   const safe=stderr.split(/\r?\n/).filter(line=>!/password|secret|authorization|bearer|token/i.test(line)).join('\n').split(sandbox).join('[sandbox]').split(root).join('[repo]').slice(-3000);
   return Error(stage+': '+reason+(safe?'\n'+safe:''));
 };
 for(let i=0;i<80;i++){
   if(spawnError||child.exitCode!==null||child.signalCode!==null){await exited;throw startupFailure(spawnError?.code||'Server exited during upgrade startup ('+child.exitCode+')');}
   try{assertInstance(await request('/health'),instance);return;}catch{}await delay(200);
 }
 throw startupFailure('Upgrade health timeout');
};
const stop=async()=>{if(!child)return;const p=child;if(p.exitCode===null&&p.signalCode===null)p.kill();const timer=setTimeout(()=>p.kill('SIGKILL'),5000);try{await exited;}finally{clearTimeout(timer);child=null;}};
const inspect=(file,fn)=>{const db=new DatabaseSync(file,{readOnly:true});try{return fn(db);}finally{db.close();}};
const rows=(db,table,columns='*')=>db.prepare('SELECT '+columns+' FROM '+table+' ORDER BY rowid').all();
const oldTables=['users','transactions','inventory_items','stock_movements','app_settings','cashier_shifts','customers','print_jobs','order_conversions','transaction_reversals','balance_collections','inventory_mutation_results','applied_mutations'];
const hash=file=>createHash('sha256').update(fs.readFileSync(file)).digest('hex');
try{
 fs.mkdirSync(path.dirname(evidence),{recursive:true});
 assert.equal(execFileSync('git',['-C',baseline,'rev-parse','HEAD'],{encoding:'utf8'}).trim(),baselineSha);
 assert.equal(JSON.parse(fs.readFileSync(path.join(baseline,'package.json'))).version,'0.9.5');
 fs.cpSync(path.join(root,'dist'),path.join(sandbox,'dist'),{recursive:true});
 let auth,shift,customer,stock,service,sale,original,settings,oldRows,secretHash,newSale,newRequest;
 const credentials={username:'upgrade-admin',fullName:'Synthetic Upgrade Admin',password:randomUUID()};
 const legacy=path.join(sandbox,'v095.db'),current=path.join(sandbox,'current.db');
 const makeOrder=(type,tender,extra={})=>{
  const tax=settings.taxAndCurrency,config={quantity:2,pages:1,copies:1,width:1,height:1,dimensionUnit:'in',designFee:0,laborFee:0,isRush:false,selectedAttributes:{}};
  const calc=calculateServicePrice(service,config,tax);
  const item={id:randomUUID(),serviceId:service.id,serviceName:service.name,config,...calc,discountType:'none',discountValue:0,consumables:service.consumables};
  Object.assign(item,calculateCartItemTotals(item,tax));
  const totals=calculateOrderTotals([item],'none',0,tax),paid=Math.min(totals.totalAmount,tender);
  return {id:randomUUID(),clientMutationId:randomUUID(),receiptNumber:'UPGRADE-'+randomUUID(),type,items:[item],...totals,shiftId:shift.id,cashierId:auth.user.id,cashierName:auth.user.fullName,customerId:customer.id,customerName:customer.name,orderDiscountType:'none',orderDiscountValue:0,amountPaid:paid,balanceDue:totals.totalAmount-paid,changeAmount:tender-paid,paymentStatus:paid===totals.totalAmount?'paid':paid?'partial':'unpaid',payments:tender?[{id:randomUUID(),methodId:'cash',methodName:'Cash',methodType:'cash',amount:tender,verificationStatus:'verified',paidAt:new Date().toISOString()}]:[],notes:'Synthetic upgrade test',syncStatus:'synced',createdAt:new Date().toISOString(),...extra};
 };
 await check('immutable v0.9.5 server creates original business records',async()=>{
  await start(baseline);
  auth=await request('/auth/setup-admin',credentials,201);token=auth.token;
  shift=await request('/shifts/start',{openingCash:100},201);
  const category=await request('/categories',{name:'Upgrade services'},201);
  stock=await request('/inventory',{name:'Upgrade paper',unit:'sheet',quantityOnHand:100,unitCost:2},201);
  service=await request('/services',{name:'Upgrade prints',categoryId:category.id,pricingMethod:'fixed',basePrice:20,requiresProductionJob:true,consumables:[{inventoryItemId:stock.id,quantityPerUnit:1}]},201);
  customer=await request('/customers',{name:'Upgrade customer'},201);
  settings=(await request('/bootstrap')).settings;
  const quote=(await request('/transactions',makeOrder('quotation',0),201)).transaction;
  original=makeOrder('sale',15,{sourceOrderId:quote.id});
  sale=(await request('/transactions',original,201)).transaction;
  await request('/customers/'+customer.id+'/pay-balance',{amount:10},200);
  await request('/inventory/'+stock.id+'/adjust',{movementType:'purchase',quantityDelta:3,clientMutationId:randomUUID()},200);
  const state=await request('/bootstrap');assert.equal(state.customers.find(c=>c.id===customer.id).outstandingBalance,15);
  assert.equal(state.inventory.find(i=>i.id===stock.id).quantityOnHand,101);
  await stop();
  assert.equal(inspect(live,db=>db.prepare('PRAGMA user_version').get().user_version),0);
  snapshot(live,legacy);secretHash=hash(path.join(data,'session.secret'));
  oldRows=inspect(legacy,db=>Object.fromEntries(oldTables.map(t=>[t,{columns:db.prepare('PRAGMA table_info('+t+')').all().map(c=>c.name),rows:rows(db,t)}])));
 });
 await check('candidate upgrades original database without changing historical ledger fields',async()=>{
  await start(root);
  const state=await request('/bootstrap'); // preserved original authenticated session
  assert.equal(hash(path.join(data,'session.secret')),secretHash);
  assert.equal(state.transactions.find(t=>t.id===sale.id).receiptNumber,sale.receiptNumber);
  assert.equal(state.transactions.find(t=>t.id===sale.id).amountPaid,25);
  assert.equal(state.customers.find(c=>c.id===customer.id).outstandingBalance,15);
  inspect(live,db=>{
   assert.equal(db.prepare('PRAGMA user_version').get().user_version,1);
   for(const [table,prior] of Object.entries(oldRows))assert.deepEqual(rows(db,table,prior.columns.map(c=>'"'+c+'"').join(',')),prior.rows,table);
  });
  await request('/transactions',original,409); // legacy digest unavailable; never recreate paid sale
  newRequest=makeOrder('sale',40);
  newSale=(await request('/transactions',newRequest,201)).transaction;
  assert.ok(newSale.receiptSnapshot);
  assert.equal((await request('/transactions',newRequest,200)).duplicate,true);
  await stop();verify(live);snapshot(live,current);
 });
 await check('restore current backup preserves original transaction identity and receipt',async()=>{
  await start(root);
  await request('/shifts/'+shift.id+'/movement',{type:'cash_in',amount:1,reason:'Restore fixture',clientMutationId:randomUUID()});
  await stop();
  const {previous}=restoreStoppedDatabase(data,current);verify(previous);
  await start(root);
  const duplicate=await request('/transactions',newRequest,200);
  assert.equal(duplicate.duplicate,true);assert.deepEqual(duplicate.transaction.receiptSnapshot,newSale.receiptSnapshot);
  const state=await request('/bootstrap');
  assert.equal(state.transactions.filter(t=>t.clientMutationId===newRequest.clientMutationId).length,1);
  assert.equal(state.shifts.find(s=>s.id===shift.id).expectedCash,165);
  await stop();
 });
 await check('restored legacy backup upgrades again while preserving newer database',async()=>{
  const {previous}=restoreStoppedDatabase(data,legacy);verify(previous);
  assert.equal(inspect(previous,db=>db.prepare('SELECT COUNT(*) AS n FROM transaction_request_identities WHERE mutation_id=?').get(newRequest.clientMutationId).n),1);
  await start(root);
  const state=await request('/bootstrap');
  assert.equal(state.transactions.find(t=>t.id===sale.id).receiptNumber,sale.receiptNumber);
  assert.equal(state.transactions.some(t=>t.id===newSale.id),false);
  assert.equal(state.customers.find(c=>c.id===customer.id).outstandingBalance,15);
  assert.equal(hash(path.join(data,'session.secret')),secretHash);
  await stop();verify(live);
 });
 report.status='PASS';
}catch(e){report.error=e.message;console.error(e.message);process.exitCode=1;}
finally{await stop();fs.mkdirSync(path.dirname(evidence),{recursive:true});fs.writeFileSync(evidence,JSON.stringify(report,null,2));fs.rmSync(sandbox,{recursive:true,force:true,maxRetries:3});console.log('Upgrade evidence:',evidence);}
