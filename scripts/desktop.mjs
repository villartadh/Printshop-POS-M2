import fs from 'node:fs';
import path from 'node:path';
import {spawn} from 'node:child_process';
export const desktopURL = 'http://127.0.0.1:3000/';
export function browserCandidates(env) {
  return [env['ProgramFiles(x86)'],env.ProgramFiles,env.LOCALAPPDATA].filter(Boolean).flatMap(base=>['Microsoft/Edge/Application/msedge.exe','Google/Chrome/Application/chrome.exe'].map(file=>path.join(base,file)));
}
export function browserArgs(){return [`--app=${desktopURL}`];}
export async function openDesktop(token){
  if(process.platform!=='win32')throw new Error('Desktop launcher currently supports Windows only; use the local web URL.');
  const response=await fetch(new URL('/api/health',desktopURL),{signal:AbortSignal.timeout(2000)});
  if(!response.ok || (await response.json()).instance!==token || !token)throw new Error('Managed POS is not ready. Start it from LDC first.');
  const browser=browserCandidates(process.env).find(p=>fs.existsSync(p));
  if(!browser)throw new Error('Edge or Chrome not found. Open '+desktopURL+' in your browser.');
  await new Promise((resolve,reject)=>{const p=spawn(browser,browserArgs(),{detached:true,stdio:'ignore',shell:false});p.once('error',reject);p.once('spawn',()=>{p.unref();resolve();});});
}
