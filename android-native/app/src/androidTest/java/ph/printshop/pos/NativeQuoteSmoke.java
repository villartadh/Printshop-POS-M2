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
            phase = "material costing"; verifyMaterials();
            phase = "recipe and quote screen"; verifyRecipeScreen(screen);
            phase = "pricing store migration"; verifyPricingStore();
            phase = "pricing settings screen"; verifyPricingScreen(screen);
            evidence.putString("stream", "NATIVE_QUOTE_SMOKE_PASS\nNATIVE_CATALOG_STORE_PASS\nNATIVE_CATALOG_SCREEN_PASS\nNATIVE_STOCK_LEDGER_PASS\nNATIVE_STOCK_SCREEN_PASS\nNATIVE_MATERIAL_COST_PASS\nNATIVE_RECIPE_SCREEN_PASS\nNATIVE_PRICING_STORE_PASS\nNATIVE_PRICING_SETTINGS_PASS\n");
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
                require(store.getReadableDatabase().getVersion()==4,"v3-to-v4 migration version wrong");
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
                waitForIdleSync();
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
                runOnMainChecked(form::finish);waitForIdleSync();
                runOnMainChecked(()->{
                    View root=shown.getWindow().getDecorView();
                    require(hasText(root,"Pricing: Per page · 1 tier(s)"),"catalog pricing summary not refreshed");
                    require(findText(root,"Use in quotation")==null,"advanced profile incorrectly offered legacy quote");
                });
                runOnMainChecked(shown::finish);waitForIdleSync();
                runOnMainChecked(()->{
                    View root=screen.getWindow().getDecorView();
                    require(hasText(root,"Total — advanced quotation controls pending"),"stale legacy quote not blocked");
                    View clear=findText(root,"Clear service link");
                    require(clear!=null&&clear.performClick(),"clear stale service unavailable");
                    ((EditText)screen.findViewById(R.id.quote_quantity)).setText("2");
                    require(hasText(root,"Total: PHP 6.00"),"manual quote altered by profile");
                });
                require(store.get(service.id).centavos==300,"pricing settings changed the base catalog price");
            }finally{
                removeMonitor(cm);removeMonitor(pm);
                if(editor!=null){final Activity form=editor;runOnMainChecked(form::finish);}
                if(catalog!=null){final Activity shown=catalog;runOnMainChecked(shown::finish);}
                store.archive(service.id,service.revision,true);
            }
        }
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
                waitForIdleSync();
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
                waitForIdleSync();
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
}
