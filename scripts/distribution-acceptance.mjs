import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {pathToFileURL} from 'node:url';
import {randomUUID} from 'node:crypto';
import {spawn} from 'node:child_process';
import {setTimeout as delay} from 'node:timers/promises';
import {acceptanceEnv,assertInstance} from './acceptance.mjs';
import {installDistribution,uninstallDistribution,restoreDistribution} from './windows/lifecycle.mjs';
import {stopControlled} from './windows/control.mjs';
import {verify} from './database-maintenance.mjs';

if(process.platform!=='linux' || process.arch!=='x64' || process.argv.length!==4) {
  throw Error('Usage on Linux x64: node scripts/distribution-acceptance.mjs OLD_RELEASE NEW_RELEASE');
}
const [baseline,candidate] = process.argv.slice(2).map(p=>path.resolve(p));
const sandbox = fs.mkdtempSync(path.join(os.tmpdir(),'pos-distribution-'));
const home = path.join(sandbox,'Printshop POS'), data = path.join(home,'data');
const credentials = {username:'stage-test',fullName:'Synthetic Install',password:randomUUID()};
const port=3000, instance=randomUUID(), base='http://127.0.0.1:'+port;
const binary=path.resolve(import.meta.dirname,'../node_modules/better-sqlite3/prebuilds/linux-x64.node');
let child, exited;
const health=()=>fetch(base+'/api/health',{signal:AbortSignal.timeout(1000)})
  .then(r=>r.ok?r.json():null).catch(()=>null);
const stop=async(signal='SIGKILL')=>{
  if(child?.exitCode===null && child.signalCode===null) {
    child.kill(signal);
    await exited;
  }
};
const start=async(install,legacy=false)=>{
  assert.equal(await health(),null,'The smoke test requires an unused POS port.');
  // Substitute only the native addon for this Linux run of the packaged application.
  fs.copyFileSync(binary,path.join(install,'node_modules/better-sqlite3/prebuilds/linux-x64.node'));
  const module=pathToFileURL(path.join(install,'scripts/windows/lifecycle.mjs')).href;
  const args=legacy ? ['scripts/start.ts'] :
    ['--input-type=module','-e','const [url,home]=process.argv.slice(1); await (await import(url)).run(home);',module,home];
  child=spawn(process.execPath,args,{cwd:install,env:{...acceptanceEnv(port,instance),POS_DATA_DIR:data,POS_NO_BROWSER:'1'},
    stdio:['ignore','ignore','inherit']});
  exited=new Promise((resolve,reject)=>{
    child.once('error',reject);child.once('exit',(code,signal)=>resolve({code,signal}));
  });
  for(let i=0;i<80 && child.exitCode===null;i++){
    const ready=await health();
    if(ready?.status==='ok'){
      if(legacy) assertInstance(ready,instance);
      else assert.match(ready.instance,/^[a-f0-9-]{36}$/);
      return;
    }
    await delay(100);
  }
  throw Error('The staged application did not become healthy.');
};
const request=async(endpoint,status)=>{
  const response=await fetch(base+'/api/auth/'+endpoint,{method:'POST',headers:{'Content-Type':'application/json'},
    body:JSON.stringify(credentials),signal:AbortSignal.timeout(3500)});
  assert.equal(response.status,status);
};
const graceful=async()=>{
  await stopControlled(data);
  assert.deepEqual(await exited,{code:0,signal:null});
  assert.equal(await health(),null);
};

try {
  const previous=installDistribution(baseline,home);
  const latest=installDistribution(candidate,home);
  assert.throws(()=>installDistribution(candidate,home),/already installed/);
  await start(previous,true);
  await request('setup-admin',201);
  await stop();
  await start(latest);
  await request('login',200);
  assert.ok(fs.existsSync(previous));
  console.log('PASS staged runtime-only startup and v0.9.4 to v0.9.5 database upgrade (Linux mirror)');

  await graceful();
  await start(latest);
  await request('login',200);
  await stop();
  assert.ok(fs.existsSync(path.join(data,'.distribution-control.json')));
  await start(latest);
  await request('login',200);
  await graceful();
  assert.equal(fs.existsSync(path.join(data,'.distribution-control.json')),false);
  assert.equal(fs.existsSync(path.join(data,'.distribution-running')),false);
  console.log('PASS packaged graceful stop, restart and restart after forced process termination');

  const snapshots=fs.readdirSync(path.join(data,'backups')).filter(name=>name.endsWith('.db'));
  assert.equal(snapshots.length,3);
  for(const name of snapshots) verify(path.join(data,'backups',name));
  const backup=path.join(data,'backups',snapshots[0]);
  const {previous:preserved}=await restoreDistribution(home,backup);
  verify(preserved);
  await start(latest);
  await request('login',200);
  await assert.rejects(restoreDistribution(home,backup),/already active/);
  await graceful();
  console.log('PASS packaged restore, preserved prior database and active server refusal');
  uninstallDistribution(candidate,home);
  assert.ok(fs.existsSync(previous));
  assert.ok(fs.existsSync(path.join(data,'session.secret')));
  verify(path.join(data,'printshop-pos.db'));
  console.log('PASS verified startup snapshots and per-version removal with data preservation');
} finally {
  await stop();
  fs.rmSync(sandbox,{recursive:true,force:true,maxRetries:3});
}
