import fs from 'node:fs';
import path from 'node:path';
import net from 'node:net';
import {randomUUID} from 'node:crypto';
import {createInterface} from 'node:readline/promises';
import {verifyRelease} from '../install.mjs';
import {snapshot,restoreStoppedDatabase} from '../database-maintenance.mjs';
import {acquireControl,isControlled,stopControlled} from './control.mjs';
import {openDesktop} from '../desktop.mjs';
import {writeSupportArchive} from '../support-archive.mjs';

const root = path.resolve(import.meta.dirname,'../..');

export function installDistribution(source, home) {
  const manifest = JSON.parse(fs.readFileSync(path.join(source,'RELEASE-MANIFEST.json')));
  verifyRelease(source,manifest);
  const releases = path.join(home,'releases');
  const target = path.join(releases,manifest.version);
  fs.mkdirSync(releases,{recursive:true});
  if (fs.existsSync(target)) throw Error('This version is already installed.');
  const stage = fs.mkdtempSync(path.join(releases,'.stage-'));
  try {
    for (const file of [...Object.keys(manifest.files),'RELEASE-MANIFEST.json']) {
      const dest = path.join(stage,file);
      fs.mkdirSync(path.dirname(dest),{recursive:true});
      fs.copyFileSync(path.join(source,file),dest,fs.constants.COPYFILE_EXCL);
    }
    verifyRelease(stage,manifest);
    fs.renameSync(stage,target);
    return target;
  } finally { fs.rmSync(stage,{recursive:true,force:true}); }
}

export function uninstallDistribution(source, home) {
  const manifest = JSON.parse(fs.readFileSync(path.join(source,'RELEASE-MANIFEST.json')));
  verifyRelease(source,manifest);
  const target = path.join(home,'releases',manifest.version);
  if (path.relative(source,target)==='') throw Error('Run uninstall from the extracted distribution, outside the installed release.');
  const installed = JSON.parse(fs.readFileSync(path.join(target,'RELEASE-MANIFEST.json')));
  verifyRelease(target,installed);
  if (installed.version!==manifest.version) throw Error('Installed version mismatch.');
  const removed = path.join(home,'releases','.removed-'+randomUUID());
  // Windows can briefly retain executable or directory handles after exit.
  // Keep the existing exclusive launcher guard held throughout this bounded retry.
  for (let attempt=0;;attempt++) {
    try { fs.renameSync(target,removed); break; }
    catch (error) {
      if (process.platform!=='win32' || !['EPERM','EBUSY','EACCES'].includes(error.code) || attempt>=9) throw error;
      Atomics.wait(new Int32Array(new SharedArrayBuffer(4)),0,0,250*(attempt+1));
    }
  }
  fs.rmSync(removed,{recursive:true,maxRetries:8,retryDelay:250});
}

export async function run(home) {
  const data = path.join(home,'data');
  fs.mkdirSync(data,{recursive:true});
  let finish;
  const stopped = new Promise(resolve=>{finish=resolve;});
  const release = await acquireControl(data,finish);
  let stopServer,releaseLegacy;
  try {
    // v0.9.3 retained a marker across abrupt shutdown. Give an older launcher
    // exclusive access while its recorded process is alive.
    releaseLegacy=claimLegacyLock(data);
    await assertPortFree();
    const live = path.join(data,'printshop-pos.db');
    if (fs.existsSync(live)) {
      const backups = path.join(data,'backups');
      fs.mkdirSync(backups,{recursive:true});
      snapshot(live,path.join(backups,`before-start-${Date.now()}-${randomUUID()}.db`));
    }
    const token=randomUUID();
    const inherited = Object.fromEntries(Object.entries(process.env).filter(([key]) =>
      /^(SYSTEMROOT|WINDIR|TEMP|TMP|LOCALAPPDATA|USERPROFILE|PATH|POS_HTTPS_ORIGIN|POS_HTTPS_BIND|POS_HTTPS_CERT|POS_HTTPS_KEY)$/i.test(key)));
    const launchBrowser=process.env.POS_NO_BROWSER!=='1';
    for (const key of Object.keys(process.env)) if (!(key in inherited)) delete process.env[key];
    Object.assign(process.env,{POS_DATA_DIR:data,HOST:'127.0.0.1',PORT:'3000',SEED_DEMO:'false',NODE_ENV:'production',
      LDC_INSTANCE:token,DOTENV_CONFIG_PATH:path.join(root,'.distribution-no-dotenv')});
    process.on('SIGINT',finish);
    process.on('SIGTERM',finish);
    const {startServer}=await import('../../server.ts');
    stopServer=await startServer();
    if(launchBrowser) {
      try { await openDesktop(token); }
      catch { console.log('Open http://127.0.0.1:3000/ in Edge or Chrome.'); }
    }
    console.log('Use STOP-POS.cmd or Ctrl+C to stop Printshop POS.');
    await stopped;
  } finally {
    try { await stopServer?.(); }
    finally {
      try { releaseLegacy?.(); }
      finally { await release(); }
    }
    process.off('SIGINT',finish);
    process.off('SIGTERM',finish);
  }
}

