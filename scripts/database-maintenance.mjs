import { DatabaseSync } from 'node:sqlite';
import fs from 'node:fs';
import {randomUUID} from 'node:crypto';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const required=['users','transactions','inventory_items','stock_movements','app_settings','cashier_shifts','customers','print_jobs'];
export function verify(file) {
  if (!fs.existsSync(file)) throw new Error('Database file not found: '+file);
  const db=new DatabaseSync(file,{readOnly:true});
  try {
    const rows=db.prepare('PRAGMA integrity_check').all();
    if(rows.length!==1 || rows[0].integrity_check!=='ok') throw new Error('SQLite integrity check failed');
    const tables=new Set(db.prepare("SELECT name FROM sqlite_master WHERE type='table'").all().map(x=>x.name));
    if(required.some(t=>!tables.has(t))) throw new Error('Not a complete Printshop POS database');
    if(db.prepare('PRAGMA foreign_key_check').all().length) throw new Error('Foreign key check failed');
  } finally { db.close(); }
}
export function snapshot(source,dest) {
  if(fs.existsSync(dest)) throw new Error('Destination already exists; refusing to overwrite');
  verify(source);
  const stage=fs.mkdtempSync(path.join(path.dirname(dest),'.snapshot-'));
  const candidate=path.join(stage,'copy.db');
  try {
    const db=new DatabaseSync(source,{readOnly:true});
    try {
      db.exec('PRAGMA busy_timeout=5000');
      db.prepare('VACUUM INTO ?').run(candidate);
    } finally {db.close();}
    verify(candidate);
    const fd=fs.openSync(candidate,'r+');
    try {fs.fsyncSync(fd);} finally {fs.closeSync(fd);}
    try {fs.linkSync(candidate,dest);}
    catch(error) {
      if(error.code==='EEXIST') throw new Error('Destination already exists; refusing to overwrite');
      throw error;
    }
  } finally {fs.rmSync(stage,{recursive:true,force:true});}
}
// The caller must own the distribution locks and have stopped the server.
export function restoreStoppedDatabase(data, source) {
  const live=path.join(data,'printshop-pos.db');
  const sidecars=['-wal','-shm','-journal'].map(suffix=>live+suffix);
  const stage=fs.mkdtempSync(path.join(data,'.restore-'));
  let previous;
  try {
    const candidate=path.join(stage,'replacement.db');
    snapshot(source,candidate);
    if (fs.existsSync(live)) {
      const recovery=path.join(data,'recovery');
      fs.mkdirSync(recovery,{recursive:true});
      previous=path.join(recovery,`before-restore-${randomUUID()}.db`);
      snapshot(live,previous);
      const db=new DatabaseSync(live);
      try {
        db.exec('PRAGMA busy_timeout=1000');
        const checkpoint=db.prepare('PRAGMA wal_checkpoint(TRUNCATE)').get();
        if(checkpoint.busy) throw Error('Another process still has the database open.');
        if(db.prepare('PRAGMA journal_mode=DELETE').get().journal_mode!=='delete') {
          throw Error('Could not close the database journal safely.');
        }
      } finally {db.close();}
    }
    if(sidecars.some(file=>fs.existsSync(file))) {
      throw Error('Existing SQLite journal files need review before restore. Preserve them with the database.');
    }
    // Replace the primary only after validating both copies and retiring its journal.
    fs.renameSync(candidate,live);
    return {previous};
  } finally {fs.rmSync(stage,{recursive:true,force:true});}
}
if(process.argv[1] && path.resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
  try {
    const [action,file]=process.argv.slice(2);
    const data=path.resolve(process.env.POS_DATA_DIR || 'data');
    const live=path.join(data,'printshop-pos.db');
    const backupDir=process.env.POS_DATA_DIR?path.join(data,'backups'):path.resolve('backups');
    const recoveryDir=process.env.POS_DATA_DIR?path.join(data,'recovery'):path.resolve('recovery');
    if(action==='backup') {
      fs.mkdirSync(backupDir,{recursive:true});
      const target=path.join(backupDir,`printshop-pos-${new Date().toISOString().replace(/[:.]/g,'-')}.db`);
      snapshot(live,target); console.log('Verified backup:',target);
    } else if(action==='verify' && file) {verify(path.resolve(file)); console.log('Verified:',file);}
    else if(action==='recover-copy' && file) {
      fs.mkdirSync(recoveryDir,{recursive:true});
      const target=path.join(recoveryDir,`recovered-${randomUUID()}.db`);
      snapshot(path.resolve(file),target); console.log('Verified recovery copy:',target,'\nStop POS and preserve the entire data directory before manually replacing the database. Never mix WAL/SHM files.');
    } else throw new Error('Usage: npm run db:backup | node scripts/database-maintenance.mjs verify FILE | node scripts/database-maintenance.mjs recover-copy FILE');
  } catch(e) {console.error(e.message);process.exitCode=1;}
}
