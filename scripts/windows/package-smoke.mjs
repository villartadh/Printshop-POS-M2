import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawn,execFileSync} from 'node:child_process';
import {setTimeout as delay} from 'node:timers/promises';
import {randomUUID} from 'node:crypto';

if(process.platform!=='win32'||process.arch!=='x64'||process.argv.length!==3)
  throw Error('Usage on Windows x64: node scripts/windows/package-smoke.mjs STAGED_PACKAGE');
const source=path.resolve(process.argv[2]);
const sandbox=fs.mkdtempSync(path.join(os.tmpdir(),'pos-package-ci-'));
const dist=path.join(sandbox,'distribution');
const local=path.join(sandbox,'local');
const home=path.join(local,'Printshop POS');
const data=path.join(home,'data');
const env={...Object.fromEntries(Object.entries(process.env).filter(([k])=>
  /^(SYSTEMROOT|WINDIR|PATH|COMSPEC|TEMP|TMP|USERPROFILE|APPDATA)$/i.test(k))),
  LOCALAPPDATA:local,POS_NO_BROWSER:'1'};
const credentials={username:'package-ci',fullName:'Synthetic Package Test',password:randomUUID()};
const origin='http://127.0.0.1:3000';
let child, exited;
const health=()=>fetch(origin+'/api/health',{signal:AbortSignal.timeout(1000)})
  .then(r=>r.ok?r.json():null).catch(()=>null);
const command=(root,action)=>execFileSync(path.join(root,'runtime','node.exe'),
  ['scripts/windows/lifecycle.mjs',action],{cwd:root,env,timeout:60000,stdio:'pipe'});
async function start(root) {
  assert.equal(await health(),null,'Requires unused loopback port 3000');
  child=spawn(path.join(root,'runtime','node.exe'),['scripts/windows/lifecycle.mjs','run'],
    {cwd:root,env,stdio:'inherit',windowsHide:true});
  exited=new Promise((resolve,reject)=>{
    child.once('error',reject);child.once('exit',(code,signal)=>resolve({code,signal}));
  });
  for(let i=0;i<100&&child.exitCode===null;i++){
    if((await health())?.status==='ok')return;
    await delay(250);
  }
  throw Error('Packaged application startup failed');
}
async function stop(root) {
  command(root,'stop');
  assert.deepEqual(await Promise.race([exited,delay(15000).then(()=>null)]),{code:0,signal:null});
  assert.equal(await health(),null);
  child=undefined;
}
async function auth(endpoint,status) {
  const response=await fetch(origin+'/api/auth/'+endpoint,{method:'POST',
    headers:{'Content-Type':'application/json'},body:JSON.stringify(credentials),signal:AbortSignal.timeout(5000)});
  assert.equal(response.status,status);
  const result=await response.json();
  assert.ok(result.token);
}
try {
  fs.cpSync(source,dist,{recursive:true});
  const version=JSON.parse(fs.readFileSync(path.join(dist,'RELEASE-MANIFEST.json'))).version;
  const installed=path.join(home,'releases',version);
  command(dist,'install');
  await start(installed);
  await auth('setup-admin',201);
  await stop(installed);
  await start(installed);
  await auth('login',200);
  await stop(installed);
  assert.ok(fs.readdirSync(path.join(data,'backups')).some(f=>f.endsWith('.db')));
  command(dist,'uninstall');
  assert.equal(fs.existsSync(installed),false);
  assert.ok(fs.existsSync(path.join(data,'printshop-pos.db')));
  assert.ok(fs.existsSync(path.join(data,'session.secret')));
  console.log('PASS isolated bundled-runtime install, first-run setup, stop, restart, login, startup backup, uninstall and data preservation');
} finally {
  if(child&&child.exitCode===null&&child.signalCode===null){
    child.kill();await exited;
  }
  fs.rmSync(sandbox,{recursive:true,force:true,maxRetries:5,retryDelay:200});
}
