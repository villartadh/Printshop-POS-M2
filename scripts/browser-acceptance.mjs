import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {randomUUID} from 'node:crypto';
import {spawn} from 'node:child_process';
import {setTimeout as delay} from 'node:timers/promises';
import {chromium} from 'playwright';
import {acceptanceEnv, assertInstance, freePort} from './acceptance.mjs';
import {paperLayout} from '../src/shared/printDocument.ts';

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
  // Respect the real global credential throttle when a large suite creates many sessions.
  // Never bypass it, change its configuration, or retry invalid credentials.
  const authenticate = async (p,touch=false) => {
    await p.getByPlaceholder('Enter username').fill('browser-test');
    await p.getByPlaceholder('Enter password',{exact:true}).fill(password);
    for(let attempt=0;attempt<2;attempt++){
      const pending=p.waitForResponse(r=>r.url().endsWith('/api/auth/login')&&r.request().method()==='POST');
      const submit=p.getByRole('button',{name:'Sign In',exact:true});
      if(touch)await submit.tap();else await submit.click();
      const response=await pending;
      if(response.status()===429&&attempt===0){
        const seconds=Number(response.headers()['retry-after']);
        assert.ok(Number.isInteger(seconds)&&seconds>=1&&seconds<=60,'Valid credential throttle Retry-After');
        const message=await response.json();
        assert.match(message.error,/Too many authentication attempts/);
        console.log('Credential throttle enforced; respecting Retry-After ('+seconds+' seconds)');
        await delay((seconds+1)*1000);
        continue;
      }
      assert.equal(response.status(),200,'Password authentication');
      const result=await response.json();assert.ok(result.token);return result;
    }
    throw Error('Authentication remained throttled after Retry-After');
  };
  const signIn=async()=>{session=await authenticate(page);};
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
    const item=await api('/inventory',{name:'Synthetic Alert Paper',unit:'sheet',quantityOnHand:100,reservedQuantity:90,reorderLevel:50,minimumThreshold:20,unitCost:2,supplier:'Synthetic Paper Supplier',reorderQuantity:40});
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
    await page.evaluate(()=>localStorage.clear());
    await page.reload(); await signIn();
    await click('Inventory notifications');
    await dialog.getByText('1 active alerts · 0 unread',{exact:true}).waitFor();
    await dialog.getByRole('button',{name:'Close',exact:true}).click();
    await click('POS Terminal');
  });
  await check('supplier reorder draft quantity override and export',async()=>{
    await click('Inventory notifications');
    await click('Create Reorder Request');
    const dialog=page.getByRole('dialog').filter({has:page.getByText('Supplier Reorder Request',{exact:true})});
    await dialog.getByText('Synthetic Paper Supplier',{exact:true}).waitFor();
    const qty=dialog.getByLabel('Order quantity Synthetic Alert Paper',{exact:true});
    assert.equal(await qty.inputValue(),'40');
    await qty.fill('60');
    assert.match(await dialog.getByLabel('Estimated procurement total').innerText(),/120/);
    const dl=page.waitForEvent('download');
    await dialog.getByRole('button',{name:'Export CSV'}).click();
    const file=await dl;
    const csv=await fs.readFile(await file.path(),'utf8');
    assert.match(csv,/Synthetic Paper Supplier/);assert.match(csv,/Draft estimate/);
    const pop=page.waitForEvent('popup');
    await dialog.getByRole('button',{name:'Print Request'}).click();
    const popup=await pop;
    await popup.locator('#print-now').waitFor();
    assert.match(await popup.locator('main').innerText(),/NOT A PURCHASE ORDER/);
    assert.match(await popup.locator('main').innerText(),/60 sheet/);
    await popup.close();
    await page.screenshot({path:path.join(evidence,'supplier-reorder.png'),animations:'disabled'});
    await dialog.getByRole('button',{name:'Close',exact:true}).click();
    const state=await api('/bootstrap');
    assert.equal(state.inventory.find(i=>i.name==='Synthetic Alert Paper').quantityOnHand,100);
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
    // Exercise the real receipt preview, not a reconstructed test document.
    const receiptState=await api('/bootstrap');
    const recorded=receiptState.transactions[0];
    const custom=receiptState.settings.receiptTemplate.customWidthMm;
    const currency=receiptState.settings.taxAndCurrency.currencySymbol;
    const measurements=[];
    for(const format of ['58mm','80mm','A4','Letter','Custom']){
      await page.getByLabel('Paper Format',{exact:true}).selectOption(format);
      const popupPromise=page.waitForEvent('popup');
      await click('Print / Save as PDF');
      const popup=await popupPromise;
      try{
        await popup.locator('#print-now').waitFor();
        const main=popup.locator('main');
        const content=await main.innerText();
        assert.ok(content.includes(recorded.receiptNumber));
        assert.match(content,/NOT A TAX INVOICE/);
        for(const [label,amount] of [['TOTAL',40],['Amount Paid',40],['Change',10]]){
          const row=main.locator('div.flex').filter({has:popup.getByText(label,{exact:true})}).last();
          assert.ok((await row.innerText()).includes(currency+amount.toFixed(2)),label+' '+format);
        }
        const p=paperLayout(format,custom);
        const widthMm=format==='A4'?210:format==='Letter'?215.9:p.width+8;
        // Chromium virtual PDF: explicit roll width; physical roll length remains printer-dependent.
        await popup.setViewportSize({width:Math.ceil(widthMm*96/25.4),height:1100});
        await popup.emulateMedia({media:'print'});
        const m=await main.evaluate(el=>({width:el.getBoundingClientRect().width,scrollWidth:el.scrollWidth,clientWidth:el.clientWidth,height:el.scrollHeight}));
        assert.ok(Math.abs(m.width-p.width*96/25.4)<2,format+' content width');
        assert.ok(m.scrollWidth<=m.clientWidth+1,format+' receipt overflow');
        assert.equal(await popup.locator('aside').isVisible(),false);
        const heightMm=format==='A4'?297:format==='Letter'?279.4:Math.max(100,Math.ceil(m.height*25.4/96+16));
        const pdf=await popup.pdf({width:widthMm+'mm',height:heightMm+'mm',preferCSSPageSize:true,printBackground:true,path:path.join(evidence,'virtual-receipt-'+format+'.pdf')});
        assert.equal(pdf.subarray(0,5).toString(),'%PDF-');
        const boxes=[...pdf.toString('latin1').matchAll(/\/MediaBox\s*\[\s*0\s+0\s+([\d.]+)\s+([\d.]+)\s*\]/g)];
        assert.equal(boxes.length,1,format+' expected one page');
        const [actualWidth,actualHeight]=boxes[0].slice(1).map(Number);
        assert.ok(Math.abs(actualWidth-widthMm*72/25.4)<2,format+' PDF width');
        assert.ok(Math.abs(actualHeight-heightMm*72/25.4)<2,format+' PDF height');
        measurements.push({format,widthMm,heightMm,pdfWidthPt:actualWidth,pdfHeightPt:actualHeight,scope:'virtual Chromium PDF, not physical printer'});
      }finally{await popup.close();}
    }
    await fs.writeFile(path.join(evidence,'virtual-receipt-geometry.json'),JSON.stringify(measurements,null,2));
    await page.getByLabel('Paper Format',{exact:true}).selectOption('58mm');
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
    const tender=page.getByRole('dialog').getByLabel(/Amount Tendered/);
    await page.waitForFunction(()=>{const field=document.querySelector('input[id^="inp_amount_tendered"]');return field instanceof HTMLInputElement&&field.value==='20.00';});
    await tender.fill('10');
    assert.equal(await tender.inputValue(),'10');
    await click('Complete Payment');
    await page.getByRole('dialog').getByRole('alert').filter({hasText:'Amount received is short'}).waitFor();
    assert.equal((await api('/bootstrap')).transactions.filter(t=>t.type==='sale').length,1);
    await page.getByText('Allow partial / down payment',{exact:true}).click();
    await click('Complete Payment');
    await waitText('Document & Receipt Preview');
    await click('Close');
    const state=await api('/bootstrap');
    assert.ok(state.transactions.some(t=>t.type==='sale'&&t.balanceDue===10&&t.paymentStatus==='partial'));
    assert.ok(state.transactions.some(t=>t.type==='quotation'&&t.convertedSaleId));
  });
  await check('offline sale, conflict retry, lost acknowledgment and reload recovery',async()=>{
    const initial=await api('/bootstrap');
    const service=initial.services.find(s=>s.name==='Synthetic Print');
    assert.ok(service);
    const setPrice=async(basePrice)=>{
      const response=await context.request.put(origin+'/api/services/'+service.id,{data:{...service,basePrice},headers:{Authorization:'Bearer '+session.token}});
      assert.equal(response.status(),200);
    };
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
    // A real server-side price conflict: do not alter the recorded offline payment.
    await setPrice(21);
    await context.setOffline(false);
    await click('Offline Record Recovery');
    const recovery=page.getByRole('dialog').filter({has:page.getByText('Offline Record Recovery',{exact:true})});
    const conflictResponse=page.waitForResponse(r=>r.url().endsWith('/api/sync/batch'),{timeout:22000});
    await recovery.getByRole('button',{name:'Synchronize Pending'}).click();
    const rejected=await conflictResponse;
    const original=rejected.request().postDataJSON().mutations[0];
    assert.equal((await rejected.json()).results[0].status,'error');
    await recovery.getByText(/Price or quantity changed/).waitFor();
    assert.equal((await api('/bootstrap')).transactions.some(t=>t.clientMutationId===original.clientMutationId),false);
    await page.screenshot({path:path.join(evidence,'offline-price-conflict.png'),animations:'disabled'});
    await setPrice(20);
    // Server accepts the original mutation, but the browser receives an incomplete acknowledgment.
    let forwarded=null;
    await page.route('**/api/sync/batch',async route=>{
      if(forwarded){await route.abort();return;}
      const upstream=await route.fetch();
      const body=await upstream.json();
      forwarded={request:route.request().postDataJSON(),body};
      await route.fulfill({response:upstream,json:{...body,results:[]}});
    });
    await recovery.getByRole('button',{name:'Retry Original Record'}).click();
    await recovery.getByRole('button',{name:'Confirm Retry'}).click();
    await recovery.getByText('Synchronization could not be confirmed. Records remain queued for safe retry.',{exact:true}).waitFor({timeout:22000});
    assert.equal(forwarded.body.results[0].status,'applied');
    assert.deepEqual(forwarded.request.mutations[0].payload,original.payload);
    assert.equal(forwarded.request.mutations[0].clientMutationId,original.clientMutationId);
    const accepted=await api('/bootstrap');
    assert.equal(accepted.transactions.filter(t=>t.clientMutationId===original.clientMutationId).length,1);
    await page.screenshot({path:path.join(evidence,'offline-lost-ack.png'),animations:'disabled'});
    // Restart the browser page and recover the durable local record.
    await page.reload();await signIn();
    await click('Offline Record Recovery');
    await recovery.getByText('Mutation: '+original.clientMutationId,{exact:true}).waitFor();
    await page.unroute('**/api/sync/batch');
    const duplicateResponse=page.waitForResponse(r=>r.url().endsWith('/api/sync/batch'),{timeout:22000});
    await recovery.getByRole('button',{name:'Synchronize Pending'}).click();
    const replay=await duplicateResponse;
    assert.equal((await replay.json()).results[0].status,'duplicate');
    await recovery.getByText('No unresolved records for this operator.',{exact:true}).waitFor();
    const final=await api('/bootstrap');
    assert.equal(final.transactions.filter(t=>t.clientMutationId===original.clientMutationId).length,1);
    assert.deepEqual(final.shifts,accepted.shifts);
    assert.deepEqual(final.printJobs,accepted.printJobs);
    assert.deepEqual(final.stockMovements,accepted.stockMovements);
    await page.screenshot({path:path.join(evidence,'offline-recovered.png'),animations:'disabled'});
    await recovery.getByRole('button',{name:'Close',exact:true}).click();
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
  await check('inventory purchase history, archive and restore through operator interface',async()=>{
    const item=await api('/inventory',{name:'Synthetic Retired Material',unit:'sheet',quantityOnHand:0,unitCost:2});
    const adjustment=(body)=>api('/inventory/'+item.id+'/adjust',{clientMutationId:randomUUID(),...body},200);
    await adjustment({movementType:'purchase',quantityDelta:2,purchaseUnitCost:3,reference:'PO-BROWSER-1'});
    await adjustment({movementType:'purchase',quantityDelta:2,purchaseUnitCost:5,reference:'PO-BROWSER-2'});
    await adjustment({movementType:'wastage',quantityDelta:4});
    await page.reload(); await signIn();
    await click('Inventory Management');
    await page.getByText(item.name,{exact:true}).filter({visible:true}).first().click();
    const detail=page.getByRole('dialog').filter({has:page.getByText(item.name,{exact:true})});
    await detail.getByText('Recorded supplier purchase prices',{exact:true}).waitFor();
    await detail.getByText('Recorded purchases: 2',{exact:true}).waitFor();
    assert.match(await detail.innerText(),/PO-BROWSER-1/);
    assert.match(await detail.innerText(),/PO-BROWSER-2/);
    await detail.getByRole('img',{name:'Recorded supplier purchase price trend'}).waitFor();
    await page.screenshot({path:path.join(evidence,'material-purchase-history.png'),animations:'disabled'});
    await detail.getByRole('button',{name:'Close dialog'}).click();
    await click('Delete '+item.name);
    const discontinue=page.getByRole('dialog').filter({has:page.getByText('Discontinue Inventory Item',{exact:true})});
    await discontinue.getByLabel('Archive reason').fill('Synthetic discontinued supply');
    const archived=page.waitForResponse(r=>r.url().endsWith('/inventory/'+item.id+'/archive')&&r.request().method()==='POST');
    await discontinue.getByRole('button',{name:'Archive Item'}).click();
    assert.equal((await archived).status(),200);
    await discontinue.waitFor({state:'hidden'});
    await page.getByText(item.name,{exact:true}).filter({visible:true}).first().waitFor({state:'hidden'});
    await click('Archived Inventory');
    const archive=page.getByRole('dialog').filter({has:page.getByText('Archived Inventory',{exact:true})});
    await archive.getByText(item.name,{exact:true}).waitFor();
    await archive.getByRole('button',{name:'View History',exact:true}).click();
    await archive.getByText('Recorded purchases: 2',{exact:true}).waitFor();
    await page.screenshot({path:path.join(evidence,'archived-inventory.png'),animations:'disabled'});
    const restored=page.waitForResponse(r=>r.url().endsWith('/inventory/'+item.id+'/restore')&&r.request().method()==='POST');
    await archive.getByRole('button',{name:'Restore Item'}).click();
    assert.equal((await restored).status(),200);
    await archive.getByText('No archived inventory items.',{exact:true}).waitFor();
    await archive.getByRole('button',{name:'Close dialog'}).click();
    await page.getByText(item.name,{exact:true}).filter({visible:true}).first().waitFor();
  });
  await check('customer balance collection recovers original reference after incomplete acknowledgment',async()=>{
    const original=await api('/bootstrap');
    const customer=original.customers.find(c=>c.name==='Synthetic Customer');
    assert.equal(customer.outstandingBalance,10);
    await click('Customers');
    await page.getByText(customer.name,{exact:true}).filter({visible:true}).first().click();
    const detail=page.getByRole('dialog');
    await detail.getByLabel(/Collect Cash Balance Payment/).fill('10');
    let forwarded;
    await page.route('**/api/customers/*/pay-balance',async route=>{
      const upstream=await route.fetch();
      forwarded={request:route.request().postDataJSON(),response:await upstream.json()};
      await route.fulfill({response:upstream,json:{...forwarded.response,clientMutationId:'invalid-ack'}});
    });
    await detail.getByRole('button',{name:'Record Payment'}).click();
    await detail.getByText('Payment confirmation could not be verified. Retry the original reference.',{exact:true}).waitFor();
    assert.equal(forwarded.response.duplicate,false);
    assert.equal(forwarded.response.outstandingBalance,0);
    const accepted=await api('/bootstrap');
    await page.reload();await signIn();
    await click('Customers');
    await waitText('Unconfirmed balance payment');
    await waitText('Reference: '+forwarded.request.clientMutationId);
    await page.unroute('**/api/customers/*/pay-balance');
    const retried=page.waitForResponse(r=>r.url().endsWith('/pay-balance'));
    await click('Retry Original Balance Payment');
    const response=await retried;
    assert.equal((await response.json()).duplicate,true);
    assert.deepEqual(response.request().postDataJSON(),forwarded.request);
    await page.getByText('Unconfirmed balance payment',{exact:true}).waitFor({state:'hidden'});
    const final=await api('/bootstrap');
    assert.deepEqual(final.shifts,accepted.shifts);
    assert.deepEqual(final.transactions,accepted.transactions);
    assert.deepEqual(final.printJobs,accepted.printJobs);
    assert.equal(final.customers.find(c=>c.id===customer.id).outstandingBalance,0);
  });
  await check('drawer adjustment survives incomplete acknowledgment and reload',async()=>{
    const before=await api('/bootstrap');
    const shift=before.shifts.find(s=>s.status==='open');
    assert.ok(shift);
    await click('Cashier Shifts');
    await click('Cash In / Cash Out');
    const dialog=page.getByRole('dialog');
    await dialog.getByLabel('Movement Type').selectOption('cash_in');
    await dialog.getByLabel(/^Amount /).fill('0.25');
    await dialog.getByLabel('Reason',{exact:true}).fill('Synthetic drawer recovery');
    let forwarded;
    await page.route('**/api/shifts/*/movement',async route=>{
      const upstream=await route.fetch();
      forwarded={request:route.request().postDataJSON(),response:await upstream.json()};
      await route.fulfill({response:upstream,json:{...forwarded.response,clientMutationId:'invalid-ack'}});
    });
    await dialog.getByRole('button',{name:'Record',exact:true}).click();
    await dialog.getByText('Drawer confirmation could not be verified. Retry the original reference.',{exact:true}).waitFor();
    assert.equal(forwarded.response.duplicate,false);
    const accepted=await api('/bootstrap');
    assert.equal(accepted.shifts.find(s=>s.id===shift.id).expectedCash,Math.round((shift.expectedCash+0.25)*100)/100);
    await page.reload();await signIn();await click('Cashier Shifts');
    await waitText('Unconfirmed drawer adjustment');await waitText('Reference: '+forwarded.request.clientMutationId);
    assert.equal(await button('End & Reconcile Shift').isEnabled(),false);
    await page.unroute('**/api/shifts/*/movement');
    const retried=page.waitForResponse(r=>r.url().endsWith('/movement'));
    await click('Retry Original Drawer Adjustment');
    const response=await retried;assert.equal((await response.json()).duplicate,true);assert.deepEqual(response.request().postDataJSON(),forwarded.request);
    await page.getByText('Unconfirmed drawer adjustment',{exact:true}).waitFor({state:'hidden'});
    assert.deepEqual((await api('/bootstrap')).shifts,accepted.shifts);
  });
  await check('browser DPI simulation at 100, 125 and 150 percent',async()=>{
    const measurements=[];
    for(const scale of [1,1.25,1.5]){
      const viewport={width:Math.floor(1920/scale),height:Math.floor(1080/scale)};
      const scaled=await browser.newContext({viewport,deviceScaleFactor:scale});
      let p;
      try{
        p=await scaled.newPage();
        p.setDefaultTimeout(10000);
        await p.goto(origin);
        await authenticate(p);
        const pos=p.getByRole('button',{name:'POS Terminal',exact:true}).filter({visible:true}).first();
        await pos.waitFor();await pos.click();
        const service=p.getByText('Synthetic Print',{exact:true}).filter({visible:true}).first();
        await service.waitFor();await service.click();
        const payment=p.getByRole('button',{name:/Receive payment/}).filter({visible:true}).first();
        await payment.waitFor();
        const geometry=await p.evaluate(()=>({dpr:devicePixelRatio,width:innerWidth,height:innerHeight,scrollWidth:document.documentElement.scrollWidth}));
        assert.equal(geometry.dpr,scale);assert.equal(geometry.width,viewport.width);
        assert.ok(geometry.scrollWidth<=geometry.width+1);
        const box=await payment.boundingBox();assert.ok(box&&box.width>0&&box.height>=24);
        await payment.click();
        const dialog=p.getByRole('dialog').filter({has:p.getByText('Receive Payment',{exact:true})});
        await dialog.getByLabel(/Amount Tendered/).waitFor();
        assert.equal(await dialog.getByLabel(/Amount Tendered/).inputValue(),'20.00');
        const bounds=await dialog.boundingBox();assert.ok(bounds&&bounds.x>=-1&&bounds.y>=-1&&bounds.x+bounds.width<=viewport.width+1&&bounds.y+bounds.height<=viewport.height+1);
        await p.screenshot({path:path.join(evidence,'simulated-dpi-'+Math.round(scale*100)+'.png'),animations:'disabled'});
        measurements.push({scale:scale*100,...geometry,scope:'Chromium device scale simulation; not native Windows display setting'});
        await dialog.getByRole('button',{name:'Cancel',exact:true}).click();
      }catch(error){await p?.screenshot({path:path.join(evidence,'failed-simulated-dpi-'+Math.round(scale*100)+'.png')}).catch(()=>{});throw Error('DPR '+scale+': '+error.message);}finally{await scaled.close();}
    }
    await fs.writeFile(path.join(evidence,'simulated-dpi.json'),JSON.stringify(measurements,null,2));
  });
  await check('mobile touch checkout and offline synchronization with PWA readiness',async()=>{
    const mobile=await browser.newContext({viewport:{width:390,height:844},deviceScaleFactor:3,isMobile:true,hasTouch:true,userAgent:'Mozilla/5.0 (Linux; Android 14; Synthetic Test) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36'});
    let p;
    try{
      p=await mobile.newPage();p.setDefaultTimeout(10000);
      await p.goto(origin);
      await authenticate(p,true);
      const nav=p.getByRole('navigation',{name:'Mobile navigation'});
      await nav.getByRole('button',{name:'POS',exact:true}).tap();
      const service=p.getByText('Synthetic Print',{exact:true}).filter({visible:true}).first();
      await service.waitFor();
      assert.equal(await p.evaluate(()=>navigator.maxTouchPoints>0),true);
      const navSize=await nav.getByRole('button',{name:'POS',exact:true}).boundingBox();
      assert.ok(navSize&&navSize.height>=44);
      const manifest=await p.evaluate(async()=>{
        const link=document.querySelector('link[rel="manifest"]');
        if(!link)throw Error('Manifest missing');
        const response=await fetch(link.href);if(!response.ok)throw Error('Manifest unavailable');
        return response.json();
      });
      assert.equal(manifest.display,'standalone');assert.equal(manifest.start_url,'/');
      assert.ok(manifest.icons.some(i=>i.sizes==='192x192'));assert.ok(manifest.icons.some(i=>i.sizes==='512x512'));
      await p.waitForFunction(async()=>Boolean((await navigator.serviceWorker.getRegistration())?.active));
      const before=await api('/bootstrap');
      await mobile.setOffline(true);
      await p.getByRole('button',{name:'Offline Record Recovery',exact:true}).filter({visible:true}).first().waitFor();
      await service.tap();
      await p.getByRole('button',{name:/View cart/}).tap();
      const cart=p.getByRole('dialog').filter({has:p.getByText('Current Order',{exact:true})});
      await cart.getByRole('button',{name:/Receive payment/}).tap();
      const checkout=p.getByRole('dialog').filter({has:p.getByText('Receive Payment',{exact:true})});
      await checkout.getByLabel(/Amount Tendered/).waitFor();
      assert.equal(await checkout.getByLabel(/Amount Tendered/).inputValue(),'20.00');
      await p.screenshot({path:path.join(evidence,'mobile-touch-checkout.png'),animations:'disabled'});
      await checkout.getByRole('button',{name:'Complete Payment',exact:true}).tap();
      const receipt=p.getByRole('dialog').filter({has:p.getByText('Document & Receipt Preview',{exact:true})});
      await receipt.waitFor();
      assert.match(await receipt.locator('#printable-document').innerText(),/20\.00/);
      await receipt.getByRole('button',{name:'Close',exact:true}).tap();
      assert.equal((await api('/bootstrap')).transactions.length,before.transactions.length);
      await mobile.setOffline(false);
      const deadline=Date.now()+25000;
      let after;
      do{after=await api('/bootstrap');if(after.transactions.length===before.transactions.length+1)break;await delay(500);}while(Date.now()<deadline);
      assert.equal(after.transactions.length,before.transactions.length+1);
      const added=after.transactions.find(t=>!before.transactions.some(old=>old.id===t.id));
      assert.ok(added&&added.totalAmount===20&&added.paymentStatus==='paid');
      assert.equal(after.shifts.find(s=>s.status==='open').expectedCash,before.shifts.find(s=>s.status==='open').expectedCash+20);
      assert.ok(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));
      await p.screenshot({path:path.join(evidence,'mobile-touch-recovered.png'),animations:'disabled'});
      // Tablet portrait and landscape are geometry checks in the same touch context.
      for(const viewport of [{width:768,height:1024},{width:1024,height:768}]){
        await p.setViewportSize(viewport);
        await service.waitFor();
        assert.ok(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));
        await p.screenshot({path:path.join(evidence,'tablet-'+viewport.width+'x'+viewport.height+'.png'),animations:'disabled'});
      }
      await fs.writeFile(path.join(evidence,'mobile-acceptance.json'),JSON.stringify({scope:'Android Chromium touch emulation, not physical device or installed PWA',manifestDisplay:manifest.display,serviceWorker:true,touchCheckout:true,offlineSaleReconciled:true,tabletViewports:[[768,1024],[1024,768]]},null,2));
    }catch(error){await p?.screenshot({path:path.join(evidence,'failed-mobile-touch.png')}).catch(()=>{});throw error;}finally{await mobile.close();}
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
