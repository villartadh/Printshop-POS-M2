import fs from 'node:fs';
import path from 'node:path';
import {randomUUID} from 'node:crypto';
import {createInterface} from 'node:readline/promises';
import {fileURLToPath} from 'node:url';

export const physicalChecks=Object.freeze({
 printer:Object.freeze([
 ['driver','Print the driver test page on the intended printer.'],
 ['receipt','Print a synthetic non-tax receipt; inspect legibility, totals and margins.'],
 ['feed','Check paper feed and configured paper width.'],
 ['reprint','Reprint the synthetic receipt; confirm it is marked as a copy.']
 ]),
 device:Object.freeze([
 ['trust','On the actual device, verify the configured HTTPS origin and certificate trust without bypassing warnings.'],
 ['touch','On the actual device, sign in with synthetic credentials and check touch layout and checkout.'],
 ['install','Install the PWA and reopen it from its device icon.'],
 ['offline','Submit a synthetic offline sale, reconnect and confirm exactly one recorded sale.'],
 ['preview','Verify receipt preview and sign out after the test.']
 ])
});
export function buildPhysicalReport(scope,version,results){
 if(!Object.hasOwn(physicalChecks,scope)||!/^\d+\.\d+\.\d+$/.test(version))throw Error('Invalid scope or version');
 const expected=physicalChecks[scope];
 if(!Array.isArray(results)||results.length!==expected.length)throw Error('Incomplete physical checklist');
 const checks=expected.map(([id],i)=>{
  const result=results[i];
  if(result?.id!==id||!['PASS','FAIL','BLOCKED'].includes(result.status))throw Error('Invalid physical checklist result');
  return {id,status:result.status};
 });
 return {schema:1,kind:'operator-attested-physical-acceptance',scope,version,recordedAt:new Date().toISOString(),status:checks.some(c=>c.status==='FAIL')?'FAIL':checks.some(c=>c.status==='BLOCKED')?'BLOCKED':'PASS',checks};
}
export async function guidedPhysicalAcceptance(rl,{scope,version,outputDir}){
 if(!Object.hasOwn(physicalChecks,scope))throw Error('Choose printer or device');
 console.log('Use a separate disposable test installation and synthetic transactions only. No real accounts, customer information or live shop database. This tool records operator observations; it does not perform or independently verify physical checks.');
 if(await rl.question('Type SYNTHETIC ONLY to confirm: ')!=='SYNTHETIC ONLY')return null;
 const results=[];
 for(const [id,instruction] of physicalChecks[scope]){
  console.log(instruction);
  const answer=(await rl.question('PASS / FAIL / BLOCKED: ')).trim().toUpperCase();
  if(!['PASS','FAIL','BLOCKED'].includes(answer))throw Error('Invalid result; no acceptance report saved');
  results.push({id,status:answer});
 }
 const report=buildPhysicalReport(scope,version,results);
 fs.mkdirSync(outputDir,{recursive:true});
 const file=path.join(outputDir,'physical-'+scope+'-'+randomUUID()+'.json');
 fs.writeFileSync(file,JSON.stringify(report,null,2)+'\n',{flag:'wx'});
 console.log('Operator-attested '+report.status+'; evidence: '+file);
 return {report,file};
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
 const rl=createInterface({input:process.stdin,output:process.stdout});
 try{
  if(!process.stdin.isTTY)throw Error('Interactive operator required');
  const version=JSON.parse(fs.readFileSync(path.join(root,'package.json'))).version;
  const result=await guidedPhysicalAcceptance(rl,{scope:process.argv[2],version,outputDir:path.join(root,'.ldc')});
  process.exitCode=!result?2:result.report.status==='PASS'?0:result.report.status==='FAIL'?1:2;
 }catch(error){console.error(error.message);process.exitCode=1;}finally{rl.close();}
}
