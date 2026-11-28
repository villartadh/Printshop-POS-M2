import fs from 'node:fs';
import path from 'node:path';
import {randomUUID,createHash} from 'node:crypto';
import {crc32} from 'node:zlib';

export function safeSupport(info) {
  return {
    version:/^\d+\.\d+\.\d+$/.test(info.version) ? info.version : 'unknown',
    node:/^v\d+\.\d+\.\d+$/.test(info.node) ? info.node : 'unknown',
    platform:['win32','linux','darwin'].includes(info.platform) ? info.platform : 'unknown',
    arch:['x64','arm64','ia32'].includes(info.arch) ? info.arch : 'unknown',
    dependencies:!!info.dependencies,build:!!info.build,databasePresent:!!info.databasePresent,
    health:!!info.health,managed:!!info.managed
  };
}

// Only allowlisted generated diagnostics; never archive arbitrary local files.
export function safeGate(gate){
 if(!gate||!['startup','comprehensive'].includes(gate.mode)||!Array.isArray(gate.checks))return null;
 const allowed=['ci','test','lint','build','acceptance','acceptance:browser','acceptance:https','upgrade'];
 const checks=gate.checks.filter(c=>allowed.includes(c?.name)&&['PASS','FAIL','BLOCKED'].includes(c.status)).map(c=>({name:c.name,status:c.status,durationMs:Number.isSafeInteger(c.durationMs)&&c.durationMs>=0?c.durationMs:0}));
 const required=['test','lint','build','acceptance',...(gate.mode==='comprehensive'?['acceptance:browser','acceptance:https','upgrade']:[])];
 return {mode:gate.mode,status:checks.some(c=>c.status==='FAIL')?'FAIL':required.every(n=>checks.some(c=>c.name===n&&c.status==='PASS'))&&checks.every(c=>c.status==='PASS')&&checks.length===gate.checks.length?'PASS':'BLOCKED',checks};
}
export function writeSupportArchive(directory, info) {
  const entries = [
    ['status.json',JSON.stringify({schema:1,generatedAt:new Date().toISOString(),...safeSupport(info)},null,2)+'\n'],
    ['README.txt','Printshop POS support snapshot\nReview status.json before sharing. It contains application and runtime status fields.\nRaw logs, settings, databases, paths and credentials are excluded.\n']
  ];
  const gate=safeGate(info.gate);
  if(gate)entries.push(['gate.json',JSON.stringify(gate,null,2)+'\n']);
  entries.push(['SHA256SUMS.txt',entries.map(([name,content])=>createHash('sha256').update(content).digest('hex')+'  '+name).join('\n')+'\n']);
  const local=[], central=[];
  let offset=0;
  for(const [name,content] of entries) {
    const filename=Buffer.from(name), bytes=Buffer.from(content), crc=crc32(bytes);
    const header=Buffer.alloc(30), index=Buffer.alloc(46);
    header.writeUInt32LE(0x04034b50,0);
    header.writeUInt16LE(20,4);
    header.writeUInt16LE(33,12); // Minimum DOS date (1980-01-01).
    header.writeUInt32LE(crc,14);
    header.writeUInt32LE(bytes.length,18);
    header.writeUInt32LE(bytes.length,22);
    header.writeUInt16LE(filename.length,26);
    index.writeUInt32LE(0x02014b50,0);
    index.writeUInt16LE(20,4);
    header.copy(index,6,4,30);
    index.writeUInt32LE(offset,42);
    local.push(header,filename,bytes);
    central.push(index,filename);
    offset+=header.length+filename.length+bytes.length;
  }
  const end=Buffer.alloc(22), catalog=Buffer.concat(central);
  end.writeUInt32LE(0x06054b50,0);
  end.writeUInt16LE(entries.length,8);
  end.writeUInt16LE(entries.length,10);
  end.writeUInt32LE(catalog.length,12);
  end.writeUInt32LE(offset,16);
  const file=path.join(directory,`support-${randomUUID()}.zip`);
  fs.writeFileSync(file,Buffer.concat([...local,catalog,end]),{flag:'wx',mode:0o600});
  return file;
}
