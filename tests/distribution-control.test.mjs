import test from 'node:test';
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {once} from 'node:events';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {acquireControl,isControlled,stopControlled} from '../scripts/windows/control.mjs';

const supported=['linux','win32'].includes(process.platform);

test('distribution control excludes a second launcher and accepts authorized stop', {skip:!supported}, async t=>{
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'pos-ctl-'));
  t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));
  let stopped;
  const received = new Promise(resolve=>{stopped=resolve;});
  const release=await acquireControl(dir,stopped);
  try {
    assert.equal(await isControlled(dir),true);
    await assert.rejects(acquireControl(dir,()=>{}),/already active/);
    await stopControlled(dir);
    await received;
  } finally {await release();}
});

test('a forced launcher exit leaves no operating system lock', {skip:!supported,timeout:8000}, async t=>{
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'pos-ctl-'));
  t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));
  const module=new URL('../scripts/windows/control.mjs',import.meta.url).href;
  const child=spawn(process.execPath,['--input-type=module','-e',
    'const [module,dir]=process.argv.slice(1); const {acquireControl}=await import(module); await acquireControl(dir,()=>{}); console.log("ready")',module,dir],
    {stdio:['ignore','pipe','inherit']});
  const exited=once(child,'exit');
  try {
    const chunk=await Promise.race([once(child.stdout,'data').then(([chunk])=>chunk),exited.then(()=>null)]);
    assert.match(String(chunk),/ready/);
    child.kill('SIGKILL');
    await exited;
    assert.ok(fs.existsSync(path.join(dir,'.distribution-control.json')));
    assert.equal(await isControlled(dir),false);
    const release=await acquireControl(dir,()=>{});
    await release();
    assert.equal(fs.existsSync(path.join(dir,'.distribution-control.json')),false);
  } finally {
    if(child.exitCode===null && child.signalCode===null){child.kill('SIGKILL');await exited;}
  }
});
