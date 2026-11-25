import test from 'node:test';
import assert from 'node:assert/strict';
import {resolveLayoutTier} from '../src/shared/layoutPolicy.ts';
import {browserCandidates,browserArgs} from '../scripts/desktop.mjs';
test('responsive layout boundaries',()=>{for(const [w,t] of [[320,'mobile'],[767,'mobile'],[768,'tablet'],[1023,'tablet'],[1024,'desktop']])assert.equal(resolveLayoutTier(w,'auto'),t);});
test('preview cannot force desktop onto phone',()=>{assert.equal(resolveLayoutTier(375,'windows'),'mobile');assert.equal(resolveLayoutTier(900,'web'),'tablet');});
test('desktop can preview narrower layouts',()=>{assert.equal(resolveLayoutTier(1600,'mobile'),'mobile');assert.equal(resolveLayoutTier(1600,'tablet'),'tablet');});
test('desktop launcher uses fixed loopback URL',()=>assert.deepEqual(browserArgs(),['--app=http://127.0.0.1:3000/']));
test('desktop browser discovery handles missing environment',()=>{assert.deepEqual(browserCandidates({}),[]);assert.equal(browserCandidates({ProgramFiles:'C:/Program Files'}).length,2);});