async function assertPortFree() {
  const listener=net.createServer();
  await new Promise((resolve,reject)=>{
    listener.once('error',reject);
    listener.listen({host:'127.0.0.1',port:3000,exclusive:true},resolve);
  });
  await new Promise(resolve=>listener.close(resolve));
}

function claimLegacyLock(data) {
  const marker=path.join(data,'.distribution-running');
  if(checkLegacyLock(data)) fs.unlinkSync(marker);
  fs.writeFileSync(marker,String(process.pid),{flag:'wx',mode:0o600});
  return ()=>fs.unlinkSync(marker);
}

function checkLegacyLock(data) {
  const marker=path.join(data,'.distribution-running');
  if(!fs.existsSync(marker)) return;
  const pid=Number(fs.readFileSync(marker,'utf8'));
  if(!Number.isSafeInteger(pid)||pid<=0) throw Error('The legacy launcher marker is unreadable. Preserve it for support.');
  try { process.kill(pid,0); }
  catch(error) {
    if(error.code!=='ESRCH') throw error;
    return true;
  }
  throw Error('The previous Printshop POS launcher is still running. Stop it before continuing.');
}

export async function restoreDistribution(home,file) {
  const data=path.join(home,'data');
  fs.mkdirSync(data,{recursive:true});
  const release=await acquireControl(data,()=>{});
  try {
    const releaseLegacy=claimLegacyLock(data);
    try {
      await assertPortFree();
      return restoreStoppedDatabase(data,file);
    } finally {releaseLegacy();}
  } finally {await release();}
}

if (process.argv[1] && path.resolve(process.argv[1])===path.join(import.meta.dirname,'lifecycle.mjs')) {
  try {
    if (process.platform!=='win32' || !process.env.LOCALAPPDATA) throw Error('Windows and LOCALAPPDATA are required.');
    const home = path.join(process.env.LOCALAPPDATA,'Printshop POS');
    const data=path.join(home,'data');
    switch(process.argv[2]) {
      case 'install': console.log('Installed:',installDistribution(root,home)); break;
      case 'uninstall':
        fs.mkdirSync(data,{recursive:true});
        {
          const release=await acquireControl(data,()=>{});
          try { checkLegacyLock(data); uninstallDistribution(root,home); }
          finally { await release(); }
        }
        console.log('Removed this version. Shared data and backups are preserved.');
        break;
      case 'run': await run(home); break;
      case 'stop': await stopControlled(data); break;
      case 'backup':
      case 'recover-copy': {
        const restore=process.argv[2]==='recover-copy';
        if(restore && !process.argv[3]) throw Error('Drag a backup file onto RECOVERY-COPY.cmd.');
        const dir=path.join(data,restore ? 'recovery' : 'backups');
        fs.mkdirSync(dir,{recursive:true});
        const source=restore ? path.resolve(process.argv[3]) : path.join(data,'printshop-pos.db');
        const target=path.join(dir,`${restore?'recovered':'manual'}-${Date.now()}-${randomUUID()}.db`);
        snapshot(source,target);
        console.log('Verified copy:',target);
        break;
      }
      case 'restore': {
        if(!process.argv[3]) throw Error('Drag a verified backup file onto RESTORE-POS.cmd.');
        if(!process.stdin.isTTY) throw Error('Restore requires an interactive confirmation.');
        const rl=createInterface({input:process.stdin,output:process.stdout});
        let confirmation;
        try {
          confirmation=await rl.question('Restoring replaces current POS records with the selected backup. Type RESTORE to continue: ');
        } finally {rl.close();}
        if(confirmation!=='RESTORE') {console.log('Restore cancelled.');break;}
        const result=await restoreDistribution(home,path.resolve(process.argv[3]));
        if(result.previous) console.log('Preserved previous database:',result.previous);
        console.log('Verified backup restored. Open RUN-POS.cmd and review the recovered records.');
        break;
      }
      case 'support': {
        const manifest=JSON.parse(fs.readFileSync(path.join(root,'RELEASE-MANIFEST.json')));
        const output=path.join(home,'support');
        fs.mkdirSync(output,{recursive:true});
        const managed=await isControlled(data);
        const health=managed && await fetch('http://127.0.0.1:3000/api/health',{signal:AbortSignal.timeout(1500)})
          .then(r=>r.ok ? r.json() : null).then(result=>result?.status==='ok').catch(()=>false);
        console.log(writeSupportArchive(output,{version:manifest.version,node:process.version,managed,health,
          platform:process.platform,arch:process.arch,dependencies:true,build:true,
          databasePresent:fs.existsSync(path.join(data,'printshop-pos.db'))}));
        break;
      }
      default: throw Error('Expected install, uninstall, run, stop, backup, recover-copy or support.');
    }
  } catch (error) {
    console.error(error.message);
    process.exitCode=1;
  }
}
