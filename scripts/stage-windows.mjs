import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';

const root = path.resolve(import.meta.dirname,'..');
const [nodeFolder, outputFolder] = process.argv.slice(2);
if (!nodeFolder || !outputFolder) throw Error('Usage: node scripts/stage-windows.mjs VERIFIED-NODE-FOLDER NEW-STAGING-FOLDER');
const target = path.resolve(outputFolder);
const runtime = path.resolve(nodeFolder);
const lock = JSON.parse(fs.readFileSync(path.join(root,'package-lock.json')));
const selected = new Set();

function select(name, parent = '') {
  const direct = parent ? parent+'/node_modules/'+name : '';
  const entry = direct && lock.packages[direct] ? direct : 'node_modules/'+name;
  const record = lock.packages[entry];
  if (!record) throw Error('Missing runtime package in lock: '+name);
  if (selected.has(entry)) return;
  selected.add(entry);
  for (const dep of Object.keys(record.dependencies ?? {})) select(dep,entry);
}
for (const name of ['dotenv','express','better-sqlite3']) select(name);

fs.mkdirSync(target);
function copy(file, destination = file, from = root, options = {}) {
  const dest = path.join(target,destination);
  fs.mkdirSync(path.dirname(dest),{recursive:true});
  fs.cpSync(path.join(from,file),dest,{recursive:true,errorOnExist:true,force:false,...options});
}
for (const entry of selected) {
  copy(entry,entry,root,{filter: filename => {
    const local = path.relative(path.join(root,entry),filename).split(path.sep);
    if (local[0]==='node_modules') return false;
    if (entry==='node_modules/better-sqlite3' && local[0]==='build') {
      return process.platform==='win32' && process.arch==='x64' && ['build','build/Release','build/Release/better_sqlite3.node'].includes(local.join('/'));
    }
    if (entry==='node_modules/better-sqlite3' && local[0]==='prebuilds' && local.length>1) return local[1]==='win32-x64.node';
    return true;
  }});
}
for (const file of ['dist','package.json','server.ts','src/server','src/shared','scripts/start.ts',
  'scripts/install.mjs','scripts/database-maintenance.mjs','scripts/desktop.mjs','scripts/support-archive.mjs','scripts/windows/control.mjs','scripts/windows/lifecycle.mjs','OPERATOR-GUIDE.md']) copy(file);
for (const [source, destination] of Object.entries({
  'install.cmd':'INSTALL.cmd', 'uninstall.cmd':'UNINSTALL.cmd', 'run.cmd':'RUN-POS.cmd', 'stop.cmd':'STOP-POS.cmd', 'support.cmd':'SUPPORT.cmd',
  'backup.cmd':'BACKUP.cmd', 'recover-copy.cmd':'RECOVERY-COPY.cmd', 'restore.cmd':'RESTORE-POS.cmd'
})) {
  const contents = fs.readFileSync(path.join(root,'scripts','windows',source),'utf8');
  fs.writeFileSync(path.join(target,destination),contents.replace(/\r?\n/g,'\r\n'),{flag:'wx'});
}
copy('node.exe','runtime/node.exe',runtime);
copy('LICENSE','runtime/LICENSE',runtime);
const sqliteRoot = path.join(target,'node_modules','better-sqlite3');
if (!['prebuilds/win32-x64.node','build/Release/better_sqlite3.node'].some(file=>fs.existsSync(path.join(sqliteRoot,file)))) throw Error('Missing Windows x64 SQLite native binary');
const files = Object.fromEntries(fs.readdirSync(target,{recursive:true,withFileTypes:true})
  .filter(entry=>entry.isFile())
  .map(entry=>path.join(entry.parentPath,entry.name))
  .map(file=>[path.relative(target,file).split(path.sep).join('/'),createHash('sha256').update(fs.readFileSync(file)).digest('hex')])
  .sort(([a],[b])=>a.localeCompare(b)));
fs.writeFileSync(path.join(target,'RELEASE-MANIFEST.json'),JSON.stringify({version:lock.version,files},null,2)+'\n',{flag:'wx'});
