import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import {createHash,randomUUID,timingSafeEqual} from 'node:crypto';

export function controlName(data) {
  const resolved = path.resolve(data);
  const name = 'printshop-pos-'+createHash('sha256').update(
    process.platform==='win32' ? resolved.toLowerCase() : resolved).digest('hex');
  return process.platform==='win32' ? '\\\\.\\pipe\\'+name : '\0'+name;
}

const stateFile = data => path.join(data,'.distribution-control.json');

export async function acquireControl(data,shutdown) {
  const secret=randomUUID(), socketPath=controlName(data);
  const server = http.createServer((req,res)=>{
    const supplied = Buffer.from(req.headers.authorization ?? '');
    const expected = Buffer.from('Bearer '+secret);
    if(supplied.length!==expected.length || !timingSafeEqual(supplied,expected)){
      res.writeHead(403,{'Connection':'close'}).end();
      return;
    }
    if(req.method==='GET' && req.url==='/status') res.writeHead(204,{'Connection':'close'}).end();
    else if(req.method==='POST' && req.url==='/stop') res.writeHead(202,{'Connection':'close'}).end(shutdown);
    else res.writeHead(404,{'Connection':'close'}).end();
  });
  server.requestTimeout=5000;
  await new Promise((resolve,reject)=>{
    server.once('error',reject);
    server.listen(socketPath,()=>{server.off('error',reject);resolve();});
  }).catch(error=>{
    if(error.code==='EADDRINUSE') throw Error('Printshop POS is already active for this data directory.');
    throw error;
  });
  const file=stateFile(data), temp=path.join(data,'.control-'+randomUUID());
  try {
    fs.writeFileSync(temp,JSON.stringify({secret}),{mode:0o600,flag:'wx'});
    fs.renameSync(temp,file);
  } catch(error) {
    fs.rmSync(temp,{force:true});
    server.close();
    throw error;
  }
  return async ()=>{
    fs.rmSync(file,{force:true});
    await new Promise(resolve=>{server.close(resolve);server.closeAllConnections();});
  };
}

async function controlRequest(data,method,endpoint,expectedStatus) {
  const {secret}=JSON.parse(fs.readFileSync(stateFile(data),'utf8'));
  await new Promise((resolve,reject)=>{
    const request=http.request({socketPath:controlName(data),method,path:endpoint,
      headers:{Authorization:'Bearer '+secret},timeout:3000},response=>{
      response.resume();
      response.once('end',()=>response.statusCode===expectedStatus ? resolve() : reject(Error('POS declined the request.')));
    });
    request.once('error',reject);
    request.once('timeout',()=>request.destroy(Error('POS control request timed out.')));
    request.end();
  });
}

export async function stopControlled(data) {
  try { await controlRequest(data,'POST','/stop',202); }
  catch(error) {
    if(['ENOENT','ECONNREFUSED'].includes(error.code)) throw Error('No running Printshop POS was found.');
    throw error;
  }
}

export async function isControlled(data) {
  try { await controlRequest(data,'GET','/status',204); return true; }
  catch { return false; }
}
