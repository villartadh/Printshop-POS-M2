import test from 'node:test';import assert from 'node:assert/strict';import {disposition,prerequisites} from '../scripts/readiness.mjs';
test('release disposition never promotes blocked or failed evidence',()=>{assert.equal(disposition([{status:'PASS'},{status:'BLOCKED'}]),'BLOCKED');assert.equal(disposition([{status:'BLOCKED'},{status:'FAIL'}]),'FAIL');assert.equal(disposition([{status:'PASS'}]),'PASS');});
test('readiness detects missing dependency and lockfile prerequisites',()=>{assert.equal(prerequisites('/nonexistent-pos-test-directory').length,5);});
