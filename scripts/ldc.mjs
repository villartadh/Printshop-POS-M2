import {openDesktop} from './desktop.mjs';
import {guidedPhysicalAcceptance} from './physical-acceptance.mjs';
import {safeSupport,writeSupportArchive} from './support-archive.mjs';
export {safeSupport};
import fs from 'node:fs';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { createInterface } from 'node:readline/promises';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const dir=path.join(root,'.ldc');
const endpoint='http://127.0.0.1:3000/api/health';
export function runtimeOK(version){const [a,b]=version.split('.').map(Number);return a===22&&b>=12;}
export function gateSteps(installed,locked){return [...(!installed||!locked?[locked?'ci':'install']:[]),'test','lint','build','acceptance'];}
let child=null, token=null, logFile=null;
const sessionId=randomUUID();
let latestGate=null;
export function comprehensiveSteps(){return ['acceptance:browser','acceptance:https','upgrade'];}
function log(message){fs.mkdirSync(dir,{recursive:true});const line=`${new Date().toISOString()} ${message}`;fs.appendFileSync(path.join(dir,'operations.log'),line+'\n');console.log(line);}
async function health(){try{const r=await fetch(endpoint,{signal:AbortSignal.timeout(1500)});if(!r.ok)return null;const x=await r.json();return x.status==='ok'?x:null;}catch{return null;}}
function exists(p){return fs.existsSync(path.join(root,p));}
function runNpm(args){return new Promise((resolve,reject)=>{const p=spawn(process.platform==='win32'?'cmd.exe':'npm',process.platform==='win32'?['/d','/s','/c','npm',...args]:args,{cwd:root,stdio:'inherit',shell:false});p.once('error',reject);p.once('exit',code=>code===0?resolve():reject(new Error(`npm ${args.join(' ')} failed (${code})`)));});}
export async function executeGate(steps,runner){
 const checks=[];
 for(const name of steps){
  const started=Date.now();
  if(checks.some(c=>c.status!=='PASS')){checks.push({name,status:'BLOCKED',durationMs:0});continue;}
  try{await runner(name);checks.push({name,status:'PASS',durationMs:Date.now()-started});}
  catch{checks.push({name,status:'FAIL',durationMs:Date.now()-started});}
 }
 return {status:checks.some(c=>c.status==='FAIL')?'FAIL':checks.some(c=>c.status!=='PASS')?'BLOCKED':'PASS',checks};
}
function runUpgrade(){return new Promise((resolve,reject)=>{
 const baseline=path.join(root,'.baseline-v095');
 if(!fs.existsSync(path.join(baseline,'node_modules/tsx/package.json'))){log('Pinned baseline checkout and dependencies required; see operator guide.');return reject(Error('Upgrade prerequisites missing'));}
 const p=spawn(process.execPath,['scripts/upgrade-acceptance.mjs',baseline],{cwd:root,stdio:'inherit'});
 p.once('error',reject);p.once('exit',code=>code===0?resolve():reject(Error('Upgrade gate failed')));
});}
async function gate(full=false){
 if(!runtimeOK(process.versions.node))throw Error('Requires Node.js 22.12+ in the 22 series');
 if(!exists('package-lock.json'))throw Error('Missing lockfile. Restore the source archive; do not generate a replacement lockfile.');
 const steps=[...gateSteps(exists('node_modules/tsx/package.json'),true),...(full?comprehensiveSteps():[])];
 const startedAt=new Date().toISOString();
 const result=await executeGate(steps,async step=>{
  log('Gate: '+step);
  if(step==='upgrade')await runUpgrade();
  else await runNpm(step==='ci'?['ci','--no-audit','--no-fund','--fetch-retries=1','--fetch-timeout=20000']:step==='test'?['test']:['run',step]);
 });
 latestGate={sessionId,mode:full?'comprehensive':'startup',startedAt,finishedAt:new Date().toISOString(),...result};
 fs.mkdirSync(dir,{recursive:true});fs.writeFileSync(path.join(dir,'gate-'+sessionId+'-'+randomUUID()+'.json'),JSON.stringify(latestGate,null,2));
 if(result.status!=='PASS')throw Error('Validation failed at '+result.checks.find(c=>c.status==='FAIL')?.name+'. Review the test output; later gates were not run.');
 log((full?'Comprehensive software':'Startup')+' gate passed; physical acceptance remains separate.');
}
async function stop(){if(!child){log('No server owned by this LDC session; no external process terminated');return;}const p=child;await new Promise((resolve,reject)=>{const timer=setTimeout(()=>reject(new Error('Server did not exit; check runtime logs')),10000);p.once('exit',()=>{clearTimeout(timer);resolve();});p.kill();});log('Managed server stopped');}
async function start(){if(child)throw new Error('Managed server already running');if(await health())throw new Error('Port 3000 has an existing POS server; refusing to adopt it');await gate();if(fs.existsSync(path.resolve(process.env.POS_DATA_DIR || path.join(root,'data'),'printshop-pos.db')))await runNpm(['run','db:backup']);if(await health())throw new Error('Existing server detected after validation');token=randomUUID();fs.mkdirSync(dir,{recursive:true});logFile=path.join(dir,`server-${Date.now()}.log`);const fd=fs.openSync(logFile,'a');try{child=spawn(process.execPath,['--import','tsx','scripts/start.ts'],{cwd:root,env:{...process.env,HOST:'127.0.0.1',PORT:'3000',LDC_INSTANCE:token},stdio:['ignore',fd,fd]});}finally{fs.closeSync(fd);}const p=child;p.once('error',e=>{log('Server spawn failed: '+e.message);if(child===p)child=null;});p.once('exit',(code,signal)=>{log(`Server exited (${code??signal})`);if(child===p)child=null;});for(let i=0;i<40&&child===p;i++){const h=await health();if(h?.instance===token){log('Managed POS ready: http://127.0.0.1:3000');return;}await new Promise(r=>setTimeout(r,750));}if(child===p)await stop();throw new Error('Startup health gate failed; see '+logFile);}
async function status(){const h=await health();return safeSupport({version:JSON.parse(fs.readFileSync(path.join(root,'package.json'))).version,node:process.version,platform:process.platform,arch:process.arch,dependencies:exists('node_modules/tsx/package.json'),build:exists('dist/index.html'),databasePresent:fs.existsSync(path.resolve(process.env.POS_DATA_DIR || path.join(root,'data'),'printshop-pos.db')),health:h,managed:child&&h?.instance===token});}
async function support(){fs.mkdirSync(dir,{recursive:true});log('Support archive: '+writeSupportArchive(dir,{...await status(),gate:latestGate}));}
export function launchMode(args){if(args.length===0)return 'menu';if(args.length===1&&args[0]==='--run')return 'run';throw new Error('Usage: ldc.mjs [--run]');}
async function autoRun(){try{await start();try{await openDesktop(token);}catch(e){log('Desktop window unavailable: '+e.message+' Open http://127.0.0.1:3000/');}const p=child;if(p)await new Promise(resolve=>p.once('exit',resolve));throw new Error('Managed POS stopped; inspect runtime logs');}finally{if(child)await stop();}}
async function main(){const rl=createInterface({input:process.stdin,output:process.stdout});try{for(;;){console.log('\nPRINTSHOP POS LDC\n1 Run production POS (startup gate)\n2 Test local / build gate\n3 Status\n4 Stop managed POS\n5 Restart managed POS\n6 Create support archive\n7 Backup database\n8 Show runtime log path\n9 Open Windows desktop window\n10 Comprehensive software gate (browser, HTTPS, upgrade)\n11 Record physical printer acceptance\n12 Record actual-device PWA acceptance\n0 Exit');let action;try{action=(await rl.question('Select: ')).trim();}catch(e){if(e.code==='ERR_USE_AFTER_CLOSE')break;throw e;}try{if(action==='0'){await stop();break;}if(action==='1')await start();else if(action==='2'){if(child)throw new Error('Stop managed server before rebuilding');await gate();}else if(action==='3')console.log(await status());else if(action==='4')await stop();else if(action==='5'){await stop();await start();}else if(action==='6')await support();else if(action==='7')await runNpm(['run','db:backup']);else if(action==='8')console.log(logFile??'No runtime log this session');else if(action==='9'){if(!child)throw new Error('Start managed POS first');await openDesktop(token);}else if(action==='10'){if(child)throw Error('Stop managed server before rebuilding');await gate(true);}else if(action==='11'||action==='12'){if(child)throw Error('Stop managed POS; use a separate disposable test installation');await guidedPhysicalAcceptance(rl,{scope:action==='11'?'printer':'device',version:JSON.parse(fs.readFileSync(path.join(root,'package.json'))).version,outputDir:dir});}else console.log('Unknown selection');}catch(e){log('ERROR: '+e.message);}}}finally{rl.close();if(child)await stop();}}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){process.on('SIGINT',()=>{if(child)child.kill();process.exit(130);});Promise.resolve().then(()=>launchMode(process.argv.slice(2))==='run'?autoRun():main()).catch(e=>{console.error(e.message);process.exitCode=1;});}
