import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import net from 'node:net';
import {createHash} from 'node:crypto';
import {verifyWindowsPackage,requiredWindowsFiles,requireFreePort} from '../scripts/windows/native-preflight.mjs';

function fixture(t,omit){
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'pos-preflight-'));
 t.after(()=>fs.rmSync(root,{recursive:true,force:true}));
 const files={};
 for(const name of requiredWindowsFiles){
  const file=path.join(root,name);
  fs.mkdirSync(path.dirname(file),{recursive:true});
  const bytes=name==='package.json'?JSON.stringify({version:'0.9.7'}):'synthetic '+name;
  fs.writeFileSync(file,bytes);
  if(name!==omit)files[name]=createHash('sha256').update(bytes).digest('hex');
 }
 fs.writeFileSync(path.join(root,'RELEASE-MANIFEST.json'),JSON.stringify({version:'0.9.7',files}));
 return root;
}
test('native preflight accepts intact synthetic package',t=>{
 const root=fixture(t);
 assert.deepEqual(verifyWindowsPackage(root,['0.9.7']),{folder:fs.realpathSync(root),version:'0.9.7'});
});
test('native preflight rejects wrong version',t=>{
 assert.throws(()=>verifyWindowsPackage(fixture(t),['0.9.5']),/Unexpected package version/);
});
test('native preflight rejects tampered executable',t=>{
 const root=fixture(t);
 fs.appendFileSync(path.join(root,'runtime/node.exe'),'tampered');
 assert.throws(()=>verifyWindowsPackage(root,['0.9.7']),/Release verification failed/);
});
test('native preflight rejects untracked launcher',t=>{
 assert.throws(()=>verifyWindowsPackage(fixture(t,'RUN-POS.cmd'),['0.9.7']),/missing manifest-covered RUN-POS/);
});
test('native preflight rejects untracked package metadata',t=>{
 assert.throws(()=>verifyWindowsPackage(fixture(t,'package.json'),['0.9.7']),/missing manifest-covered package.json/);
});
test('native preflight rejects an occupied TCP port, even without HTTP',async()=>{
 const server=net.createServer();
 await new Promise((resolve,reject)=>{server.once('error',reject);server.listen(0,'127.0.0.1',resolve);});
 const port=server.address().port;
 try{await assert.rejects(requireFreePort(port),{code:'EADDRINUSE'});}
 finally{await new Promise(resolve=>server.close(resolve));}
});
test('native preflight can bind and release an ephemeral port',async()=>{
 await requireFreePort(0);
});
