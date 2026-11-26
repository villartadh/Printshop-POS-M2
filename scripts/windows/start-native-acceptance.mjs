import fs from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {createInterface} from 'node:readline/promises';
import {verifyWindowsPackage,requireFreePort} from './native-preflight.mjs';
import {randomUUID} from 'node:crypto';

const root=path.resolve(import.meta.dirname,'../..');
const normalize=answer=>answer.trim().replace(/^"([^"]+)"$/,'$1');
const validate=(input,allowed)=>verifyWindowsPackage(input,allowed).folder;
async function main(){
 const [major,minor]=process.versions.node.split('.').map(Number);
 if(process.platform!=='win32'||process.arch!=='x64'||major!==22||minor<12)throw Error('Requires Windows x64 and Node 22.12+ in major 22.');
 if(!process.stdin.isTTY)throw Error('Interactive consent is required.');
 const {chromium}=await import('playwright');
 if(!fs.existsSync(chromium.executablePath()))throw Error('Install Chromium from the locked source workspace: npx playwright install chromium');
 const rl=createInterface({input:process.stdin,output:process.stdout});
 let oldRelease,newRelease;
 try{
  console.log('NATIVE WINDOWS ACCEPTANCE — SYNTHETIC DATA ONLY');
  console.log('Use a disposable English Windows 11 console, one monitor, preset 100/125/150% scaling. Do not use a live shop PC. This test temporarily changes Windows scaling.');
  console.log('Close POS and ensure port 3000 is unused. Keep both verified extracted distributions available. This is not printer, tablet or BIR acceptance.');
  if(await rl.question('Type DISPOSABLE DESKTOP to confirm: ')!=='DISPOSABLE DESKTOP'){console.log('Cancelled; no tests started.');return;}
  oldRelease=validate(normalize(await rl.question('Extracted v0.9.5 or v0.9.6 baseline folder: ')),['0.9.5','0.9.6']);
  newRelease=validate(normalize(await rl.question('Extracted v0.9.7 candidate folder: ')),['0.9.7']);
 }finally{rl.close();}
 await requireFreePort();
 const dir=path.join(root,'.ldc');fs.mkdirSync(dir,{recursive:true});
 const evidence=path.join(dir,'native-windows-'+randomUUID());
 const result=spawnSync(process.execPath,[path.join(import.meta.dirname,'native-acceptance.mjs'),oldRelease,newRelease,evidence],{
  cwd:root,env:{...process.env,POS_WINDOWS_ACCEPTANCE:'disposable-desktop'},stdio:'inherit'
 });
 if(result.error)throw result.error;
 console.log('Expected native evidence folder: '+evidence);
 if(result.status!==0)throw Error('Native gate did not pass. Inspect evidence, and verify original display settings before leaving the test machine.');
 const report=JSON.parse(fs.readFileSync(path.join(evidence,'native-windows.json'),'utf8'));
 if(report.status!=='PASS')throw Error('Native report does not confirm PASS.');
 console.log('Native Windows gate reported PASS. Physical printer, tablet and BIR gates remain separate.');
}
main().catch(error=>{console.error(error.message);process.exitCode=1;});
