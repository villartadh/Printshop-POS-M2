import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {execFileSync,spawn} from 'node:child_process';
import {once} from 'node:events';
import {randomUUID} from 'node:crypto';
import {setTimeout as wait} from 'node:timers/promises';

const args=process.argv.slice(2);
if(process.platform!=='win32' || process.arch!=='x64') throw Error('Native acceptance requires Windows x64.');
if(process.env.POS_WINDOWS_ACCEPTANCE!=='disposable-desktop' || args.length!==3) {
  throw Error('On an isolated desktop set POS_WINDOWS_ACCEPTANCE=disposable-desktop and pass OLD_RELEASE NEW_RELEASE NEW_EVIDENCE_DIR.');
}
const [oldRelease,newRelease,evidence]=args.map(value=>path.resolve(value));
const {chromium}=await import('playwright');
const sandbox=fs.mkdtempSync(path.join(os.tmpdir(),'pos-native-'));
const local=path.join(sandbox,'LocalAppData');
const home=path.join(local,'Printshop POS'), data=path.join(home,'data');
const credentials={username:'windows-acceptance',fullName:'Synthetic Windows Test',password:randomUUID()};
const version=root=>JSON.parse(fs.readFileSync(path.join(root,'RELEASE-MANIFEST.json'))).version;
const oldVersion=version(oldRelease), newVersion=version(newRelease);
if(oldVersion!=='0.9.4' || newVersion!=='0.9.5') throw Error('Expected the v0.9.4 baseline and v0.9.5 candidate.');
fs.mkdirSync(evidence);
const env={...Object.fromEntries(Object.entries(process.env).filter(([key])=>
  /^(SYSTEMROOT|WINDIR|PATH|COMSPEC|TEMP|TMP|USERPROFILE|APPDATA|PROGRAMFILES|PROGRAMFILES\(X86\)|PROGRAMDATA)$/i.test(key))),
  LOCALAPPDATA:local,POS_NO_BROWSER:'1'};
const base='http://127.0.0.1:3000';
const report={version:newVersion,platform:os.platform(),windowsRelease:os.release(),arch:os.arch(),checks:[]};
let cmd, exited, browser, originalScale;
const check=async(name,action)=>{
  const item={name,status:'FAIL'};
  report.checks.push(item);
  item.evidence=await action();
  item.status='PASS';
  console.log('PASS',name);
};
const command=(root,name)=>execFileSync(process.env.ComSpec ?? 'cmd.exe',
  ['/d','/c',name],{cwd:root,env,timeout:60000,windowsHide:true,stdio:'pipe'});
const kill=async()=>{
  if(cmd?.exitCode===null && cmd.signalCode===null) {
    execFileSync('taskkill.exe',['/f','/t','/pid',String(cmd.pid)],{timeout:10000,stdio:'ignore'});
    await exited;
  }
  cmd=undefined;
};
const health=()=>fetch(base+'/api/health',{signal:AbortSignal.timeout(1000)})
  .then(r=>r.ok?r.json():null).catch(()=>null);
const untilOffline=async()=>{
  for(let i=0;i<50;i++){
    if(!await health()) return;
    await wait(100);
  }
  throw Error('POS remains active.');
};
const start=async(root)=>{
  assert.equal(await health(),null,'The test requires an unused loopback port 3000.');
  cmd=spawn(process.env.ComSpec ?? 'cmd.exe',['/d','/c','RUN-POS.cmd'],
    {cwd:root,env,windowsHide:true,stdio:'ignore'});
  exited=once(cmd,'exit');
  for(let i=0;i<80 && cmd.exitCode===null;i++){
    const result=await health();
    if(result?.status==='ok') return result;
    await wait(250);
  }
  throw Error('Installed POS did not start.');
};
const stop=async(root)=>{
  command(root,'STOP-POS.cmd');
  const result=await Promise.race([exited,wait(16000).then(()=>null)]);
  assert.deepEqual(result,[0,null]);
  await untilOffline();
  cmd=undefined;
};
const api=async(endpoint,body,status)=>{
  const r=await fetch(base+'/api'+endpoint,{method:'POST',headers:{'Content-Type':'application/json'},
    body:JSON.stringify(body),signal:AbortSignal.timeout(5000)});
  assert.equal(r.status,status);
  return r.json();
};
async function assertPersistedCustomer() {
  const session=await api('/auth/login',credentials,200);
  assert.equal(session.user.username,credentials.username);
  const r=await fetch(base+'/api/bootstrap',{headers:{Authorization:'Bearer '+session.token},
    signal:AbortSignal.timeout(5000)});
  assert.equal(r.status,200);
  assert.ok((await r.json()).customers.some(c=>c.name==='Synthetic Upgrade Customer'));
}
function scale(action,percent) {
  return JSON.parse(execFileSync('powershell.exe',['-NoProfile','-NonInteractive','-File',
    path.join(import.meta.dirname,'display-scaling.ps1'),'-Action',action,
    ...(percent?['-Percent',String(percent)]:[])],{encoding:'utf8',timeout:30000,stdio:['ignore','pipe','pipe']}));
}

