import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';
import {randomUUID} from 'node:crypto';
const root=path.dirname(fileURLToPath(import.meta.url));
const dir=path.join(root,'.ldc','handoff-'+randomUUID());fs.mkdirSync(dir,{recursive:true});
const report={startedAt:new Date().toISOString(),checks:[],status:'BLOCKED'};
const record=(name,status,detail='')=>{report.checks.push({name,status,detail});console.log(name+': '+status+' '+detail);};
const npm=(name,args)=>{
 const log=path.join(dir,name+'.log');const fd=fs.openSync(log,'wx');let r;
 try{r=spawnSync(process.platform==='win32'?'cmd.exe':'npm',process.platform==='win32'?['/d','/s','/c','npm',...args]:args,{cwd:root,stdio:['ignore',fd,fd],timeout:600000,windowsHide:true,env:{...process.env}});}finally{fs.closeSync(fd);}
 record(name,r.status===0?'PASS':'FAIL',r.error?.message??`exit ${r.status}; log ${path.basename(log)}`);return r.status===0;
};
try{
 const [major,minor]=process.versions.node.split('.').map(Number);const ok=major===22&&minor>=12;record('runtime',ok?'PASS':'BLOCKED',process.version);
 let ready=ok;
 // Always use the committed lock when present. Never force or bypass peer dependency checks.
 if(ready)ready=npm('dependencies',[fs.existsSync(path.join(root,'package-lock.json'))?'ci':'install','--no-audit','--no-fund','--fetch-retries=1','--fetch-timeout=20000']);else record('dependencies','BLOCKED');
 if(ready&&!fs.existsSync(path.join(root,'package-lock.json'))){record('lockfile','FAIL','Missing package-lock.json');ready=false;}else record('lockfile',ready?'PASS':'BLOCKED');
 for(const [name,args] of [['regression',['test']],['typecheck',['run','lint']],['build',['run','build']],['api-acceptance',['run','acceptance']]]){if(ready)ready=npm(name,args);else record(name,'BLOCKED','Earlier gate did not pass');}
 report.status=ready?'PASS':report.checks.some(x=>x.status==='FAIL')?'FAIL':'BLOCKED';
} catch(e){record('runner','FAIL',e.message);report.status='FAIL';}
finally{report.finishedAt=new Date().toISOString();report.scope='Software gate only; not production approval. Browser, Windows, hardware, compliance independent.';fs.writeFileSync(path.join(dir,'report.json'),JSON.stringify(report,null,2));console.log('Evidence: '+dir);process.exitCode=report.status==='PASS'?0:1;}
