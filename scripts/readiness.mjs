import fs from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {verifyRelease} from './install.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
export function disposition(checks){return checks.some(c=>c.status==='FAIL')?'FAIL':checks.some(c=>c.status!=='PASS')?'BLOCKED':'PASS';}
export function prerequisites(root){return ['node_modules/tsx/package.json','node_modules/better-sqlite3/package.json','node_modules/typescript/package.json','node_modules/vite/package.json','package-lock.json'].filter(p=>!fs.existsSync(path.join(root,p)));}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 const checks=[];const add=(name,status,detail='')=>checks.push({name,status,detail});
 try{verifyRelease(root,JSON.parse(fs.readFileSync(path.join(root,'RELEASE-MANIFEST.json'))));add('Release manifest','PASS');}catch(e){add('Release manifest','FAIL',e.message);}
 const run=(name,args)=>{const r=spawnSync(process.execPath,args,{cwd:root,stdio:'inherit',timeout:300000});add(name,r.status===0?'PASS':'FAIL',r.error?.message??`exit ${r.status}`);return r.status===0;};
 run('Regression tests',['--experimental-strip-types','--test',...fs.readdirSync(path.join(root,'tests')).filter(x=>x.endsWith('.test.mjs')).map(x=>'tests/'+x)]);
 const missing=prerequisites(root);add('Dependency and lockfile prerequisites',missing.length?'BLOCKED':'PASS',missing.join(', '));
 let ready=checks.every(c=>c.status==='PASS');
 for(const [name,args] of [['TypeScript validation',['node_modules/typescript/bin/tsc','--noEmit']],['Production build',['node_modules/vite/bin/vite.js','build']],['Isolated API acceptance',['scripts/acceptance.mjs']]]){if(ready)ready=run(name,args);else add(name,'BLOCKED','Earlier gate did not pass');}
 for(const name of ['Browser checkout and offline recovery','Windows installation and display scaling','Physical printer acceptance','BIR compliance review'])add(name,'BLOCKED','Independent acceptance evidence required; not automated by this command');
 const report={version:JSON.parse(fs.readFileSync(path.join(root,'package.json'))).version,generatedAt:new Date().toISOString(),status:disposition(checks),checks};
 fs.mkdirSync(path.join(root,'.ldc'),{recursive:true});fs.writeFileSync(path.join(root,'.ldc','release-readiness.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));process.exitCode=report.status==='PASS'?0:1;
}
