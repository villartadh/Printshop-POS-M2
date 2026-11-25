import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {DatabaseSync} from 'node:sqlite';
import {snapshot,verify} from '../scripts/database-maintenance.mjs';
test('backup includes committed WAL data and refuses overwrite',()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'pos-backup-'));
 const source=path.join(dir,'live.db'), dest=path.join(dir,'copy.db');
 const db=new DatabaseSync(source);
 try {
 db.exec('PRAGMA journal_mode=WAL;');
 for(const t of ['users','transactions','inventory_items','stock_movements','app_settings','cashier_shifts','customers','print_jobs']) db.exec(`CREATE TABLE ${t}(id TEXT PRIMARY KEY)`);
 db.exec("INSERT INTO transactions VALUES ('sale-1')");
 snapshot(source,dest); verify(dest);
 const copy=new DatabaseSync(dest,{readOnly:true});try{assert.equal(copy.prepare('SELECT id FROM transactions').get().id,'sale-1');}finally{copy.close();}
 assert.throws(()=>snapshot(source,dest),/overwrite/);
 }finally{db.close();fs.rmSync(dir,{recursive:true,force:true});}
});
test('non-POS database is rejected',()=>{const dir=fs.mkdtempSync(path.join(os.tmpdir(),'pos-invalid-'));try{const file=path.join(dir,'empty.db');new DatabaseSync(file).close();assert.throws(()=>verify(file),/complete/);}finally{fs.rmSync(dir,{recursive:true,force:true});}});

import {restoreStoppedDatabase} from '../scripts/database-maintenance.mjs';
test('restore preserves the prior database and activates only a verified replacement',t=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'pos-restore-'));
 t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));
 const live=path.join(dir,'printshop-pos.db'), backup=path.join(dir,'older.db');
 const read=file=>{
   const db=new DatabaseSync(file,{readOnly:true});
   try {return db.prepare('SELECT id FROM transactions ORDER BY id').all().map(row=>row.id);}
   finally {db.close();}
 };
 const db=new DatabaseSync(live);
 try {
   db.exec('PRAGMA journal_mode=WAL');
   for(const name of ['users','transactions','inventory_items','stock_movements','app_settings','cashier_shifts','customers','print_jobs']) {
     db.exec(`CREATE TABLE ${name}(id TEXT PRIMARY KEY)`);
   }
   db.exec("INSERT INTO transactions VALUES ('first')");
   snapshot(live,backup);
   db.exec("INSERT INTO transactions VALUES ('second')");
 } finally {db.close();}
 const invalid=path.join(dir,'invalid.db');
 new DatabaseSync(invalid).close();
 assert.throws(()=>restoreStoppedDatabase(dir,invalid),/complete/);
 assert.deepEqual(read(live),['first','second']);
 const {previous}=restoreStoppedDatabase(dir,backup);
 assert.deepEqual(read(previous),['first','second']);
 assert.deepEqual(read(live),['first']);
 assert.deepEqual(read(backup),['first']);
 assert.ok(!fs.readdirSync(dir).some(name=>name.startsWith('.restore-')));
});
