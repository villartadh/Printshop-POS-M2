import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {physicalChecks,buildPhysicalReport,guidedPhysicalAcceptance} from '../scripts/physical-acceptance.mjs';
const results=(scope,status='PASS')=>physicalChecks[scope].map(([id])=>({id,status}));
test('physical reports distinguish operator attestation from automated acceptance',()=>{
 const r=buildPhysicalReport('printer','0.9.7',results('printer'));
 assert.equal(r.kind,'operator-attested-physical-acceptance');
 assert.equal(r.status,'PASS');
 assert.equal(r.checks.length,4);
});
test('physical reports aggregate blocked and failed checks conservatively',()=>{
 const checks=results('device');
 checks[0].status='BLOCKED';
 assert.equal(buildPhysicalReport('device','0.9.7',checks).status,'BLOCKED');
 checks[1].status='FAIL';
 assert.equal(buildPhysicalReport('device','0.9.7',checks).status,'FAIL');
});
test('physical reports reject invalid scope, version, incomplete and reordered checks',()=>{
 assert.throws(()=>buildPhysicalReport('invalid','0.9.7',[]));
 assert.throws(()=>buildPhysicalReport('device','invalid',results('device')));
 assert.throws(()=>buildPhysicalReport('printer','0.9.7',[]));
 assert.throws(()=>buildPhysicalReport('printer','0.9.7',results('printer').reverse()));
});
test('physical reports exclude arbitrary operator details',()=>{
 const checks=results('device').map(c=>({...c,notes:'private',password:'secret'}));
 const r=buildPhysicalReport('device','0.9.7',checks);
 assert.deepEqual(r.checks,results('device'));
});
test('guided physical acceptance cancellation does not write evidence',async t=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'pos-physical-'));
 t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));
 const result=await guidedPhysicalAcceptance({question:async()=> 'NO'},{scope:'printer',version:'0.9.7',outputDir:dir});
 assert.equal(result,null);
 assert.deepEqual(fs.readdirSync(dir),[]);
});
test('guided physical acceptance saves unique fixed-schema reports',async t=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'pos-physical-'));
 t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));
 const run=()=>{let i=0;return guidedPhysicalAcceptance({question:async()=>i++===0?'SYNTHETIC ONLY':'PASS'},{scope:'printer',version:'0.9.7',outputDir:dir});};
 const a=await run(),b=await run();
 assert.notEqual(a.file,b.file);
 assert.deepEqual(JSON.parse(fs.readFileSync(a.file,'utf8')),a.report);
 assert.equal(a.report.status,'PASS');
});
test('invalid physical response cannot generate a passing report',async t=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'pos-physical-'));
 t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));
 let i=0;
 await assert.rejects(guidedPhysicalAcceptance({question:async()=>i++===0?'SYNTHETIC ONLY':'unknown'},{scope:'printer',version:'0.9.7',outputDir:dir}),/Invalid result/);
 assert.deepEqual(fs.readdirSync(dir),[]);
});
