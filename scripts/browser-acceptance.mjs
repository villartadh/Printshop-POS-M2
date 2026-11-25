import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {randomUUID} from 'node:crypto';
import {spawn} from 'node:child_process';
import {setTimeout as delay} from 'node:timers/promises';
import {chromium} from 'playwright';
import {acceptanceEnv, assertInstance, freePort} from './acceptance.mjs';

const root = path.resolve(import.meta.dirname, '..');
const sandbox = await fs.mkdtemp(path.join(os.tmpdir(), 'printshop-pos-browser-'));
const evidence = path.join(root,'.ldc','browser-'+randomUUID());
const port = await freePort();
const instance = randomUUID();
const origin = 'http://127.0.0.1:'+port;
const password = randomUUID();
const checks = [];
let server, browser, session;
const check = async (name, action) => {
  try {
    await action();
    checks.push({name,status:'PASS'});
    console.log('PASS',name);
  } catch (error) {
    checks.push({name,status:'FAIL',error:error.message});
    throw error;
  }
};
try {
  await fs.mkdir(evidence,{recursive:true});
  await fs.cp(path.join(root,'dist'),path.join(sandbox,'dist'),{recursive:true});
  server = spawn(process.execPath,['--import',import.meta.resolve('tsx'),path.join(root,'scripts/start.ts')],
    {cwd:sandbox,env:acceptanceEnv(port,instance),stdio:'ignore'});
  let health;
  for (let i=0; i<50 && server.exitCode===null; i++) {
    try {health=await (await fetch(origin+'/api/health')).json();break;} catch {await delay(100);}
  }
  assertInstance(health,instance);
  browser = await chromium.launch({executablePath:process.env.CHROMIUM_PATH || undefined});
  console.log('Chromium',browser.version());
  const context = await browser.newContext({viewport:{width:1280,height:720}});
  const page = await context.newPage();
  const runtimeErrors = [];
  page.on('pageerror',error=>runtimeErrors.push(error.message));
  page.setDefaultTimeout(6000);
  const button = (name) => page.getByRole('button',{name,exact:typeof name==='string'}).filter({visible:true});
  const click = (name) => button(name).first().click();
  const waitText = (text) => page.getByText(text,{exact:typeof text==='string'}).filter({visible:true}).first().waitFor();
  const signIn = async () => {
    await page.getByPlaceholder('Enter username').fill('browser-test');
    await page.getByPlaceholder('Enter password',{exact:true}).fill(password);
    const login=page.waitForResponse(r=>r.url().endsWith('/api/auth/login'));
    await click('Sign In');
    const response=await login;
    assert.equal(response.status(),200);
    session=await response.json();
  };
  const api = async (url, body, expected = body===undefined?200:201) => {
    const response = await context.request.fetch(origin+'/api'+url,{
      method:body===undefined?'GET':'POST', data:body,
      headers:{Authorization:'Bearer '+session.token}
    });
    assert.equal(response.status(),expected,url);
    return response.json();
  };
  await check('first-run administrator setup',async()=>{
    await page.goto(origin);
    await page.getByPlaceholder('e.g. Maria Santos').fill('Synthetic Browser');
    await page.getByPlaceholder('e.g. admin').fill('browser-test');
    await page.getByPlaceholder('Enter a strong password').fill(password);
    const setup = page.waitForResponse(r=>r.url().endsWith('/api/auth/setup-admin'));
    await click('Create Admin & Open POS');
    session = await (await setup).json();
    assert.ok(session.token);
  });
  await check('guided business setup saves authoritative profile',async()=>{
    await click('Configure business profile');
    const wizard=page.getByRole('region',{name:'Guided business setup'});
    await wizard.waitFor();
    assert.equal(await wizard.getByRole('button',{name:'Continue',exact:true}).isEnabled(),false);
    await wizard.getByLabel('Business name (required)',{exact:true}).fill('Synthetic Browser Shop');
    await wizard.getByLabel('Business address (required)',{exact:true}).fill('Synthetic test address');
    await wizard.getByRole('button',{name:'Continue',exact:true}).click();
    await wizard.getByLabel('Receipt paper size').selectOption('58mm');
    await wizard.getByRole('button',{name:'Continue',exact:true}).click();
    await wizard.getByLabel('GCash',{exact:true}).uncheck();
    await wizard.getByRole('button',{name:'Continue',exact:true}).click();
    await wizard.getByRole('button',{name:'Back',exact:true}).click();
    assert.equal(await wizard.getByLabel('GCash',{exact:true}).isChecked(),false);
    await wizard.getByRole('button',{name:'Continue',exact:true}).click();
    await page.screenshot({path:path.join(evidence,'business-setup.png'),animations:'disabled'});
    const save=page.waitForResponse(r=>r.url().endsWith('/api/settings')&&r.request().method()==='PUT');
    await wizard.getByRole('button',{name:'Save business setup'}).click();
    assert.equal((await save).status(),200);
    await wizard.waitFor({state:'hidden'});
    const {settings}=await api('/bootstrap');
    assert.equal(settings.branding.businessName,'Synthetic Browser Shop');
    assert.equal(settings.branding.address,'Synthetic test address');
    assert.equal(settings.receiptTemplate.defaultPaperSize,'58mm');
    assert.equal(settings.paymentMethods.find(m=>m.id==='gcash').enabled,false);
    assert.equal(await button('Configure business profile').count(),0);
  });
  await check('catalog fixture and authenticated first-run dashboard',async()=>{
    const category=await api('/categories',{name:'Browser Fixtures'});
    await api('/services',{name:'Synthetic Print',categoryId:category.id,pricingMethod:'fixed',basePrice:20,requiresProductionJob:true});
    await api('/customers',{name:'Synthetic Customer'});
    await api('/shifts/start',{openingCash:100});
    await page.reload();
    await signIn();
    await click('POS Terminal');
    await waitText('Synthetic Print');
  });
  await check('reservation-aware inventory notification center and read tracking',async()=>{
    const item=await api('/inventory',{name:'Synthetic Alert Paper',unit:'sheet',quantityOnHand:100,reservedQuantity:90,reorderLevel:50,minimumThreshold:20});
    assert.equal(item.minimumThreshold,20);
    await page.reload(); await signIn();
    await click('Inventory notifications');
    const dialog=page.getByRole('dialog').filter({has:page.getByText('Inventory Notification Center',{exact:true})});
    await dialog.waitFor();
    await dialog.getByText('Synthetic Alert Paper',{exact:true}).waitFor();
    assert.match(await dialog.innerText(),/Critical shortage/);
    assert.match(await dialog.innerText(),/Available: 10 sheet/);
    await dialog.getByRole('button',{name:'Mark all read'}).click();
    await dialog.getByText('1 active alerts · 0 unread',{exact:true}).waitFor();
    await page.screenshot({path:path.join(evidence,'inventory-notifications.png'),animations:'disabled'});
    await dialog.getByRole('button',{name:'Close',exact:true}).click();
    await page.reload(); await signIn();
    await click('Inventory notifications');
    await dialog.getByText('1 active alerts · 0 unread',{exact:true}).waitFor();
    await dialog.getByRole('button',{name:'Close',exact:true}).click();
    await click('POS Terminal');
  });
  await check('cart editing, CSS viewports and cash checkout',async()=>{
    await waitText('Synthetic Print');
    await page.getByText('Synthetic Print',{exact:true}).first().click();
    await click('Increase quantity');
    await page.getByText('Signed in as Synthetic Browser.',{exact:true}).waitFor({state:'hidden'});
    for (const [width,height] of [[390,844],[768,1024],[1280,720],[1920,1080]]) {
      await page.setViewportSize({width,height});
      await waitText(/Synthetic Print/);
      await (width===390 ? button(/View cart/) : button('Increase quantity')).first().waitFor();
      assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
      await page.screenshot({path:path.join(evidence,`pos-${width}x${height}.png`),animations:'disabled'});
    }
    await page.setViewportSize({width:1280,height:720});
    await click(/Receive payment/);
    await page.getByRole('spinbutton').filter({visible:true}).last().fill('50');
    await waitText('Change Due');
    await click('Complete Payment');
    await waitText('Document & Receipt Preview');
    await click('Close');
    await click('Receipt');
    await waitText('Document & Receipt Preview');
    const popupPromise=page.waitForEvent('popup');
    await click('Print / Save as PDF');
    const popup=await popupPromise;
    await popup.locator('#print-now').waitFor();
    assert.match(await popup.locator('body').innerText(), /OR-/);
    await popup.close();
    const download=page.waitForEvent('download');
    await click('Export File');
    assert.match((await download).suggestedFilename(),/\.html$/);
    await click('Close');
    const state=await api('/bootstrap');
    assert.equal(state.transactions[0].totalAmount,40);
    assert.equal(state.transactions[0].changeAmount,10);
  });
  await check('quotation recall, checkout error recovery and partial payment',async()=>{
    await page.getByText('Synthetic Print',{exact:true}).first().click();
    await page.locator('select').filter({has:page.getByRole('option',{name:'Synthetic Customer'})}).selectOption({label:'Synthetic Customer'});
    await click('Quote');
    await waitText('Document & Receipt Preview');
    await click('Close');
    await page.getByTitle('Recall held order (F10)').click();
    await click('Recall');
    await click(/Receive payment/);
    await page.getByRole('spinbutton').filter({visible:true}).last().fill('10');
    await click('Complete Payment');
    await waitText(/Amount received is short/);
    await page.getByText('Allow partial / down payment',{exact:true}).click();
    await click('Complete Payment');
    await waitText('Document & Receipt Preview');
    await click('Close');
    const state=await api('/bootstrap');
    assert.ok(state.transactions.some(t=>t.type==='sale'&&t.balanceDue===10&&t.paymentStatus==='partial'));
    assert.ok(state.transactions.some(t=>t.type==='quotation'&&t.convertedSaleId));
  });
  await check('offline sale, reconnect and replay deduplication',async()=>{
    await page.waitForFunction(async()=>Boolean((await navigator.serviceWorker.getRegistration())?.active));
    await context.setOffline(true);
    const offlineTab = await context.newPage();
    try {
      assert.equal((await offlineTab.goto(origin,{timeout:6000})).status(),200);
      await offlineTab.getByPlaceholder('Enter username').waitFor({timeout:6000});
      assert.equal(await offlineTab.getByText('Synthetic Print',{exact:true}).count(),0);
    } finally { await offlineTab.close(); }
    await page.getByText('Synthetic Print',{exact:true}).first().click();
    await click(/Receive payment/);
    await page.getByRole('spinbutton').filter({visible:true}).last().fill('20');
    await click('Complete Payment');
    await waitText('Document & Receipt Preview');
    await click('Close');
    const sync = page.waitForResponse(r=>r.url().endsWith('/api/sync/batch'),{timeout:22000});
    await context.setOffline(false);
    const response=await sync;
    assert.equal((await response.json()).results[0].status,'applied');
    const replay=await api('/sync/batch',response.request().postDataJSON(),200);
    assert.equal(replay.results[0].status,'duplicate');
  });
  await check('production stages and reports',async()=>{
    await click('Print Jobs');
    await waitText('Print Job Production');
    const state=await api('/bootstrap');
    const job=state.printJobs.find(j=>j.paymentStatus==='paid');
    await page.getByText(job.jobOrderNumber,{exact:true}).filter({visible:true}).first().click();
    const status=page.getByRole('dialog').getByRole('combobox');
    for(const stage of ['in_production','quality_checking','ready_for_pickup','completed']) {
      const update=page.waitForResponse(r=>r.url().endsWith('/api/print-jobs/'+job.id)&&r.request().method()==='PATCH');
      await status.selectOption(stage);
      assert.equal((await update).status(),200);
    }
    await click('Close');
    await click('Expenses & Reports');
    await waitText('Expenses, Profit & Reports');
    await waitText('Collected on Selected Sales');
    const revenue=await page.getByText('Total Revenue',{exact:true}).locator('..').innerText();
    assert.match(revenue,/80\.00/);
  });
  await check('historical receipt survives business identity change and reprint',async()=>{
    const original=await api('/bootstrap');
    const sale=original.transactions.find(t=>t.type==='sale'&&t.totalAmount===40);
    assert.ok(sale?.receiptSnapshot);
    assert.equal(sale.receiptSnapshot.branding.businessName,'Synthetic Browser Shop');
    const next={...original.settings,branding:{...original.settings.branding,businessName:'Renamed Synthetic Shop',address:'Changed synthetic address'}};
    const saved=await context.request.put(origin+'/api/settings',{data:next,headers:{Authorization:'Bearer '+session.token}});
    assert.equal(saved.status(),200);
    await page.reload();
    await signIn();
    await click('Dashboard');
    await page.getByText(sale.receiptNumber,{exact:true}).filter({visible:true}).first().click();
    await waitText('Document & Receipt Preview');
    const receipt=page.getByRole('dialog').locator('#printable-document');
    assert.match(await receipt.innerText(),/Synthetic Browser Shop/);
    assert.match(await receipt.innerText(),/Synthetic test address/);
    assert.doesNotMatch(await receipt.innerText(),/Renamed Synthetic Shop/);
    assert.match(await receipt.innerText(),/REPRINT \/ COPY/);
    const popupPromise=page.waitForEvent('popup');
    await click('Print / Save as PDF');
    const popup=await popupPromise;
    await popup.locator('#print-now').waitFor();
    assert.match(await popup.locator('main').innerText(),/Synthetic Browser Shop/);
    assert.doesNotMatch(await popup.locator('main').innerText(),/Renamed Synthetic Shop/);
    await popup.close();
    await page.screenshot({path:path.join(evidence,'historical-reprint.png'),animations:'disabled'});
    await click('Close');
  });
  await check('logout and password login',async()=>{
    await page.getByRole('button',{name:'Lock Terminal / Switch User',exact:true}).filter({visible:true}).first().click();
    await click('Sign Out Completely');
    await signIn();
    assert.deepEqual(runtimeErrors,[]);
  });
} catch (error) {
  console.error(error.message);
  const failedPage = browser?.contexts()[0]?.pages()[0];
  if (failedPage) await failedPage.screenshot({path:path.join(evidence,'failure.png')}).catch(()=>{});
  process.exitCode=1;
} finally {
  await browser?.close();
  if (server && server.exitCode===null && server.signalCode===null) {
    const exit=new Promise(resolve=>server.once('exit',resolve));
    server.kill();
    await exit;
  }
  await fs.rm(sandbox,{recursive:true,force:true});
  await fs.writeFile(path.join(evidence,'report.json'),JSON.stringify({status:process.exitCode?'FAIL':'PASS',checks},null,2));
  console.log('Browser evidence:',evidence);
}
