import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import https from 'node:https';
import os from 'node:os';
import path from 'node:path';
import {randomUUID} from 'node:crypto';
import {execFileSync,spawn} from 'node:child_process';
import {setTimeout as delay} from 'node:timers/promises';
import {acceptanceEnv,assertInstance,freePort} from './acceptance.mjs';

const work = await fs.mkdtemp(path.join(os.tmpdir(),'pos-https-acceptance-'));
let server;
try {
  const root = path.resolve(import.meta.dirname,'..');
  await fs.cp(path.join(root,'dist'),path.join(work,'dist'),{recursive:true});
  const key = path.join(work,'localhost.key'), cert = path.join(work,'localhost.crt');
  execFileSync('openssl',['req','-x509','-newkey','rsa:2048','-nodes','-days','1',
    '-subj','/CN=127.0.0.1','-addext','subjectAltName=IP:127.0.0.1,DNS:localhost','-keyout',key,'-out',cert],{stdio:'ignore'});
  const ca = await fs.readFile(cert);
  const httpPort = await freePort();
  let tlsPort;
  do {tlsPort=await freePort();} while(tlsPort===httpPort);
  const local = 'http://127.0.0.1:'+httpPort, origin = 'https://127.0.0.1:'+tlsPort;
  const instance = randomUUID();
  server = spawn(process.execPath,[path.join(root,'scripts/start.ts')],{
    cwd:work,env:{...acceptanceEnv(httpPort,instance),POS_HTTPS_ORIGIN:origin,POS_HTTPS_BIND:'127.0.0.1',
      POS_HTTPS_CERT:cert,POS_HTTPS_KEY:key},stdio:'ignore'
  });
  const request = (url,{body,headers={}}={})=>new Promise((resolve,reject)=>{
    const request = https.request(origin+url,{
      ca, servername:'localhost', method:body ? 'POST' : 'GET',
      headers:{...(body ? {'Content-Type':'application/json'} : {}),...headers},
      timeout:3000
    },response=>{
      let payload='';
      response.setEncoding('utf8').on('data',chunk=>payload+=chunk);
      response.on('end',()=>resolve({status:response.statusCode,payload:JSON.parse(payload)}));
    });
    request.on('error',reject).on('timeout',()=>request.destroy(new Error('HTTPS request timeout')));
    request.end(body ? JSON.stringify(body) : undefined);
  });
  let health;
  for(let i=0;i<60 && server.exitCode===null;i++){
    try {health=await request('/api/health');break;} catch {await delay(100);}
  }
  assert.equal(health?.status,200);
  assertInstance(health.payload,instance);
  assert.equal((await request('/api/health',{headers:{Host:'bad.invalid'}})).status,403);
  assert.equal((await request('/api/health',{headers:{Origin:'https://bad.invalid'}})).status,403);
  const credentials = {username:'tls-acceptance',fullName:'Synthetic TLS',password:randomUUID()};
  assert.equal((await request('/api/auth/setup-admin',{body:credentials})).status,403);
  assert.equal((await request('/api/auth/pin-login',{body:{pin:'0123'}})).status,403);
  assert.deepEqual((await request('/api/auth/status')).payload,{needsSetup:false,quickUsers:[]});
  assert.equal((await request('/api/bootstrap')).status,401);
  const setup = await fetch(local+'/api/auth/setup-admin',{method:'POST',
    headers:{'Content-Type':'application/json'},body:JSON.stringify(credentials)});
  assert.equal(setup.status,201);
  const session = await request('/api/auth/login',{body:credentials,headers:{Origin:origin}});
  assert.equal(session.status,200);
  const bootstrap = await request('/api/bootstrap',{headers:{Authorization:'Bearer '+session.payload.token}});
  assert.equal(bootstrap.status,200);
  assert.deepEqual(bootstrap.payload.transactions,[]);
  assert.equal((await fetch(local+'/api/health',{headers:{Origin:origin}})).status,403);
  console.log('PASS trusted HTTPS, exact Host/Origin, local-only setup and PIN, password login, anonymous denial and loopback isolation');
} finally {
  if(server && server.exitCode===null && server.signalCode===null){
    const exit=new Promise(resolve=>server.once('exit',resolve));
    server.kill();
    await exit;
  }
  await fs.rm(work,{recursive:true,force:true});
}
