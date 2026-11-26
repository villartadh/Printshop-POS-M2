package ph.printshop.pos;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

/** Exercises the real installed native activity, not a WebView or mock. */
public final class NativeQuoteSmoke extends Instrumentation {
    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        start();
    }
    @Override public void onStart() {
        Bundle evidence = new Bundle();
        Activity activity = null;
        int result = Activity.RESULT_CANCELED;
        String phase = "start quote Activity";
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName(getTargetContext().getPackageName(), MainActivity.class.getName());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity = startActivitySync(intent);
            final Activity screen = activity;
            runOnMainChecked(() -> {
                EditText price = screen.findViewById(R.id.quote_price);
                EditText quantity = screen.findViewById(R.id.quote_quantity);
                EditText received = screen.findViewById(R.id.quote_received);
                require(price != null && quantity != null && received != null, "native input widgets missing");
                View root = screen.getWindow().getDecorView();
                require(hasText(root, "PREVIEW ONLY — NOT A SALE OR TAX DOCUMENT"), "preview warning missing");
                price.setText("1.99");
                quantity.setText("3");
                received.setText("10.00");
                require(hasText(root, "Total: PHP 5.97"), "total mismatch");
                require(hasText(root, "Change: PHP 4.03"), "change mismatch");
                received.setText("5.96");
                require(hasText(root, "Insufficient cash."), "underpayment not rejected");
                price.setText("1.999");
                require(hasText(root, "Total — enter valid price and quantity"), "fractional centavo accepted");
                require(hasText(root, "Change —"), "stale change");
                price.setText("0.01");
                quantity.setText("1");
                received.setText("0.01");
                require(hasText(root, "Total: PHP 0.01"), "centavo total mismatch");
                require(hasText(root, "Change: PHP 0.00"), "exact tender mismatch");
            });
            phase = "catalog store"; verifyCatalog();
            phase = "catalog screen"; verifyCatalogScreen(screen);
            phase = "stock ledger"; verifyStockLedger();
            phase = "stock screen"; verifyStockScreen();
            phase = "durable stock recovery"; verifyStockRecovery();
            phase = "durable stock screen"; verifyStockRecoveryScreen();
            phase = "checkout read-only confirmation plan"; verifyCheckoutConfirmationPlan();
            phase = "material costing"; verifyMaterials();
            phase = "recipe and quote screen"; verifyRecipeScreen(screen);
            phase = "pricing store migration"; verifyPricingStore();
            phase = "pricing settings and document quote"; verifyPricingScreen(screen);
            phase = "measured quote"; verifyMeasuredQuote(screen);
            evidence.putString("stream", "NATIVE_QUOTE_SMOKE_PASS\nNATIVE_CATALOG_STORE_PASS\nNATIVE_CATALOG_SCREEN_PASS\nNATIVE_STOCK_LEDGER_PASS\nNATIVE_STOCK_SCREEN_PASS\nNATIVE_STOCK_RECOVERY_PASS\nNATIVE_CHECKOUT_PLAN_PASS\nNATIVE_MATERIAL_COST_PASS\nNATIVE_RECIPE_SCREEN_PASS\nNATIVE_PRICING_STORE_PASS\nNATIVE_PRICING_SETTINGS_PASS\nNATIVE_ADVANCED_QUOTE_PASS\n");
            result = Activity.RESULT_OK;
        } catch (Throwable error) {
            evidence.putString("stream", failureDetails(phase, error));
        } finally {
            if (activity != null) {
                final Activity screen = activity;
                try { runOnMainChecked(screen::finish); }
                catch (Throwable cleanup) {
                    evidence.putString("stream", evidence.getString("stream", "") + failureDetails("Activity cleanup", cleanup));
                    result = Activity.RESULT_CANCELED;
                }
            }
            finish(result, evidence);
        }
    }
    /** Preserve exceptions from the UI thread without terminating instrumentation. */
    private void runOnMainChecked(Runnable operation) {
        java.util.concurrent.atomic.AtomicReference<Throwable> fault = new java.util.concurrent.atomic.AtomicReference<>();
        runOnMainSync(() -> {
            try { operation.run(); }
            catch (Throwable error) { fault.set(error); }
        });
        if (fault.get() != null) throw new AssertionError("UI-thread verification failed", fault.get());
    }
    private static String failureDetails(String phase, Throwable error) {
        java.io.StringWriter trace = new java.io.StringWriter();
        error.printStackTrace(new java.io.PrintWriter(trace));
        return "NATIVE_QUOTE_SMOKE_FAIL: " + phase + "\n" + trace + "\n";
    }
    /** Activity results cross the system lifecycle; an idle UI queue alone is not delivery proof. */
    private void awaitServiceSelection(Activity screen,String expectedPrice,String serviceName) {
        long deadline=android.os.SystemClock.elapsedRealtime()+10000;
        java.util.concurrent.atomic.AtomicBoolean delivered=new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicReference<String> actual=new java.util.concurrent.atomic.AtomicReference<>("");
        do {
            waitForIdleSync();
            runOnMainChecked(()->{
                EditText price=screen.findViewById(R.id.quote_price);
                actual.set(price.getText().toString());
                delivered.set(expectedPrice.equals(actual.get())&&hasText(screen.getWindow().getDecorView(),
                    "Linked service: "+serviceName+" (editable selling price)"));
            });
            if(delivered.get())return;
            android.os.SystemClock.sleep(50);
        }while(android.os.SystemClock.elapsedRealtime()<deadline);
        throw new AssertionError("Service activity result not delivered: expected "+expectedPrice+", actual "+actual.get());
    }
    private void verifyRecipeScreen(Activity screen) {
        android.content.Context context = getTargetContext();
        try (CatalogStore store = new CatalogStore(context)) {
            CatalogEntry service = store.create(CatalogEntry.Kind.SERVICE, "Synthetic recipe service", "page", "5.00");
            CatalogEntry paper = store.create(CatalogEntry.Kind.MATERIAL, "Synthetic recipe paper", "sheet", "1.99");
            store.moveStock(java.util.UUID.randomUUID().toString(), paper.id, paper.revision, 0, 1500, "Synthetic stock");
            waitForIdleSync();
            ActivityMonitor cm = addMonitor(CatalogActivity.class.getName(), null, false);
            Activity catalog = null;
            Activity recipe = null;
            ActivityMonitor rm = addMonitor(RecipeActivity.class.getName(), null, false);
            try {
                runOnMainChecked(() -> screen.findViewById(R.id.open_catalog).performClick());
                catalog = waitForMonitorWithTimeout(cm, 10000);
                require(catalog != null, "recipe catalog did not open");
                final Activity shown = catalog;
                runOnMainChecked(() -> {
                    View configure = findText(shown.getWindow().getDecorView(), "Configure service materials");
                    require(configure != null && configure.performClick(), "recipe configuration action unavailable");
                });
                recipe = waitForMonitorWithTimeout(rm, 10000);
                require(recipe != null, "recipe Activity did not open");
                final Activity editor = recipe;
                waitForIdleSync();
                runOnMainChecked(() -> {
                    View root = editor.getWindow().getDecorView();
                    require(hasText(root, "No materials configured."), "empty recipe not distinguished");
                    EditText usage = editor.findViewById(R.id.recipe_usage);
                    usage.setText("0.5");
                    require(editor.findViewById(R.id.recipe_save).performClick(), "recipe save unavailable");
                    require(hasText(root, "Synthetic recipe paper · 0.5 sheet / page"), "configured material missing");
                    require(hasText(root, "Estimated cost per unit: PHP 1.00 · Stock sufficient"), "per-unit estimate wrong");
                });
                waitForIdleSync();
                require(store.recipeBindings(service.id).size() == 1, "recipe not persisted");
                runOnMainChecked(editor::finish);
                waitForIdleSync();
                runOnMainChecked(() -> {
                    View select = findText(shown.getWindow().getDecorView(), "Use in quotation");
                    require(select != null && select.performClick(), "configured service selection failed");
                });
                awaitServiceSelection(screen,"5.00",service.name);
                runOnMainChecked(() -> {
                    View root = screen.getWindow().getDecorView();
                    EditText price = screen.findViewById(R.id.quote_price);
                    EditText qty = screen.findViewById(R.id.quote_quantity);
                    require(price.getText().toString().equals("5.00"), "configured selling price not copied");
                    qty.setText("3");
                    require(hasText(root, "Total: PHP 15.00"), "quote selling total wrong");
                    require(hasText(root, "Material cost: PHP 2.99 · Stock sufficient"), "quote material estimate wrong");
                    qty.setText("4");
                    require(hasText(root, "Material cost: PHP 3.98 · Insufficient stock"), "shortage warning missing");
                    price.setText("6.00");
                    require(hasText(root, "Total: PHP 24.00"), "manual selling price lost");
                    require(hasText(root, "Material cost: PHP 3.98 · Insufficient stock"), "manual selling price altered cost");
                    View clear = findText(root, "Clear service link");
                    require(clear != null && clear.performClick(), "clear service link unavailable");
                    require(hasText(root, "Material cost — select a service"), "stale cost after clearing");
                    require(hasText(root, "Total: PHP 24.00"), "clear link changed sale estimate");
                });
                require(store.stockBalance(paper.id).quantity == 1500, "quote consumed inventory");
                require(store.get(service.id).centavos == 500, "quote changed catalog selling price");
            } finally {
                removeMonitor(cm); removeMonitor(rm);
                if (recipe != null) { final Activity editor = recipe; runOnMainChecked(editor::finish); }
                if (catalog != null) { final Activity shown = catalog; runOnMainChecked(shown::finish); }
                if (store.recipeRevision(service.id) != 0 && !store.recipeBindings(service.id).isEmpty())
                    store.setMaterialUsage(service.id, service.revision, paper.id, paper.revision, store.recipeRevision(service.id), 0);
                if (store.stockBalance(paper.id).quantity == 1500)
                    store.moveStock(java.util.UUID.randomUUID().toString(), paper.id, paper.revision, 1, -1500, "Synthetic cleanup");
                store.archive(service.id, service.revision, true);
                if (store.stockBalance(paper.id).quantity == 0) store.archive(paper.id, paper.revision, true);
            }
        }
    }
    private void verifyMaterials() {
        android.content.Context context=getTargetContext();
        String filename="recipe-migration-"+java.util.UUID.randomUUID()+".db";
        String service=java.util.UUID.randomUUID().toString(), material=java.util.UUID.randomUUID().toString();
        try {
            try(android.database.sqlite.SQLiteDatabase db=context.openOrCreateDatabase(filename,0,null)) {
        db.execSQL("CREATE TABLE catalog (id TEXT PRIMARY KEY NOT NULL, kind TEXT NOT NULL CHECK(kind IN ('SERVICE','MATERIAL')), name TEXT NOT NULL, unit TEXT NOT NULL, centavos INTEGER NOT NULL CHECK(centavos BETWEEN 0 AND 99999999999), revision INTEGER NOT NULL CHECK(revision >= 1), archived INTEGER NOT NULL CHECK(archived IN (0,1)))");
        db.execSQL("CREATE TABLE catalog_history (id TEXT NOT NULL REFERENCES catalog(id), revision INTEGER NOT NULL, kind TEXT NOT NULL, name TEXT NOT NULL, unit TEXT NOT NULL, centavos INTEGER NOT NULL, archived INTEGER NOT NULL, action TEXT NOT NULL CHECK(action IN ('CREATE','EDIT','ARCHIVE','RESTORE')), PRIMARY KEY(id,revision))");
        db.execSQL("CREATE TABLE stock_movements (request_id TEXT PRIMARY KEY NOT NULL, material_id TEXT NOT NULL REFERENCES catalog(id), catalog_revision INTEGER NOT NULL CHECK(catalog_revision>=1), stock_revision INTEGER NOT NULL CHECK(stock_revision>=1), unit TEXT NOT NULL, reason TEXT NOT NULL, delta INTEGER NOT NULL CHECK(delta BETWEEN -999999999999 AND 999999999999 AND delta<>0), before_quantity INTEGER NOT NULL CHECK(before_quantity BETWEEN 0 AND 999999999999), after_quantity INTEGER NOT NULL CHECK(after_quantity BETWEEN 0 AND 999999999999 AND after_quantity=before_quantity+delta), created_ms INTEGER NOT NULL, UNIQUE(material_id,stock_revision))");
                db.execSQL("INSERT INTO catalog VALUES(?,?,?,?,?,?,?)",new Object[]{service,"SERVICE","Synthetic print","page",500,1,0});
                db.execSQL("INSERT INTO catalog VALUES(?,?,?,?,?,?,?)",new Object[]{material,"MATERIAL","Synthetic paper","sheet",199,1,0});
                db.execSQL("INSERT INTO catalog_history VALUES(?,?,?,?,?,?,?,?)",new Object[]{service,1,"SERVICE","Synthetic print","page",500,0,"CREATE"});
                db.execSQL("INSERT INTO catalog_history VALUES(?,?,?,?,?,?,?,?)",new Object[]{material,1,"MATERIAL","Synthetic paper","sheet",199,0,"CREATE"});
                db.setVersion(2);
            }
            try(CatalogStore store=new CatalogStore(context,filename)) {
                require(store.get(service).centavos==500&&store.recipeRevision(service)==0,"v2 migration changed service");
                require(store.setMaterialUsage(service,1,material,1,0,500)==1,"recipe revision wrong");
                rejectStock(()->store.archive(material,1,true));
                rejectStock(()->store.edit(material,1,"Synthetic paper","ream","1.99"));
                MaterialCost.Estimate first=store.estimateMaterials(service,3);
                require(first.costCentavos==299&&!first.stockSufficient,"unstocked cost estimate wrong");
                store.moveStock(java.util.UUID.randomUUID().toString(),material,1,0,1500,"Synthetic stock");
                require(store.estimateMaterials(service,3).stockSufficient,"exact stock availability wrong");
                store.edit(material,1,"Synthetic paper","sheet","2.99");
                require(store.estimateMaterials(service,3).costCentavos==449,"current material cost not reflected");
                require(store.get(service).centavos==500&&store.stockBalance(material).quantity==1500,"estimate mutated sale price or stock");
                rejectStock(()->store.setMaterialUsage(service,1,material,2,0,1000));
                require(store.setMaterialUsage(service,1,material,2,1,0)==2,"recipe removal wrong");
                require(store.estimateMaterials(service,3).costCentavos==0,"removed recipe still charged");
            }
            try(CatalogStore reopened=new CatalogStore(context,filename)) {
                require(reopened.recipeRevision(service)==2&&reopened.stockBalance(material).quantity==1500,"recipe/stock persistence wrong");
                try(android.database.Cursor c=reopened.getReadableDatabase().rawQuery("SELECT count(*) FROM recipe_history WHERE service_id=?",new String[]{service})){
                    require(c.moveToFirst()&&c.getInt(0)==2,"recipe history altered");
                }
            }
        } finally {context.deleteDatabase(filename);}
    }
    private void verifyStockScreen() {
        android.content.Context context=getTargetContext();
        try(CatalogStore store=new CatalogStore(context)) {
            CatalogEntry material=store.create(CatalogEntry.Kind.MATERIAL,"Synthetic inventory","sheet","1.20");
            Intent intent=new Intent(Intent.ACTION_MAIN);
            intent.setClassName(context.getPackageName(),StockActivity.class.getName());
            intent.putExtra("materialId",material.id);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Activity stock=startActivitySync(intent);
            try {
                runOnMainChecked(()->{
                    View root=stock.getWindow().getDecorView();
                    EditText qty=stock.findViewById(R.id.stock_quantity);
                    EditText reason=stock.findViewById(R.id.stock_reason);
                    require(hasText(root,"On hand: 0 sheet"),"initial screen balance missing");
                    qty.setText("2.500");reason.setText("Synthetic receipt");
                    require(stock.findViewById(R.id.stock_receive).performClick(),"receipt action unavailable");
                    require(hasText(root,"On hand: 2.5 sheet"),"receipt screen balance wrong");
                    require(hasText(root,"#1 +2.5 sheet · Synthetic receipt"),"receipt history absent");
                    qty.setText("3");reason.setText("Invalid consumption");
                    stock.findViewById(R.id.stock_consume).performClick();
                    require(hasText(root,"On hand: 2.5 sheet"),"overdraw changed balance");
                    qty.setText("2.5");reason.setText("Synthetic usage");
                    stock.findViewById(R.id.stock_consume).performClick();
                    require(hasText(root,"On hand: 0 sheet"),"consumption screen balance wrong");
                    require(hasText(root,"#2 -2.5 sheet · Synthetic usage"),"consumption history absent");
                    require(stock.findViewById(R.id.stock_older).getVisibility()==View.GONE,"pagination rendered without overflow");
                });
                require(store.stockBalance(material.id).revision==2,"screen operation count wrong");
                java.util.List<CatalogStore.StockMovement> newest=store.stockHistory(material.id,0,1);
                require(newest.size()==1&&newest.get(0).revision==2,"history newest page wrong");
                java.util.List<CatalogStore.StockMovement> older=store.stockHistory(material.id,2,1);
                require(older.size()==1&&older.get(0).revision==1,"history older page wrong");
            } finally {
                runOnMainChecked(stock::finish);
                if(store.stockBalance(material.id).quantity==0)store.archive(material.id,material.revision,true);
            }
        }
    }
    private static String stockId(){return java.util.UUID.randomUUID().toString();}
    private void verifyStockRecovery() {
        android.content.Context context=getTargetContext();
        String filename="stock-recovery-"+stockId()+".db";
        String material=stockId(),service=stockId(),legacy=stockId(),pending=stockId(),cancelled=stockId();
        try{
            // Exact existing v4 tables, populated with history, before additive v5 migration.
            try(android.database.sqlite.SQLiteDatabase db=context.openOrCreateDatabase(filename,0,null)){
                db.execSQL("CREATE TABLE catalog (id TEXT PRIMARY KEY NOT NULL, kind TEXT NOT NULL CHECK(kind IN ('SERVICE','MATERIAL')), name TEXT NOT NULL, unit TEXT NOT NULL, centavos INTEGER NOT NULL CHECK(centavos BETWEEN 0 AND 99999999999), revision INTEGER NOT NULL CHECK(revision >= 1), archived INTEGER NOT NULL CHECK(archived IN (0,1)))");
                db.execSQL("CREATE TABLE catalog_history (id TEXT NOT NULL REFERENCES catalog(id), revision INTEGER NOT NULL, kind TEXT NOT NULL, name TEXT NOT NULL, unit TEXT NOT NULL, centavos INTEGER NOT NULL, archived INTEGER NOT NULL, action TEXT NOT NULL CHECK(action IN ('CREATE','EDIT','ARCHIVE','RESTORE')), PRIMARY KEY(id,revision))");
                db.execSQL("CREATE TABLE stock_movements (request_id TEXT PRIMARY KEY NOT NULL, material_id TEXT NOT NULL REFERENCES catalog(id), catalog_revision INTEGER NOT NULL CHECK(catalog_revision>=1), stock_revision INTEGER NOT NULL CHECK(stock_revision>=1), unit TEXT NOT NULL, reason TEXT NOT NULL, delta INTEGER NOT NULL CHECK(delta BETWEEN -999999999999 AND 999999999999 AND delta<>0), before_quantity INTEGER NOT NULL CHECK(before_quantity BETWEEN 0 AND 999999999999), after_quantity INTEGER NOT NULL CHECK(after_quantity BETWEEN 0 AND 999999999999 AND after_quantity=before_quantity+delta), created_ms INTEGER NOT NULL, UNIQUE(material_id,stock_revision))");
                db.execSQL("CREATE TABLE service_materials (service_id TEXT NOT NULL REFERENCES catalog(id), material_id TEXT NOT NULL REFERENCES catalog(id), quantity_milli INTEGER NOT NULL CHECK(quantity_milli BETWEEN 1 AND 999999999999), unit TEXT NOT NULL, PRIMARY KEY(service_id,material_id))");
                db.execSQL("CREATE TABLE recipe_history (service_id TEXT NOT NULL REFERENCES catalog(id), revision INTEGER NOT NULL CHECK(revision>=1), material_id TEXT NOT NULL REFERENCES catalog(id), quantity_milli INTEGER NOT NULL CHECK(quantity_milli BETWEEN 0 AND 999999999999), unit TEXT NOT NULL, material_revision INTEGER NOT NULL, service_revision INTEGER NOT NULL, created_ms INTEGER NOT NULL, PRIMARY KEY(service_id,revision))");
                db.execSQL("CREATE TABLE pricing_profile_versions (service_id TEXT NOT NULL REFERENCES catalog(id), revision INTEGER NOT NULL CHECK(revision>=1), method TEXT NOT NULL CHECK(method IN ('FIXED','PER_UNIT','PER_PAGE','PER_COPY','PER_SHEET','PER_SQFT','PER_LINEAR_FT','PER_METER','TIERED_BULK')), pages_copies INTEGER NOT NULL CHECK(pages_copies IN (0,1) AND (pages_copies=0 OR method IN ('PER_PAGE','PER_COPY','PER_SHEET'))), service_revision INTEGER NOT NULL CHECK(service_revision>=1), catalog_unit TEXT NOT NULL, created_ms INTEGER NOT NULL, PRIMARY KEY(service_id,revision))");
                db.execSQL("CREATE TABLE pricing_tier_versions (service_id TEXT NOT NULL, profile_revision INTEGER NOT NULL, threshold_hundredths INTEGER NOT NULL CHECK(threshold_hundredths BETWEEN 1 AND 10000000), rate_centavos INTEGER NOT NULL CHECK(rate_centavos BETWEEN 0 AND 99999999999), PRIMARY KEY(service_id,profile_revision,threshold_hundredths), FOREIGN KEY(service_id,profile_revision) REFERENCES pricing_profile_versions(service_id,revision))");
                db.execSQL("INSERT INTO catalog VALUES(?,?,?,?,?,?,?)",new Object[]{material,"MATERIAL","Synthetic recovery paper","sheet",199,1,0});
                db.execSQL("INSERT INTO catalog_history VALUES(?,?,?,?,?,?,?,?)",new Object[]{material,1,"MATERIAL","Synthetic recovery paper","sheet",199,0,"CREATE"});
                db.execSQL("INSERT INTO catalog VALUES(?,?,?,?,?,?,?)",new Object[]{service,"SERVICE","Synthetic recovery print","page",300,1,0});
                db.execSQL("INSERT INTO catalog_history VALUES(?,?,?,?,?,?,?,?)",new Object[]{service,1,"SERVICE","Synthetic recovery print","page",300,0,"CREATE"});
                db.execSQL("INSERT INTO stock_movements VALUES(?,?,?,?,?,?,?,?,?,?)",new Object[]{legacy,material,1,1,"sheet","Legacy receipt",1500,0,1500,1});
                db.execSQL("INSERT INTO service_materials VALUES(?,?,?,?)",new Object[]{service,material,500,"sheet"});
                db.execSQL("INSERT INTO recipe_history VALUES(?,?,?,?,?,?,?,?)",new Object[]{service,1,material,500,"sheet",1,1,1});
                db.execSQL("INSERT INTO pricing_profile_versions VALUES(?,?,?,?,?,?,?)",new Object[]{service,1,"PER_PAGE",1,1,"page",1});
                db.execSQL("INSERT INTO pricing_tier_versions VALUES(?,?,?,?)",new Object[]{service,1,1000,250});
                db.setVersion(4);
            }
            try(CatalogStore store=new CatalogStore(context,filename)){
                require(store.getReadableDatabase().getVersion()==5,"v4-to-v5 migration version wrong");
                require(store.stockBalance(material).quantity==1500&&store.stockHistory(material,0,5).size()==1,"v4 stock lost");
                require(store.recipeBindings(service).get(0).quantityMilli==500&&store.pricing(service).tiers.get(0).rateCentavos==250,"v4 recipe/pricing lost");
                require(store.pendingStock(material)==null,"unexpected migrated pending request");
                require(store.stageStock(pending,material,1,1,-500,"Synthetic pending issue").state.equals("PENDING"),"stage wrong");
                require(store.stageStock(pending,material,1,1,-500,"Synthetic pending issue").id.equals(pending),"stage replay wrong");
                rejectStock(()->store.stageStock(pending,material,1,1,-501,"Synthetic pending issue"));
                rejectStock(()->store.stageStock(stockId(),material,1,1,100,"Competing request"));
                rejectStock(()->store.moveStock(stockId(),material,1,1,100,"Bypass"));
                rejectStock(()->store.moveStock(pending,material,1,1,-500,"Synthetic pending issue"));
                rejectStock(()->store.edit(material,1,"Changed","sheet","1.99"));
                rejectStock(()->store.archive(material,1,true));
                require(store.stockBalance(material).revision==1,"staging changed ledger");
            }
            try(CatalogStore reopened=new CatalogStore(context,filename)){
                CatalogStore.StockRequest recovered=reopened.pendingStock(material);
                require(recovered!=null&&recovered.id.equals(pending)&&recovered.delta==-500&&recovered.stockRevision==1&&recovered.catalogRevision==1&&recovered.reason.equals("Synthetic pending issue"),"durable request payload lost");
                // Simulate a database failure precisely between ledger insertion and resolution.
                reopened.getWritableDatabase().execSQL("CREATE TRIGGER test_fail_resolution BEFORE UPDATE ON stock_requests BEGIN SELECT RAISE(ABORT,'synthetic interrupted commit'); END");
                boolean failed=false;
                try{reopened.applyStagedStock(pending);}catch(android.database.sqlite.SQLiteException expected){failed=true;}
                require(failed&&reopened.stockBalance(material).quantity==1500&&reopened.pendingStock(material)!=null,"transaction rollback lost request or changed ledger");
                reopened.getWritableDatabase().execSQL("DROP TRIGGER test_fail_resolution");
                require(reopened.applyStagedStock(pending).quantity==1000,"recovered adjustment wrong");
                require(reopened.applyStagedStock(pending).revision==2,"applied historical replay wrong");
                require(reopened.pendingStock(material)==null&&reopened.stockRequest(pending).state.equals("APPLIED"),"applied state not persisted");
                rejectStock(()->reopened.cancelStagedStock(pending));
                reopened.stageStock(cancelled,material,1,2,350,"Synthetic cancellation");
                reopened.cancelStagedStock(cancelled);reopened.cancelStagedStock(cancelled);
                require(reopened.stockRequest(cancelled).state.equals("CANCELLED")&&reopened.pendingStock(material)==null,"cancellation state wrong");
                rejectStock(()->reopened.applyStagedStock(cancelled));
                rejectStock(()->reopened.moveStock(cancelled,material,1,2,350,"Synthetic cancellation"));
                require(reopened.stageStock(cancelled,material,1,2,350,"Synthetic cancellation").state.equals("CANCELLED"),"cancelled identity revived");
                require(reopened.moveStock(legacy,material,1,0,1500,"Legacy receipt").revision==1,"legacy replay lost");
                require(reopened.stockBalance(material).quantity==1000&&reopened.stockHistory(material,0,10).size()==2,"duplicate or cancelled movement altered ledger");
            }
            try(CatalogStore reopened=new CatalogStore(context,filename)){
                require(reopened.stockRequest(pending).state.equals("APPLIED")&&reopened.stockRequest(cancelled).state.equals("CANCELLED"),"terminal audit states lost after reopen");
                require(reopened.applyStagedStock(pending).quantity==1000&&reopened.stockBalance(material).revision==2,"restart retry duplicated movement");
            }
        }finally{context.deleteDatabase(filename);}
    }
    private void verifyStockRecoveryScreen() {
        android.content.Context context=getTargetContext();
        try(CatalogStore store=new CatalogStore(context)){
            CatalogEntry material=store.create(CatalogEntry.Kind.MATERIAL,"Synthetic durable inventory","sheet","1.20");
            String id=stockId();
            store.stageStock(id,material.id,material.revision,0,2500,"Recover after reopening");
            Intent intent=new Intent(Intent.ACTION_MAIN).setClassName(context.getPackageName(),StockActivity.class.getName());
            intent.putExtra("materialId",material.id);intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Activity first=startActivitySync(intent);
            try{
                runOnMainChecked(()->{
                    require(!first.findViewById(R.id.stock_receive).isEnabled()&&!first.findViewById(R.id.stock_consume).isEnabled(),"pending did not lock competing actions");
                    require(first.findViewById(R.id.stock_retry).getVisibility()==View.VISIBLE&&first.findViewById(R.id.stock_cancel).getVisibility()==View.VISIBLE,"recovery actions missing");
                    require(hasText(first.getWindow().getDecorView(),"On hand: 0 sheet"),"screen staging mutated stock");
                });
            }finally{runOnMainChecked(first::finish);}
            waitForIdleSync();
            Activity second=startActivitySync(intent);
            try{
                runOnMainChecked(()->{
                    require(second.findViewById(R.id.stock_retry).performClick(),"recovery retry unavailable");
                    require(hasText(second.getWindow().getDecorView(),"On hand: 2.5 sheet"),"retry did not recover movement");
                    require(second.findViewById(R.id.stock_retry).getVisibility()==View.GONE,"applied request still pending");
                    require(second.findViewById(R.id.stock_receive).isEnabled(),"resolved material remained locked");
                });
                require(store.applyStagedStock(id).revision==1&&store.stockHistory(material.id,0,5).size()==1,"reopened screen duplicated request");
            }finally{runOnMainChecked(second::finish);}
            store.moveStock(stockId(),material.id,material.revision,1,-2500,"Synthetic cleanup");
            store.archive(material.id,material.revision,true);
        }
    }

    private void verifyCheckoutConfirmationPlan() {
        android.content.Context context=getTargetContext();
        String filename="checkout-plan-"+stockId()+".db";
        try(CatalogStore store=new CatalogStore(context,filename)){
            CatalogEntry print=store.create(CatalogEntry.Kind.SERVICE,"Synthetic checkout print","page","2.50");
            CatalogEntry paper=store.create(CatalogEntry.Kind.MATERIAL,"Synthetic checkout paper","sheet","1.99");
            String receipt=stockId(),request=stockId();
            store.moveStock(receipt,paper.id,paper.revision,0,5000,"Synthetic initial stock");
            store.setMaterialUsage(print.id,print.revision,paper.id,paper.revision,0,500);
            store.savePricing(print.id,print.revision,0,PricingMath.Method.PER_PAGE,true,
                java.util.Collections.singletonList(new PricingMath.Tier(1000,225)));
            CheckoutPlan.Inputs input=new CheckoutPlan.Inputs(1,5,2,null,null,null,CheckoutPlan.UsageBasis.BILLABLE_UNIT,2500);
            CheckoutPlan.Proposal proposed=store.prepareCheckout(request,print.id,1,1,1,input);
            require(proposed.totalCentavos==2250&&proposed.changeCentavos==250&&proposed.materialCostCentavos==995,"checkout total or cost not authoritative");
            require(proposed.materials.size()==1&&proposed.materials.get(0).afterMilli==0,"checkout stock calculation wrong");
            require(proposed.sameSnapshot(store.prepareCheckout(request,print.id,1,1,1,input)),"repeat plan differs");
            require(store.stockBalance(paper.id).quantity==5000&&store.stockBalance(paper.id).revision==1&&store.stockHistory(paper.id,0,5).size()==1,"preview changed stock");
            rejectStock(()->store.prepareCheckout(request,print.id,0,1,1,input));
            rejectStock(()->store.prepareCheckout(request,print.id,1,0,1,input));
            rejectStock(()->store.prepareCheckout(request,print.id,1,1,0,input));
            String pending=stockId();
            store.stageStock(pending,paper.id,paper.revision,1,-1,"Synthetic pending");
            rejectStock(()->store.prepareCheckout(request,print.id,1,1,1,input));
            store.cancelStagedStock(pending);
            proposed.requireSameSnapshot(store.prepareCheckout(request,print.id,1,1,1,input));
            store.moveStock(stockId(),paper.id,paper.revision,1,-1,"Synthetic shortage");
            rejectStock(()->store.prepareCheckout(request,print.id,1,1,1,input));
            store.moveStock(stockId(),paper.id,paper.revision,2,1,"Synthetic replenish");
            CheckoutPlan.Proposal changed=store.prepareCheckout(request,print.id,1,1,1,input);
            require(!proposed.sameSnapshot(changed),"same balance with changed ledger revision accepted as old confirmation");
            require(CheckoutPreview.render(changed).contains("NOT A TAX DOCUMENT OR PROOF OF PAYMENT"),"preview disclaimer missing");
            require(store.stockBalance(paper.id).revision==3&&store.stockBalance(paper.id).quantity==5000,"checkout plan mutated ledger");
            try(android.database.Cursor c=store.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM stock_requests WHERE request_id=?",new String[]{request})){
                require(c.moveToFirst()&&c.getInt(0)==0,"checkout proposal created stock request");
            }
        }finally{context.deleteDatabase(filename);}
    }

    private static void rejectStock(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("invalid stock operation accepted");
    }
    private void verifyStockLedger() {
        android.content.Context context = getTargetContext();
        String filename = "stock-migration-" + java.util.UUID.randomUUID() + ".db";
        String id = java.util.UUID.randomUUID().toString();
        String request = java.util.UUID.randomUUID().toString();
        try {
            // Fixture uses the exact former v1 schema, not a destructive reset.
            try (android.database.sqlite.SQLiteDatabase db = context.openOrCreateDatabase(filename, 0, null)) {
        db.execSQL("CREATE TABLE catalog (id TEXT PRIMARY KEY NOT NULL, kind TEXT NOT NULL CHECK(kind IN ('SERVICE','MATERIAL')), name TEXT NOT NULL, unit TEXT NOT NULL, centavos INTEGER NOT NULL CHECK(centavos BETWEEN 0 AND 99999999999), revision INTEGER NOT NULL CHECK(revision >= 1), archived INTEGER NOT NULL CHECK(archived IN (0,1)))");
        db.execSQL("CREATE TABLE catalog_history (id TEXT NOT NULL REFERENCES catalog(id), revision INTEGER NOT NULL, kind TEXT NOT NULL, name TEXT NOT NULL, unit TEXT NOT NULL, centavos INTEGER NOT NULL, archived INTEGER NOT NULL, action TEXT NOT NULL CHECK(action IN ('CREATE','EDIT','ARCHIVE','RESTORE')), PRIMARY KEY(id,revision))");
                db.execSQL("INSERT INTO catalog VALUES(?,?,?,?,?,?,?)", new Object[]{id,"MATERIAL","Synthetic paper","sheet",199,1,0});
                db.execSQL("INSERT INTO catalog_history VALUES(?,?,?,?,?,?,?,?)", new Object[]{id,1,"MATERIAL","Synthetic paper","sheet",199,0,"CREATE"});
                db.setVersion(1);
            }
            try (CatalogStore store = new CatalogStore(context,filename)) {
                require(store.get(id).centavos == 199 && store.get(id).revision == 1, "v1 catalog migration changed record");
                require(store.stockBalance(id).quantity == 0 && store.stockBalance(id).revision == 0, "initial stock wrong");
                StockMath.Balance added = store.moveStock(request,id,1,0,1500,"Synthetic receipt");
                require(added.quantity == 1500 && added.revision == 1, "stock receipt wrong");
                require(store.moveStock(request,id,1,0,1500,"Synthetic receipt").revision == 1, "exact replay wrong");
                rejectStock(() -> store.moveStock(request,id,1,0,1501,"Synthetic receipt"));
                rejectStock(() -> store.moveStock(java.util.UUID.randomUUID().toString(),id,1,0,1,"Stale"));
                rejectStock(() -> store.moveStock(java.util.UUID.randomUUID().toString(),id,1,1,-1501,"Overdraw"));
                rejectStock(() -> store.archive(id,1,true));
                rejectStock(() -> store.edit(id,1,"Synthetic paper","ream","1.99"));
                require(store.stockBalance(id).quantity == 1500 && store.stockBalance(id).revision == 1, "rejected movement changed stock");
                store.moveStock(java.util.UUID.randomUUID().toString(),id,1,1,-1500,"Synthetic usage");
                store.archive(id,1,true);
                require(store.moveStock(request,id,1,0,1500,"Synthetic receipt").quantity == 1500, "historical replay after archive wrong");
                require(store.stockBalance(id).quantity == 0 && store.stockBalance(id).revision == 2, "historical replay changed current balance");
            }
            try (CatalogStore reopened = new CatalogStore(context,filename)) {
                require(reopened.get(id).archived && reopened.stockBalance(id).revision == 2, "stock persistence wrong");
                try (android.database.Cursor c = reopened.getReadableDatabase().rawQuery("SELECT count(*) FROM stock_movements WHERE material_id=?",new String[]{id})) {
                    require(c.moveToFirst() && c.getInt(0)==2,"stock movement history count wrong");
                }
                try (android.database.Cursor c = reopened.getReadableDatabase().rawQuery("SELECT count(*) FROM catalog_history WHERE id=?",new String[]{id})) {
                    require(c.moveToFirst() && c.getInt(0)==2,"catalog migration history count wrong");
                }
            }
        } finally { context.deleteDatabase(filename); }
    }
    private void verifyCatalogScreen(Activity screen) {
        android.content.Context context = getTargetContext();
        try (CatalogStore store = new CatalogStore(context)) {
            require(store.list(true).isEmpty(), "catalog UI test requires a fresh preview installation");
            CatalogEntry fixture = store.create(CatalogEntry.Kind.SERVICE, "Synthetic service", "page", "3.25");
            Activity catalog = null;
            ActivityMonitor monitor = addMonitor(CatalogActivity.class.getName(), null, false);
            try {
                runOnMainChecked(() -> screen.findViewById(R.id.open_catalog).performClick());
                catalog = waitForMonitorWithTimeout(monitor, 10000);
                require(catalog != null, "catalog Activity did not open");
                final Activity shown = catalog;
                runOnMainChecked(() -> {
                    View root = shown.getWindow().getDecorView();
                    require(hasText(root, "Services & Materials"), "catalog heading missing");
                    require(hasText(root, "Synthetic service · SERVICE"), "catalog fixture missing");
                    require(hasText(root, "PHP 3.25 / page · Revision 1"), "catalog unit price missing");
                    View choice = findText(root, "Use in quotation");
                    require(choice != null && choice.performClick(), "service selection failed");
                });
                awaitServiceSelection(screen,"3.25",fixture.name);
                runOnMainChecked(() -> {
                    EditText price = screen.findViewById(R.id.quote_price);
                    require(price.getText().toString().equals("3.25"), "catalog quotation price not copied");
                });
            } finally {
                removeMonitor(monitor);
                if (catalog != null) {
                    final Activity shown = catalog;
                    runOnMainChecked(shown::finish);
                }
                CatalogEntry current = store.get(fixture.id);
                if (!current.archived) store.archive(current.id, current.revision, true);
            }
        }
    }
    private static View findText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView)view).getText())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i=0; i<group.getChildCount(); i++) {
                View match = findText(group.getChildAt(i), text);
                if (match != null) return match;
            }
        }
        return null;
    }
    private void verifyCatalog() {
        android.content.Context context = getTargetContext();
        String filename = "catalog-test-" + java.util.UUID.randomUUID() + ".db";
        try {
            String id;
            try (CatalogStore store = new CatalogStore(context, filename)) {
                CatalogEntry entry = store.create(CatalogEntry.Kind.SERVICE, "Synthetic print", "page", "1.99");
                id = entry.id;
                require(store.list(false).size() == 1, "catalog create missing");
                CatalogEntry edited = store.edit(id, 1, "Synthetic color", "page", "2.01");
                require(edited.revision == 2 && edited.centavos == 201, "catalog update mismatch");
                try {
                    store.edit(id, 1, "Stale", "page", "1");
                    throw new AssertionError("stale catalog edit accepted");
                } catch (IllegalStateException expected) { }
                require(store.get(id).revision == 2, "stale edit changed record");
                store.archive(id, 2, true);
                require(store.list(false).isEmpty() && store.list(true).size() == 1, "archive visibility mismatch");
            }
            try (CatalogStore reopened = new CatalogStore(context, filename)) {
                CatalogEntry archived = reopened.get(id);
                require(archived.archived && archived.revision == 3 && archived.centavos == 201, "catalog persistence mismatch");
                reopened.archive(id, 3, false);
                CatalogEntry restored = reopened.get(id);
                require(!restored.archived && restored.revision == 4 && restored.name.equals("Synthetic color"), "restore mismatch");
                try (android.database.Cursor cursor = reopened.getReadableDatabase().rawQuery("SELECT count(*) FROM catalog_history WHERE id=?", new String[]{id})) {
                    require(cursor.moveToFirst() && cursor.getInt(0) == 4, "catalog history mismatch");
                }
            }
        } finally {
            context.deleteDatabase(filename);
        }
    }
    private static boolean hasText(View view, String value) {
        if (view instanceof TextView && value.contentEquals(((TextView)view).getText())) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); i++) if (hasText(group.getChildAt(i), value)) return true;
        }
        return false;
    }
    private static void require(boolean ok, String description) {
        if (!ok) throw new AssertionError(description);
    }
    private void awaitText(Activity activity,String expected,String failure) {
        long deadline=android.os.SystemClock.elapsedRealtime()+10000;
        java.util.concurrent.atomic.AtomicBoolean found=new java.util.concurrent.atomic.AtomicBoolean();
        do{
            waitForIdleSync();
            runOnMainChecked(()->found.set(hasText(activity.getWindow().getDecorView(),expected)));
            if(found.get())return;
            android.os.SystemClock.sleep(50);
        }while(android.os.SystemClock.elapsedRealtime()<deadline);
        throw new AssertionError(failure+": "+expected);
    }
    private void verifyPricingStore() {
        android.content.Context context=getTargetContext();
        String filename="pricing-v3-migration-"+java.util.UUID.randomUUID()+".db";
        String service=java.util.UUID.randomUUID().toString(),material=java.util.UUID.randomUUID().toString();
        String movement=java.util.UUID.randomUUID().toString();
        try{
            // Exact former v3 schema, including existing ledger and recipe.
            try(android.database.sqlite.SQLiteDatabase db=context.openOrCreateDatabase(filename,0,null)){
                db.execSQL("CREATE TABLE catalog (id TEXT PRIMARY KEY NOT NULL, kind TEXT NOT NULL CHECK(kind IN ('SERVICE','MATERIAL')), name TEXT NOT NULL, unit TEXT NOT NULL, centavos INTEGER NOT NULL CHECK(centavos BETWEEN 0 AND 99999999999), revision INTEGER NOT NULL CHECK(revision >= 1), archived INTEGER NOT NULL CHECK(archived IN (0,1)))");
                db.execSQL("CREATE TABLE catalog_history (id TEXT NOT NULL REFERENCES catalog(id), revision INTEGER NOT NULL, kind TEXT NOT NULL, name TEXT NOT NULL, unit TEXT NOT NULL, centavos INTEGER NOT NULL, archived INTEGER NOT NULL, action TEXT NOT NULL CHECK(action IN ('CREATE','EDIT','ARCHIVE','RESTORE')), PRIMARY KEY(id,revision))");
                db.execSQL("CREATE TABLE stock_movements (request_id TEXT PRIMARY KEY NOT NULL, material_id TEXT NOT NULL REFERENCES catalog(id), catalog_revision INTEGER NOT NULL CHECK(catalog_revision>=1), stock_revision INTEGER NOT NULL CHECK(stock_revision>=1), unit TEXT NOT NULL, reason TEXT NOT NULL, delta INTEGER NOT NULL CHECK(delta BETWEEN -999999999999 AND 999999999999 AND delta<>0), before_quantity INTEGER NOT NULL CHECK(before_quantity BETWEEN 0 AND 999999999999), after_quantity INTEGER NOT NULL CHECK(after_quantity BETWEEN 0 AND 999999999999 AND after_quantity=before_quantity+delta), created_ms INTEGER NOT NULL, UNIQUE(material_id,stock_revision))");
                db.execSQL("CREATE TABLE service_materials (service_id TEXT NOT NULL REFERENCES catalog(id), material_id TEXT NOT NULL REFERENCES catalog(id), quantity_milli INTEGER NOT NULL CHECK(quantity_milli BETWEEN 1 AND 999999999999), unit TEXT NOT NULL, PRIMARY KEY(service_id,material_id))");
                db.execSQL("CREATE TABLE recipe_history (service_id TEXT NOT NULL REFERENCES catalog(id), revision INTEGER NOT NULL CHECK(revision>=1), material_id TEXT NOT NULL REFERENCES catalog(id), quantity_milli INTEGER NOT NULL CHECK(quantity_milli BETWEEN 0 AND 999999999999), unit TEXT NOT NULL, material_revision INTEGER NOT NULL, service_revision INTEGER NOT NULL, created_ms INTEGER NOT NULL, PRIMARY KEY(service_id,revision))");
                db.execSQL("INSERT INTO catalog VALUES(?,?,?,?,?,?,?)",new Object[]{service,"SERVICE","Synthetic document","page",300,1,0});
                db.execSQL("INSERT INTO catalog VALUES(?,?,?,?,?,?,?)",new Object[]{material,"MATERIAL","Synthetic paper","sheet",199,1,0});
                db.execSQL("INSERT INTO catalog_history VALUES(?,?,?,?,?,?,?,?)",new Object[]{service,1,"SERVICE","Synthetic document","page",300,0,"CREATE"});
                db.execSQL("INSERT INTO catalog_history VALUES(?,?,?,?,?,?,?,?)",new Object[]{material,1,"MATERIAL","Synthetic paper","sheet",199,0,"CREATE"});
                db.execSQL("INSERT INTO stock_movements VALUES(?,?,?,?,?,?,?,?,?,?)",new Object[]{movement,material,1,1,"sheet","Synthetic receipt",1500,0,1500,1});
                db.execSQL("INSERT INTO service_materials VALUES(?,?,?,?)",new Object[]{service,material,500,"sheet"});
                db.execSQL("INSERT INTO recipe_history VALUES(?,?,?,?,?,?,?,?)",new Object[]{service,1,material,500,"sheet",1,1,1});
                db.setVersion(3);
            }
            java.util.List<PricingMath.Tier> tiers=java.util.Arrays.asList(new PricingMath.Tier(2000,200),new PricingMath.Tier(1000,250));
            try(CatalogStore store=new CatalogStore(context,filename)){
                require(store.getReadableDatabase().getVersion()==5,"v3-to-v5 migration version wrong");
                require(store.pricing(service).revision==0&&!store.pricing(service).needsAdvancedQuote(),"legacy default changed");
                require(store.recipeRevision(service)==1&&store.stockBalance(material).quantity==1500,"migration changed recipe or stock");
                PricingProfile saved=store.savePricing(service,1,0,PricingMath.Method.PER_PAGE,true,tiers);
                require(saved.revision==1&&saved.tiers.size()==2&&saved.tiers.get(0).minQuantityHundredths==1000,"pricing saved incorrectly");
                rejectStock(()->store.savePricing(service,1,0,PricingMath.Method.PER_PAGE,true,tiers));
                rejectStock(()->store.savePricing(service,1,1,PricingMath.Method.PER_PAGE,true,tiers));
                rejectStock(()->store.savePricing(service,1,1,PricingMath.Method.PER_SQFT,true,tiers));
                rejectStock(()->store.edit(service,1,"Synthetic document","sq ft","3.00"));
                rejectStock(()->store.pricing(material));
                CatalogEntry revised=store.edit(service,1,"Synthetic document","page","3.50");
                require(revised.revision==2,"ordinary price edit blocked");
                rejectStock(()->store.savePricing(service,1,1,PricingMath.Method.PER_UNIT,false,java.util.Collections.emptyList()));
                require(store.savePricing(service,2,1,PricingMath.Method.PER_UNIT,false,java.util.Collections.emptyList()).revision==2,"pricing reset revision wrong");
                store.archive(service,2,true);
                rejectStock(()->store.savePricing(service,3,2,PricingMath.Method.PER_PAGE,true,tiers));
            }
            try(CatalogStore reopened=new CatalogStore(context,filename)){
                PricingProfile profile=reopened.pricing(service);
                require(profile.revision==2&&profile.tiers.isEmpty()&&!profile.needsAdvancedQuote(),"latest pricing snapshot not preserved");
                require(reopened.get(service).archived&&reopened.get(service).centavos==350,"catalog changed by pricing");
                require(reopened.recipeRevision(service)==1&&reopened.stockBalance(material).quantity==1500,"pricing changed recipe or ledger");
                try(android.database.Cursor c=reopened.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM pricing_profile_versions WHERE service_id=?",new String[]{service})){
                    require(c.moveToFirst()&&c.getInt(0)==2,"pricing history changed");
                }
                try(android.database.Cursor c=reopened.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM pricing_tier_versions WHERE service_id=? AND profile_revision=1",new String[]{service})){
                    require(c.moveToFirst()&&c.getInt(0)==2,"historical bulk tiers lost");
                }
                try(android.database.Cursor c=reopened.getReadableDatabase().rawQuery("PRAGMA foreign_key_check",null)){
                    require(!c.moveToFirst(),"migration foreign key violations");
                }
            }
        }finally{context.deleteDatabase(filename);}
    }
    private void verifyPricingScreen(Activity screen) {
        android.content.Context context=getTargetContext();
        try(CatalogStore store=new CatalogStore(context)){
            CatalogEntry service=store.create(CatalogEntry.Kind.SERVICE,"Synthetic pricing configuration","page","3.00");
            CatalogEntry paper=store.create(CatalogEntry.Kind.MATERIAL,"Synthetic pricing paper","sheet","1.99");
            store.moveStock(java.util.UUID.randomUUID().toString(),paper.id,paper.revision,0,2500,"Synthetic receipt");
            store.setMaterialUsage(service.id,service.revision,paper.id,paper.revision,0,500);
            Activity catalog=null,editor=null;
            ActivityMonitor cm=addMonitor(CatalogActivity.class.getName(),null,false);
            ActivityMonitor pm=addMonitor(PricingSettingsActivity.class.getName(),null,false);
            try{
                runOnMainChecked(()->screen.findViewById(R.id.open_catalog).performClick());
                catalog=waitForMonitorWithTimeout(cm,10000);
                require(catalog!=null,"pricing fixture catalog did not open");
                final Activity initial=catalog;
                runOnMainChecked(()->{
                    View select=findText(initial.getWindow().getDecorView(),"Use in quotation");
                    require(select!=null&&select.performClick(),"unconfigured service selection failed");
                });
                awaitServiceSelection(screen,"3.00",service.name);
                removeMonitor(cm);
                cm=addMonitor(CatalogActivity.class.getName(),null,false);
                runOnMainChecked(()->screen.findViewById(R.id.open_catalog).performClick());
                catalog=waitForMonitorWithTimeout(cm,10000);
                require(catalog!=null,"pricing catalog did not reopen");
                final Activity shown=catalog;
                runOnMainChecked(()->{
                    View configure=findText(shown.getWindow().getDecorView(),"Configure service pricing");
                    require(configure!=null&&configure.performClick(),"pricing configuration action missing");
                });
                editor=waitForMonitorWithTimeout(pm,10000);
                require(editor!=null,"pricing settings Activity did not open");
                final Activity form=editor;
                waitForIdleSync();
                runOnMainChecked(()->{
                    android.widget.Spinner method=form.findViewById(R.id.pricing_method);
                    method.setSelection(PricingMath.Method.PER_PAGE.ordinal());
                });
                waitForIdleSync();
                runOnMainChecked(()->{
                    View root=form.getWindow().getDecorView();
                    android.widget.CheckBox pages=form.findViewById(R.id.pricing_pages);
                    require(pages.isEnabled(),"document page option disabled");
                    pages.setChecked(true);
                    ((EditText)form.findViewById(R.id.pricing_threshold)).setText("10");
                    ((EditText)form.findViewById(R.id.pricing_rate)).setText("2.25");
                    require(form.findViewById(R.id.pricing_add).performClick(),"tier add disabled");
                    require(hasText(root,"10+ units · PHP 2.25 per unit"),"draft tier missing");
                    require(form.findViewById(R.id.pricing_save).performClick(),"pricing save disabled");
                    require(hasText(root,"Pricing settings saved."),"pricing save not confirmed");
                });
                PricingProfile saved=store.pricing(service.id);
                require(saved.revision==1&&saved.method==PricingMath.Method.PER_PAGE&&saved.pagesAndCopies&&saved.tiers.size()==1&&saved.tiers.get(0).rateCentavos==225,"editor profile not persisted");
                runOnMainChecked(form::finish);
                awaitText(shown,"Pricing: Per page · 1 tier(s)","catalog pricing summary not refreshed");
                runOnMainChecked(shown::finish);
                awaitText(screen,"Total — pricing settings changed; reselect service","stale legacy quote not blocked");
                runOnMainChecked(()->((EditText)screen.findViewById(R.id.quote_price)).setText("9.00"));
                removeMonitor(cm);cm=addMonitor(CatalogActivity.class.getName(),null,false);
                runOnMainChecked(()->screen.findViewById(R.id.open_catalog).performClick());
                catalog=waitForMonitorWithTimeout(cm,10000);
                require(catalog!=null,"configured document service catalog did not reopen");
                final Activity configured=catalog;
                runOnMainChecked(()->{
                    View select=findText(configured.getWindow().getDecorView(),"Use in quotation");
                    require(select!=null&&select.performClick(),"advanced profile selection failed");
                });
                awaitServiceSelection(screen,"3.00",service.name);
                runOnMainChecked(()->{
                    View root=screen.getWindow().getDecorView();
                    EditText pageCount=screen.findViewById(R.id.quote_pages),copyCount=screen.findViewById(R.id.quote_copies);
                    require(pageCount.isShown()&&copyCount.isShown()&&!screen.findViewById(R.id.quote_quantity).isShown(),"incorrect document quantity controls");
                    pageCount.setText("5");copyCount.setText("2");
                    require(hasText(root,"Billable: 10 units · Rate: PHP 2.25 · Bulk tiers apply"),"document bulk threshold incorrect");
                    require(hasText(root,"Total: PHP 22.50"),"document quote total incorrect");
                    require(hasText(root,"Material cost — choose usage basis"),"material basis not explicit");
                    ((android.widget.Spinner)screen.findViewById(R.id.quote_material_basis)).setSelection(1);
                });
                awaitText(screen,"Material cost: PHP 9.95 · Insufficient stock","billed document material estimate incorrect");
                runOnMainChecked(()->((android.widget.Spinner)screen.findViewById(R.id.quote_material_basis)).setSelection(2));
                awaitText(screen,"Material cost: PHP 1.99 · Stock sufficient","produced-copy material basis incorrect");
                runOnMainChecked(()->{
                    View root=screen.getWindow().getDecorView();
                    ((EditText)screen.findViewById(R.id.quote_price)).setText("6.00");
                    require(hasText(root,"Total: PHP 22.50"),"bulk tier not applied to edited base rate");
                    ((EditText)screen.findViewById(R.id.quote_pages)).setText("4");
                    require(hasText(root,"Total: PHP 48.00"),"below-tier base rate incorrect");
                    View clear=findText(root,"Clear service link");
                    require(clear!=null&&clear.performClick(),"clear service unavailable");
                    ((EditText)screen.findViewById(R.id.quote_quantity)).setText("2");
                    require(hasText(root,"Total: PHP 12.00"),"manual quote altered by profile");
                });
                require(store.get(service.id).centavos==300,"pricing settings changed the base catalog price");
                require(store.stockBalance(paper.id).quantity==2500,"document quoting consumed material");
            }finally{
                removeMonitor(cm);removeMonitor(pm);
                if(editor!=null){final Activity form=editor;runOnMainChecked(form::finish);}
                if(catalog!=null){final Activity shown=catalog;runOnMainChecked(shown::finish);}
                if(!store.recipeBindings(service.id).isEmpty())
                    store.setMaterialUsage(service.id,service.revision,paper.id,paper.revision,store.recipeRevision(service.id),0);
                if(store.stockBalance(paper.id).quantity==2500)
                    store.moveStock(java.util.UUID.randomUUID().toString(),paper.id,paper.revision,1,-2500,"Synthetic cleanup");
                store.archive(service.id,service.revision,true);
                if(store.stockBalance(paper.id).quantity==0)store.archive(paper.id,paper.revision,true);
            }
        }
    }
    private void verifyMeasuredQuote(Activity screen) {
        android.content.Context context=getTargetContext();
        try(CatalogStore store=new CatalogStore(context)){
            CatalogEntry service=store.create(CatalogEntry.Kind.SERVICE,"Synthetic measured banner","sq ft","25.00");
            CatalogEntry material=store.create(CatalogEntry.Kind.MATERIAL,"Synthetic banner material","sheet","10.00");
            store.savePricing(service.id,service.revision,0,PricingMath.Method.PER_SQFT,false,java.util.Collections.emptyList());
            store.moveStock(java.util.UUID.randomUUID().toString(),material.id,material.revision,0,1249,"Synthetic receipt");
            store.setMaterialUsage(service.id,service.revision,material.id,material.revision,0,500);
            Activity catalog=null;
            ActivityMonitor cm=addMonitor(CatalogActivity.class.getName(),null,false);
            try{
                runOnMainChecked(()->screen.findViewById(R.id.open_catalog).performClick());
                catalog=waitForMonitorWithTimeout(cm,10000);
                require(catalog!=null,"measured catalog did not open");
                final Activity shown=catalog;
                runOnMainChecked(()->{
                    View select=findText(shown.getWindow().getDecorView(),"Use in quotation");
                    require(select!=null&&select.performClick(),"measured service selection failed");
                });
                awaitServiceSelection(screen,"25.00",service.name);
                runOnMainChecked(()->{
                    View root=screen.getWindow().getDecorView();
                    EditText w=screen.findViewById(R.id.quote_width),h=screen.findViewById(R.id.quote_height);
                    require(w.isShown()&&h.isShown()&&!screen.findViewById(R.id.quote_pages).isShown(),"dimension controls hidden");
                    ((EditText)screen.findViewById(R.id.quote_quantity)).setText("1");
                    w.setText("1.25");h.setText("2");
                    require(hasText(root,"Billable: 2.5 sq ft · Rate: PHP 25.00"),"measured effective area wrong");
                    require(hasText(root,"Total: PHP 62.50"),"measured selling price wrong");
                    require(hasText(root,"Material cost — choose usage basis"),"measured basis not explicit");
                    ((android.widget.Spinner)screen.findViewById(R.id.quote_material_basis)).setSelection(1);
                });
                awaitText(screen,"Material cost: PHP 12.50 · Insufficient stock","fractional shortage was not detected");
                runOnMainChecked(()->((android.widget.Spinner)screen.findViewById(R.id.quote_material_basis)).setSelection(2));
                awaitText(screen,"Material cost: PHP 5.00 · Stock sufficient","per-item measured material estimate incorrect");
                runOnMainChecked(()->{
                    ((android.widget.Spinner)screen.findViewById(R.id.quote_dimension_unit)).setSelection(1);
                    ((EditText)screen.findViewById(R.id.quote_width)).setText("30");
                    ((EditText)screen.findViewById(R.id.quote_height)).setText("12");
                });
                awaitText(screen,"Billable: 2.5 sq ft · Rate: PHP 25.00","inch conversion in native quote wrong");
                runOnMainChecked(()->{
                    View root=screen.getWindow().getDecorView();
                    require(hasText(root,"Total: PHP 62.50"),"converted quote total wrong");
                    ((EditText)screen.findViewById(R.id.quote_width)).setText("0");
                    require(hasText(root,"Total — enter valid price and quantity"),"invalid dimensions accepted");
                    require(hasText(root,"Change —"),"stale tender after invalid dimensions");
                    View clear=findText(root,"Clear service link");
                    require(clear!=null&&clear.performClick(),"clear measured quote unavailable");
                });
                require(store.stockBalance(material.id).quantity==1249&&store.get(service.id).centavos==2500,"measured quote mutated stock or price");
            }finally{
                removeMonitor(cm);
                if(catalog!=null){final Activity shown=catalog;runOnMainChecked(shown::finish);}
                if(!store.recipeBindings(service.id).isEmpty())
                    store.setMaterialUsage(service.id,service.revision,material.id,material.revision,store.recipeRevision(service.id),0);
                if(store.stockBalance(material.id).quantity==1249)
                    store.moveStock(java.util.UUID.randomUUID().toString(),material.id,material.revision,1,-1249,"Synthetic cleanup");
                store.archive(service.id,service.revision,true);
                if(store.stockBalance(material.id).quantity==0)store.archive(material.id,material.revision,true);
            }
        }
    }
}
