import fs from 'node:fs';
import path from 'node:path';
import net from 'node:net';
import {verifyRelease} from '../install.mjs';

export const requiredWindowsFiles=Object.freeze(['package.json','runtime/node.exe','INSTALL.cmd','RUN-POS.cmd','STOP-POS.cmd','UNINSTALL.cmd','SUPPORT.cmd']);

export function verifyWindowsPackage(input,allowed){
 if(!input)throw Error('An extracted package directory is required.');
 const folder=fs.realpathSync(path.resolve(input));
 const manifest=JSON.parse(fs.readFileSync(path.join(folder,'RELEASE-MANIFEST.json'),'utf8'));
 if(!allowed.includes(manifest.version))throw Error('Unexpected package version: '+manifest.version);
 verifyRelease(folder,manifest);
 for(const file of requiredWindowsFiles){
  if(!Object.hasOwn(manifest.files,file))throw Error('Package missing manifest-covered '+file);
 }
 return {folder,version:manifest.version};
}

export async function requireFreePort(port=3000){
 const probe=net.createServer();
 await new Promise((resolve,reject)=>{
  probe.once('error',reject);
  probe.listen({host:'127.0.0.1',port,exclusive:true},resolve);
 });
 await new Promise((resolve,reject)=>probe.close(error=>error?reject(error):resolve()));
}
