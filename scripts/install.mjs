import fs from 'node:fs';
import path from 'node:path';
import { createHash, randomUUID } from 'node:crypto';
import { fileURLToPath } from 'node:url';
const source=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const digest=file=>createHash('sha256').update(fs.readFileSync(file)).digest('hex');
export function verifyRelease(root,manifest){
 if(!/^\d+\.\d+\.\d+$/.test(manifest.version)||!manifest.files||!Object.keys(manifest.files).length)throw Error('Invalid release manifest');
 for(const [name,hash] of Object.entries(manifest.files)){
  if(!/^[\w.@ -]+(?:\/[\w.@ -]+)*$/.test(name)||name.split('/').some(s=>s==='.'||s==='..')||!/^\w{64}$/.test(hash))throw Error('Unsafe manifest entry');
  const file=path.join(root,name);if(!fs.lstatSync(file).isFile()||digest(file)!==hash)throw Error('Release verification failed: '+name);
 }
 if(JSON.parse(fs.readFileSync(path.join(root,'package.json'))).version!==manifest.version)throw Error('Version mismatch');
}
export function stageRelease(root,home){
 const manifest=JSON.parse(fs.readFileSync(path.join(root,'RELEASE-MANIFEST.json')));verifyRelease(root,manifest);
 const releases=path.join(home,'releases'),target=path.join(releases,manifest.version);fs.mkdirSync(releases,{recursive:true});
 if(fs.existsSync(target))throw Error('Version already installed; use its existing launcher or preserve it before reinstalling');
 const stage=fs.mkdtempSync(path.join(releases,'.stage-'));
 try{for(const name of Object.keys(manifest.files)){const dest=path.join(stage,name);fs.mkdirSync(path.dirname(dest),{recursive:true});fs.copyFileSync(path.join(root,name),dest,fs.constants.COPYFILE_EXCL);}fs.copyFileSync(path.join(root,'RELEASE-MANIFEST.json'),path.join(stage,'RELEASE-MANIFEST.json'));verifyRelease(stage,manifest);
 fs.writeFileSync(path.join(stage,'INSTALLED-LDC.cmd'),'@echo off\r\nsetlocal\r\ncd /d "%~dp0"\r\nfor %%I in ("%~dp0..\\..") do set "POS_DATA_DIR=%%~fI\\data"\r\ncall START-LDC.cmd\r\n',{flag:'wx'});
 fs.writeFileSync(path.join(stage,'RUN-POS.cmd'),'@echo off\r\nsetlocal\r\ncd /d "%~dp0"\r\nfor %%I in ("%~dp0..\\..") do set "POS_DATA_DIR=%%~fI\\data"\r\ncall START-PRINTSHOP-POS.cmd\r\nexit /b %errorlevel%\r\n',{flag:'wx'});
 fs.renameSync(stage,target);return target;}finally{fs.rmSync(stage,{recursive:true,force:true});}
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url))try{
 const [major,minor]=process.versions.node.split('.').map(Number);if(major!==22||minor<12)throw Error('Requires Node 22.12+ in the 22 series');
 if(process.platform!=='win32'||!process.env.LOCALAPPDATA)throw Error('Windows per-user installation requires LOCALAPPDATA');
 const target=stageRelease(source,path.join(process.env.LOCALAPPDATA,'Printshop POS'));console.log('Staged release:',target,'\nOpen RUN-POS.cmd in that folder for automatic startup, or INSTALLED-LDC.cmd for diagnostics. Existing data is not imported automatically.');
}catch(e){console.error(e.message);process.exitCode=1;}
