import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawn,execFileSync} from 'node:child_process';
import {setTimeout as delay} from 'node:timers/promises';
import {randomUUID,createHash} from 'node:crypto';
import {pathToFileURL} from 'node:url';
import {DatabaseSync} from 'node:sqlite';
import {verify} from '../database-maintenance.mjs';

if(process.platform!=='win32'||process.arch!=='x64'||process.argv.length!==4)
 throw Error('Usage on Windows x64: node scripts/windows/package-upgrade.mjs BASELINE_PACKAGE CANDIDATE_PACKAGE');
const [baseline,candidate]=process.argv.slice(2).map(p=>path.resolve(p));
const manifest=root=>JSON.parse(fs.readFileSync(path.join(root,'RELEASE-MANIFEST.json')));
assert.equal(manifest(baseline).version,'0.9.5');
assert.equal(manifest(candidate).version,'0.9.7');
const sandbox=fs.mkdtempSync(path.join(os.tmpdir(),'pos-bundled-upgrade-'));
const local=path.join(sandbox,'local'),home=path.join(local,'Printshop POS'),data=path.join(home,'data');
const live=path.join(data,'printshop-pos.db'),secret=path.join(data,'session.secret');
const old=path.join(home,'releases','0.9.5'),next=path.join(home,'releases','0.9.7');
const env={...Object.fromEntries(Object.entries(process.env).filter(([k])=>/^(SYSTEMROOT|WINDIR|PATH|COMSPEC|TEMP|TMP|USERPROFILE|APPDATA)$/i.test(k))),LOCALAPPDATA:local,POS_NO_BROWSER:'1'};
const report={status:'FAIL',scope:'Hosted Windows x64 bundled-runtime lifecycle; not shop hardware or native DPI acceptance',baseline:'0.9.5',candidate:'0.9.7',checks:[]};
const evidence=path.resolve('.ldc','package-upgrade-'+randomUUID()+'.json');
const credentials={username:'package-upgrade',fullName:'Synthetic Package Upgrade',password:randomUUID()};
const origin='http://127.0.0.1:3000';
let child,exited,token;
const check=async(name,fn)=>{try{await fn();report.checks.push({name,status:'PASS'});console.log('PASS',name);}catch(e){report.checks.push({name,status:'FAIL',error:String(e.message).split(sandbox).join('[sandbox]')});throw e;}};
const health=()=>fetch(origin+'/api/health',{signal:AbortSignal.timeout(1000)}).then(r=>r.ok?r.json():null).catch(()=>null);
const command=(root,action,...args)=>execFileSync(path.join(root,'runtime','node.exe'),['scripts/windows/lifecycle.mjs',action,...args],{cwd:root,env,timeout:60000,stdio:'pipe',encoding:'utf8'});
const restore=(root,file)=>execFileSync(path.join(root,'runtime','node.exe'),['--input-type=module','-e','const [url,home,file]=process.argv.slice(1);const result=await (await import(url)).restoreDistribution(home,file);console.log(JSON.stringify(result));',pathToFileURL(path.join(root,'scripts/windows/lifecycle.mjs')).href,home,file],{cwd:root,env,timeout:60000,stdio:'pipe',encoding:'utf8'});
const inspect=(file,query,...args)=>{const db=new DatabaseSync(file,{readOnly:true});try{return db.prepare(query).get(...args);}finally{db.close();}};
const sha=file=>createHash('sha256').update(fs.readFileSync(file)).digest('hex');
async function start(root){
 assert.equal(await health(),null,'Requires unused loopback port 3000');
 child=spawn(path.join(root,'runtime','node.exe'),['scripts/windows/lifecycle.mjs','run'],{cwd:root,env,stdio:'inherit',windowsHide:true});
 exited=new Promise((resolve,reject)=>{child.once('error',reject);child.once('exit',(code,signal)=>resolve({code,signal}));});
 for(let i=0;i<120&&child.exitCode===null;i++){const h=await health();if(h?.status==='ok'){assert.match(h.instance,/^[a-f0-9-]{36}$/);return;}await delay(250);}
 throw Error('Bundled application startup failed');
}
async function stop(root){
 command(root,'stop');
 assert.deepEqual(await Promise.race([exited,delay(15000).then(()=>null)]),{code:0,signal:null});
 assert.equal(await health(),null);child=undefined;
}
async function crash(){
 const p=child;assert.ok(p&&p.exitCode===null);p.kill('SIGKILL');
 const result=await Promise.race([exited,delay(15000).then(()=>null)]);
 assert.ok(result,'Crash fixture did not exit');
 assert.equal(await health(),null);child=undefined;
}
async function request(url,body,status=200){
 const r=await fetch(origin+'/api'+url,{method:body===undefined?'GET':'POST',headers:{...(body===undefined?{}:{'Content-Type':'application/json'}),...(token?{Authorization:'Bearer '+token}:{})},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(5000)});
 assert.equal(r.status,status,url);
 return r.json();
}
try{
 let shift,customer,stock,legacyBackup,currentBackup,secretHash,movement;
 const backups=()=>fs.readdirSync(path.join(data,'backups')).filter(n=>n.endsWith('.db')).map(n=>path.join(data,'backups',n));
 const takeBackup=root=>{const before=new Set(fs.existsSync(path.join(data,'backups'))?backups():[]);command(root,'backup');const added=backups().filter(f=>!before.has(f));assert.equal(added.length,1);verify(added[0]);return added[0];};
 await check('bundled baseline installs and creates synthetic shop records',async()=>{
  command(baseline,'install');await start(old);
  token=(await request('/auth/setup-admin',credentials,201)).token;
  shift=await request('/shifts/start',{openingCash:100},201);
  customer=await request('/customers',{name:'Synthetic Package Customer'},201);
  stock=await request('/inventory',{name:'Synthetic Package Paper',quantityOnHand:20,unit:'sheet',unitCost:2},201);
  legacyBackup=takeBackup(old);
  assert.equal(inspect(legacyBackup,'PRAGMA user_version').user_version,0);
  await stop(old);secretHash=sha(secret);
 });
 await check('candidate installs side by side and upgrades shared legacy data',async()=>{
  command(candidate,'install');assert.ok(fs.existsSync(old)&&fs.existsSync(next));
  assert.throws(()=>command(candidate,'install'),/already installed/i);
  await start(next);
  const state=await request('/bootstrap');
  assert.equal(sha(secret),secretHash);
  assert.ok(state.customers.some(c=>c.id===customer.id));
  assert.equal(state.inventory.find(i=>i.id===stock.id).quantityOnHand,20);
  assert.equal(state.shifts.find(s=>s.id===shift.id).expectedCash,100);
  assert.equal(inspect(live,'PRAGMA user_version').user_version,2);
  movement={type:'cash_in',amount:0.25,reason:'Synthetic replay proof',clientMutationId:randomUUID()};
  const applied=await request('/shifts/'+shift.id+'/movement',movement);assert.equal(applied.duplicate,false);
  currentBackup=takeBackup(next);
  assert.equal(inspect(currentBackup,'SELECT COUNT(*) AS n FROM drawer_movement_requests WHERE mutation_id=?',movement.clientMutationId).n,1);
 });
 await check('active server rejects restore and additional launcher; crash restart preserves data',async()=>{
  assert.throws(()=>restore(next,legacyBackup),/already active/i);
  assert.throws(()=>command(next,'run'),/already active/i);
  await crash();await start(next);
  const replay=await request('/shifts/'+shift.id+'/movement',movement);
  assert.equal(replay.duplicate,true);assert.equal(replay.expectedCash,100.25);
  await request('/shifts/'+shift.id+'/movement',{type:'cash_in',amount:2,reason:'Synthetic later movement',clientMutationId:randomUUID()});
  await stop(next);
 });
 await check('invalid backup cannot replace current records; current restore retains replay ledger',async()=>{
  const invalid=path.join(sandbox,'invalid.db');new DatabaseSync(invalid).close();
  assert.throws(()=>restore(next,invalid),/complete/i);
  assert.equal(inspect(live,'SELECT expected_cash FROM cashier_shifts WHERE id=?',shift.id).expected_cash,102.25);
  const result=JSON.parse(restore(next,currentBackup).trim());assert.ok(result.previous);verify(result.previous);
  assert.equal(inspect(result.previous,'SELECT expected_cash FROM cashier_shifts WHERE id=?',shift.id).expected_cash,102.25);
  await start(next);
  const replay=await request('/shifts/'+shift.id+'/movement',movement);assert.equal(replay.duplicate,true);
  const state=await request('/bootstrap');assert.equal(state.shifts.find(s=>s.id===shift.id).expectedCash,100.25);
  await stop(next);
 });
 await check('legacy restore re-upgrades and uninstall preserves shared data',async()=>{
  const result=JSON.parse(restore(next,legacyBackup).trim());verify(result.previous);
  assert.equal(inspect(result.previous,'SELECT COUNT(*) AS n FROM drawer_movement_requests WHERE mutation_id=?',movement.clientMutationId).n,1);
  await start(next);
  token=(await request('/auth/login',credentials)).token;
  const state=await request('/bootstrap');assert.ok(state.customers.some(c=>c.id===customer.id));
  assert.equal(state.shifts.find(s=>s.id===shift.id).expectedCash,100);
  await stop(next);
  command(candidate,'uninstall');assert.ok(fs.existsSync(old));assert.equal(fs.existsSync(next),false);
  verify(live);assert.equal(sha(secret),secretHash);assert.ok(fs.existsSync(legacyBackup)&&fs.existsSync(currentBackup));
 });
 report.status='PASS';
}catch(error){report.error=String(error.message).split(sandbox).join('[sandbox]');console.error(report.error);process.exitCode=1;}
finally{
 if(child&&child.exitCode===null&&child.signalCode===null){child.kill();await exited;}
 fs.mkdirSync(path.dirname(evidence),{recursive:true});fs.writeFileSync(evidence,JSON.stringify(report,null,2));
 fs.rmSync(sandbox,{recursive:true,force:true,maxRetries:5,retryDelay:200});
 console.log('Packaged upgrade evidence:',evidence);
}
