// Read-only diagnostic; not a BIR journal, fiscal report, or proof of historical immutability.
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import {DatabaseSync} from 'node:sqlite';
import {verify} from './database-maintenance.mjs';

const money=v=>typeof v==='number'&&Number.isFinite(v)&&v>=0&&v<=1e9&&Math.abs(v*100-Math.round(v*100))<0.0001;
const cents=v=>Math.round(v*100);
export function reconcileLedger({sales,collections,reversals,customers}){
 const findings=[];
 const issue=(code,reference)=>findings.push({code,reference});
 const saleMap=new Map(sales.map(s=>[s.id,s]));
 const customerMap=new Map(customers.map(c=>[c.id,c]));
 const reversalMap=new Map(reversals.map(r=>[r.transaction_id,r]));
 const collectionMap=new Map();
 const balances=new Map();
 for(const c of collections){
  const s=saleMap.get(c.transaction_id);
  if(!s||!money(c.amount)||c.amount<=0||c.customer_id!==s.customer_id){issue('INVALID_COLLECTION_LINK_OR_AMOUNT',c.id);continue;}
  const list=collectionMap.get(s.id)||[];list.push(c);collectionMap.set(s.id,list);
 }
 for(const r of reversals)if(!saleMap.has(r.transaction_id))issue('ORPHAN_REVERSAL',r.transaction_id);
 for(const s of sales){
  if(![s.total_amount,s.amount_paid,s.balance_due,s.change_amount].every(money)){
   issue('INVALID_SALE_AMOUNTS',s.id);continue;
  }
  if(cents(s.total_amount)!==cents(s.amount_paid)+cents(s.balance_due))issue('SALE_TOTAL_MISMATCH',s.id);
  let payments;
  try{payments=JSON.parse(s.payments_json);}catch{payments=null;}
  if(!Array.isArray(payments)||payments.some(p=>!p||!money(p.amount)||typeof p.methodType!=='string'||p.methodType==='credit_balance')){
   issue('INVALID_PAYMENT_RECORD',s.id);
  }else{
   const cash=payments.filter(p=>p.methodType==='cash').reduce((n,p)=>n+cents(p.amount),0);
   const tender=payments.reduce((n,p)=>n+cents(p.amount),0);
   if(!Number.isSafeInteger(tender)||cash<cents(s.change_amount)||tender-cents(s.change_amount)!==cents(s.amount_paid))issue('PAYMENT_TOTAL_MISMATCH',s.id);
   for(const c of collectionMap.get(s.id)||[]){
    const matches=payments.filter(p=>p.id===c.id&&p.methodType==='cash'&&money(p.amount)&&cents(p.amount)===cents(c.amount));
    if(matches.length!==1)issue('COLLECTION_PAYMENT_MISMATCH',c.id);
   }
  }
  const r=reversalMap.get(s.id),reversed=['voided','refunded'].includes(s.payment_status);
  if(reversed){
   if(!r||r.kind!==s.payment_status||![r.cash_amount,r.electronic_amount,r.cancelled_receivable].every(money)||cents(r.cash_amount)+cents(r.electronic_amount)!==cents(s.amount_paid)||cents(r.cancelled_receivable)!==cents(s.balance_due))
    issue('REVERSAL_MISMATCH',s.id);
   if((collectionMap.get(s.id)||[]).length)issue('REVERSED_SALE_HAS_COLLECTION',s.id);
  }else{
   if(r)issue('UNEXPECTED_REVERSAL',s.id);
   const expected=s.balance_due===0?'paid':s.amount_paid===0?'unpaid':'partial';
   if(s.payment_status!==expected)issue('SALE_STATUS_MISMATCH',s.id);
   if(s.customer_id){
    if(!customerMap.has(s.customer_id))issue('MISSING_CUSTOMER',s.id);
    else balances.set(s.customer_id,(balances.get(s.customer_id)||0)+cents(s.balance_due));
   }else if(s.balance_due>0)issue('WALK_IN_BALANCE',s.id);
  }
 }
 for(const c of customers){
  const expected=balances.get(c.id)||0;
  if(!money(c.outstanding_balance)||!Number.isSafeInteger(expected)||cents(c.outstanding_balance)!==expected)issue('CUSTOMER_BALANCE_MISMATCH',c.id);
 }
 return {status:findings.length?'REVIEW_REQUIRED':'PASS',counts:{sales:sales.length,collections:collections.length,reversals:reversals.length,customers:customers.length,findings:findings.length},findings};
}
export function reviewSnapshot(file){
 const absolute=path.resolve(file);
 if(!fs.lstatSync(absolute).isFile())throw Error('Regular verified backup file required');
 for(const suffix of ['-wal','-shm','-journal'])if(fs.existsSync(absolute+suffix))throw Error('Use a standalone verified backup without SQLite sidecars');
 const hash=()=>createHash('sha256').update(fs.readFileSync(absolute)).digest('hex');
 const before=hash();verify(absolute);
 const db=new DatabaseSync(absolute,{readOnly:true});
 let result;
 try{
  if(db.prepare('PRAGMA user_version').get().user_version!==1)throw Error('Current versioned backup required; do not migrate the backup in place');
  db.exec('BEGIN');
  try{
   result=reconcileLedger({
    sales:db.prepare("SELECT id,customer_id,total_amount,amount_paid,balance_due,change_amount,payment_status,payments_json FROM transactions WHERE type='sale' AND is_demo=0 ORDER BY id").all(),
    collections:db.prepare('SELECT id,transaction_id,customer_id,amount FROM balance_collections ORDER BY id').all(),
    reversals:db.prepare('SELECT transaction_id,kind,cash_amount,electronic_amount,cancelled_receivable FROM transaction_reversals ORDER BY transaction_id').all(),
    customers:db.prepare('SELECT id,outstanding_balance FROM customers WHERE is_demo=0 ORDER BY id').all()
   });
  }finally{db.exec('ROLLBACK');}
 }finally{db.close();}
 if(hash()!==before)throw Error('Backup changed during review; repeat on an isolated verified copy');
 return {schemaVersion:1,scope:'NON-FISCAL LEDGER REVIEW — NOT A BIR REPORT',generatedAt:new Date().toISOString(),databaseSha256:before,...result};
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 try{
  if(process.argv.length!==4)throw Error('Usage: node scripts/ledger-review.mjs VERIFIED_BACKUP.db NEW_REPORT.json');
  const report=reviewSnapshot(process.argv[2]);
  fs.writeFileSync(path.resolve(process.argv[3]),JSON.stringify(report,null,2)+'\n',{flag:'wx',mode:0o600});
  console.log(report.status,report.counts);
  if(report.status!=='PASS')process.exitCode=2;
 }catch(e){console.error(e.message);process.exitCode=1;}
}
