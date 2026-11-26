import test from 'node:test';
import assert from 'node:assert/strict';
import {runtimeOK,safeSupport,gateSteps} from '../scripts/ldc.mjs';
test('LDC runtime version gate',()=>{assert.equal(runtimeOK('22.12.0'),true);assert.equal(runtimeOK('22.11.0'),false);assert.equal(runtimeOK('23.0.0'),false);});
test('LDC installation gate chooses lockfile install',()=>{assert.deepEqual(gateSteps(false,true),['ci','test','lint','build','acceptance']);assert.deepEqual(gateSteps(false,false),['install','test','lint','build','acceptance']);assert.deepEqual(gateSteps(true,true),['test','lint','build','acceptance']);});
test('LDC diagnostic report omits secrets and records',()=>{const result=safeSupport({version:'0.5.0',health:true,secret:'secret',customers:[1],env:{password:'x'}});assert.equal(result.health,true);assert.equal('secret' in result,false);assert.equal('customers' in result,false);assert.equal('env' in result,false);});

import {launchMode} from "../scripts/ldc.mjs";
test("automatic startup mode is explicit and rejects unknown arguments",()=>{assert.equal(launchMode([]),"menu");assert.equal(launchMode(["--run"]),"run");assert.throws(()=>launchMode(["--unknown"]));});

import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {crc32} from 'node:zlib';
import {writeSupportArchive} from '../scripts/support-archive.mjs';

test('support ZIP contains only checksummed allowlisted diagnostics',t=>{
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'pos-support-'));
  t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));
  const file=writeSupportArchive(dir,{version:'token-from-untrusted-field',platform:'win32',health:true,
    database:'DANGER_RECORD',rawLog:'DANGER_LOG'});
  const zip=fs.readFileSync(file);
  const entries={};
  let i=0;
  while(zip.readUInt32LE(i)===0x04034b50){
    const size=zip.readUInt32LE(i+18), nameSize=zip.readUInt16LE(i+26);
    const from=i+30+nameSize;
    const content=zip.subarray(from,from+size);
    const name=zip.subarray(i+30,from).toString();
    assert.equal(crc32(content),zip.readUInt32LE(i+14));
    entries[name]=content.toString();
    i=from+size;
  }
  assert.deepEqual(Object.keys(entries),['status.json','README.txt','SHA256SUMS.txt']);
  for(const name of ['status.json','README.txt'])assert.ok(entries['SHA256SUMS.txt'].includes(createHash('sha256').update(entries[name]).digest('hex')+'  '+name));
  assert.equal(zip.readUInt32LE(i),0x02014b50);
  assert.equal(JSON.parse(entries['status.json']).version,'unknown');
  assert.equal(JSON.parse(entries['status.json']).health,true);
  assert.doesNotMatch(zip.toString(),/DANGER_|token-from-untrusted/);
});

import {createHash} from 'node:crypto';
import {executeGate,comprehensiveSteps} from '../scripts/ldc.mjs';
import {safeGate} from '../scripts/support-archive.mjs';
test('comprehensive LDC gate stops on failure and records blocked checks',async()=>{const ran=[];const result=await executeGate(comprehensiveSteps(),async name=>{ran.push(name);if(name==='acceptance:https')throw Error('synthetic');});assert.deepEqual(ran,['acceptance:browser','acceptance:https']);assert.equal(result.status,'FAIL');assert.deepEqual(result.checks.map(c=>c.status),['PASS','FAIL','BLOCKED']);});
test('gate support summary excludes arbitrary data and does not claim unknown checks passed',()=>{const result=safeGate({mode:'comprehensive',checks:[{name:'test',status:'PASS',durationMs:1,password:'SECRET'},{name:'SECRET',status:'PASS'}]});assert.equal(result.status,'BLOCKED');assert.doesNotMatch(JSON.stringify(result),/SECRET/);});
