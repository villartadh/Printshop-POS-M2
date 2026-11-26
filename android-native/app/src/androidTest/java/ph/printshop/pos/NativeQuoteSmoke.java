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
            phase = "v5 checkout migration"; verifyCheckoutMigration(5);
            phase = "v6 cashier migration"; verifyCheckoutMigration(6);
            phase = "v7 credential migration"; verifyCheckoutMigration(7);
            phase = "v8 staff audit migration"; verifyCheckoutMigration(8);
            phase = "durable atomic checkout fixture"; verifyDurableCheckout();
            phase = "cashier authorization and shift fixture"; verifyCashierAuthShift();
            phase = "audited credential lifecycle"; verifyCredentialLifecycle();
            phase = "owner-only fixture administration"; verifyOwnerAdministration();
            phase = "owner-controlled cashier disable"; verifyStaffDisable();
            phase = "material costing"; verifyMaterials();
            phase = "recipe and quote screen"; verifyRecipeScreen(screen);
            phase = "pricing store migration"; verifyPricingStore();
            phase = "pricing settings and document quote"; verifyPricingScreen(screen);
            phase = "measured quote"; verifyMeasuredQuote(screen);
            phase = "native cashier training UI"; verifyCashierTrainingScreen(screen);
            evidence.putString("stream", "NATIVE_QUOTE_SMOKE_PASS\nNATIVE_CATALOG_STORE_PASS\nNATIVE_CATALOG_SCREEN_PASS\nNATIVE_STOCK_LEDGER_PASS\nNATIVE_STOCK_SCREEN_PASS\nNATIVE_STOCK_RECOVERY_PASS\nNATIVE_CHECKOUT_PLAN_PASS\nNATIVE_DURABLE_CHECKOUT_PASS\nNATIVE_CASHIER_SHIFT_PASS\nNATIVE_CASHIER_UI_PASS\nNATIVE_CREDENTIAL_LIFECYCLE_PASS\nNATIVE_ADMIN_AUTH_PASS\nNATIVE_STAFF_DISABLE_PASS\nNATIVE_MATERIAL_COST_PASS\nNATIVE_RECIPE_SCREEN_PASS\nNATIVE_PRICING_STORE_PASS\nNATIVE_PRICING_SETTINGS_PASS\nNATIVE_ADVANCED_QUOTE_PASS\n");
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

    private void trainingClick(Activity activity,int id){
        runOnMainChecked(()->{
            View view=activity.findViewById(id);
            require(view!=null&&view.isEnabled(),"training action missing or disabled: "+id);
            require(view.performClick(),"training action not handled: "+id);
        });
    }
    private void trainingField(Activity activity,int id,String value){
        runOnMainChecked(()->{
            EditText field=activity.findViewById(id);
            require(field!=null&&field.isEnabled(),"training input missing: "+id);
            field.setText(value);
        });
    }
    private void trainingSelect(Activity activity,String label){
        runOnMainChecked(()->{
            View view=findText(activity.getWindow().getDecorView(),label);
            require(view!=null&&view.isEnabled()&&view.performClick(),"training record not selectable: "+label);
        });
    }
    private void trainingLogin(Activity activity,String user,String password){
        trainingField(activity,R.id.cashier_username,user);
        trainingField(activity,R.id.cashier_password,password);
        trainingClick(activity,R.id.cashier_login);
    }
    private static long trainingCount(CatalogStore store,String query){
        try(android.database.Cursor c=store.getReadableDatabase().rawQuery(query,null)){
            require(c.moveToFirst(),"training audit query missing");
            return c.getLong(0);
        }
    }
    private void verifyCashierTrainingScreen(Activity screen){
        android.content.Context context=getTargetContext();
        final String owner="training-owner",cashier="training-cashier";
        final String ownerPass="Synthetic-Owner-UI-01",cashierPass="Synthetic-Cashier-UI-01",resetPass="Synthetic-Cashier-Reset-UI-02",newPass="Synthetic-Cashier-New-UI-03";
        java.util.List<CatalogEntry> before;
        try(CatalogStore ordinary=new CatalogStore(context)){before=ordinary.list(true);}
        android.content.SharedPreferences preferences=context.getSharedPreferences(CashierPreviewActivity.PREFS,0);
        require(preferences.getString(CashierPreviewActivity.FIXTURE_KEY,null)==null,"training UI test requires fresh preview installation");
        ActivityMonitor monitor=addMonitor(CashierPreviewActivity.class.getName(),null,false);
        Activity first=null,second=null;
        try{
            trainingClick(screen,R.id.open_cashier_training);
            first=waitForMonitorWithTimeout(monitor,10000);
            require(first!=null,"native training Activity did not launch");
            awaitText(first,"Set up training owner","first-run setup missing");
            trainingClick(first,R.id.cashier_setup);
            awaitText(first,"Confirm synthetic-only use before setup.","synthetic consent not enforced");
            trainingField(first,R.id.cashier_username,owner);
            trainingField(first,R.id.cashier_password,ownerPass);
            final Activity setup=first;
            runOnMainChecked(()->{
                android.widget.CheckBox consent=setup.findViewById(R.id.cashier_consent);
                consent.setChecked(true);
                require((setup.getWindow().getAttributes().flags&android.view.WindowManager.LayoutParams.FLAG_SECURE)!=0,"training screen not protected");
            });
            trainingClick(first,R.id.cashier_setup);
            awaitText(first,"Training sign in","owner not created");
            trainingLogin(first,owner,ownerPass);
            awaitText(first,"No open shift","owner login failed");
            trainingClick(first,R.id.cashier_inventory);
            awaitText(first,"Training inventory","owner inventory missing");
            awaitText(first,"Training paper · 50 sheet","initial training stock wrong");
            trainingSelect(first,"Adjust Training paper");
            trainingField(first,R.id.cashier_inventory_quantity,"1.25");
            trainingField(first,R.id.cashier_inventory_reason,"Synthetic UI adjustment");
            trainingClick(first,R.id.cashier_inventory_add);
            awaitText(first,"Pending training stock adjustment","owner stock was not staged");
            awaitText(first,"Training paper · Add 1.25 sheet","wrong pending adjustment");
            trainingClick(first,R.id.cashier_inventory_back);
            awaitText(first,"Training paper · 50 sheet","staging changed stock");
            trainingSelect(first,"Recover Training paper adjustment");
            awaitText(first,"Pending training stock adjustment","pending inventory not recoverable");
            trainingClick(first,R.id.cashier_inventory_cancel);
            awaitText(first,"Training paper · 50 sheet","cancel changed stock");
            trainingSelect(first,"Adjust Training paper");
            trainingField(first,R.id.cashier_inventory_quantity,"1");
            trainingField(first,R.id.cashier_inventory_reason,"Synthetic UI addition");
            trainingClick(first,R.id.cashier_inventory_add);
            awaitText(first,"Pending training stock adjustment","addition not staged");
            trainingClick(first,R.id.cashier_inventory_apply);
            awaitText(first,"Training paper · 51 sheet","owner stock addition wrong");
            trainingSelect(first,"Adjust Training paper");
            trainingField(first,R.id.cashier_inventory_quantity,"1");
            trainingField(first,R.id.cashier_inventory_reason,"Synthetic UI removal");
            trainingClick(first,R.id.cashier_inventory_remove);
            awaitText(first,"Pending training stock adjustment","removal not staged");
            trainingClick(first,R.id.cashier_inventory_apply);
            awaitText(first,"Training paper · 50 sheet","owner stock removal wrong");
            trainingClick(first,R.id.cashier_back);
            awaitText(first,"No open shift","inventory return failed");
            trainingClick(first,R.id.cashier_add_staff);
            awaitText(first,"Owner-only creation. No real shop credentials.","owner-only screen missing");
            trainingField(first,R.id.cashier_username,cashier);
            trainingField(first,R.id.cashier_password,cashierPass);
            trainingClick(first,R.id.cashier_confirm_staff);
            awaitText(first,"No open shift","cashier creation failed");
            trainingClick(first,R.id.cashier_lock);
            awaitText(first,"Training sign in","owner not locked");
            trainingLogin(first,cashier,cashierPass);
            awaitText(first,"No open shift","cashier login failed");
            runOnMainChecked(()->{
                require(setup.findViewById(R.id.cashier_add_staff)==null,"cashier visible owner-only action");
                require(setup.findViewById(R.id.cashier_inventory)==null,"cashier visible owner-only inventory");
                require(setup.findViewById(R.id.cashier_manage_staff)==null,"cashier visible owner-only staff management");
            });
            trainingField(first,R.id.cashier_opening,"10.00");
            trainingClick(first,R.id.cashier_open_shift);
            awaitText(first,"Shift open · Expected cash: PHP 10.00","training shift not opened");
            trainingClick(first,R.id.cashier_new_checkout);
            awaitText(first,"New training checkout","training checkout missing");
            trainingField(first,R.id.cashier_count,"2");
            trainingField(first,R.id.cashier_cash,"10.00");
            trainingClick(first,R.id.cashier_review);
            awaitText(first,"Review training checkout","training review missing");
            trainingClick(first,R.id.cashier_stage);
            awaitText(first,"Pending training checkout","training request not staged");
            String fixtureId=preferences.getString(CashierPreviewActivity.FIXTURE_KEY,null);
            require(fixtureId!=null&&preferences.getAll().size()==1,"credentials or session persisted in preferences");
            try(CatalogStore fixture=CatalogStore.checkoutFixture(context,fixtureId)){
                require(trainingCount(fixture,"SELECT COUNT(*) FROM checkout_sales")==0,"stage recorded a sale");
                require(trainingCount(fixture,"SELECT COUNT(*) FROM checkout_requests WHERE state='PENDING'")==1,"pending request absent");
            }
            runOnMainChecked(first::finish);waitForIdleSync();first=null;
            // Relaunch through the actual main-screen navigation, not an exported or test-only entry.
            removeMonitor(monitor);monitor=addMonitor(CashierPreviewActivity.class.getName(),null,false);
            trainingClick(screen,R.id.open_cashier_training);
            second=waitForMonitorWithTimeout(monitor,10000);
            require(second!=null,"native training Activity did not reopen");
            awaitText(second,"Training sign in","reopen failed to lock");
            final Activity recovered=second;
            runOnMainChecked(()->{
                EditText password=recovered.findViewById(R.id.cashier_password);
                require(password!=null&&password.getText().length()==0,"password restored after reopen");
            });
            // Owner-assisted recovery preserves the original unresolved checkout.
            trainingLogin(second,owner,ownerPass);
            awaitText(second,"No open shift","owner login for reset failed");
            trainingClick(second,R.id.cashier_manage_staff);
            awaitText(second,cashier+" · Open shift — cannot disable","owner cannot see blocked open shift");
            runOnMainChecked(()->require(findText(recovered.getWindow().getDecorView(),"Disable "+cashier)==null,"open shift disable action visible"));
            trainingClick(second,R.id.cashier_back);
            awaitText(second,"No open shift","owner management return failed");
            trainingClick(second,R.id.cashier_reset_pass);
            awaitText(second,"Reset training cashier passphrase","reset screen missing");
            trainingField(second,R.id.cashier_username,cashier);
            trainingField(second,R.id.cashier_current_pass,ownerPass);
            trainingField(second,R.id.cashier_new_pass,resetPass);
            trainingField(second,R.id.cashier_confirm_pass,resetPass);
            trainingClick(second,R.id.cashier_rotate_pass);
            awaitText(second,"Training cashier passphrase reset","owner reset failed");
            trainingClick(second,R.id.cashier_back);
            awaitText(second,"No open shift","reset dashboard missing");
            trainingClick(second,R.id.cashier_lock);
            awaitText(second,"Training sign in","owner not locked after reset");
            trainingLogin(second,cashier,cashierPass);
            awaitText(second,"Sign in denied. Check your synthetic credentials or wait for the lockout.","old credential was not rejected");
            trainingLogin(second,cashier,resetPass);
            awaitText(second,"Unresolved training requests: 1","pending request not recovered after reset");
            trainingClick(second,R.id.cashier_change_pass);
            awaitText(second,"Change training passphrase","own credential screen missing");
            trainingField(second,R.id.cashier_current_pass,resetPass);
            trainingField(second,R.id.cashier_new_pass,newPass);
            trainingField(second,R.id.cashier_confirm_pass,"Mismatch-Synthetic-UI-01");
            trainingClick(second,R.id.cashier_rotate_pass);
            awaitText(second,"New synthetic passphrases do not match.","confirmation mismatch accepted");
            trainingField(second,R.id.cashier_current_pass,resetPass);
            trainingField(second,R.id.cashier_new_pass,newPass);
            trainingField(second,R.id.cashier_confirm_pass,newPass);
            trainingClick(second,R.id.cashier_rotate_pass);
            awaitText(second,"Training sign in","credential change did not sign out");
            trainingLogin(second,cashier,newPass);
            awaitText(second,"Unresolved training requests: 1","pending request not recovered after self change");
            runOnMainChecked(()->{
                require(!recovered.findViewById(R.id.cashier_new_checkout).isEnabled(),"new checkout not blocked by pending");
                require(!recovered.findViewById(R.id.cashier_close_shift).isEnabled(),"close shift not blocked by pending");
            });
            trainingClick(second,R.id.cashier_pending);
            awaitText(second,"Pending training checkouts","pending list missing");
            trainingSelect(second,"Training document print · PHP 5.00");
            awaitText(second,"Pending training checkout","original pending request missing");
            trainingClick(second,R.id.cashier_record);
            awaitText(second,"Recorded training receipt","training receipt missing");
            trainingClick(second,R.id.cashier_return_home);
            awaitText(second,"Shift open · Expected cash: PHP 15.00","cash total not net sale");
            trainingClick(second,R.id.cashier_new_checkout);
            awaitText(second,"New training checkout","second training checkout missing");
            trainingField(second,R.id.cashier_count,"1");
            trainingField(second,R.id.cashier_cash,"3.00");
            trainingClick(second,R.id.cashier_review);
            awaitText(second,"Review training checkout","second review missing");
            trainingClick(second,R.id.cashier_stage);
            awaitText(second,"Pending training checkout","second request missing");
            trainingField(second,R.id.cashier_cancel_reason,"Synthetic test cancellation");
            trainingClick(second,R.id.cashier_cancel);
            awaitText(second,"No pending training checkouts","training cancellation not recovered");
            trainingClick(second,R.id.cashier_close_shift);
            awaitText(second,"Close training shift","shift reconciliation missing");
            trainingField(second,R.id.cashier_counted,"14.50");
            trainingClick(second,R.id.cashier_confirm_close);
            awaitText(second,"Variance: PHP -0.50","training reconciliation wrong");
            trainingClick(second,R.id.cashier_return_home);
            awaitText(second,"No open shift","shift remained open");
            trainingClick(second,R.id.cashier_history);
            awaitText(second,"Recorded training receipts","history missing");
            trainingSelect(second,"Training document print · PHP 5.00");
            awaitText(second,"Recorded training receipt","historical receipt missing");
            trainingClick(second,R.id.cashier_return_home);
            awaitText(second,"No open shift","historical receipt changed shift");
            trainingClick(second,R.id.cashier_lock);
            awaitText(second,"Training sign in","cashier did not lock");
            trainingLogin(second,owner,ownerPass);
            awaitText(second,"No open shift","owner login for disable failed");
            trainingClick(second,R.id.cashier_manage_staff);
            awaitText(second,cashier+" · Active","closed shift cashier not eligible");
            trainingSelect(second,"Disable "+cashier);
            awaitText(second,"Confirm permanent training disable","disable confirmation missing");
            trainingField(second,R.id.cashier_disable_reason,"Synthetic end of training");
            trainingField(second,R.id.cashier_disable_pass,"Synthetic-Wrong-Owner-Proof-01");
            trainingClick(second,R.id.cashier_disable_confirm);
            awaitText(second,"Credential confirmation denied. Check your current synthetic passphrase or wait for lockout.","incorrect owner proof accepted");
            trainingField(second,R.id.cashier_disable_pass,ownerPass);
            trainingClick(second,R.id.cashier_disable_confirm);
            awaitText(second,"Training cashier disabled","cashier disable not confirmed");
            trainingClick(second,R.id.cashier_staff_refresh);
            awaitText(second,cashier+" · Disabled","disabled account not listed");
            awaitText(second,"Reason: Synthetic end of training","disable audit reason missing");
            trainingClick(second,R.id.cashier_back);
            awaitText(second,"No open shift","owner return after disable failed");
            trainingClick(second,R.id.cashier_lock);
            awaitText(second,"Training sign in","owner did not lock");
            trainingLogin(second,cashier,newPass);
            awaitText(second,"Sign in denied. Check your synthetic credentials or wait for the lockout.","disabled user signed in");
            try(CatalogStore fixture=CatalogStore.checkoutFixture(context,fixtureId)){
                require(trainingCount(fixture,"SELECT COUNT(*) FROM checkout_sales")==1,"not exactly one recorded sale");
                require(trainingCount(fixture,"SELECT COUNT(*) FROM checkout_cash")==1,"not exactly one cash payment");
                require(trainingCount(fixture,"SELECT COUNT(*) FROM checkout_requests WHERE state='APPLIED'")==1,"wrong applied request count");
                require(trainingCount(fixture,"SELECT COUNT(*) FROM checkout_requests WHERE state='CANCELLED'")==1,"wrong cancelled request count");
                require(trainingCount(fixture,"SELECT COUNT(*) FROM cashier_shifts WHERE state='CLOSED' AND expected_cash=1500 AND counted_cash=1450 AND variance=-50")==1,"shift audit mismatch");
                require(trainingCount(fixture,"SELECT COUNT(*) FROM stock_movements")==4,"training stock movement count wrong (initial, addition, removal, checkout)");
                require(trainingCount(fixture,"SELECT COUNT(*) FROM stock_requests WHERE state='APPLIED'")==2,"UI owner stock requests not applied exactly once");
                require(trainingCount(fixture,"SELECT COUNT(*) FROM stock_requests WHERE state='CANCELLED'")==1,"UI owner cancellation missing");
                require(trainingCount(fixture,"SELECT COUNT(*) FROM stock_movements WHERE reason='Synthetic UI addition' AND delta=1000 AND before_quantity=50000 AND after_quantity=51000")==1,"owner UI addition audit mismatch");
                require(trainingCount(fixture,"SELECT COUNT(*) FROM stock_movements WHERE reason='Synthetic UI removal' AND delta=-1000 AND before_quantity=51000 AND after_quantity=50000")==1,"owner UI removal audit mismatch");
                require(trainingCount(fixture,"SELECT after_quantity FROM stock_movements ORDER BY stock_revision DESC LIMIT 1")==48000,"training stock not deducted exactly");
                require(trainingCount(fixture,"SELECT COUNT(*) FROM cashier_sessions WHERE revoked_ms IS NULL")==0,"session still active after logout");
                require(trainingCount(fixture,"SELECT COUNT(*) FROM cashier_credential_events")==2,"UI credential audit count wrong");
                require(trainingCount(fixture,"SELECT COUNT(*) FROM cashier_disable_events WHERE reason='Synthetic end of training' AND actor_id<>account_id AND prior_revision=3 AND next_revision=4")==1,"UI disable audit absent or wrong revision");
                require(trainingCount(fixture,"SELECT COUNT(*) FROM cashier_accounts WHERE role='CASHIER' AND active=0 AND revision=4")==1,"UI cashier not disabled");
            }
            try(CatalogStore ordinary=new CatalogStore(context)){
                java.util.List<CatalogEntry> after=ordinary.list(true);
                require(before.size()==after.size(),"ordinary catalog changed");
                for(CatalogEntry prior:before){
                    CatalogEntry current=ordinary.get(prior.id);
                    require(current.revision==prior.revision&&current.centavos==prior.centavos&&current.archived==prior.archived,"ordinary catalog row changed");
                }
                require(trainingCount(ordinary,"SELECT COUNT(*) FROM checkout_sales")==0,"ordinary preview received training sale");
            }
        }finally{
            if(first!=null){final Activity a=first;runOnMainChecked(a::finish);}
            if(second!=null){final Activity a=second;runOnMainChecked(a::finish);}
            removeMonitor(monitor);
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
                require(store.getReadableDatabase().getVersion()==9,"v4-to-v9 migration version wrong");
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

    private static long checkoutCount(CatalogStore store,String table,String id){
        try(android.database.Cursor c=store.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM "+table+" WHERE request_id=?",new String[]{id})){
            require(c.moveToFirst(),"checkout count unavailable");return c.getLong(0);
        }
    }
    private void verifyCheckoutMigration(int former){
        android.content.Context context=getTargetContext();
        String fixture=stockId(),filename=former>=7?"checkout-fixture-"+fixture+".db":"checkout-v"+former+"-"+fixture+".db";
        String material=stockId(),pending=stockId(),historic=stockId(),pendingCheckout=stockId();
        String migratedToken=null,migratedShift=stockId();
        try{
            try(android.database.sqlite.SQLiteDatabase db=context.openOrCreateDatabase(filename,0,null)){
                db.execSQL("CREATE TABLE catalog (id TEXT PRIMARY KEY NOT NULL, kind TEXT NOT NULL CHECK(kind IN ('SERVICE','MATERIAL')), name TEXT NOT NULL, unit TEXT NOT NULL, centavos INTEGER NOT NULL CHECK(centavos BETWEEN 0 AND 99999999999), revision INTEGER NOT NULL CHECK(revision >= 1), archived INTEGER NOT NULL CHECK(archived IN (0,1)))");
                db.execSQL("CREATE TABLE catalog_history (id TEXT NOT NULL REFERENCES catalog(id), revision INTEGER NOT NULL, kind TEXT NOT NULL, name TEXT NOT NULL, unit TEXT NOT NULL, centavos INTEGER NOT NULL, archived INTEGER NOT NULL, action TEXT NOT NULL CHECK(action IN ('CREATE','EDIT','ARCHIVE','RESTORE')), PRIMARY KEY(id,revision))");
                db.execSQL("CREATE TABLE stock_movements (request_id TEXT PRIMARY KEY NOT NULL, material_id TEXT NOT NULL REFERENCES catalog(id), catalog_revision INTEGER NOT NULL CHECK(catalog_revision>=1), stock_revision INTEGER NOT NULL CHECK(stock_revision>=1), unit TEXT NOT NULL, reason TEXT NOT NULL, delta INTEGER NOT NULL CHECK(delta BETWEEN -999999999999 AND 999999999999 AND delta<>0), before_quantity INTEGER NOT NULL CHECK(before_quantity BETWEEN 0 AND 999999999999), after_quantity INTEGER NOT NULL CHECK(after_quantity BETWEEN 0 AND 999999999999 AND after_quantity=before_quantity+delta), created_ms INTEGER NOT NULL, UNIQUE(material_id,stock_revision))");
                db.execSQL("CREATE TABLE service_materials (service_id TEXT NOT NULL REFERENCES catalog(id), material_id TEXT NOT NULL REFERENCES catalog(id), quantity_milli INTEGER NOT NULL CHECK(quantity_milli BETWEEN 1 AND 999999999999), unit TEXT NOT NULL, PRIMARY KEY(service_id,material_id))");
                db.execSQL("CREATE TABLE recipe_history (service_id TEXT NOT NULL REFERENCES catalog(id), revision INTEGER NOT NULL CHECK(revision>=1), material_id TEXT NOT NULL REFERENCES catalog(id), quantity_milli INTEGER NOT NULL CHECK(quantity_milli BETWEEN 0 AND 999999999999), unit TEXT NOT NULL, material_revision INTEGER NOT NULL, service_revision INTEGER NOT NULL, created_ms INTEGER NOT NULL, PRIMARY KEY(service_id,revision))");
                db.execSQL("CREATE TABLE pricing_profile_versions (service_id TEXT NOT NULL REFERENCES catalog(id), revision INTEGER NOT NULL CHECK(revision>=1), method TEXT NOT NULL CHECK(method IN ('FIXED','PER_UNIT','PER_PAGE','PER_COPY','PER_SHEET','PER_SQFT','PER_LINEAR_FT','PER_METER','TIERED_BULK')), pages_copies INTEGER NOT NULL CHECK(pages_copies IN (0,1) AND (pages_copies=0 OR method IN ('PER_PAGE','PER_COPY','PER_SHEET'))), service_revision INTEGER NOT NULL CHECK(service_revision>=1), catalog_unit TEXT NOT NULL, created_ms INTEGER NOT NULL, PRIMARY KEY(service_id,revision))");
                db.execSQL("CREATE TABLE pricing_tier_versions (service_id TEXT NOT NULL, profile_revision INTEGER NOT NULL, threshold_hundredths INTEGER NOT NULL CHECK(threshold_hundredths BETWEEN 1 AND 10000000), rate_centavos INTEGER NOT NULL CHECK(rate_centavos BETWEEN 0 AND 99999999999), PRIMARY KEY(service_id,profile_revision,threshold_hundredths), FOREIGN KEY(service_id,profile_revision) REFERENCES pricing_profile_versions(service_id,revision))");
                db.execSQL("CREATE TABLE stock_requests (request_id TEXT PRIMARY KEY NOT NULL, material_id TEXT NOT NULL REFERENCES catalog(id), catalog_revision INTEGER NOT NULL CHECK(catalog_revision>=1), expected_stock_revision INTEGER NOT NULL CHECK(expected_stock_revision>=0 AND expected_stock_revision<9223372036854775807), delta INTEGER NOT NULL CHECK(delta BETWEEN -999999999999 AND 999999999999 AND delta<>0), reason TEXT NOT NULL CHECK(length(reason) BETWEEN 1 AND 160), created_ms INTEGER NOT NULL, state TEXT NOT NULL CHECK(state IN ('PENDING','APPLIED','CANCELLED')), resolved_ms INTEGER, CHECK((state='PENDING' AND resolved_ms IS NULL) OR (state<>'PENDING' AND resolved_ms IS NOT NULL)))");
                db.execSQL("CREATE UNIQUE INDEX one_pending_stock_request ON stock_requests(material_id) WHERE state='PENDING'");
                db.execSQL("CREATE TRIGGER stock_request_no_delete BEFORE DELETE ON stock_requests BEGIN SELECT RAISE(ABORT,'Stock request audit cannot be deleted'); END");
                db.execSQL("CREATE TRIGGER stock_request_initial_state BEFORE INSERT ON stock_requests WHEN NEW.state<>'PENDING' BEGIN SELECT RAISE(ABORT,'Stock request must begin pending'); END");
                db.execSQL("CREATE TRIGGER stock_request_resolution BEFORE UPDATE ON stock_requests WHEN OLD.state<>'PENDING' OR NEW.state NOT IN ('APPLIED','CANCELLED') OR NEW.request_id IS NOT OLD.request_id OR NEW.material_id IS NOT OLD.material_id OR NEW.catalog_revision IS NOT OLD.catalog_revision OR NEW.expected_stock_revision IS NOT OLD.expected_stock_revision OR NEW.delta IS NOT OLD.delta OR NEW.reason IS NOT OLD.reason OR NEW.created_ms IS NOT OLD.created_ms OR (NEW.state='APPLIED' AND NOT EXISTS (SELECT 1 FROM stock_movements m WHERE m.request_id=OLD.request_id AND m.material_id=OLD.material_id AND m.catalog_revision=OLD.catalog_revision AND m.stock_revision=OLD.expected_stock_revision+1 AND m.delta=OLD.delta AND m.reason=OLD.reason)) OR (NEW.state='CANCELLED' AND EXISTS (SELECT 1 FROM stock_movements m WHERE m.request_id=OLD.request_id)) BEGIN SELECT RAISE(ABORT,'Invalid stock request transition'); END");
                db.execSQL("INSERT INTO catalog VALUES(?,?,?,?,?,?,?)",new Object[]{material,"MATERIAL","Synthetic old paper","sheet",199,1,0});
                db.execSQL("INSERT INTO catalog_history VALUES(?,?,?,?,?,?,?,?)",new Object[]{material,1,"MATERIAL","Synthetic old paper","sheet",199,0,"CREATE"});
                db.execSQL("INSERT INTO stock_movements VALUES(?,?,?,?,?,?,?,?,?,?)",new Object[]{stockId(),material,1,1,"sheet","Synthetic old receipt",1500,0,1500,1});
                db.execSQL("INSERT INTO stock_requests VALUES(?,?,?,?,?,?,?,?,?)",new Object[]{pending,material,1,1,-500,"Synthetic pending",1,"PENDING",null});
                if(former>=6){
                    CheckoutJournal.create(db);
                    CatalogEntry service=CatalogEntry.create(CatalogEntry.Kind.SERVICE,"Synthetic historic service","page","2.50");
                    db.execSQL("INSERT INTO catalog VALUES(?,?,?,?,?,?,?)",new Object[]{service.id,"SERVICE",service.name,service.unit,service.centavos,1,0});
                    CheckoutPlan.Proposal plan=CheckoutPlan.prepare(historic,service,PricingProfile.defaults(),0,
                        new CheckoutPlan.Inputs(1,1,1,null,null,null,CheckoutPlan.UsageBasis.NONE,250),java.util.Collections.emptyList());
                    CashierJournal.Shift shift=null;
                    if(former>=7){
                        CashierJournal.create(db);
                        if(former==8)CredentialJournal.upgrade(db);
                        char[] synthetic="Synthetic-Migration-Owner-01".toCharArray();
                        try{
                            long now=System.currentTimeMillis();
                            CashierJournal.Principal owner=CashierJournal.bootstrap(db,"migration-owner",synthetic,now);
                            migratedToken=CashierJournal.signIn(db,owner.username,synthetic,now);
                            shift=CashierJournal.openShift(db,owner,migratedShift,1000,now);
                            if(former==8){
                                CashierJournal.Principal legacy=CashierJournal.createCashier(db,owner,"legacy-disabled",synthetic,now);
                                CashierJournal.createCashier(db,owner,"legacy-rotated",synthetic,now);
                                char[] changed="Synthetic-Migration-Rotated-02".toCharArray();
                                try{require(CashierJournal.resetCashierCredential(db,owner,synthetic,"legacy-rotated",changed,now),"v8 credential fixture failed");}
                                finally{java.util.Arrays.fill(changed,'\0');}
                                db.execSQL("UPDATE cashier_accounts SET active=0,revision=2 WHERE id=?",new Object[]{legacy.id});
                            }
                        }finally{java.util.Arrays.fill(synthetic,'\0');}
                    }
                    CheckoutJournal.stage(db,plan);
                    if(shift!=null)CashierJournal.link(db,historic,shift);
                    CheckoutJournal.saleAndCash(db,CheckoutJournal.read(db,historic));CheckoutJournal.resolve(db,historic,"APPLIED",null);
                    if(shift!=null){
                        CheckoutPlan.Proposal unresolved=CheckoutPlan.prepare(pendingCheckout,service,PricingProfile.defaults(),0,
                            new CheckoutPlan.Inputs(1,1,1,null,null,null,CheckoutPlan.UsageBasis.NONE,250),java.util.Collections.emptyList());
                        CheckoutJournal.stage(db,unresolved);CashierJournal.link(db,pendingCheckout,shift);
                    }
                }
                db.setVersion(former);
            }
            try(CatalogStore store=former>=7?CatalogStore.checkoutFixture(context,fixture):new CatalogStore(context,filename)){
                require(store.getReadableDatabase().getVersion()==9,"v5/v6/v7/v8 migration failed");
                require(store.stockBalance(material).quantity==1500&&store.stockHistory(material,0,10).size()==1,"v5 stock changed");
                require(store.pendingStock(material)!=null&&store.pendingStock(material).id.equals(pending),"v5 pending stock lost");
                require(store.get(material).centavos==199,"v5 catalog changed");
                if(former>=7){
                    rejectStock(()->store.applyStagedStock(pending));
                    require(store.applyAuthorizedFixtureStock(migratedToken,pending).quantity==1000,"authenticated v7 pending stock not recoverable");
                }else require(store.applyStagedStock(pending).quantity==1000,"v5 pending request not recoverable");
                try(android.database.Cursor c=store.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM checkout_requests",null)){
                    require(c.moveToFirst()&&c.getInt(0)==(former>=7?2:former==6?1:0),"migration changed checkout history");
                }
                require(CashierJournal.initialized(store.getReadableDatabase())==(former>=7),"migration changed account initialization");
                require(trainingCount(store,"SELECT COUNT(*) FROM cashier_credential_events")==(former==8?1:0),"migration changed credential audit");
                require(trainingCount(store,"SELECT COUNT(*) FROM cashier_disable_events")==0,"migration invented disable events");
                if(former==8){
                    require(trainingCount(store,"SELECT COUNT(*) FROM cashier_accounts WHERE username='legacy-disabled' AND active=0 AND revision=2")==1,"v8 disabled identity lost");
                    require(trainingCount(store,"SELECT COUNT(*) FROM cashier_accounts WHERE username='legacy-rotated' AND active=1 AND revision=2")==1,"v8 credential revision lost");
                }
                if(former>=6)require(CheckoutJournal.read(store.getReadableDatabase(),historic).state.equals("APPLIED"),"historic sale lost");
                if(former>=7){
                    CashierJournal.Principal owner=CashierJournal.requireSession(store.getReadableDatabase(),migratedToken,System.currentTimeMillis());
                    require(CashierJournal.activeShift(store.getReadableDatabase(),owner).id.equals(migratedShift),"v7 open shift lost");
                    require(CheckoutJournal.read(store.getReadableDatabase(),pendingCheckout).state.equals("PENDING"),"v7 pending checkout lost");
                    require(CashierJournal.expected(store.getReadableDatabase(),CashierJournal.shift(store.getReadableDatabase(),migratedShift))==1250,"v7 drawer changed");
                }
            }
        }finally{context.deleteDatabase(filename);}
    }

    private void verifyStaffDisable(){
        android.content.Context context=getTargetContext();
        String fixture=stockId(),filename="checkout-fixture-"+fixture+".db";
        char[] owner="Synthetic-Disable-Owner-01".toCharArray(),staff="Synthetic-Disable-Cashier-02".toCharArray(),
            wrong="Synthetic-Wrong-Owner-Proof-03".toCharArray(),replacement="Synthetic-New-Cashier-04".toCharArray();
        String targetId,ownerId,shift=stockId(),sale=stockId();
        try{
            try(CatalogStore ordinary=new CatalogStore(context)){
                rejectStock(()->ordinary.authorizedFixtureStaff("bad"));
                rejectStock(()->ordinary.disableFixtureCashier("bad",stockId(),1,owner,"Synthetic denied"));
            }
            try(CatalogStore store=CatalogStore.checkoutFixture(context,fixture)){
                ownerId=store.bootstrapFixtureOwner("disable-owner",owner).id;
                String ownerToken=store.signInFixture("disable-owner",owner);
                CashierJournal.Principal target=store.createFixtureCashier(ownerToken,"disable-cashier",staff);
                targetId=target.id;
                String cashier=store.signInFixture("disable-cashier",staff);
                rejectStock(()->store.authorizedFixtureStaff(cashier));
                rejectStock(()->store.disableFixtureCashier(cashier,target.id,1,staff,"Synthetic forbidden"));
                rejectStock(()->store.disableFixtureCashier(ownerToken,ownerId,1,owner,"Synthetic owner forbidden"));
                rejectStock(()->store.disableFixtureCashier(ownerToken,target.id,2,owner,"Synthetic stale revision"));
                rejectStock(()->store.disableFixtureCashier(ownerToken,target.id,1,owner,""));
                rejectStock(()->store.disableFixtureCashier(ownerToken,target.id,1,wrong,"Synthetic wrong proof"));
                require(trainingCount(store,"SELECT COUNT(*) FROM cashier_accounts WHERE role='OWNER' AND failures=1")==1,"failed owner proof not persisted");
                require(trainingCount(store,"SELECT COUNT(*) FROM cashier_disable_events")==0,"failed action created audit");
                require(store.fixturePrincipal(cashier).id.equals(target.id),"failed action revoked session");
                CatalogEntry service=store.createAuthorizedFixtureCatalog(ownerToken,CatalogEntry.Kind.SERVICE,"Synthetic disable print","page","2.50");
                store.openFixtureShift(cashier,shift,1000);
                CheckoutPlan.Proposal plan=store.prepareCheckout(sale,service.id,1,0,0,new CheckoutPlan.Inputs(2,1,1,null,null,null,CheckoutPlan.UsageBasis.NONE,600));
                store.stageAuthorizedFixtureCheckout(cashier,shift,plan);
                require(store.authorizedFixtureStaff(ownerToken).get(0).openShift,"owner did not see open shift");
                rejectStock(()->store.disableFixtureCashier(ownerToken,target.id,1,owner,"Synthetic pending denial"));
                require(store.authorizedFixtureCheckout(cashier,sale).state.equals("PENDING"),"denial altered pending checkout");
                store.applyAuthorizedFixtureCheckout(cashier,sale,plan.fingerprint());
                rejectStock(()->store.disableFixtureCashier(ownerToken,target.id,1,owner,"Synthetic open shift denial"));
                store.closeFixtureShift(cashier,shift,1500);
                // Credential change makes an already reviewed account revision stale.
                store.resetFixtureCashierPassphrase(ownerToken,owner,"disable-cashier",replacement);
                String first=store.signInFixture("disable-cashier",replacement),second=store.signInFixture("disable-cashier",replacement);
                rejectStock(()->store.disableFixtureCashier(ownerToken,target.id,1,owner,"Synthetic stale confirmation"));
                require(store.authorizedFixtureStaff(ownerToken).get(0).revision==2,"owner did not see fresh revision");
                store.getWritableDatabase().execSQL("CREATE TRIGGER synthetic_disable_abort BEFORE UPDATE ON cashier_accounts WHEN NEW.active=0 BEGIN SELECT RAISE(ABORT,'Synthetic test fault'); END");
                boolean failed=false;
                try{store.disableFixtureCashier(ownerToken,target.id,2,owner,"Synthetic failure injection");}
                catch(android.database.sqlite.SQLiteException expected){failed=true;}
                require(failed,"injected disable did not fail");
                require(trainingCount(store,"SELECT COUNT(*) FROM cashier_disable_events")==0,"fault left orphan event");
                require(store.fixturePrincipal(first).id.equals(target.id)&&store.fixturePrincipal(second).id.equals(target.id),"fault revoked session");
                store.getWritableDatabase().execSQL("DROP TRIGGER synthetic_disable_abort");
                store.disableFixtureCashier(ownerToken,target.id,2,owner,"Synthetic training completed");
                rejectStock(()->store.fixturePrincipal(first));rejectStock(()->store.fixturePrincipal(second));
                rejectStock(()->store.signInFixture("disable-cashier",replacement));
                rejectStock(()->store.disableFixtureCashier(ownerToken,target.id,3,owner,"Synthetic repeat denied"));
                rejectStock(()->store.resetFixtureCashierPassphrase(ownerToken,owner,"disable-cashier",staff));
                rejectStock(()->store.createFixtureCashier(ownerToken,"disable-cashier",staff));
                CashierJournal.StaffSummary disabled=store.authorizedFixtureStaff(ownerToken).get(0);
                require(!disabled.active&&!disabled.openShift&&disabled.revision==3&&disabled.disableReason.equals("Synthetic training completed"),"disable summary wrong");
                require(store.fixturePrincipal(ownerToken).id.equals(ownerId),"owner session lost");
                require(trainingCount(store,"SELECT COUNT(*) FROM cashier_disable_events WHERE prior_revision=2 AND next_revision=3 AND actor_revision=1")==1,"exact disable audit missing");
                require(trainingCount(store,"SELECT COUNT(*) FROM cashier_sessions s JOIN cashier_accounts a ON a.id=s.account_id WHERE a.role='CASHIER' AND revoked_ms IS NULL")==0,"target session not durably revoked");
                require(trainingCount(store,"SELECT COUNT(*) FROM checkout_sales")==1&&trainingCount(store,"SELECT COUNT(*) FROM checkout_cash")==1,"disable changed historical sale");
                require(trainingCount(store,"SELECT COUNT(*) FROM cashier_shifts WHERE state='CLOSED' AND expected_cash=1500 AND variance=0")==1,"disable changed shift");
            }
            try(CatalogStore reopened=CatalogStore.checkoutFixture(context,fixture)){
                String token=reopened.signInFixture("disable-owner",owner);
                CashierJournal.StaffSummary disabled=reopened.authorizedFixtureStaff(token).get(0);
                require(disabled.id.equals(targetId)&&!disabled.active&&disabled.revision==3,"disabled identity not durable");
                require(trainingCount(reopened,"SELECT COUNT(*) FROM cashier_disable_events")==1,"disable event not durable");
                require(trainingCount(reopened,"SELECT COUNT(*) FROM checkout_sales")==1,"historical sale not durable");
                rejectStock(()->reopened.signInFixture("disable-cashier",replacement));
            }
        }finally{
            java.util.Arrays.fill(owner,'\0');java.util.Arrays.fill(staff,'\0');java.util.Arrays.fill(wrong,'\0');java.util.Arrays.fill(replacement,'\0');context.deleteDatabase(filename);
        }
    }

    private void verifyOwnerAdministration(){
        android.content.Context context=getTargetContext();
        String fixture=stockId(),filename="checkout-fixture-"+fixture+".db";
        char[] owner="Synthetic-Admin-Owner-01".toCharArray(),staff="Synthetic-Admin-Cashier-02".toCharArray();
        try{
            try(CatalogStore ordinary=new CatalogStore(context)){
                rejectStock(()->ordinary.createAuthorizedFixtureCatalog("invalid",CatalogEntry.Kind.MATERIAL,"Denied ordinary","sheet","1.00"));
                rejectStock(()->ordinary.authorizedFixtureInventory("invalid"));
                rejectStock(()->ordinary.applyAuthorizedFixtureStock("invalid",stockId()));
            }
            try(CatalogStore store=CatalogStore.checkoutFixture(context,fixture)){
                rejectStock(()->store.createAuthorizedFixtureCatalog(null,CatalogEntry.Kind.SERVICE,"Denied unauthenticated","page","2.50"));
                CatalogEntry service=store.create(CatalogEntry.Kind.SERVICE,"Synthetic pre-auth fixture","page","2.50");
                store.bootstrapFixtureOwner("admin-owner",owner);
                String token=store.signInFixture("admin-owner",owner);
                store.createFixtureCashier(token,"admin-cashier",staff);
                String cashier=store.signInFixture("admin-cashier",staff);
                CatalogEntry material=store.createAuthorizedFixtureCatalog(token,CatalogEntry.Kind.MATERIAL,"Synthetic admin paper","sheet","1.00");
                String materialId=material.id,serviceId=service.id,request=stockId();
                java.util.List<PricingMath.Tier> tiers=java.util.Collections.emptyList();
                // All ordinary bypasses, null/invalid tokens and authenticated non-owner writes must fail.
                rejectStock(()->store.create(CatalogEntry.Kind.MATERIAL,"Denied","sheet","1.00"));
                rejectStock(()->store.edit(materialId,1,"Denied","sheet","2.00"));
                rejectStock(()->store.archive(materialId,1,true));
                rejectStock(()->store.savePricing(serviceId,1,0,PricingMath.Method.PER_PAGE,true,tiers));
                rejectStock(()->store.setMaterialUsage(serviceId,1,materialId,1,0,1000));
                rejectStock(()->store.stageStock(request,materialId,1,0,5000,"Denied"));
                rejectStock(()->store.moveStock(request,materialId,1,0,5000,"Denied"));
                rejectStock(()->store.createAuthorizedFixtureCatalog(cashier,CatalogEntry.Kind.MATERIAL,"Denied","sheet","1.00"));
                rejectStock(()->store.createAuthorizedFixtureCatalog(null,CatalogEntry.Kind.MATERIAL,"Denied","sheet","1.00"));
                rejectStock(()->store.createAuthorizedFixtureCatalog("invalid",CatalogEntry.Kind.MATERIAL,"Denied","sheet","1.00"));
                rejectStock(()->store.editAuthorizedFixtureCatalog(cashier,materialId,1,"Denied","sheet","2.00"));
                rejectStock(()->store.archiveAuthorizedFixtureCatalog(cashier,materialId,1,true));
                rejectStock(()->store.saveAuthorizedFixturePricing(cashier,serviceId,1,0,PricingMath.Method.PER_PAGE,true,tiers));
                rejectStock(()->store.setAuthorizedFixtureMaterialUsage(cashier,serviceId,1,materialId,1,0,1000));
                rejectStock(()->store.stageAuthorizedFixtureStock(cashier,request,materialId,1,0,5000,"Denied"));
                rejectStock(()->store.authorizedFixtureInventory(cashier));
                rejectStock(()->store.authorizedFixtureInventory(null));
                require(store.list(true).size()==2&&store.get(materialId).revision==1&&store.recipeRevision(serviceId)==0&&store.pricing(serviceId).revision==0,"denied admin mutation changed catalog");
                require(store.stockBalance(materialId).quantity==0&&store.stockRequest(request)==null,"denied admin mutation changed stock");
                try(CatalogStore alias=new CatalogStore(context,filename)){
                    rejectStock(()->alias.create(CatalogEntry.Kind.MATERIAL,"Denied alias","sheet","1.00"));
                    rejectStock(()->alias.moveStock(request,materialId,1,0,5000,"Denied alias"));
                    rejectStock(()->alias.createAuthorizedFixtureCatalog(token,CatalogEntry.Kind.MATERIAL,"Denied alias","sheet","1.00"));
                }
                CatalogEntry edited=store.editAuthorizedFixtureCatalog(token,serviceId,1,"Synthetic authorized service","page","2.50");
                require(edited.revision==2,"owner edit not recorded");
                CatalogEntry spare=store.createAuthorizedFixtureCatalog(token,CatalogEntry.Kind.MATERIAL,"Synthetic spare","sheet","1.00");
                require(store.archiveAuthorizedFixtureCatalog(token,spare.id,1,true).archived,"owner archive refused");
                require(!store.archiveAuthorizedFixtureCatalog(token,spare.id,2,false).archived,"owner restore refused");
                require(store.saveAuthorizedFixturePricing(token,serviceId,2,0,PricingMath.Method.PER_PAGE,true,tiers).revision==1,"owner pricing refused");
                require(store.saveAuthorizedFixturePricing(token,serviceId,2,1,PricingMath.Method.PER_UNIT,false,tiers).revision==2,"owner pricing restore refused");
                require(store.setAuthorizedFixtureMaterialUsage(token,serviceId,2,materialId,1,0,1000)==1,"owner recipe refused");
                require(store.stageAuthorizedFixtureStock(token,request,materialId,1,0,5000,"Synthetic owner addition").state.equals("PENDING"),"owner stage refused");
                require(store.stockBalance(materialId).quantity==0,"stage changed stock");
                rejectStock(()->store.applyStagedStock(request));rejectStock(()->store.cancelStagedStock(request));
                rejectStock(()->store.applyAuthorizedFixtureStock(cashier,request));
                rejectStock(()->store.cancelAuthorizedFixtureStock(cashier,request));
                rejectStock(()->store.applyAuthorizedFixtureStock(null,request));
                require(store.authorizedFixtureInventory(token).stream().anyMatch(i->i.material.id.equals(materialId)&&i.pending!=null&&i.pending.id.equals(request)),"pending owner inventory not recoverable");
                require(store.applyAuthorizedFixtureStock(token,request).quantity==5000,"owner apply refused");
                require(store.applyAuthorizedFixtureStock(token,request).revision==1,"owner replay duplicated movement");
                rejectStock(()->store.applyAuthorizedFixtureStock(cashier,request));
                rejectStock(()->store.cancelAuthorizedFixtureStock(token,request));
                String cancelled=stockId();
                store.stageAuthorizedFixtureStock(token,cancelled,materialId,1,1,-1000,"Synthetic pending removal");
                store.cancelAuthorizedFixtureStock(token,cancelled);
                require(store.stockRequest(cancelled).state.equals("CANCELLED")&&store.stockBalance(materialId).quantity==5000,"owner cancellation mutated stock");
                rejectStock(()->store.applyAuthorizedFixtureStock(token,cancelled));
                String shift=stockId(),sale=stockId();
                store.openFixtureShift(cashier,shift,0);
                CheckoutPlan.Proposal plan=store.prepareCheckout(sale,serviceId,2,2,1,
                    new CheckoutPlan.Inputs(1,1,1,null,null,null,CheckoutPlan.UsageBasis.BILLABLE_UNIT,300));
                store.stageAuthorizedFixtureCheckout(cashier,shift,plan);
                String added=stockId();
                store.stageAuthorizedFixtureStock(token,added,materialId,1,1,1000,"Synthetic changed stock");
                store.applyAuthorizedFixtureStock(token,added);
                rejectStock(()->store.applyAuthorizedFixtureCheckout(cashier,sale,plan.fingerprint()));
                require(store.authorizedFixtureCheckout(cashier,sale).state.equals("PENDING")&&store.expectedFixtureCash(cashier,shift)==0,"stale checkout altered cash or request");
                require(trainingCount(store,"SELECT COUNT(*) FROM checkout_sales")==0,"stale checkout recorded");
                store.cancelAuthorizedFixtureCheckout(cashier,sale,plan.fingerprint(),"Synthetic stale checkout");
                require(store.stockBalance(materialId).quantity==6000&&store.stockBalance(materialId).revision==2,"rejected checkout changed stock");
                String recovery=stockId();
                store.stageAuthorizedFixtureStock(token,recovery,materialId,1,2,-1000,"Synthetic recovery");
                store.signOutFixture(token);
                rejectStock(()->store.applyAuthorizedFixtureStock(token,recovery));
                rejectStock(()->store.cancelAuthorizedFixtureStock(token,recovery));
                rejectStock(()->store.authorizedFixtureInventory(token));
                // A new authenticated owner, after database reopen, can resolve original identity.
                try(CatalogStore reopened=CatalogStore.checkoutFixture(context,fixture)){
                    String again=reopened.signInFixture("admin-owner",owner);
                    require(reopened.authorizedFixtureInventory(again).stream().anyMatch(i->i.pending!=null&&i.pending.id.equals(recovery)),"owner pending after reopen missing");
                    require(reopened.applyAuthorizedFixtureStock(again,recovery).quantity==5000,"original owner stock recovery failed");
                    require(reopened.applyAuthorizedFixtureStock(again,recovery).revision==3,"recovery replay duplicated movement");
                    require(trainingCount(reopened,"SELECT COUNT(*) FROM stock_movements")==3,"owner stock history wrong");
                }
            }
        }finally{
            java.util.Arrays.fill(owner,'\0');java.util.Arrays.fill(staff,'\0');context.deleteDatabase(filename);
        }
    }

    private void verifyCredentialLifecycle(){
        android.content.Context context=getTargetContext();
        String fixture=stockId(),filename="checkout-fixture-"+fixture+".db",shift=stockId(),request=stockId();
        char[] owner="Synthetic-Owner-Original-01".toCharArray(),staff="Synthetic-Cashier-Original-02".toCharArray(),
            changed="Synthetic-Cashier-Reset-03".toCharArray(),next="Synthetic-Cashier-Changed-04".toCharArray(),
            ownerNext="Synthetic-Owner-Changed-05".toCharArray(),wrong="Synthetic-Wrong-Proof-06".toCharArray();
        String cashierId,ownerId;
        try{
            try(CatalogStore ordinary=new CatalogStore(context)){
                rejectStock(()->ordinary.changeFixturePassphrase("bad",owner,next));
                rejectStock(()->ordinary.resetFixtureCashierPassphrase("bad",owner,"fixture-cashier",next));
            }
            try(CatalogStore store=CatalogStore.checkoutFixture(context,fixture)){
                ownerId=store.bootstrapFixtureOwner("fixture-owner",owner).id;
                String ownerToken=store.signInFixture("fixture-owner",owner),ownerParallel=store.signInFixture("fixture-owner",owner);
                cashierId=store.createFixtureCashier(ownerToken,"fixture-cashier",staff).id;
                String cashier=store.signInFixture("fixture-cashier",staff),parallel=store.signInFixture("fixture-cashier",staff);
                CatalogEntry service=store.createAuthorizedFixtureCatalog(ownerToken,CatalogEntry.Kind.SERVICE,"Synthetic credential test","page","2.50");
                store.openFixtureShift(cashier,shift,1000);
                CheckoutPlan.Proposal proposal=store.prepareCheckout(request,service.id,1,0,0,
                    new CheckoutPlan.Inputs(2,1,1,null,null,null,CheckoutPlan.UsageBasis.NONE,600));
                store.stageAuthorizedFixtureCheckout(cashier,shift,proposal);
                rejectStock(()->store.resetFixtureCashierPassphrase(cashier,staff,"fixture-owner",changed));
                rejectStock(()->store.resetFixtureCashierPassphrase(ownerToken,owner,"fixture-owner",changed));
                rejectStock(()->store.resetFixtureCashierPassphrase(ownerToken,wrong,"fixture-cashier",changed));
                rejectStock(()->store.changeFixturePassphrase(cashier,staff,staff));
                for(int i=0;i<CashierSecurity.FAILURE_LIMIT;i++)rejectStock(()->store.changeFixturePassphrase(cashier,wrong,next));
                rejectStock(()->store.changeFixturePassphrase(cashier,staff,next));
                require(trainingCount(store,"SELECT COUNT(*) FROM cashier_accounts WHERE username='fixture-cashier' AND failures=5 AND locked_until>0")==1,"proof lockout not persisted");
                require(trainingCount(store,"SELECT COUNT(*) FROM cashier_credential_events")==0,"failed updates created audit");
                store.resetFixtureCashierPassphrase(ownerToken,owner,"fixture-cashier",changed);
                rejectStock(()->store.fixturePrincipal(cashier));rejectStock(()->store.fixturePrincipal(parallel));
                rejectStock(()->store.signInFixture("fixture-cashier",staff));
                String recovered=store.signInFixture("fixture-cashier",changed);
                CashierJournal.Principal p=store.fixturePrincipal(recovered);
                require(p.id.equals(cashierId)&&p.revision==2,"reset changed cashier identity");
                require(store.activeFixtureShift(recovered).id.equals(shift),"reset lost shift");
                require(store.pendingAuthorizedFixtureCheckouts(recovered,shift,10).get(0).id.equals(request),"reset lost original pending checkout");
                require(store.expectedFixtureCash(recovered,shift)==1000,"reset altered drawer");
                store.changeFixturePassphrase(recovered,changed,next);
                rejectStock(()->store.fixturePrincipal(recovered));rejectStock(()->store.signInFixture("fixture-cashier",changed));
                String newSession=store.signInFixture("fixture-cashier",next);
                require(store.fixturePrincipal(newSession).id.equals(cashierId)&&store.fixturePrincipal(newSession).revision==3,"self change identity/revision");
                require(store.applyAuthorizedFixtureCheckout(newSession,request,proposal.fingerprint()).total==500,"original request not recoverable");
                require(store.expectedFixtureCash(newSession,shift)==1500,"cash after recovery incorrect");
                store.closeFixtureShift(newSession,shift,1500);
                store.changeFixturePassphrase(ownerToken,owner,ownerNext);
                rejectStock(()->store.fixturePrincipal(ownerToken));rejectStock(()->store.fixturePrincipal(ownerParallel));
                rejectStock(()->store.signInFixture("fixture-owner",owner));
                String newOwner=store.signInFixture("fixture-owner",ownerNext);
                require(store.fixturePrincipal(newOwner).id.equals(ownerId),"owner identity changed");
                store.signOutFixture(newOwner);store.signOutFixture(newSession);
                require(trainingCount(store,"SELECT COUNT(*) FROM cashier_credential_events")==3,"credential audit count");
                require(trainingCount(store,"SELECT COUNT(*) FROM cashier_credential_events WHERE action='OWNER_RESET' AND actor_id<>account_id")==1,"owner reset audit");
                require(trainingCount(store,"SELECT COUNT(*) FROM cashier_credential_events WHERE action='SELF_CHANGE' AND actor_id=account_id")==2,"self change audit");
                require(trainingCount(store,"SELECT COUNT(*) FROM cashier_sessions WHERE revoked_ms IS NULL")==0,"stale session survived");
                require(trainingCount(store,"SELECT COUNT(*) FROM checkout_sales")==1,"credential change duplicated sale");
            }
            try(CatalogStore reopened=CatalogStore.checkoutFixture(context,fixture)){
                String token=reopened.signInFixture("fixture-cashier",next);
                require(reopened.fixturePrincipal(token).id.equals(cashierId),"new credential not durable");
                require(reopened.authorizedFixtureCheckout(token,request).state.equals("APPLIED"),"receipt not durable after reset");
                reopened.signOutFixture(token);
            }
        }finally{
            for(char[] secret:new char[][]{owner,staff,changed,next,ownerNext,wrong})java.util.Arrays.fill(secret,'\0');
            context.deleteDatabase(filename);
        }
    }

    private void verifyDurableCheckout(){
        android.content.Context context=getTargetContext();
        String fixture=stockId(),filename="checkout-fixture-"+fixture+".db";
        String first=stockId(),other=stockId(),second=stockId();
        String serviceId,paperId,inkId,firstHash,otherHash;
        CheckoutPlan.Inputs inputs=new CheckoutPlan.Inputs(1,5,2,null,null,null,CheckoutPlan.UsageBasis.BILLABLE_UNIT,2500);
        try{
            try(CatalogStore store=CatalogStore.checkoutFixture(context,fixture)){
                CatalogEntry service=store.create(CatalogEntry.Kind.SERVICE,"Synthetic checkout","page","2.50");
                CatalogEntry paper=store.create(CatalogEntry.Kind.MATERIAL,"Synthetic paper","sheet","1.99");
                CatalogEntry ink=store.create(CatalogEntry.Kind.MATERIAL,"Synthetic ink","ml","1.25");
                serviceId=service.id;paperId=paper.id;inkId=ink.id;
                store.moveStock(stockId(),paperId,1,0,5000,"Synthetic receipt");
                store.moveStock(stockId(),inkId,1,0,1000,"Synthetic receipt");
                store.setMaterialUsage(serviceId,1,paperId,1,0,500);
                store.setMaterialUsage(serviceId,1,inkId,1,1,100);
                store.savePricing(serviceId,1,0,PricingMath.Method.PER_PAGE,true,java.util.Collections.singletonList(new PricingMath.Tier(1000,225)));
                CheckoutPlan.Proposal plan=store.prepareCheckout(first,serviceId,1,1,2,inputs);
                firstHash=plan.fingerprint();
                require(store.stageFixtureCheckout(plan).state.equals("PENDING"),"checkout staging wrong");
                require(store.stageFixtureCheckout(plan).fingerprint.equals(firstHash),"exact stage replay wrong");
                CheckoutPlan.Proposal mismatch=store.prepareCheckout(first,serviceId,1,1,2,new CheckoutPlan.Inputs(1,5,2,null,null,null,CheckoutPlan.UsageBasis.BILLABLE_UNIT,2600));
                rejectStock(()->store.stageFixtureCheckout(mismatch));
                CheckoutPlan.Proposal competitor=store.prepareCheckout(other,serviceId,1,1,2,inputs);
                otherHash=competitor.fingerprint();store.stageFixtureCheckout(competitor);
                require(store.pendingFixtureCheckouts(50).size()==2,"pending checkout listing wrong");
                require(store.stockBalance(paperId).quantity==5000&&store.stockBalance(inkId).quantity==1000,"staging consumed stock");
                require(checkoutCount(store,"checkout_sales",first)==0&&checkoutCount(store,"checkout_cash",first)==0,"staging recorded payment");
                rejectStock(()->store.fixtureCheckout(first).previewReceipt());
                try(CatalogStore blocked=new CatalogStore(context,filename)){
                    rejectStock(()->blocked.stageFixtureCheckout(plan));
                    rejectStock(()->blocked.applyFixtureCheckout(first,firstHash));
                    rejectStock(()->blocked.cancelFixtureCheckout(first,firstHash,"Denied mutation"));
                }
            }
            try(CatalogStore reopened=CatalogStore.checkoutFixture(context,fixture)){
                require(reopened.fixtureCheckout(first).state.equals("PENDING"),"pending checkout lost after reopening");
                require(reopened.fixtureCheckout(first).fingerprint.equals(firstHash),"durable fingerprint lost");
                rejectStock(()->reopened.applyFixtureCheckout(first,"wrong"));
                reopened.getWritableDatabase().execSQL("CREATE TRIGGER test_checkout_abort BEFORE UPDATE ON checkout_requests WHEN NEW.state='APPLIED' BEGIN SELECT RAISE(ABORT,'synthetic finalization failure'); END");
                boolean failed=false;
                try{reopened.applyFixtureCheckout(first,firstHash);}catch(android.database.sqlite.SQLiteException expected){failed=true;}
                require(failed,"injected checkout fault did not fail");
                require(reopened.stockBalance(paperId).quantity==5000&&reopened.stockBalance(inkId).quantity==1000,"rollback lost stock");
                require(reopened.stockHistory(paperId,0,10).size()==1&&reopened.stockHistory(inkId,0,10).size()==1,"rollback left ledger row");
                require(checkoutCount(reopened,"checkout_sales",first)==0&&checkoutCount(reopened,"checkout_cash",first)==0&&checkoutCount(reopened,"checkout_stock_links",first)==0,"rollback left finance rows");
                require(reopened.fixtureCheckout(first).state.equals("PENDING"),"rollback lost pending request");
                reopened.getWritableDatabase().execSQL("DROP TRIGGER test_checkout_abort");
            }
            // Separate helper instances, same durable request, race through the real SQLite transaction.
            java.util.concurrent.CountDownLatch ready=new java.util.concurrent.CountDownLatch(2),go=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.atomic.AtomicReference<Throwable> error=new java.util.concurrent.atomic.AtomicReference<>();
            Runnable worker=()->{
                try(CatalogStore store=CatalogStore.checkoutFixture(context,fixture)){
                    ready.countDown();
                    if(!go.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("checkout race start timed out");
                    CheckoutJournal.Record applied=store.applyFixtureCheckout(first,firstHash);
                    require(applied.state.equals("APPLIED")&&applied.total==2250&&applied.received==2500&&applied.change==250,"concurrent replay result wrong");
                }catch(Throwable t){error.compareAndSet(null,t);}
            };
            Thread a=new Thread(worker,"fixture-checkout-a"),b=new Thread(worker,"fixture-checkout-b");
            a.setDaemon(true);b.setDaemon(true);a.start();b.start();
            try{
                require(ready.await(10,java.util.concurrent.TimeUnit.SECONDS),"checkout race helpers not ready");
                go.countDown();a.join(15000);b.join(15000);
            }catch(InterruptedException ex){Thread.currentThread().interrupt();throw new AssertionError(ex);}
            finally{go.countDown();}
            require(!a.isAlive()&&!b.isAlive(),"checkout race timed out");
            if(error.get()!=null)throw new AssertionError("checkout race failed",error.get());
            try(CatalogStore reopened=CatalogStore.checkoutFixture(context,fixture)){
                CheckoutJournal.Record applied=reopened.fixtureCheckout(first);
                require(applied.state.equals("APPLIED")&&applied.total==2250&&applied.materialCost==1120,"checkout recorded totals wrong");
                require(applied.previewReceipt().contains("SYNTHETIC CHECKOUT RECORD — NOT A TAX DOCUMENT")&&applied.previewReceipt().contains("Change: PHP 2.50"),"synthetic receipt wrong");
                require(reopened.stockBalance(paperId).quantity==0&&reopened.stockBalance(inkId).quantity==0,"checkout stock debit wrong");
                require(reopened.stockHistory(paperId,0,10).size()==2&&reopened.stockHistory(inkId,0,10).size()==2,"duplicate checkout movement");
                require(checkoutCount(reopened,"checkout_sales",first)==1&&checkoutCount(reopened,"checkout_cash",first)==1&&checkoutCount(reopened,"checkout_stock_links",first)==2,"checkout link/cardinality wrong");
                rejectStock(()->reopened.cancelFixtureCheckout(first,firstHash,"Not allowed"));
                rejectStock(()->reopened.applyFixtureCheckout(other,otherHash));
                require(reopened.fixtureCheckout(other).state.equals("PENDING")&&checkoutCount(reopened,"checkout_sales",other)==0,"competing stale checkout recorded");
                CheckoutJournal.Record cancelled=reopened.cancelFixtureCheckout(other,otherHash,"Synthetic stale proposal");
                require(cancelled.state.equals("CANCELLED")&&cancelled.cancelReason.equals("Synthetic stale proposal"),"cancellation audit wrong");
                require(reopened.cancelFixtureCheckout(other,otherHash,"Synthetic stale proposal").state.equals("CANCELLED"),"cancel replay wrong");
                rejectStock(()->reopened.cancelFixtureCheckout(other,otherHash,"Changed reason"));
                rejectStock(()->reopened.applyFixtureCheckout(other,otherHash));
                reopened.moveStock(stockId(),paperId,1,2,5000,"Synthetic replenishment");
                reopened.moveStock(stockId(),inkId,1,2,1000,"Synthetic replenishment");
                require(reopened.applyFixtureCheckout(first,firstHash).previewReceipt().equals(applied.previewReceipt()),"history changed after replenishment");
                require(reopened.stockBalance(paperId).quantity==5000&&reopened.stockBalance(inkId).quantity==1000,"historical replay mutated replenished stock");
                CheckoutPlan.Proposal fresh=reopened.prepareCheckout(second,serviceId,1,1,2,inputs);
                reopened.stageFixtureCheckout(fresh);
                String stockPending=stockId();
                reopened.stageStock(stockPending,paperId,1,3,-1,"Synthetic pending");
                rejectStock(()->reopened.applyFixtureCheckout(second,fresh.fingerprint()));
                require(reopened.fixtureCheckout(second).state.equals("PENDING"),"pending movement lost checkout identity");
                reopened.cancelStagedStock(stockPending);
                reopened.cancelFixtureCheckout(second,fresh.fingerprint(),"Synthetic test completed");
                require(reopened.pendingFixtureCheckouts(50).isEmpty(),"terminal requests remain pending");
            }
        }finally{context.deleteDatabase(filename);}
    }

    private void verifyCashierAuthShift(){
        android.content.Context context=getTargetContext();
        String fixture=stockId(),filename="checkout-fixture-"+fixture+".db",shiftId=stockId(),request=stockId(),pending=stockId();
        char[] ownerPassword="Synthetic-Owner-Passphrase-01".toCharArray(),cashPassword="Synthetic-Cashier-Passphrase-02".toCharArray(),
            wrong="Synthetic-Wrong-Passphrase-03".toCharArray();
        String ownerId,cashierId,serviceId,firstHash;
        try{
            try(CatalogStore store=CatalogStore.checkoutFixture(context,fixture)){
                require(!CashierJournal.initialized(store.getReadableDatabase()),"default credentials created");
                CashierJournal.Principal owner=store.bootstrapFixtureOwner("Synthetic.Owner",ownerPassword);
                ownerId=owner.id;
                require(owner.role==CashierSecurity.Role.OWNER&&owner.username.equals("synthetic.owner"),"owner setup wrong");
                rejectStock(()->store.bootstrapFixtureOwner("Second.Owner",ownerPassword));
                rejectStock(()->store.signInFixture("synthetic.owner",wrong));
                String ownerToken=store.signInFixture("SYNTHETIC.OWNER",ownerPassword);
                require(store.fixturePrincipal(ownerToken).id.equals(ownerId),"owner sign in wrong");
                CashierJournal.Principal cashier=store.createFixtureCashier(ownerToken,"synthetic.cashier",cashPassword);
                cashierId=cashier.id;
                store.createFixtureCashier(ownerToken,"synthetic.locked",cashPassword);
                rejectStock(()->store.createFixtureCashier(ownerToken,"SYNTHETIC.CASHIER",cashPassword));
                for(int i=0;i<CashierSecurity.FAILURE_LIMIT;i++)rejectStock(()->store.signInFixture("synthetic.locked",wrong));
                rejectStock(()->store.signInFixture("synthetic.locked",cashPassword));
                String token=store.signInFixture("synthetic.cashier",cashPassword);
                require(store.fixturePrincipal(token).role==CashierSecurity.Role.CASHIER,"cashier role wrong");
                rejectStock(()->store.createFixtureCashier(token,"denied.user",cashPassword));
                rejectStock(()->store.disableFixtureCashier(token,ownerId,1,cashPassword,"Synthetic denial"));
                try(android.database.Cursor c=store.getReadableDatabase().rawQuery("SELECT token_hash FROM cashier_sessions WHERE account_id=?",new String[]{cashierId})){
                    require(c.moveToFirst()&&c.getString(0).equals(CashierSecurity.tokenHash(token))&&!c.getString(0).equals(token),"plaintext or wrong session storage");
                }
                CatalogEntry service=store.createAuthorizedFixtureCatalog(ownerToken,CatalogEntry.Kind.SERVICE,"Synthetic shift print","page","2.50");
                serviceId=service.id;
                CheckoutPlan.Inputs input=new CheckoutPlan.Inputs(2,1,1,null,null,null,CheckoutPlan.UsageBasis.NONE,600);
                CheckoutPlan.Proposal first=store.prepareCheckout(request,serviceId,1,0,0,input);
                firstHash=first.fingerprint();
                rejectStock(()->store.stageAuthorizedFixtureCheckout(token,shiftId,first));
                rejectStock(()->store.stageFixtureCheckout(first));
                require(store.openFixtureShift(token,shiftId,1000).openingCash==1000,"opening cash");
                require(store.openFixtureShift(token,shiftId,1000).id.equals(shiftId),"open replay");
                rejectStock(()->store.openFixtureShift(token,shiftId,1001));
                rejectStock(()->store.openFixtureShift(token,stockId(),1000));
                rejectStock(()->store.disableFixtureCashier(ownerToken,cashierId,1,ownerPassword,"Synthetic open shift denial"));
                require(store.stageAuthorizedFixtureCheckout(token,shiftId,first).state.equals("PENDING"),"authorized staging wrong");
                require(store.pendingAuthorizedFixtureCheckouts(token,shiftId,10).size()==1,"pending listing");
                rejectStock(()->store.closeFixtureShift(token,shiftId,1000));
                rejectStock(()->store.applyAuthorizedFixtureCheckout(ownerToken,request,firstHash));
                rejectStock(()->store.authorizedFixtureCheckout(ownerToken,request));
                rejectStock(()->store.closeFixtureShift(ownerToken,shiftId,1000));
                rejectStock(()->store.applyFixtureCheckout(request,firstHash));
                rejectStock(()->store.applyAuthorizedFixtureCheckout("bad",request,firstHash));
                require(store.expectedFixtureCash(token,shiftId)==1000,"pending checkout counted as cash");
                require(store.applyAuthorizedFixtureCheckout(token,request,firstHash).state.equals("APPLIED"),"authorized apply failed");
                require(store.applyAuthorizedFixtureCheckout(token,request,firstHash).total==500,"authorized replay wrong");
                require(store.expectedFixtureCash(token,shiftId)==1500,"drawer used tender rather than net cash or duplicated checkout");
                CheckoutPlan.Proposal second=store.prepareCheckout(pending,serviceId,1,0,0,input);
                store.stageAuthorizedFixtureCheckout(token,shiftId,second);
                rejectStock(()->store.closeFixtureShift(token,shiftId,1450));
                store.cancelAuthorizedFixtureCheckout(token,pending,second.fingerprint(),"Synthetic cancellation");
                CashierJournal.Shift closed=store.closeFixtureShift(token,shiftId,1450);
                require("CLOSED".equals(closed.state)&&closed.expectedCash==1500&&closed.countedCash==1450&&closed.variance==-50,"shift close reconciliation wrong");
                require(store.closeFixtureShift(token,shiftId,1450).variance==-50,"close replay wrong");
                rejectStock(()->store.closeFixtureShift(token,shiftId,1451));
                require(store.applyAuthorizedFixtureCheckout(token,request,firstHash).state.equals("APPLIED"),"historical replay after close denied");
                CheckoutPlan.Proposal newPlan=store.prepareCheckout(stockId(),serviceId,1,0,0,input);
                rejectStock(()->store.stageAuthorizedFixtureCheckout(token,shiftId,newPlan));
                require(store.activeFixtureShift(token)==null,"closed shift remains open");
                store.signOutFixture(token);
                rejectStock(()->store.fixturePrincipal(token));
                rejectStock(()->store.authorizedFixtureCheckout(token,request));
                store.signOutFixture(ownerToken);
            }
            try(CatalogStore reopened=CatalogStore.checkoutFixture(context,fixture)){
                try(android.database.Cursor c=reopened.getReadableDatabase().rawQuery("SELECT failures,locked_until FROM cashier_accounts WHERE username='synthetic.locked'",null)){
                    require(c.moveToFirst()&&c.getInt(0)==CashierSecurity.FAILURE_LIMIT&&c.getLong(1)>0,"lockout not durably stored");
                }
                String newToken=reopened.signInFixture("synthetic.cashier",cashPassword);
                require(reopened.authorizedFixtureCheckout(newToken,request).total==500&&reopened.expectedFixtureCash(newToken,shiftId)==1500,"reopened historical cash wrong");
                String ownerToken=reopened.signInFixture("synthetic.owner",ownerPassword);
                reopened.disableFixtureCashier(ownerToken,cashierId,1,ownerPassword,"Synthetic end of shift");
                rejectStock(()->reopened.fixturePrincipal(newToken));
                rejectStock(()->reopened.signInFixture("synthetic.cashier",cashPassword));
                try(CatalogStore ordinary=new CatalogStore(context,filename)){
                    rejectStock(()->ordinary.bootstrapFixtureOwner("denied.owner",ownerPassword));
                    rejectStock(()->ordinary.signInFixture("synthetic.owner",ownerPassword));
                }
            }
        }finally{
            java.util.Arrays.fill(ownerPassword,'\0');java.util.Arrays.fill(cashPassword,'\0');java.util.Arrays.fill(wrong,'\0');
            context.deleteDatabase(filename);
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
                require(store.getReadableDatabase().getVersion()==9,"v3-to-v9 migration version wrong");
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
