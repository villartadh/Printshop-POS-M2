import { DatabaseSync } from 'node:sqlite';
import fs from 'node:fs';
import {randomUUID} from 'node:crypto';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const required=['users','transactions','inventory_items','stock_movements','app_settings','cashier_shifts','customers','print_jobs'];
const currentTables=["transaction_request_identities","inventory_mutation_results","applied_mutations","users","sessions","app_settings","categories","services","supply_categories","inventory_items","stock_movements","customers","transactions","order_conversions","print_jobs","expenses","cashier_shifts","transaction_reversals","drawer_movement_requests","balance_collection_requests","balance_collections","audit_logs","inventory_alert_epochs","inventory_alert_reads"];
const criticalColumns={"transactions":["id","client_mutation_id","receipt_snapshot_json","payments_json","balance_due"],"inventory_items":["id","reserved_quantity","archived_at","archive_reason","minimum_threshold"],"stock_movements":["id","inventory_item_id","purchase_unit_cost","quantity_before","quantity_delta"],"transaction_request_identities":["mutation_id","actor_id","transaction_id","request_digest"],"balance_collection_requests":["mutation_id","actor_id","customer_id","amount","outstanding_after"],"drawer_movement_requests":["mutation_id","actor_id","shift_id","movement_type","amount","reason","movement_id","expected_after"],"inventory_mutation_results":["id","actor_id","request_json","result_json"],"balance_collections":["id","transaction_id","customer_id","shift_id","amount"],"inventory_alert_epochs":["item_id","epoch"],"inventory_alert_reads":["user_id","item_id","epoch"]};
const requiredTriggers=['inventory_alert_insert','inventory_alert_transition'];
export function verify(file) {
  if (!fs.existsSync(file)) throw new Error('Database file not found: '+file);
  const db=new DatabaseSync(file,{readOnly:true});
  try {
    const rows=db.prepare('PRAGMA integrity_check').all();
    if(rows.length!==1 || rows[0].integrity_check!=='ok') throw new Error('SQLite integrity check failed');
    const tables=new Set(db.prepare("SELECT name FROM sqlite_master WHERE type='table'").all().map(x=>x.name));
    const version=db.prepare('PRAGMA user_version').get().user_version;
    if(version!==0&&version!==1)throw new Error('Unsupported Printshop POS database schema version: '+version);
    if(required.some(t=>!tables.has(t)))throw new Error('Not a complete Printshop POS database');
    if(version===1){
      const missing=currentTables.filter(t=>!tables.has(t));
      if(missing.length)throw new Error('Current schema missing required tables: '+missing.join(', '));
      for(const [table,columns] of Object.entries(criticalColumns)){
        const found=new Set(db.prepare('PRAGMA table_info('+table+')').all().map(c=>c.name));
        if(columns.some(c=>!found.has(c)))throw new Error('Current schema missing required columns: '+table);
      }
      const triggers=new Set(db.prepare("SELECT name FROM sqlite_master WHERE type='trigger'").all().map(r=>r.name));
      if(requiredTriggers.some(t=>!triggers.has(t)))throw new Error('Current schema missing required inventory alert triggers');
    }
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
