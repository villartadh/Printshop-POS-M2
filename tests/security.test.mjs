import test from 'node:test';
import assert from 'node:assert/strict';
import {localHost,allowedOrigin,attemptGate,validPassword,validPin,removesLastAdmin} from '../src/shared/securityPolicy.ts';
test('loopback host boundary',()=>{for(const h of ['localhost:3000','127.0.0.1:3000','[::1]:3000'])assert.equal(localHost(h),true);for(const h of ['evil.example','localhost.evil.example','127.0.0.2','localhost@evil.example',undefined])assert.equal(localHost(h),false);});
test('same origin requests only',()=>{assert.equal(allowedOrigin('localhost:3000','http://localhost:3000'),true);assert.equal(allowedOrigin('localhost:3000',undefined),true);for(const o of ['null','https://localhost:3000','http://localhost:4000','http://evil.example','http://localhost:3000/path'])assert.equal(allowedOrigin('localhost:3000',o),false);});
test('bounded authentication attempts and reset',()=>{let now=1000;const gate=attemptGate(2,60000,()=>now);assert.equal(gate(),0);assert.equal(gate(),0);assert.equal(gate(),60);now+=59000;assert.equal(gate(),1);now+=1000;assert.equal(gate(),0);});
test('password length and type limits',()=>{assert.equal(validPassword('abcdefgh'),true);for(const p of ['1234567','x'.repeat(129),12345678,null])assert.equal(validPassword(p),false);});
test('PIN format protection',()=>{for(const p of ['0000','123456'])assert.equal(validPin(p),true);for(const p of [1234,'123','1234567','abcd','1234 '])assert.equal(validPin(p),false);});
test('last administrator protection',()=>{assert.equal(removesLastAdmin('admin',1,'cashier',1,1),true);assert.equal(removesLastAdmin('admin',1,'admin',0,1),true);assert.equal(removesLastAdmin('admin',1,'admin',1,1),false);assert.equal(removesLastAdmin('admin',1,'cashier',1,2),false);});