try {
  await check('native bundled Node and better-sqlite3',()=>{
    const result=JSON.parse(execFileSync(path.join(newRelease,'runtime','node.exe'),['-e',`
      const db=new (require('better-sqlite3'))(':memory:');
      db.exec('CREATE TABLE pos(id INTEGER); INSERT INTO pos VALUES (42)');
      const out={node:process.version,modules:process.versions.modules,napi:process.versions.napi,
        arch:process.arch,platform:process.platform,database:db.prepare('select sqlite_version() version,id from pos').get()};
      db.close();
      console.log(JSON.stringify(out));
    `],{cwd:newRelease,env,encoding:'utf8',timeout:10000}));
    assert.equal(result.node,'v22.23.5');
    assert.equal(result.modules,'127');
    assert.equal(result.platform,'win32');
    assert.equal(result.arch,'x64');
    assert.equal(result.database.id,42);
    assert.match(result.database.version,/^3\./);
    return result;
  });
  await check('candidate fresh install and first-run setup',async()=>{
    env.LOCALAPPDATA=path.join(sandbox,'FreshLocalAppData');
    const fresh=path.join(env.LOCALAPPDATA,'Printshop POS','releases',newVersion);
    command(newRelease,'INSTALL.cmd');
    await start(fresh);
    await api('/auth/setup-admin',credentials,201);
    await stop(fresh);
    command(newRelease,'UNINSTALL.cmd');
    env.LOCALAPPDATA=local;
  });
  const oldInstalled=path.join(home,'releases',oldVersion);
  const installed=path.join(home,'releases',newVersion);
  await check('baseline install and synthetic database',async()=>{
    command(oldRelease,'INSTALL.cmd');
    await start(oldInstalled);
    const session=await api('/auth/setup-admin',credentials,201);
    const customer=await fetch(base+'/api/customers',{method:'POST',
      headers:{'Content-Type':'application/json',Authorization:'Bearer '+session.token},
      body:JSON.stringify({name:'Synthetic Upgrade Customer'}),signal:AbortSignal.timeout(5000)});
    assert.equal(customer.status,201);
    await stop(oldInstalled);
    await untilOffline();
  });
  await check('upgrade preserves baseline and signs in',async()=>{
    command(newRelease,'INSTALL.cmd');
    assert.ok(fs.existsSync(oldInstalled));
    await start(installed);
    await assertPersistedCustomer();
    assert.ok(fs.readdirSync(path.join(data,'backups')).some(name=>name.endsWith('.db')));
    command(installed,'SUPPORT.cmd');
    assert.equal(fs.readdirSync(path.join(home,'support')).filter(name=>name.endsWith('.zip')).length,1);
  });
  await check('graceful stop and restart',async()=>{
    await stop(installed);
    await start(installed);
    await assertPersistedCustomer();
  });
  await check('crash recovery and successful subsequent restart',async()=>{
    await kill();
    await untilOffline();
    await start(installed);
    await assertPersistedCustomer();
  });

  const initial=scale('read').percent;
  if(![100,125,150,175,200,225,250,300,350,400,450,500].includes(initial)) {
    throw Error('Custom scale cannot be restored by the harness. Use a preset Windows display scale.');
  }
  originalScale=initial;
  for(const percent of [100,125,150]){
    await check('native Windows display scaling '+percent+'%',async()=>{
      const native=scale('set',percent);
      browser=await chromium.launch({headless:false});
      try {
        const page=await browser.newPage({viewport:null});
        await page.goto(base);
        await page.getByPlaceholder('Enter username').fill(credentials.username);
        await page.getByPlaceholder('Enter password',{exact:true}).fill(credentials.password);
        await page.getByRole('button',{name:'Sign In',exact:true}).click();
        await page.getByRole('heading',{name:'POS Terminal',exact:true}).waitFor({timeout:10000})
          .catch(()=>page.getByText('No matching printing services',{exact:true}).waitFor({timeout:10000}));
        const observed=await page.evaluate(()=>({ratio:devicePixelRatio,viewport:[innerWidth,innerHeight],
          documentWidth:document.documentElement.scrollWidth,screen:[screen.width,screen.height]}));
        assert.equal(native.dpi,96*percent/100);
        assert.equal(observed.ratio,percent/100);
        assert.ok(observed.documentWidth<=observed.viewport[0]);
        const screenshot='windows-'+percent+'.png';
        await page.screenshot({path:path.join(evidence,screenshot)});
        return {native,observed,screenshot};
      } finally {await browser.close();browser=undefined;}
    });
  }
  await check('uninstall preserves data and the previous release',async()=>{
    await stop(installed);
    command(newRelease,'UNINSTALL.cmd');
    assert.equal(fs.existsSync(installed),false);
    assert.ok(fs.existsSync(oldInstalled));
    assert.ok(fs.existsSync(path.join(data,'printshop-pos.db')));
    assert.ok(fs.existsSync(path.join(data,'session.secret')));
  });
  report.status='PASS';
} catch(error) {
  report.status='FAIL';
  report.failure={kind:error.name,message:error.message.replaceAll(credentials.password,'[fixture]')
    .replaceAll(sandbox,'[sandbox]').slice(0,300)};
  process.exitCode=1;
} finally {
  try {
    await browser?.close();
    await kill();
  } catch {report.status='FAIL';report.processCleanup='failed';process.exitCode=1;}
  try {
    if(originalScale!==undefined) report.restoredDisplay=scale('set',originalScale);
  } catch {report.status='FAIL';report.displayRestoration='failed';process.exitCode=1;}
  fs.writeFileSync(path.join(evidence,'native-windows.json'),JSON.stringify(report,null,2));
  if(!report.processCleanup) fs.rmSync(sandbox,{recursive:true,force:true,maxRetries:4});
}
