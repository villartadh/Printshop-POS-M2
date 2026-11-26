import fs from 'node:fs';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import {verifyRelease} from './install.mjs';
const root=path.resolve(import.meta.dirname,'..');
const output=path.resolve(process.argv[2]||'release-output/source');
const version=JSON.parse(fs.readFileSync(path.join(root,'package.json'))).version;
const sha=execFileSync('git',['rev-parse','HEAD'],{cwd:root,encoding:'utf8'}).trim();
if(!/^0\.9\.\d+$/.test(version)||!/^[0-9a-f]{40}$/.test(sha))throw Error('Unexpected candidate identity');
if(fs.existsSync(output))throw Error('Refusing to overwrite source staging directory');
const tracked=execFileSync('git',['ls-files','-z'],{cwd:root}).toString().split('\0').filter(Boolean);
const allowed=name=>/^(src|scripts|tests|public|\.github)\//.test(name)||['.env.example','.gitignore','AGENTS.md','CHANGELOG.md','DEVELOPMENT_QUEUE.md','HANDOFF_SNAPSHOT.md','FEATURE-MATRIX.md','BIR-GAP-ANALYSIS.md','INSTALL-WINDOWS.cmd','OPERATOR-GUIDE.md','README.md','START-LDC.cmd','START-PRINTSHOP-POS.cmd','favicon.ico','index.html','metadata.json','package-lock.json','package.json','server.ts','tsconfig.json','validate.mjs','vite.config.ts'].includes(name);
const files={};
fs.mkdirSync(output,{recursive:true});
for(const name of tracked.filter(allowed).sort()){
 if(name.split('/').some(p=>p==='..'||p==='.')||/\.(db|sqlite|wal|shm|pem|key)$/i.test(name)||/(^|\/)\.env(?!\.example$)/.test(name))throw Error('Unsafe source path: '+name);
 const source=path.join(root,name),dest=path.join(output,name);
 if(!fs.lstatSync(source).isFile())throw Error('Nonregular source file: '+name);
 fs.mkdirSync(path.dirname(dest),{recursive:true});fs.copyFileSync(source,dest,fs.constants.COPYFILE_EXCL);
 files[name]=createHash('sha256').update(fs.readFileSync(dest)).digest('hex');
}
const identity=JSON.stringify({version,commit:sha,kind:'source-software-pilot'},null,2)+'\n';
fs.writeFileSync(path.join(output,'SOURCE-IDENTITY.json'),identity,{flag:'wx'});
files['SOURCE-IDENTITY.json']=createHash('sha256').update(identity).digest('hex');
const manifest={version,files};
fs.writeFileSync(path.join(output,'RELEASE-MANIFEST.json'),JSON.stringify(manifest,null,2)+'\n',{flag:'wx'});
verifyRelease(output,manifest);
console.log('Verified versioned source stage',version,sha,Object.keys(files).length);
