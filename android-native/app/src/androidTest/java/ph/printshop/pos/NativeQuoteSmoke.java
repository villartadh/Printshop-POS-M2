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
            phase = "v9 administrative audit migration"; verifyCheckoutMigration(9);
            phase = "v10 drawer migration"; verifyCheckoutMigration(10);
            phase = "v11 refund migration"; verifyCheckoutMigration(11);
            phase = "v12 recurring migration"; verifyCheckoutMigration(12);
            phase = "v13 customer migration"; verifyCheckoutMigration(13);
            phase = "v14 checkout-customer migration"; verifyCheckoutMigration(14);
            phase = "v15 credit migration"; verifyCheckoutMigration(15);
            phase = "v16 quotation migration"; verifyCheckoutMigration(16);
            phase = "v17 conversion migration"; verifyCheckoutMigration(17);
            phase = "v18 credit conversion migration"; verifyCheckoutMigration(18);
            phase = "v19 production migration"; verifyCheckoutMigration(19);
            phase = "v20 production policy migration"; verifyCheckoutMigration(20);
            phase = "v21 quotation job migration"; verifyCheckoutMigration(21);
            phase = "saved quotation journal"; verifySavedQuotation();
            phase = "saved quotation cash conversion"; verifyQuotationCashConversion();
            phase = "saved quotation credit conversion"; verifyQuotationCreditConversion();
            phase = "credit checkout fixture"; verifyCreditCheckout();
            phase = "customer checkout fixture"; verifyCustomerCheckoutLink();
            phase = "durable atomic checkout fixture"; verifyDurableCheckout();
            phase = "cashier authorization and shift fixture"; verifyCashierAuthShift();
            phase = "audited credential lifecycle"; verifyCredentialLifecycle();
            phase = "owner-only fixture administration"; verifyOwnerAdministration();
            phase = "authenticated dynamic catalog selection"; verifyCatalogSelectionAccess();
            phase = "durable audited drawer fixture"; verifyDrawerLedger();
            phase = "recurring expense occurrence fixture"; verifyRecurringExpense();
            phase = "owner-controlled cashier disable"; verifyStaffDisable();
            phase = "material costing"; verifyMaterials();
            phase = "recipe and quote screen"; verifyRecipeScreen(screen);
            phase = "pricing store migration"; verifyPricingStore();
            phase = "pricing settings and document quote"; verifyPricingScreen(screen);
            phase = "measured quote"; verifyMeasuredQuote(screen);
            phase = "native cashier training UI"; verifyCashierTrainingScreen(screen);
            phase = "native receipt PDF"; verifyReceiptPdf();
            phase = "native encrypted backup snapshot"; verifyNativeBackupSnapshot();
            phase = "native database lifecycle fence"; verifyDatabaseLifecycleFence();
            phase = "native pre-restore recovery"; verifyPreRestoreRecovery();
            phase = "native SQLite fixture restoration"; verifyNativeRestoreCoordinator();
            evidence.putString("stream", "NATIVE_QUOTE_SMOKE_PASS\nNATIVE_CATALOG_STORE_PASS\nNATIVE_CATALOG_SCREEN_PASS\nNATIVE_STOCK_LEDGER_PASS\nNATIVE_STOCK_SCREEN_PASS\nNATIVE_STOCK_RECOVERY_PASS\nNATIVE_CHECKOUT_PLAN_PASS\nNATIVE_DURABLE_CHECKOUT_PASS\nNATIVE_CASHIER_SHIFT_PASS\nNATIVE_CASHIER_UI_PASS\nNATIVE_CREDENTIAL_LIFECYCLE_PASS\nNATIVE_ADMIN_AUTH_PASS\nNATIVE_STAFF_DISABLE_PASS\nNATIVE_ADMIN_AUDIT_PASS\nNATIVE_OWNER_CATALOG_UI_PASS\nNATIVE_DRAWER_LEDGER_PASS\nNATIVE_REFUND_UI_PASS\nNATIVE_SALES_REPORT_PASS\nNATIVE_REPORT_CHART_HISTORY_PASS\nNATIVE_RECURRING_EXPENSE_PASS\nNATIVE_RECURRING_UI_PASS\nNATIVE_CUSTOMER_DIRECTORY_PASS\nNATIVE_CUSTOMER_CHECKOUT_PASS\nNATIVE_CREDIT_COLLECTION_API_PASS\nNATIVE_CREDIT_REPORT_PASS\nNATIVE_CREDIT_CHECKOUT_UI_PASS\nNATIVE_CREDIT_COLLECTION_UI_PASS\nNATIVE_MATERIAL_COST_PASS\nNATIVE_RECIPE_SCREEN_PASS\nNATIVE_PRICING_STORE_PASS\nNATIVE_PRICING_SETTINGS_PASS\nNATIVE_ADVANCED_QUOTE_PASS\nNATIVE_SAVED_QUOTATION_PASS\nNATIVE_QUOTATION_CASH_CONVERSION_PASS\nNATIVE_QUOTATION_CREDIT_CONVERSION_PASS\nNATIVE_QUOTATION_UI_PASS\nNATIVE_PRODUCTION_JOURNAL_PASS\nNATIVE_PRODUCTION_UI_PASS\nNATIVE_PRODUCTION_POLICY_PASS\nNATIVE_QUOTE_PRODUCTION_PASS\nNATIVE_QUOTE_PRODUCTION_UI_PASS\nNATIVE_RECEIPT_PDF_PASS\nNATIVE_RECEIPT_REPRINT_PASS\nNATIVE_BACKUP_SNAPSHOT_PASS\nNATIVE_BACKUP_IMPORT_UI_PASS\nNATIVE_DATABASE_LIFECYCLE_PASS\nNATIVE_PRE_RESTORE_RECOVERY_PASS\nNATIVE_SQLITE_RESTORE_PASS\n");
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
            trainingClick(first,R.id.cashier_admin_history);
            awaitText(first,"Training administrative history","owner administrative history absent");
            awaitText(first,"#6 · STOCK APPLY · "+owner+" (account revision 1)","owner stock audit missing");
            awaitText(first,"#2 · STOCK CANCEL · "+owner+" (account revision 1)","owner cancel audit missing");
            trainingClick(first,R.id.cashier_back);
            awaitText(first,"No open shift","audit dashboard return failed");
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
                require(setup.findViewById(R.id.cashier_backup_import)==null&&setup.findViewById(R.id.cashier_backup_validate)==null,"cashier visible owner backup review");
                require(setup.findViewById(R.id.cashier_inventory)==null,"cashier visible owner-only inventory");
                require(setup.findViewById(R.id.cashier_manage_staff)==null,"cashier visible owner-only staff management");
                require(setup.findViewById(R.id.cashier_admin_history)==null,"cashier visible owner-only audit");
                require(setup.findViewById(R.id.cashier_catalog_services)==null&&setup.findViewById(R.id.cashier_catalog_materials)==null,"cashier visible owner catalog controls");
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
            verifyOwnerCatalogWorkflow(second,fixtureId,owner,ownerPass);
            verifyDrawerTrainingUI(second,fixtureId,owner,ownerPass);
            verifyRefundTrainingUI(second,fixtureId,owner,ownerPass);
            verifySalesReportTrainingUI(second,fixtureId,owner,ownerPass);
            verifyRecurringTrainingUI(second,fixtureId,owner,ownerPass);
            verifyCustomerTrainingUI(second,fixtureId,owner,ownerPass);
            verifyCustomerCheckoutUI(second);
            verifyCreditCheckoutUI(second,fixtureId);
            verifyCollectionUI(second,fixtureId,owner,ownerPass);
            verifyQuotationUI(second,fixtureId,owner,ownerPass);
            verifyProductionPolicy();
            verifyProductionJournal(fixtureId);
            verifyProductionUI(second);
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

    private void trainingSpinner(Activity activity,int id,int position){
        runOnMainChecked(()->{
            android.widget.Spinner spinner=activity.findViewById(id);
            require(spinner!=null&&spinner.isEnabled(),"training selection missing: "+id);
            spinner.setSelection(position);
        });waitForIdleSync();
    }
    private void verifyOwnerCatalogWorkflow(Activity activity,String fixtureId,String owner,String ownerPass){
        // Earlier UI workflow has completed and its original financial invariants were checked.
        trainingLogin(activity,owner,ownerPass);
        awaitText(activity,"No open shift","owner login for catalog failed");
        trainingClick(activity,R.id.cashier_backup_import);
        awaitText(activity,"Review encrypted backup","owner backup import screen missing");
        runOnMainChecked(()->require(activity.findViewById(R.id.cashier_backup_choose)!=null,"backup picker action missing"));
        // Stub only the external picker result; the real activity, resolver, owner login,
        // archive authentication, SQLite review and returned summary all run installed.
        java.io.File archiveFile=null;char[] ownerSecret=ownerPass.toCharArray();
        char[] archiveSecret="Synthetic-import-archive-01".toCharArray();
        try{
            archiveFile=java.io.File.createTempFile("synthetic-import-",".psposbackup",getTargetContext().getCacheDir());
            try(CatalogStore store=CatalogStore.checkoutFixture(getTargetContext(),fixtureId)){
                String token=store.signInFixture(owner,ownerSecret);
                byte[] archive=store.encryptedFixtureBackup(token,archiveSecret);
                try(java.io.FileOutputStream out=new java.io.FileOutputStream(archiveFile)){BackupFileTransfer.write(out,archive);}
                finally{java.util.Arrays.fill(archive,(byte)0);store.signOutFixture(token);}
            }
            android.content.IntentFilter filter=new android.content.IntentFilter(Intent.ACTION_OPEN_DOCUMENT);
            filter.addCategory(Intent.CATEGORY_OPENABLE);filter.addDataType("application/octet-stream");
            Intent result=new Intent().setData(android.net.Uri.fromFile(archiveFile));
            ActivityMonitor monitor=addMonitor(filter,new ActivityResult(Activity.RESULT_OK,result),true);
            try{
                trainingClick(activity,R.id.cashier_backup_choose);
                awaitText(activity,"Training sign in","backup picker must require owner reauthentication");
                require(monitor.getHits()==1,"backup document intent not observed");
            }finally{removeMonitor(monitor);}
            trainingLogin(activity,owner,ownerPass);
            awaitText(activity,"Validate selected backup","selected archive not available after owner sign in");
            trainingClick(activity,R.id.cashier_backup_validate);
            trainingField(activity,R.id.cashier_backup_password,"Synthetic-import-archive-01");
            trainingClick(activity,R.id.cashier_backup_review);
            awaitText(activity,"Backup validated","read-only imported archive failed validation");
            awaitText(activity,"Restore is not available yet.","restore unexpectedly enabled");
            trainingClick(activity,R.id.cashier_back);
            awaitText(activity,"No open shift","backup review return failed");
            runOnMainChecked(()->require(activity.findViewById(R.id.cashier_backup_validate)==null,"selected archive retained after review"));
        }catch(java.io.IOException|android.content.IntentFilter.MalformedMimeTypeException e){throw new AssertionError("synthetic picker fixture failed",e);}
        finally{java.util.Arrays.fill(ownerSecret,'\0');java.util.Arrays.fill(archiveSecret,'\0');if(archiveFile!=null)archiveFile.delete();}
        trainingClick(activity,R.id.cashier_backup);
        awaitText(activity,"Encrypted backup export","owner backup screen missing");
        trainingField(activity,R.id.cashier_backup_password,"Synthetic-export-password-01");
        trainingField(activity,R.id.cashier_backup_confirm,"Synthetic-export-password-02");
        trainingClick(activity,R.id.cashier_backup_create);
        awaitText(activity,"Backup passphrases do not match.","backup mismatch not rejected");
        trainingClick(activity,R.id.cashier_back);
        awaitText(activity,"No open shift","backup screen return failed");
        trainingClick(activity,R.id.cashier_catalog_services);
        awaitText(activity,"Training services","owner services missing");
        runOnMainChecked(()->require(activity.findViewById(R.id.cashier_catalog_next)==null,"unnecessary page control"));
        trainingSelect(activity,"Manage Training document print");
        awaitText(activity,"Training catalog item","seed service detail missing");
        trainingClick(activity,R.id.cashier_catalog_archive);
        awaitText(activity,"Confirm training archive","archive confirmation missing");
        trainingClick(activity,R.id.cashier_catalog_confirm_archive);
        awaitText(activity,"Training document print · Archived","seed service not archived");
        trainingClick(activity,R.id.cashier_back);
        awaitText(activity,"No open shift","archived sole service blocked dashboard");
        trainingField(activity,R.id.cashier_opening,"0.00");
        trainingClick(activity,R.id.cashier_open_shift);
        awaitText(activity,"Shift open · Expected cash: PHP 0.00","owner shift unavailable without service");
        trainingClick(activity,R.id.cashier_new_checkout);
        awaitText(activity,"No active training services on this page. Ask an owner to configure or restore a service.","empty catalog checkout failed");
        trainingClick(activity,R.id.cashier_back);
        awaitText(activity,"Shift open · Expected cash: PHP 0.00","empty service selection blocked dashboard");
        trainingClick(activity,R.id.cashier_close_shift);
        awaitText(activity,"Close training shift","close unavailable without service");
        trainingField(activity,R.id.cashier_counted,"0.00");
        trainingClick(activity,R.id.cashier_confirm_close);
        awaitText(activity,"Variance: PHP 0.00","empty catalog shift mismatch");
        trainingClick(activity,R.id.cashier_return_home);
        awaitText(activity,"No open shift","owner shift not closed");
        trainingClick(activity,R.id.cashier_catalog_services);
        awaitText(activity,"No active entries on this page","archive filter wrong");
        trainingClick(activity,R.id.cashier_catalog_filter);
        awaitText(activity,"Archived records — restore without deleting history.","archived menu missing");
        trainingSelect(activity,"Manage Training document print");
        awaitText(activity,"Training document print · Archived","archived item lost");
        trainingClick(activity,R.id.cashier_catalog_archive);
        awaitText(activity,"Confirm training restore","restore confirmation missing");
        trainingClick(activity,R.id.cashier_catalog_confirm_archive);
        awaitText(activity,"Training document print · Active","restore failed");
        trainingClick(activity,R.id.cashier_catalog_return);
        awaitText(activity,"Training services","active catalog reload failed");
        trainingClick(activity,R.id.cashier_catalog_create);
        awaitText(activity,"Create training service","create screen missing");
        trainingField(activity,R.id.cashier_catalog_name,"Synthetic UI advanced");
        trainingField(activity,R.id.cashier_catalog_unit,"page");
        trainingField(activity,R.id.cashier_catalog_price,"4.00");
        trainingClick(activity,R.id.cashier_catalog_save);
        awaitText(activity,"Synthetic UI advanced · Active","service create failed");
        trainingClick(activity,R.id.cashier_catalog_edit);
        awaitText(activity,"Edit training catalog item","edit screen missing");
        trainingField(activity,R.id.cashier_catalog_name,"Synthetic UI configured");
        trainingField(activity,R.id.cashier_catalog_price,"4.20");
        trainingClick(activity,R.id.cashier_catalog_save);
        awaitText(activity,"Synthetic UI configured · Active","catalog edit failed");
        trainingClick(activity,R.id.cashier_catalog_pricing);
        awaitText(activity,"Configure training pricing","pricing screen unavailable");
        trainingSpinner(activity,R.id.cashier_pricing_method,PricingMath.Method.PER_PAGE.ordinal());
        runOnMainChecked(()->{
            android.widget.CheckBox pages=activity.findViewById(R.id.cashier_pricing_pages);
            require(pages!=null&&pages.isEnabled(),"document mode checkbox disabled");
            pages.setChecked(true);
        });
        trainingField(activity,R.id.cashier_pricing_threshold,"5");
        trainingField(activity,R.id.cashier_pricing_rate,"3.50");
        trainingClick(activity,R.id.cashier_pricing_save);
        awaitText(activity,"Add the entered tier or clear both fields before saving.","unadded tier silently lost");
        trainingClick(activity,R.id.cashier_pricing_add);
        awaitText(activity,"Tier added to draft. Save pricing to apply.","tier draft not added");
        trainingField(activity,R.id.cashier_pricing_threshold,"5.00");
        trainingField(activity,R.id.cashier_pricing_rate,"3.00");
        trainingClick(activity,R.id.cashier_pricing_add);
        awaitText(activity,"Duplicate tier threshold","duplicate tier accepted");
        trainingField(activity,R.id.cashier_pricing_threshold,"");
        trainingField(activity,R.id.cashier_pricing_rate,"");
        trainingClick(activity,R.id.cashier_pricing_remove);
        awaitText(activity,"Tier removed from draft. Save pricing to apply.","tier removal missing");
        trainingField(activity,R.id.cashier_pricing_threshold,"5");
        trainingField(activity,R.id.cashier_pricing_rate,"3.50");
        trainingClick(activity,R.id.cashier_pricing_add);
        awaitText(activity,"Tier added to draft. Save pricing to apply.","tier re-add failed");
        trainingClick(activity,R.id.cashier_pricing_save);
        awaitText(activity,"Pricing: Per page · revision 1 · 1 tier(s)","pricing not persisted");
        trainingClick(activity,R.id.cashier_back);
        awaitText(activity,"No open shift","catalog dashboard unavailable");
        trainingClick(activity,R.id.cashier_catalog_materials);
        awaitText(activity,"Training materials","owner materials menu missing");
        trainingClick(activity,R.id.cashier_catalog_create);
        awaitText(activity,"Create training material","material add missing");
        trainingField(activity,R.id.cashier_catalog_name,"Synthetic UI card");
        trainingField(activity,R.id.cashier_catalog_unit,"sheet");
        trainingField(activity,R.id.cashier_catalog_price,"1.20");
        trainingClick(activity,R.id.cashier_catalog_save);
        awaitText(activity,"Synthetic UI card · Active","material not created");
        trainingClick(activity,R.id.cashier_catalog_edit);
        awaitText(activity,"Edit training catalog item","material editor missing");
        trainingField(activity,R.id.cashier_catalog_name,"Synthetic UI card edited");
        trainingField(activity,R.id.cashier_catalog_price,"1.30");
        trainingClick(activity,R.id.cashier_catalog_save);
        awaitText(activity,"Synthetic UI card edited · Active","material edit failed");
        trainingClick(activity,R.id.cashier_back);
        awaitText(activity,"No open shift","material return failed");
        trainingClick(activity,R.id.cashier_catalog_services);
        awaitText(activity,"Training services","services missing after material edit");
        trainingSelect(activity,"Manage Synthetic UI configured");
        awaitText(activity,"Synthetic UI configured · Active","configured service missing");
        trainingClick(activity,R.id.cashier_catalog_recipe);
        awaitText(activity,"Configure training recipe","recipe screen missing");
        trainingSelect(activity,"Set usage for Synthetic UI card edited");
        awaitText(activity,"Training material usage","material usage editor missing");
        trainingField(activity,R.id.cashier_recipe_usage,"0.25");
        trainingClick(activity,R.id.cashier_recipe_save);
        awaitText(activity,"Recipe: 1 linked material(s) · revision 1","material usage not linked");
        trainingClick(activity,R.id.cashier_catalog_recipe);
        awaitText(activity,"Configure training recipe","recipe reopen failed");
        trainingSelect(activity,"Set usage for Synthetic UI card edited");
        awaitText(activity,"Training material usage","recipe removal editor missing");
        trainingClick(activity,R.id.cashier_recipe_remove);
        awaitText(activity,"Confirm training recipe removal","remove confirmation missing");
        trainingClick(activity,R.id.cashier_recipe_confirm_remove);
        awaitText(activity,"Recipe: 0 linked material(s) · revision 2","material unlink failed");
        trainingClick(activity,R.id.cashier_catalog_recipe);
        awaitText(activity,"Configure training recipe","recipe selection missing");
        trainingSelect(activity,"Set usage for Training paper");
        awaitText(activity,"Training material usage","paper recipe editor missing");
        trainingField(activity,R.id.cashier_recipe_usage,"0.5");
        trainingClick(activity,R.id.cashier_recipe_save);
        awaitText(activity,"Recipe: 1 linked material(s) · revision 3","paper recipe not saved");
        trainingClick(activity,R.id.cashier_back);
        awaitText(activity,"No open shift","recipe return failed");
        trainingClick(activity,R.id.cashier_catalog_materials);
        awaitText(activity,"Training materials","materials missing for archive");
        trainingSelect(activity,"Manage Synthetic UI card edited");
        awaitText(activity,"Synthetic UI card edited · Active","material detail unavailable");
        trainingClick(activity,R.id.cashier_catalog_archive);
        awaitText(activity,"Confirm training archive","material archive missing");
        trainingClick(activity,R.id.cashier_catalog_confirm_archive);
        awaitText(activity,"Synthetic UI card edited · Archived","unused material not archived");
        trainingClick(activity,R.id.cashier_catalog_archive);
        awaitText(activity,"Confirm training restore","material restore missing");
        trainingClick(activity,R.id.cashier_catalog_confirm_archive);
        awaitText(activity,"Synthetic UI card edited · Active","material restore failed");
        trainingClick(activity,R.id.cashier_back);
        awaitText(activity,"No open shift","owner dashboard unavailable");
        trainingClick(activity,R.id.cashier_add_staff);
        awaitText(activity,"Owner-only creation. No real shop credentials.","cashier creation missing");
        trainingField(activity,R.id.cashier_username,"catalog-cashier");
        trainingField(activity,R.id.cashier_password,"Synthetic-Catalog-Cashier-01");
        trainingClick(activity,R.id.cashier_confirm_staff);
        awaitText(activity,"No open shift","catalog cashier creation failed");
        trainingClick(activity,R.id.cashier_lock);
        awaitText(activity,"Training sign in","owner could not lock");
        trainingLogin(activity,"catalog-cashier","Synthetic-Catalog-Cashier-01");
        awaitText(activity,"No open shift","catalog cashier sign-in failed");
        runOnMainChecked(()->require(activity.findViewById(R.id.cashier_catalog_services)==null&&activity.findViewById(R.id.cashier_catalog_materials)==null,"cashier owner screens visible"));
        trainingField(activity,R.id.cashier_opening,"0.00");
        trainingClick(activity,R.id.cashier_open_shift);
        awaitText(activity,"Shift open · Expected cash: PHP 0.00","new cashier shift failed");
        trainingClick(activity,R.id.cashier_new_checkout);
        awaitText(activity,"Choose training service","dynamic service picker missing");
        trainingSelect(activity,"Choose Synthetic UI configured");
        awaitText(activity,"New training checkout","configured service checkout missing");
        runOnMainChecked(()->require(activity.findViewById(R.id.cashier_count)==null&&activity.findViewById(R.id.cashier_checkout_width)==null,"irrelevant fields visible"));
        trainingField(activity,R.id.cashier_checkout_pages,"3");
        trainingField(activity,R.id.cashier_checkout_copies,"2");
        trainingField(activity,R.id.cashier_cash,"25.00");
        trainingClick(activity,R.id.cashier_review);
        awaitText(activity,"Choose the material usage basis","missing usage basis accepted");
        trainingSpinner(activity,R.id.cashier_checkout_basis,CheckoutPlan.UsageBasis.PRODUCED_ITEM.ordinal());
        trainingClick(activity,R.id.cashier_review);
        awaitText(activity,"Review training checkout","configured checkout review missing");
        awaitText(activity,"Proposed use: 1 sheet (Training paper)","wrong produced-item recipe usage");
        trainingClick(activity,R.id.cashier_stage);
        awaitText(activity,"Pending training checkout","configured checkout not staged");
        awaitText(activity,"Total: PHP 21.00","tier total incorrect");
        trainingClick(activity,R.id.cashier_record);
        awaitText(activity,"Recorded training receipt","configured checkout not recorded");
        trainingClick(activity,R.id.cashier_return_home);
        awaitText(activity,"Shift open · Expected cash: PHP 21.00","configured shift cash mismatch");
        trainingClick(activity,R.id.cashier_close_shift);
        awaitText(activity,"Close training shift","configured shift close unavailable");
        trainingField(activity,R.id.cashier_counted,"21.00");
        trainingClick(activity,R.id.cashier_confirm_close);
        awaitText(activity,"Variance: PHP 0.00","configured shift variance");
        trainingClick(activity,R.id.cashier_return_home);
        awaitText(activity,"No open shift","configured shift not closed");
        trainingClick(activity,R.id.cashier_lock);
        awaitText(activity,"Training sign in","catalog cashier did not sign out");
        try(CatalogStore fixture=CatalogStore.checkoutFixture(getTargetContext(),fixtureId)){
            require(trainingCount(fixture,"SELECT COUNT(*) FROM checkout_sales")==2,"configured checkout recorded wrong sale count");
            require(trainingCount(fixture,"SELECT COUNT(*) FROM checkout_requests WHERE service_name='Synthetic UI configured' AND state='APPLIED' AND total=2100 AND received=2500 AND change_due=400")==1,"configured checkout persisted wrong amounts");
            require(trainingCount(fixture,"SELECT COUNT(*) FROM stock_movements")==5,"configured checkout stock movement count");
            require(trainingCount(fixture,"SELECT after_quantity FROM stock_movements ORDER BY stock_revision DESC LIMIT 1")==47000,"configured checkout stock deduction wrong");
            require(trainingCount(fixture,"SELECT COUNT(*) FROM cashier_admin_events WHERE action='PRICING_SAVE'")==1,"UI pricing audit missing");
            require(trainingCount(fixture,"SELECT COUNT(*) FROM cashier_admin_events WHERE action='RECIPE_SAVE'")==3,"UI recipe audit mismatch");
            require(trainingCount(fixture,"SELECT COUNT(*) FROM cashier_admin_events WHERE action LIKE 'CATALOG_%'")==8,"UI catalog audit count mismatch");
            require(trainingCount(fixture,"SELECT COUNT(*) FROM cashier_sessions WHERE revoked_ms IS NULL")==0,"UI session left open");
        }
    }

    private void verifyDrawerTrainingUI(Activity activity,String fixtureId,String owner,String ownerPass){
        trainingLogin(activity,owner,ownerPass);
        awaitText(activity,"No open shift","drawer owner sign-in failed");
        trainingField(activity,R.id.cashier_opening,"10.00");trainingClick(activity,R.id.cashier_open_shift);
        awaitText(activity,"Shift open · Expected cash: PHP 10.00","drawer opening mismatch");
        trainingClick(activity,R.id.cashier_drawer);
        trainingClick(activity,R.id.cashier_drawer_in);
        awaitText(activity,"Prepare training cash in","owner cash-in form missing");
        trainingField(activity,R.id.cashier_drawer_amount,"5.00");trainingField(activity,R.id.cashier_drawer_reason,"Synthetic UI float");
        trainingClick(activity,R.id.cashier_drawer_review);
        awaitText(activity,"Review training drawer activity","drawer review missing");
        awaitText(activity,"Proposed after: PHP 15.00","cash-in review mismatch");
        trainingClick(activity,R.id.cashier_drawer_stage);awaitText(activity,"Pending training drawer activity","drawer request not staged");
        trainingClick(activity,R.id.cashier_drawer_return);awaitText(activity,"Unresolved training drawer activity","pending drawer omitted");
        awaitText(activity,"Shift open · Expected cash: PHP 10.00","drawer stage counted as cash");
        runOnMainChecked(()->require(!activity.findViewById(R.id.cashier_new_checkout).isEnabled()&&!activity.findViewById(R.id.cashier_close_shift).isEnabled(),"pending drawer did not block checkout/close"));
        trainingClick(activity,R.id.cashier_lock);awaitText(activity,"Training sign in","owner drawer lock failed");
        trainingLogin(activity,owner,ownerPass);awaitText(activity,"Unresolved training drawer activity","drawer request not recovered after lock");
        trainingClick(activity,R.id.cashier_drawer);awaitText(activity,"Pending training drawer activity","original drawer request absent");
        trainingClick(activity,R.id.cashier_drawer_apply);awaitText(activity,"Recorded training drawer activity","cash-in not applied");
        awaitText(activity,"After: PHP 15.00","cash-in result incorrect");
        trainingClick(activity,R.id.cashier_drawer_return);awaitText(activity,"Shift open · Expected cash: PHP 15.00","cash-in total missing");
        trainingClick(activity,R.id.cashier_drawer);trainingClick(activity,R.id.cashier_drawer_out);
        awaitText(activity,"Prepare training cash out","owner cash-out form missing");
        trainingField(activity,R.id.cashier_drawer_amount,"100.00");trainingField(activity,R.id.cashier_drawer_reason,"Synthetic UI removal");
        trainingClick(activity,R.id.cashier_drawer_review);awaitText(activity,"Insufficient expected drawer cash","overdraw allowed");
        trainingField(activity,R.id.cashier_drawer_amount,"2.00");trainingClick(activity,R.id.cashier_drawer_review);
        awaitText(activity,"Review training drawer activity","cash-out review missing");trainingClick(activity,R.id.cashier_drawer_stage);
        awaitText(activity,"Pending training drawer activity","cash-out not saved");
        trainingField(activity,R.id.cashier_drawer_cancel_reason,"Synthetic UI cancel");
        trainingClick(activity,R.id.cashier_drawer_cancel);awaitText(activity,"Cancelled training drawer activity","cash-out cancellation missing");
        trainingClick(activity,R.id.cashier_drawer_return);awaitText(activity,"Shift open · Expected cash: PHP 15.00","cancelled cash-out affected cash");
        trainingClick(activity,R.id.cashier_drawer);trainingClick(activity,R.id.cashier_drawer_out);
        awaitText(activity,"Prepare training cash out","cash-out form missing after cancellation");
        trainingField(activity,R.id.cashier_drawer_amount,"2.00");trainingField(activity,R.id.cashier_drawer_reason,"Synthetic UI cash removal");
        trainingClick(activity,R.id.cashier_drawer_review);awaitText(activity,"Review training drawer activity","cash-out review missing");
        trainingClick(activity,R.id.cashier_drawer_stage);awaitText(activity,"Pending training drawer activity","cash-out stage missing");
        trainingClick(activity,R.id.cashier_drawer_apply);awaitText(activity,"After: PHP 13.00","cash-out result incorrect");
        trainingClick(activity,R.id.cashier_drawer_return);awaitText(activity,"Shift open · Expected cash: PHP 13.00","cash-out expected incorrect");
        trainingClick(activity,R.id.cashier_close_shift);awaitText(activity,"Close training shift","drawer close missing");
        trainingField(activity,R.id.cashier_counted,"12.50");trainingClick(activity,R.id.cashier_confirm_close);
        awaitText(activity,"Variance: PHP -0.50","drawer close variance incorrect");
        trainingClick(activity,R.id.cashier_return_home);awaitText(activity,"No open shift","drawer owner shift open");
        trainingClick(activity,R.id.cashier_lock);awaitText(activity,"Training sign in","drawer owner lock failed");
        trainingLogin(activity,"catalog-cashier","Synthetic-Catalog-Cashier-01");awaitText(activity,"No open shift","drawer cashier sign-in failed");
        trainingField(activity,R.id.cashier_opening,"5.00");trainingClick(activity,R.id.cashier_open_shift);
        awaitText(activity,"Shift open · Expected cash: PHP 5.00","cashier expense opening wrong");
        trainingClick(activity,R.id.cashier_drawer);
        runOnMainChecked(()->require(activity.findViewById(R.id.cashier_drawer_in)==null&&activity.findViewById(R.id.cashier_drawer_out)==null,"cashier adjustment actions visible"));
        trainingClick(activity,R.id.cashier_drawer_expense);awaitText(activity,"Prepare training expense","cashier expense unavailable");
        trainingField(activity,R.id.cashier_drawer_amount,"1.25");trainingField(activity,R.id.cashier_drawer_category,"Synthetic UI supplies");
        trainingField(activity,R.id.cashier_drawer_reason,"Synthetic UI expense");
        trainingClick(activity,R.id.cashier_drawer_review);awaitText(activity,"Proposed after: PHP 3.75","expense review incorrect");
        trainingClick(activity,R.id.cashier_drawer_stage);awaitText(activity,"Pending training drawer activity","expense stage missing");
        trainingClick(activity,R.id.cashier_drawer_apply);awaitText(activity,"Recorded training drawer activity","expense record missing");
        trainingClick(activity,R.id.cashier_drawer_return);awaitText(activity,"Shift open · Expected cash: PHP 3.75","expense expected wrong");
        trainingClick(activity,R.id.cashier_close_shift);awaitText(activity,"Close training shift","expense shift close missing");
        trainingField(activity,R.id.cashier_counted,"3.75");trainingClick(activity,R.id.cashier_confirm_close);awaitText(activity,"Variance: PHP 0.00","expense close variance wrong");
        trainingClick(activity,R.id.cashier_return_home);awaitText(activity,"No open shift","cashier expense shift not closed");
        trainingClick(activity,R.id.cashier_drawer_history);awaitText(activity,"APPLIED · Expense · PHP 1.25 · Synthetic UI supplies","expense history missing");
        runOnMainChecked(()->require(findText(activity.getWindow().getDecorView(),"APPLIED · Cash in · PHP 5.00")==null,"cross-operator history visible"));
        trainingClick(activity,R.id.cashier_back);awaitText(activity,"No open shift","expense history return failed");
        trainingClick(activity,R.id.cashier_lock);awaitText(activity,"Training sign in","expense cashier did not sign out");
        try(CatalogStore fixture=CatalogStore.checkoutFixture(getTargetContext(),fixtureId)){
            require(trainingCount(fixture,"SELECT COUNT(*) FROM cashier_drawer_requests")==4,"drawer UI request count wrong");
            require(trainingCount(fixture,"SELECT COUNT(*) FROM cashier_drawer_requests WHERE state='APPLIED'")==3,"drawer UI apply count wrong");
            require(trainingCount(fixture,"SELECT COUNT(*) FROM cashier_drawer_requests WHERE state='CANCELLED'")==1,"drawer UI cancel count wrong");
            require(trainingCount(fixture,"SELECT COUNT(*) FROM cashier_drawer_effects WHERE drawer_id IS NOT NULL")==3,"drawer effect count wrong");
            require(trainingCount(fixture,"SELECT COUNT(*) FROM cashier_drawer_effects WHERE checkout_id IS NOT NULL")==2,"historical checkout effects wrong");
            require(trainingCount(fixture,"SELECT COUNT(*) FROM cashier_shifts WHERE expected_cash=1300 AND counted_cash=1250 AND variance=-50")==1,"owner drawer shift close audit wrong");
            require(trainingCount(fixture,"SELECT COUNT(*) FROM cashier_shifts WHERE expected_cash=375 AND counted_cash=375 AND variance=0")==1,"cashier expense shift close wrong");
            require(trainingCount(fixture,"SELECT COUNT(*) FROM checkout_sales")==2&&trainingCount(fixture,"SELECT COUNT(*) FROM stock_movements")==5,"drawer altered sales/inventory");
            require(trainingCount(fixture,"SELECT COUNT(*) FROM cashier_sessions WHERE revoked_ms IS NULL")==0,"drawer session still active");
        }
    }

    private void verifyRefundTrainingUI(Activity activity,String fixtureId,String owner,String ownerPass){
        trainingLogin(activity,owner,ownerPass);awaitText(activity,"No open shift","refund owner login missing");
        trainingField(activity,R.id.cashier_opening,"30.00");trainingClick(activity,R.id.cashier_open_shift);
        awaitText(activity,"Shift open · Expected cash: PHP 30.00","refund opening wrong");
        trainingClick(activity,R.id.cashier_refund_receipts);awaitText(activity,"Training sale refunds / voids","receipt selection missing");
        trainingSelect(activity,"Select Training document print · PHP 5.00");
        awaitText(activity,"Original training cash receipt","historic receipt review missing");
        awaitText(activity,"Remaining: PHP 5.00","original amount wrong");
        trainingClick(activity,R.id.cashier_refund_new);awaitText(activity,"Prepare training refund","refund form missing");
        trainingField(activity,R.id.cashier_refund_amount,"5.01");trainingField(activity,R.id.cashier_refund_reason,"Synthetic partial refund");
        trainingClick(activity,R.id.cashier_refund_review);awaitText(activity,"Original receipt changed or amount is invalid; review again","over-refund allowed");
        trainingField(activity,R.id.cashier_refund_amount,"2.00");trainingClick(activity,R.id.cashier_refund_review);
        awaitText(activity,"Proposed after: PHP 28.00","refund reviewed cash wrong");
        trainingClick(activity,R.id.cashier_refund_stage);awaitText(activity,"Pending training compensation","refund stage missing");
        trainingClick(activity,R.id.cashier_refund_return);awaitText(activity,"Unresolved training refund/void","refund recovery omitted");
        awaitText(activity,"Shift open · Expected cash: PHP 30.00","refund stage affected cash");
        runOnMainChecked(()->require(!activity.findViewById(R.id.cashier_new_checkout).isEnabled()&&!activity.findViewById(R.id.cashier_close_shift).isEnabled(),"refund did not block other pending work"));
        trainingClick(activity,R.id.cashier_lock);awaitText(activity,"Training sign in","refund lock failed");
        trainingLogin(activity,owner,ownerPass);awaitText(activity,"Unresolved training refund/void","refund reopen missing");
        trainingClick(activity,R.id.cashier_drawer);awaitText(activity,"Pending training compensation","generic adjustment bypass");
        runOnMainChecked(()->require(activity.findViewById(R.id.cashier_drawer_apply)==null,"generic apply visible for refund"));
        trainingClick(activity,R.id.cashier_refund_apply);awaitText(activity,"Recorded training compensation","refund apply missing");
        awaitText(activity,"After: PHP 28.00","refund exact cash wrong");
        trainingClick(activity,R.id.cashier_refund_return);awaitText(activity,"Shift open · Expected cash: PHP 28.00","refund cash missing");
        trainingClick(activity,R.id.cashier_refund_receipts);awaitText(activity,"Training sale refunds / voids","repeat list missing");
        trainingSelect(activity,"Select Training document print · PHP 5.00");awaitText(activity,"Remaining: PHP 3.00","refundable amount not reduced");
        runOnMainChecked(()->require(!activity.findViewById(R.id.cashier_void_new).isEnabled(),"void after refund enabled"));
        trainingClick(activity,R.id.cashier_refund_new);awaitText(activity,"Prepare training refund","second refund missing");
        trainingField(activity,R.id.cashier_refund_amount,"3.00");trainingField(activity,R.id.cashier_refund_reason,"Synthetic cancel review");
        trainingClick(activity,R.id.cashier_refund_review);awaitText(activity,"Review training compensation","second confirmation missing");
        trainingClick(activity,R.id.cashier_refund_stage);awaitText(activity,"Pending training compensation","second stage missing");
        trainingField(activity,R.id.cashier_refund_cancel_reason,"Synthetic keep original");trainingClick(activity,R.id.cashier_refund_cancel);
        awaitText(activity,"Cancelled training compensation","refund cancel missing");
        trainingClick(activity,R.id.cashier_refund_return);awaitText(activity,"Shift open · Expected cash: PHP 28.00","cancel affected cash");
        trainingClick(activity,R.id.cashier_refund_receipts);awaitText(activity,"Training sale refunds / voids","void list missing");
        trainingSelect(activity,"Select Synthetic UI configured · PHP 21.00");awaitText(activity,"Original training cash receipt","void original missing");
        trainingClick(activity,R.id.cashier_void_new);awaitText(activity,"Full void amount: PHP 21.00","void amount wrong");
        trainingField(activity,R.id.cashier_refund_reason,"Synthetic full void");trainingClick(activity,R.id.cashier_refund_review);
        awaitText(activity,"Proposed after: PHP 7.00","void cash review wrong");
        trainingClick(activity,R.id.cashier_refund_stage);awaitText(activity,"Pending training compensation","void stage missing");
        trainingClick(activity,R.id.cashier_refund_apply);awaitText(activity,"After: PHP 7.00","void apply wrong");
        trainingClick(activity,R.id.cashier_refund_history);awaitText(activity,"APPLIED · Void · PHP 21.00","void history missing");
        awaitText(activity,"APPLIED · Refund · PHP 2.00","partial history missing");
        awaitText(activity,"CANCELLED · Refund · PHP 3.00","cancel history missing");
        trainingClick(activity,R.id.cashier_back);awaitText(activity,"Shift open · Expected cash: PHP 7.00","void dashboard cash wrong");
        trainingClick(activity,R.id.cashier_drawer_history);awaitText(activity,"Training drawer history","ordinary history absent");
        runOnMainChecked(()->require(findText(activity.getWindow().getDecorView(),"APPLIED · Cash out · PHP 21.00")==null,"void duplicated as ordinary adjustment"));
        trainingClick(activity,R.id.cashier_back);awaitText(activity,"Shift open · Expected cash: PHP 7.00","history return failed");
        trainingClick(activity,R.id.cashier_close_shift);awaitText(activity,"Close training shift","refund shift close unavailable");
        trainingField(activity,R.id.cashier_counted,"7.00");trainingClick(activity,R.id.cashier_confirm_close);
        awaitText(activity,"Variance: PHP 0.00","refund close mismatch");
        trainingClick(activity,R.id.cashier_return_home);awaitText(activity,"No open shift","refund shift still open");
        trainingClick(activity,R.id.cashier_lock);awaitText(activity,"Training sign in","refund owner sign out missing");
        trainingLogin(activity,"catalog-cashier","Synthetic-Catalog-Cashier-01");awaitText(activity,"No open shift","refund cashier sign in failed");
        runOnMainChecked(()->require(activity.findViewById(R.id.cashier_refund_receipts)==null&&activity.findViewById(R.id.cashier_refund_history)==null,"cashier compensation controls exposed"));
        trainingClick(activity,R.id.cashier_lock);awaitText(activity,"Training sign in","cashier sign out failed");
        try(CatalogStore fixture=CatalogStore.checkoutFixture(getTargetContext(),fixtureId)){
            require(trainingCount(fixture,"SELECT COUNT(*) FROM cashier_refund_links")==3,"linked request audit wrong");
            require(trainingCount(fixture,"SELECT COUNT(*) FROM cashier_refund_links l JOIN cashier_drawer_requests d ON d.id=l.drawer_id WHERE d.state='APPLIED'")==2,"applied refund audit wrong");
            require(trainingCount(fixture,"SELECT SUM(compensated) FROM cashier_refund_totals")==2300,"compensation total wrong");
            require(trainingCount(fixture,"SELECT COUNT(*) FROM checkout_sales")==2&&trainingCount(fixture,"SELECT COUNT(*) FROM checkout_cash")==2&&trainingCount(fixture,"SELECT COUNT(*) FROM stock_movements")==5,"compensation changed original history");
        }
    }

    private void verifySalesReportTrainingUI(Activity activity,String fixtureId,String owner,String ownerPass){
        String inspect=null,expectedReceipt=null;
        try(CatalogStore fixture=CatalogStore.checkoutFixture(getTargetContext(),fixtureId)){
            char[] pass=ownerPass.toCharArray(),staff="Synthetic-Catalog-Cashier-01".toCharArray();
            try{
                String token=fixture.signInFixture(owner,pass),cashier=fixture.signInFixture("catalog-cashier",staff);
                ReportMath.Range range=new ReportMath.Range(0,Long.MAX_VALUE);
                ReportJournal.Report report=fixture.authorizedFixtureSalesReport(token,range);
                require(report.totals.gross.longValueExact()==2600&&report.totals.sales.intValueExact()==2,"report gross or receipt count");
                require(report.totals.refunds.longValueExact()==200&&report.totals.voids.longValueExact()==2100,"report linked compensation classification");
                require(report.totals.net().longValueExact()==300&&report.totals.expenses.longValueExact()==125,"report net or expense");
                require(report.totals.cashIn.longValueExact()==500&&report.totals.cashOut.longValueExact()==200&&report.totals.movement().longValueExact()==475,"report other cash movement");
                require(report.services.size()==2&&report.closedVariance.longValueExact()==-100,"report service or reconciliation");
                rejectStock(()->fixture.authorizedFixtureSalesReport(cashier,range));
                java.util.List<ReportJournal.Event> all=fixture.authorizedFixtureReportEvents(token,range,0,9);
                require(all.size()==7&&fixture.authorizedFixtureReportEvents(token,range,3,3).get(0).id.equals(all.get(3).id),"report event count/pagination");
                require(all.stream().filter(e->"REFUND".equals(e.kind)&&e.receiptId!=null).count()==1&&all.stream().filter(e->"VOID".equals(e.kind)&&e.receiptId!=null).count()==1,"report compensation provenance");
                rejectStock(()->fixture.authorizedFixtureReportEvents(cashier,range,0,9));
                ReportJournal.Report monthly=fixture.authorizedFixtureSalesReport(token,ReportMath.Period.MONTH);
                java.math.BigInteger chartNet=java.math.BigInteger.ZERO;
                for(ReportMath.Bucket bucket:monthly.chart.buckets)chartNet=chartNet.add(bucket.totals.net());
                require(chartNet.equals(monthly.totals.net()),"chart and report snapshot mismatch");
                for(ReportJournal.Event e:fixture.authorizedFixtureReportEvents(token,monthly.range,0,9)){
                    if("VOID".equals(e.kind)){inspect="Inspect Void "+e.id;expectedReceipt=e.receiptId;break;}
                }

                fixture.signOutFixture(token);fixture.signOutFixture(cashier);
            }finally{java.util.Arrays.fill(pass,'\0');java.util.Arrays.fill(staff,'\0');}
        }
        trainingLogin(activity,owner,ownerPass);awaitText(activity,"No open shift","report owner login");
        trainingClick(activity,R.id.cashier_sales_report);awaitText(activity,"Report period: DAY","report screen");
        awaitText(activity,"By service (original sold name)","service breakdown");
        trainingClick(activity,R.id.cashier_report_week);awaitText(activity,"Report period: WEEK","week report");
        trainingClick(activity,R.id.cashier_report_month);awaitText(activity,"Report period: MONTH","month report");
        awaitText(activity,"Closed shift reconciliation (whole shifts closed in period)","reconciliation screen");
        trainingClick(activity,R.id.cashier_report_chart);awaitText(activity,"Training net sales chart","chart screen");
        runOnMainChecked(()->require(activity.findViewById(R.id.cashier_catalog_previous)==null&&activity.findViewById(R.id.cashier_catalog_next)!=null,"chart first-page navigation"));
        trainingClick(activity,R.id.cashier_catalog_next);
        runOnMainChecked(()->require(activity.findViewById(R.id.cashier_catalog_previous)!=null,"chart second-page navigation"));
        trainingClick(activity,R.id.cashier_report_return);awaitText(activity,"Report period: MONTH","chart report return");
        trainingClick(activity,R.id.cashier_report_events);awaitText(activity,"Training report transactions","report history screen");
        runOnMainChecked(()->require(activity.findViewById(R.id.cashier_catalog_previous)==null&&activity.findViewById(R.id.cashier_catalog_next)==null,"report history unnecessary pagination"));
        if(inspect!=null){
            trainingSelect(activity,inspect);awaitText(activity,"Training report transaction","report detail missing");
            awaitText(activity,"Original receipt: "+expectedReceipt,"report detail original receipt missing");
            trainingClick(activity,R.id.cashier_report_events);awaitText(activity,"Training report transactions","report detail return");
        }
        trainingClick(activity,R.id.cashier_report_return);awaitText(activity,"Report period: MONTH","event report return");

        trainingClick(activity,R.id.cashier_back);awaitText(activity,"No open shift","report return");
        trainingClick(activity,R.id.cashier_lock);awaitText(activity,"Training sign in","report owner signout");
        trainingLogin(activity,"catalog-cashier","Synthetic-Catalog-Cashier-01");awaitText(activity,"No open shift","report cashier login");
        runOnMainChecked(()->require(activity.findViewById(R.id.cashier_sales_report)==null,"cashier report exposed"));
        trainingClick(activity,R.id.cashier_lock);awaitText(activity,"Training sign in","report cashier signout");
    }

    private void verifyRecurringTrainingUI(Activity activity,String fixtureId,String owner,String ownerPass){
        trainingLogin(activity,owner,ownerPass);awaitText(activity,"No open shift","recurring owner login");
        trainingField(activity,R.id.cashier_opening,"10.00");trainingClick(activity,R.id.cashier_open_shift);
        awaitText(activity,"Shift open · Expected cash: PHP 10.00","recurring opening");
        trainingClick(activity,R.id.cashier_recurring);awaitText(activity,"Training recurring expenses","schedule list missing");
        trainingClick(activity,R.id.cashier_recurring_new);
        trainingField(activity,R.id.cashier_recurring_name,"Synthetic UI rent");
        trainingField(activity,R.id.cashier_recurring_category,"Rent");
        trainingField(activity,R.id.cashier_recurring_amount,"2.00");
        trainingClick(activity,R.id.cashier_recurring_save);awaitText(activity,"Synthetic UI rent · PHP 2.00 · MONTHLY","schedule not saved");
        trainingSelect(activity,"Review Synthetic UI rent");awaitText(activity,"Synthetic UI rent","schedule detail");
        trainingClick(activity,R.id.cashier_recurring_due);awaitText(activity,"Prepare due training expense","due form");
        trainingField(activity,R.id.cashier_recurring_reason,"Synthetic UI rent payment");
        trainingClick(activity,R.id.cashier_recurring_review);awaitText(activity,"Proposed after: PHP 8.00","due review");
        trainingClick(activity,R.id.cashier_recurring_stage);awaitText(activity,"Training recurring expense · PENDING","due stage");
        trainingClick(activity,R.id.cashier_back);awaitText(activity,"Unresolved training recurring expense","pending routing");
        awaitText(activity,"Shift open · Expected cash: PHP 10.00","stage debited cash");
        trainingClick(activity,R.id.cashier_lock);awaitText(activity,"Training sign in","recurring lock");
        trainingLogin(activity,owner,ownerPass);awaitText(activity,"Unresolved training recurring expense","pending recovery");
        trainingClick(activity,R.id.cashier_drawer);awaitText(activity,"Training recurring expense · PENDING","dedicated recovery");
        trainingField(activity,R.id.cashier_recurring_cancel_reason,"Synthetic cancellation");
        trainingClick(activity,R.id.cashier_recurring_cancel);awaitText(activity,"Training recurring expense · CANCELLED","due cancel");
        trainingClick(activity,R.id.cashier_back);awaitText(activity,"Shift open · Expected cash: PHP 10.00","cancel debited cash");
        trainingClick(activity,R.id.cashier_recurring);awaitText(activity,"Training recurring expenses","recurring list reload");trainingSelect(activity,"Review Synthetic UI rent");awaitText(activity,"Synthetic UI rent","recurring detail reload");
        trainingClick(activity,R.id.cashier_recurring_due);trainingField(activity,R.id.cashier_recurring_reason,"Synthetic retry");
        trainingClick(activity,R.id.cashier_recurring_review);trainingClick(activity,R.id.cashier_recurring_stage);
        awaitText(activity,"Training recurring expense · PENDING","retry stage");
        trainingClick(activity,R.id.cashier_recurring_apply);awaitText(activity,"Training recurring expense · APPLIED","record missing");
        awaitText(activity,"After: PHP 8.00","applied amount");
        trainingClick(activity,R.id.cashier_recurring_history);awaitText(activity,"Training recurring expense history","history missing");
        awaitText(activity,"CANCELLED · Synthetic UI rent · PHP 2.00","cancelled history missing");
        awaitText(activity,"APPLIED · Synthetic UI rent · PHP 2.00","applied history missing");
        trainingClick(activity,R.id.cashier_back);awaitText(activity,"Shift open · Expected cash: PHP 8.00","cash not exact");
        trainingClick(activity,R.id.cashier_recurring);awaitText(activity,"Training recurring expenses","recurring list reload");trainingSelect(activity,"Review Synthetic UI rent");awaitText(activity,"Synthetic UI rent","recurring detail reload");
        runOnMainChecked(()->require(!activity.findViewById(R.id.cashier_recurring_due).isEnabled(),"future occurrence enabled"));
        trainingClick(activity,R.id.cashier_recurring_archive);trainingClick(activity,R.id.cashier_recurring_save);
        awaitText(activity,"Archived","schedule archive missing");
        trainingClick(activity,R.id.cashier_back);awaitText(activity,"Shift open · Expected cash: PHP 8.00","archive affected cash");
        trainingClick(activity,R.id.cashier_close_shift);awaitText(activity,"Close training shift","recurring close");
        trainingField(activity,R.id.cashier_counted,"8.00");trainingClick(activity,R.id.cashier_confirm_close);
        awaitText(activity,"Variance: PHP 0.00","recurring close variance");
        trainingClick(activity,R.id.cashier_return_home);awaitText(activity,"No open shift","recurring shift closed");
        trainingClick(activity,R.id.cashier_lock);awaitText(activity,"Training sign in","recurring owner logout");
        trainingLogin(activity,"catalog-cashier","Synthetic-Catalog-Cashier-01");awaitText(activity,"No open shift","recurring cashier login");
        runOnMainChecked(()->require(activity.findViewById(R.id.cashier_recurring)==null,"cashier recurring access"));
        trainingClick(activity,R.id.cashier_lock);awaitText(activity,"Training sign in","recurring cashier logout");
        try(CatalogStore fixture=CatalogStore.checkoutFixture(getTargetContext(),fixtureId)){
            require(trainingCount(fixture,"SELECT COUNT(*) FROM cashier_recurring_occurrences")==2,"recurring UI occurrence count");
            char[] pass=ownerPass.toCharArray();
            try{
                String token=fixture.signInFixture(owner,pass);
                boolean found=false;
                for(ReportJournal.Event e:fixture.authorizedFixtureReportEvents(token,new ReportMath.Range(0,Long.MAX_VALUE),0,51))
                    if("Synthetic UI rent".equals(e.recurringName)){found=true;require("EXPENSE".equals(e.kind)&&e.amount==200&&e.recurringRevision==1,"recurring report provenance");}
                require(found,"recurring report occurrence missing");fixture.signOutFixture(token);
            }finally{java.util.Arrays.fill(pass,'\0');}
        }
    }

    private void verifyCustomerTrainingUI(Activity activity,String fixtureId,String owner,String ownerPass){
        trainingLogin(activity,owner,ownerPass);awaitText(activity,"No open shift","customer owner login");
        trainingClick(activity,R.id.cashier_customers);awaitText(activity,"Training customers","customer list");
        trainingClick(activity,R.id.cashier_customer_new);awaitText(activity,"New training customer","customer editor");
        trainingField(activity,R.id.cashier_customer_name,"Synthetic Customer Alpha");
        trainingField(activity,R.id.cashier_customer_contact,"Synthetic contact");
        trainingClick(activity,R.id.cashier_customer_save);awaitText(activity,"Synthetic customer · Revision 1 · Active","customer create");
        trainingClick(activity,R.id.cashier_customer_edit);awaitText(activity,"Edit training customer","customer edit");
        trainingField(activity,R.id.cashier_customer_name,"Synthetic Customer Updated");
        trainingClick(activity,R.id.cashier_customer_save);awaitText(activity,"Synthetic customer · Revision 2 · Active","customer revision");
        trainingClick(activity,R.id.cashier_customer_archive);awaitText(activity,"Archive training customer","customer archive review");
        trainingClick(activity,R.id.cashier_customer_save);awaitText(activity,"Synthetic customer · Revision 3 · Archived","customer archived");
        trainingClick(activity,R.id.cashier_customers);awaitText(activity,"Synthetic Customer Updated · Archived","archived list");
        trainingSelect(activity,"Review Synthetic Customer Updated");
        trainingClick(activity,R.id.cashier_customer_archive);awaitText(activity,"Restore training customer","customer restore");
        trainingClick(activity,R.id.cashier_customer_save);awaitText(activity,"Synthetic customer · Revision 4 · Active","customer restored");
        trainingClick(activity,R.id.cashier_customer_history);awaitText(activity,"Training customer revision history","customer history");
        awaitText(activity,"Revision 1 · Synthetic Customer Alpha · Active","original revision missing");
        awaitText(activity,"Revision 3 · Synthetic Customer Updated · Archived","archive history missing");
        trainingClick(activity,R.id.cashier_customer_history);trainingClick(activity,R.id.cashier_customers);
        awaitText(activity,"Training customers","return list");
        trainingField(activity,R.id.cashier_customer_search,"missing");trainingClick(activity,R.id.cashier_customer_find);
        awaitText(activity,"No matching customers","customer search empty");
        trainingField(activity,R.id.cashier_customer_search,"updated");trainingClick(activity,R.id.cashier_customer_find);
        awaitText(activity,"Synthetic Customer Updated · Active","customer search match");
        trainingClick(activity,R.id.cashier_back);awaitText(activity,"No open shift","customer dashboard");
        trainingClick(activity,R.id.cashier_lock);awaitText(activity,"Training sign in","customer owner logout");
        trainingLogin(activity,"catalog-cashier","Synthetic-Catalog-Cashier-01");awaitText(activity,"No open shift","customer cashier login");
        runOnMainChecked(()->require(activity.findViewById(R.id.cashier_customers)==null,"customer owner boundary"));
        trainingClick(activity,R.id.cashier_lock);awaitText(activity,"Training sign in","customer cashier logout");
        try(CatalogStore store=CatalogStore.checkoutFixture(getTargetContext(),fixtureId)){
            require(trainingCount(store,"SELECT COUNT(*) FROM cashier_customer_versions")==4,"customer revision count");
            char[] pass=ownerPass.toCharArray(),staff="Synthetic-Catalog-Cashier-01".toCharArray();
            try{
                String token=store.signInFixture(owner,pass),other=store.signInFixture("catalog-cashier",staff);
                CustomerJournal.Customer c=store.authorizedFixtureCustomers(token,true,"updated",0,9).get(0);
                require(c.revision==4&&c.contact.equals("Synthetic contact"),"customer reopen");
                require(store.saveFixtureCustomer(token,c.id,3,c.name,c.contact,true).revision==4,"exact retry added revision");
                rejectStock(()->store.saveFixtureCustomer(token,c.id,2,"Stale revision","",true));
                rejectStock(()->store.authorizedFixtureCustomers(other,true,"",0,9));
                rejectStock(()->store.saveFixtureCustomer(other,stockId(),0,"Synthetic forbidden","",true));
                store.signOutFixture(token);store.signOutFixture(other);
            }finally{java.util.Arrays.fill(pass,'\0');java.util.Arrays.fill(staff,'\0');}
            require(trainingCount(store,"SELECT COUNT(*) FROM cashier_customer_versions")==4,"unauthorized or replay write");
        }
    }

    private void verifyCreditCheckout(){
        android.content.Context context=getTargetContext();String fixture=stockId(),filename="checkout-fixture-"+fixture+".db";
        char[] ownerPass="Synthetic-Credit-Owner-01".toCharArray(),staffPass="Synthetic-Credit-Staff-01".toCharArray();
        try(CatalogStore store=CatalogStore.checkoutFixture(context,fixture)){
            store.bootstrapDemoFixtureOwner("credit-owner",ownerPass);String owner=store.signInFixture("credit-owner",ownerPass);
            store.createFixtureCashier(owner,"credit-staff",staffPass);String staff=store.signInFixture("credit-staff",staffPass);
            String customer=stockId(),shift=stockId();store.saveFixtureCustomer(owner,customer,0,"Synthetic credit customer","",true);
            store.openFixtureShift(staff,shift,0);
            CustomerLinkJournal.Selection selected=store.authorizedFixtureCheckoutCustomers(staff,shift,"credit",0,9).get(0);
            CatalogEntry service=store.authorizedDemoService(staff);
            CatalogStore.CheckoutSelection chosen=store.authorizedFixtureCheckoutSelection(staff,service.id,service.revision);
            rejectStock(()->store.setAuthorizedFixtureProductionPolicy(staff,service.id,service.revision,0,true));
            store.setAuthorizedFixtureProductionPolicy(owner,service.id,service.revision,0,true);
            CheckoutPlan.Inputs partial=new CheckoutPlan.Inputs(1,1,1,null,null,null,CheckoutPlan.UsageBasis.BILLABLE_UNIT,100);
            CreditCheckoutPlan plan=store.prepareAuthorizedFixtureCreditCheckout(staff,shift,stockId(),chosen,selected,partial);
            require(plan.total==250&&plan.paid==100&&plan.owed==150,"partial credit pricing");
            String ownerShift=stockId();store.openFixtureShift(owner,ownerShift,0);
            rejectStock(()->store.stageAuthorizedFixtureCreditCheckout(owner,ownerShift,plan));
            rejectStock(()->store.stageAuthorizedFixtureCreditCheckout(staff,ownerShift,plan));
            require(store.stageAuthorizedFixtureCreditCheckout(staff,shift,plan).state.equals("PENDING"),"credit stage");
            require(store.stageAuthorizedFixtureCreditCheckout(staff,shift,plan).state.equals("PENDING"),"credit stage replay");
            require(store.expectedFixtureCash(staff,shift)==0,"credit stage changed cash");
            require(store.pendingAuthorizedFixtureCreditCheckout(staff,shift).id.equals(plan.id),"credit pending recovery");
            rejectStock(()->store.closeFixtureShift(staff,shift,0));
            rejectStock(()->store.authorizedFixtureCreditCheckout(owner,plan.id));
            rejectStock(()->store.applyAuthorizedFixtureCreditCheckout(staff,plan.id,"wrong"));
            require(store.applyAuthorizedFixtureCreditCheckout(staff,plan.id,plan.fingerprint).state.equals("APPLIED"),"credit apply");
            store.applyAuthorizedFixtureCreditCheckout(staff,plan.id,plan.fingerprint);
            require(store.expectedFixtureCash(staff,shift)==100,"partial credit cash replay");
            require(store.authorizedFixtureJob(staff,plan.id).financial.balance==150,"automatic direct credit job debt");
            require(trainingCount(store,"SELECT COUNT(*) FROM production_job_events")==1,"direct credit replay duplicate job");
            require(trainingCount(store,"SELECT COUNT(*) FROM credit_receivable_sources")==1,"credit source replay");
            CheckoutPlan.Inputs unpaid=new CheckoutPlan.Inputs(1,1,1,null,null,null,CheckoutPlan.UsageBasis.BILLABLE_UNIT,0);
            CreditCheckoutPlan second=store.prepareAuthorizedFixtureCreditCheckout(staff,shift,stockId(),chosen,selected,unpaid);
            store.stageAuthorizedFixtureCreditCheckout(staff,shift,second);
            store.applyAuthorizedFixtureCreditCheckout(staff,second.id,second.fingerprint);
            require(store.expectedFixtureCash(staff,shift)==100,"unpaid changed cash");
            CreditCheckoutPlan third=store.prepareAuthorizedFixtureCreditCheckout(staff,shift,stockId(),chosen,selected,unpaid);
            store.stageAuthorizedFixtureCreditCheckout(staff,shift,third);
            store.cancelAuthorizedFixtureCreditCheckout(staff,third.id,third.fingerprint,"Synthetic cancellation");
            require(store.cancelAuthorizedFixtureCreditCheckout(staff,third.id,third.fingerprint,"Synthetic cancellation").state.equals("CANCELLED"),"credit cancel replay");
            rejectStock(()->store.applyAuthorizedFixtureCreditCheckout(staff,third.id,third.fingerprint));
            require(trainingCount(store,"SELECT COUNT(*) FROM credit_receivable_sources")==2,"cancel invented credit");
            require(store.pendingAuthorizedFixtureCreditCheckout(staff,shift)==null,"resolved credit remained pending");
            require(store.authorizedFixtureOutstanding(staff,shift,customer).longValueExact()==400,"credit outstanding");
            rejectStock(()->store.prepareAuthorizedFixtureCollection(staff,shift,stockId(),customer,1,401));
            ReceivableJournal.Review collection=store.prepareAuthorizedFixtureCollection(staff,shift,stockId(),customer,1,200);
            require(collection.collection.outstandingBefore.longValueExact()==400&&collection.collection.drawerAfter==300,"collection review");
            ReceivableJournal.Record staged=store.stageAuthorizedFixtureCollection(staff,collection);
            require(staged.fingerprint.equals(collection.fingerprint)&&staged.state.equals("PENDING"),"collection staged identity");
            require(store.stageAuthorizedFixtureCollection(staff,collection).state.equals("PENDING"),"collection stage replay");
            require(store.expectedFixtureCash(staff,shift)==100&&store.authorizedFixtureOutstanding(staff,shift,customer).longValueExact()==400,"pending collection no effects");
            rejectStock(()->store.authorizedFixtureCollection(owner,collection.id));
            rejectStock(()->store.applyAuthorizedFixtureCollection(staff,collection.id,"wrong"));
            rejectStock(()->store.closeFixtureShift(staff,shift,100));
            store.signOutFixture(staff);
            String recovered=store.signInFixture("credit-staff",staffPass);
            require(store.pendingAuthorizedFixtureCollection(recovered,shift).fingerprint.equals(collection.fingerprint),"collection recovery after login");
            store.saveFixtureCustomer(owner,customer,1,"Synthetic archived credit customer","",false);
            require(store.applyAuthorizedFixtureCollection(recovered,collection.id,collection.fingerprint).state.equals("APPLIED"),"archived debt settlement");
            store.applyAuthorizedFixtureCollection(recovered,collection.id,collection.fingerprint);
            require(store.expectedFixtureCash(recovered,shift)==300&&store.authorizedFixtureOutstanding(recovered,shift,customer).longValueExact()==200,"collection exact replay");
            ReceivableJournal.Review remainder=store.prepareAuthorizedFixtureCollection(recovered,shift,stockId(),customer,2,200);
            store.stageAuthorizedFixtureCollection(recovered,remainder);
            store.cancelAuthorizedFixtureCollection(recovered,remainder.id,remainder.fingerprint,"Synthetic cancellation");
            require(store.cancelAuthorizedFixtureCollection(recovered,remainder.id,remainder.fingerprint,"Synthetic cancellation").state.equals("CANCELLED"),"collection cancel replay");
            rejectStock(()->store.applyAuthorizedFixtureCollection(recovered,remainder.id,remainder.fingerprint));
            require(store.authorizedFixtureOutstanding(recovered,shift,customer).longValueExact()==200&&store.expectedFixtureCash(recovered,shift)==300,"collection cancel no effects");
            require(store.pendingAuthorizedFixtureCollection(recovered,shift)==null,"collection pending cleared");
            require(trainingCount(store,"SELECT COUNT(*) FROM cashier_cash_effects_v2 WHERE collection_id IS NOT NULL")==1,"collection effect once");
            ReportJournal.Report creditReport=store.authorizedFixtureSalesReport(owner,new ReportMath.Range(0,Long.MAX_VALUE));
            require(creditReport.totals.gross.longValueExact()==500&&creditReport.totals.sales.intValueExact()==2,"credit gross and count");
            require(creditReport.totals.upfrontCash.longValueExact()==100&&creditReport.totals.debtCreated.longValueExact()==400&&creditReport.totals.debtCollected.longValueExact()==200,"credit report split");
            require(creditReport.totals.net().longValueExact()==500&&creditReport.totals.cashCollected().longValueExact()==300&&creditReport.totals.movement().longValueExact()==300,"credit report reconciliation");
            require(creditReport.services.size()==1&&creditReport.services.get(0).name.equals(service.name)&&creditReport.services.get(0).totals.gross.longValueExact()==500,"credit service history");
            java.util.List<ReportJournal.Event> creditEvents=store.authorizedFixtureReportEvents(owner,creditReport.range,0,9);
            require(creditEvents.size()==3&&creditEvents.stream().filter(e->e.kind.equals("COLLECTION")).count()==1,"credit events no duplicate");
            rejectStock(()->store.authorizedFixtureSalesReport(recovered,creditReport.range));
        }finally{java.util.Arrays.fill(ownerPass,'\0');java.util.Arrays.fill(staffPass,'\0');context.deleteDatabase(filename);}
    }
    private void verifySavedQuotation(){
        android.content.Context context=getTargetContext();String fixture=stockId(),filename="checkout-fixture-"+fixture+".db";
        char[] ownerPass="Synthetic-Quote-Owner-01".toCharArray(),staffPass="Synthetic-Quote-Staff-01".toCharArray();
        String quoteId=stockId(),id=stockId();
        try{
            try(CatalogStore store=CatalogStore.checkoutFixture(context,fixture)){
                store.bootstrapDemoFixtureOwner("quote-owner",ownerPass);String owner=store.signInFixture("quote-owner",ownerPass);
                store.createFixtureCashier(owner,"quote-staff",staffPass);String staff=store.signInFixture("quote-staff",staffPass);
                CatalogEntry service=store.createAuthorizedFixtureCatalog(owner,CatalogEntry.Kind.SERVICE,"Synthetic saved quote","page","5.00");
                CatalogStore.CheckoutSelection select=store.authorizedFixtureCheckoutSelection(staff,service.id,service.revision);
                store.saveFixtureCustomer(owner,id,0,"Synthetic quote customer","",true);
                CustomerLinkJournal.Selection customer=new CustomerLinkJournal.Selection(id,"Synthetic quote customer",1);
                SavedQuotation quote=store.prepareAuthorizedFixtureQuotation(staff,quoteId,select,new CheckoutPlan.Inputs(2,1,1,null,null,null,CheckoutPlan.UsageBasis.NONE,0));
                QuotationJournal.Record saved=store.saveAuthorizedFixtureQuotation(staff,select,quote,customer);
                require(saved.total==1000&&saved.customerRevision==1&&!saved.cancelled,"saved quotation terms incorrect");
                require(store.saveAuthorizedFixtureQuotation(staff,select,quote,customer).id.equals(quoteId),"quote replay failed");
                rejectStock(()->store.saveAuthorizedFixtureQuotation(staff,select,quote,null));
                rejectStock(()->store.saveAuthorizedFixtureQuotation(owner,select,quote,customer));
                require(trainingCount(store,"SELECT COUNT(*) FROM saved_quotations")==1,"duplicate quote");
                require(trainingCount(store,"SELECT COUNT(*) FROM checkout_sales")==0&&trainingCount(store,"SELECT COUNT(*) FROM stock_movements")==1,"quote had financial or stock effect");
                store.saveFixtureCustomer(owner,id,1,"Synthetic renamed","",false);
                require(store.saveAuthorizedFixtureQuotation(staff,select,quote,customer).customerRevision==1,"historical replay changed");
                SavedQuotation newQuote=store.prepareAuthorizedFixtureQuotation(staff,stockId(),select,quote.inputs);
                rejectStock(()->store.saveAuthorizedFixtureQuotation(staff,select,newQuote,customer));
            }
            try(CatalogStore store=CatalogStore.checkoutFixture(context,fixture)){
                String staff=store.signInFixture("quote-staff",staffPass);
                require(store.authorizedFixtureQuotation(staff,quoteId).total==1000,"saved quote lost after reopen");
                String owner=store.signInFixture("quote-owner",ownerPass);
                require(store.cancelAuthorizedFixtureQuotation(owner,quoteId,"Synthetic cancelled quote").cancelled,"quote cancel failed");
                require(store.cancelAuthorizedFixtureQuotation(owner,quoteId,"Synthetic cancelled quote").cancelled,"cancel replay failed");
                rejectStock(()->store.cancelAuthorizedFixtureQuotation(owner,quoteId,"different reason"));
                require(store.authorizedFixtureQuotation(staff,quoteId).cancelled,"cancel history lost");
                require(trainingCount(store,"SELECT COUNT(*) FROM saved_quotation_cancellations")==1,"duplicate cancellation");
            }
        }finally{java.util.Arrays.fill(ownerPass,'\0');java.util.Arrays.fill(staffPass,'\0');context.deleteDatabase(filename);}
    }
    private void verifyProductionPolicy(){
        android.content.Context context=getTargetContext();String fixture=stockId(),filename="checkout-fixture-"+fixture+".db";
        char[] pass="Synthetic-Policy-Owner-01".toCharArray();
        try(CatalogStore store=CatalogStore.checkoutFixture(context,fixture)){
            store.bootstrapDemoFixtureOwner("policy-owner",pass);String owner=store.signInFixture("policy-owner",pass),shift=stockId();
            CatalogEntry service=store.createAuthorizedFixtureCatalog(owner,CatalogEntry.Kind.SERVICE,"Synthetic policy service","page","5.00");
            store.openFixtureShift(owner,shift,0);
            CheckoutPlan.Inputs input=new CheckoutPlan.Inputs(1,1,1,null,null,null,CheckoutPlan.UsageBasis.NONE,500);
            CheckoutPlan.Proposal manual=store.prepareCheckout(stockId(),service.id,1,0,0,input);
            store.stageAuthorizedFixtureCheckout(owner,shift,manual);
            store.applyAuthorizedFixtureCheckout(owner,manual.requestId,manual.fingerprint());
            require(store.findAuthorizedFixtureJob(owner,manual.requestId)==null,"manual default creates no job");
            store.setAuthorizedFixtureProductionPolicy(owner,service.id,1,0,true);
            store.applyAuthorizedFixtureCheckout(owner,manual.requestId,manual.fingerprint());
            require(store.findAuthorizedFixtureJob(owner,manual.requestId)==null,"old sale replay auto-created job");
            CheckoutPlan.Proposal auto=store.prepareCheckout(stockId(),service.id,1,0,0,input);
            store.stageAuthorizedFixtureCheckout(owner,shift,auto);
            require(store.findAuthorizedFixtureJob(owner,auto.requestId)==null,"pending sale has job");
            store.applyAuthorizedFixtureCheckout(owner,auto.requestId,auto.fingerprint());
            store.applyAuthorizedFixtureCheckout(owner,auto.requestId,auto.fingerprint());
            require(store.authorizedFixtureJob(owner,auto.requestId).revision==1,"automatic job absent");
            require(trainingCount(store,"SELECT COUNT(*) FROM production_job_events")==1,"duplicate automatic job");
            require(store.expectedFixtureCash(owner,shift)==1000,"job creation changed cash");
        }finally{java.util.Arrays.fill(pass,'\0');context.deleteDatabase(filename);}
    }
    private void verifyQuotationCashConversion(){
        android.content.Context context=getTargetContext();String fixture=stockId(),filename="checkout-fixture-"+fixture+".db";
        char[] pass="Synthetic-Cash-Quote-Owner-01".toCharArray();
        try(CatalogStore store=CatalogStore.checkoutFixture(context,fixture)){
            store.bootstrapDemoFixtureOwner("conversion-owner",pass);String owner=store.signInFixture("conversion-owner",pass),shift=stockId();
            CatalogEntry service=store.createAuthorizedFixtureCatalog(owner,CatalogEntry.Kind.SERVICE,"Synthetic conversion","page","5.00");
            CatalogStore.CheckoutSelection selection=store.authorizedFixtureCheckoutSelection(owner,service.id,service.revision);
            require(!store.authorizedFixtureCatalogDetail(owner,service.id).production.enabled,"default manual policy");
            require(store.setAuthorizedFixtureProductionPolicy(owner,service.id,service.revision,0,true).revision==1,"enable automatic policy");
            rejectStock(()->store.setAuthorizedFixtureProductionPolicy(owner,service.id,service.revision,0,false));
            SavedQuotation quote=store.prepareAuthorizedFixtureQuotation(owner,stockId(),selection,new CheckoutPlan.Inputs(2,1,1,null,null,null,CheckoutPlan.UsageBasis.NONE,0));
            store.saveAuthorizedFixtureQuotation(owner,selection,quote,null);
            String event=stockId();
            require(store.appendAuthorizedFixtureQuoteJob(owner,quote.id,event,0,ProductionWorkflow.Status.QUOTATION,"Synthetic requested work").revision==1,"manual quotation job creation");
            require(store.appendAuthorizedFixtureQuoteJob(owner,quote.id,event,0,ProductionWorkflow.Status.QUOTATION,"Synthetic requested work").revision==1,"quotation job replay");
            rejectStock(()->store.appendAuthorizedFixtureQuoteJob(owner,quote.id,stockId(),1,ProductionWorkflow.Status.COMPLETED,"Forbidden unpaid release"));
            require(store.appendAuthorizedFixtureQuoteJob(owner,quote.id,stockId(),1,ProductionWorkflow.Status.AWAITING_APPROVAL,"Await approval").revision==2,"quotation approval stage");
            require(trainingCount(store,"SELECT COUNT(*) FROM checkout_sales")==0,"quotation job invented a sale");
            require(store.authorizedFixtureQuotationHistory(owner,0).rows.size()==1,"quote history unavailable");
            require(store.authorizedFixtureQuotation(owner,quote.id).historical().total==1000,"historical decode incorrect");
            for(int i=0;i<8;i++){SavedQuotation more=store.prepareAuthorizedFixtureQuotation(owner,stockId(),selection,quote.inputs);store.saveAuthorizedFixtureQuotation(owner,selection,more,null);}
            require(store.authorizedFixtureQuotationHistory(owner,0).rows.size()==8&&store.authorizedFixtureQuotationHistory(owner,0).more,"quote page missing lookahead");
            require(store.authorizedFixtureQuotationHistory(owner,8).rows.size()==1&&!store.authorizedFixtureQuotationHistory(owner,8).more,"quote last page incorrect");
            store.setAuthorizedFixtureProductionPolicy(owner,service.id,service.revision,1,false);
            store.openFixtureShift(owner,shift,0);
            CheckoutPlan.Proposal sale=store.prepareAuthorizedFixtureQuotationCash(owner,shift,quote.id,stockId(),1200);
            require(sale.totalCentavos==1000&&sale.changeCentavos==200,"conversion cash review wrong");
            require(store.expectedFixtureCash(owner,shift)==0,"quote review changed cash");
            store.convertAuthorizedFixtureQuotationCash(owner,shift,quote.id,sale);
            store.convertAuthorizedFixtureQuotationCash(owner,shift,quote.id,sale);
            require(store.authorizedFixtureJob(owner,sale.requestId).revision==1,"converted quotation job");
            require(sale.requestId.equals(store.authorizedFixtureQuoteJob(owner,quote.id).receipt),"quoted job conversion provenance");
            require(store.authorizedFixtureQuoteJobHistory(owner,quote.id,0).size()==2,"original job history lost");
            require(trainingCount(store,"SELECT COUNT(*) FROM production_job_events")==1,"automatic job replay duplicated");
            store.setAuthorizedFixtureProductionPolicy(owner,service.id,service.revision,2,true);
            store.convertAuthorizedFixtureQuotationCash(owner,shift,quote.id,sale);
            require(trainingCount(store,"SELECT COUNT(*) FROM production_job_events")==1,"policy change affected historical job");
            require(store.expectedFixtureCash(owner,shift)==1000&&trainingCount(store,"SELECT COUNT(*) FROM checkout_sales")==1,"conversion replay duplicated sale");
            require(store.authorizedFixtureQuotation(owner,quote.id).cashCheckout.equals(sale.requestId),"conversion link missing");
            rejectStock(()->store.cancelAuthorizedFixtureQuotation(owner,quote.id,"already converted"));
            rejectStock(()->store.prepareAuthorizedFixtureQuotationCash(owner,shift,quote.id,stockId(),1200));
            require(trainingCount(store,"SELECT COUNT(*) FROM saved_quotation_cash_conversions")==1,"conversion link duplicated");
        }finally{java.util.Arrays.fill(pass,'\0');context.deleteDatabase(filename);}
    }
    private void verifyQuotationCreditConversion(){
        android.content.Context context=getTargetContext();String fixture=stockId(),filename="checkout-fixture-"+fixture+".db";
        char[] pass="Synthetic-Credit-Quote-Owner-01".toCharArray();
        try(CatalogStore store=CatalogStore.checkoutFixture(context,fixture)){
            store.bootstrapDemoFixtureOwner("credit-quote-owner",pass);String owner=store.signInFixture("credit-quote-owner",pass),shift=stockId(),cid=stockId();
            CatalogEntry service=store.createAuthorizedFixtureCatalog(owner,CatalogEntry.Kind.SERVICE,"Synthetic credit conversion","page","5.00");
            CatalogStore.CheckoutSelection selection=store.authorizedFixtureCheckoutSelection(owner,service.id,service.revision);
            require(!store.authorizedFixtureCatalogDetail(owner,service.id).production.enabled,"default manual policy");
            require(store.setAuthorizedFixtureProductionPolicy(owner,service.id,service.revision,0,true).revision==1,"enable automatic policy");
            rejectStock(()->store.setAuthorizedFixtureProductionPolicy(owner,service.id,service.revision,0,false));
            store.saveFixtureCustomer(owner,cid,0,"Synthetic conversion customer","",true);
            CustomerLinkJournal.Selection customer=new CustomerLinkJournal.Selection(cid,"Synthetic conversion customer",1);
            SavedQuotation quote=store.prepareAuthorizedFixtureQuotation(owner,stockId(),selection,new CheckoutPlan.Inputs(2,1,1,null,null,null,CheckoutPlan.UsageBasis.NONE,0));
            store.saveAuthorizedFixtureQuotation(owner,selection,quote,customer);
            SavedQuotation unpaid=store.prepareAuthorizedFixtureQuotation(owner,stockId(),selection,quote.inputs);store.saveAuthorizedFixtureQuotation(owner,selection,unpaid,customer);
            SavedQuotation anonymous=store.prepareAuthorizedFixtureQuotation(owner,stockId(),selection,quote.inputs);store.saveAuthorizedFixtureQuotation(owner,selection,anonymous,null);
            store.openFixtureShift(owner,shift,0);
            rejectStock(()->store.prepareAuthorizedFixtureQuotationCredit(owner,shift,anonymous.id,stockId(),0));
            CreditCheckoutPlan partial=store.prepareAuthorizedFixtureQuotationCredit(owner,shift,quote.id,stockId(),400);
            require(partial.total==1000&&partial.owed==600,"credit conversion review incorrect");
            require(store.expectedFixtureCash(owner,shift)==0,"credit review changed cash");
            store.convertAuthorizedFixtureQuotationCredit(owner,shift,quote.id,partial);
            store.convertAuthorizedFixtureQuotationCredit(owner,shift,quote.id,partial);
            require(store.authorizedFixtureJob(owner,partial.id).financial.balance==600,"automatic credit job debt");
            require(trainingCount(store,"SELECT COUNT(*) FROM production_job_events")==1,"credit replay duplicated job");
            require(store.expectedFixtureCash(owner,shift)==400&&trainingCount(store,"SELECT COUNT(*) FROM credit_receivable_sources")==1,"credit conversion replay duplicated effects");
            require(store.authorizedFixtureQuotation(owner,quote.id).creditCheckout.equals(partial.id),"credit quote receipt link missing");
            rejectStock(()->store.prepareAuthorizedFixtureQuotationCash(owner,shift,quote.id,stockId(),1000));
            rejectStock(()->store.cancelAuthorizedFixtureQuotation(owner,quote.id,"already converted"));
            CreditCheckoutPlan zero=store.prepareAuthorizedFixtureQuotationCredit(owner,shift,unpaid.id,stockId(),0);
            store.convertAuthorizedFixtureQuotationCredit(owner,shift,unpaid.id,zero);
            require(store.expectedFixtureCash(owner,shift)==400&&trainingCount(store,"SELECT COUNT(*) FROM credit_receivable_sources")==2,"unpaid conversion affected cash");
            require(store.authorizedFixtureJob(owner,zero.id).financial.balance==1000,"automatic unpaid job debt");
            require(trainingCount(store,"SELECT COUNT(*) FROM production_job_events")==2,"credit jobs missing");
            require(trainingCount(store,"SELECT COUNT(*) FROM saved_quotation_credit_conversions")==2,"quote credit history incorrect");
        }finally{java.util.Arrays.fill(pass,'\0');context.deleteDatabase(filename);}
    }
    private void verifyCustomerCheckoutLink(){
        android.content.Context context=getTargetContext();String fixture=stockId(),filename="checkout-fixture-"+fixture+".db";
        char[] ownerPass="Synthetic-Link-Owner-01".toCharArray(),staffPass="Synthetic-Link-Staff-01".toCharArray();
        try(CatalogStore store=CatalogStore.checkoutFixture(context,fixture)){
            store.bootstrapDemoFixtureOwner("link-owner",ownerPass);String owner=store.signInFixture("link-owner",ownerPass);
            store.createFixtureCashier(owner,"link-staff",staffPass);String staff=store.signInFixture("link-staff",staffPass);
            String customer=stockId(),shift=stockId();store.saveFixtureCustomer(owner,customer,0,"Synthetic original","Synthetic private",true);
            store.openFixtureShift(staff,shift,0);
            CustomerLinkJournal.Selection selected=store.authorizedFixtureCheckoutCustomers(staff,shift,"original",0,9).get(0);
            CheckoutPlan.Proposal plan=store.prepareDemoFixtureCheckout(staff,shift,stockId(),1,300);
            store.saveFixtureCustomer(owner,customer,1,"Synthetic renamed","Synthetic private",true);
            rejectStock(()->store.stageAuthorizedFixtureCheckout(staff,shift,plan,selected));
            require(trainingCount(store,"SELECT COUNT(*) FROM checkout_requests")==0,"stale customer stranded checkout");
            CustomerLinkJournal.Selection fresh=store.authorizedFixtureCheckoutCustomers(staff,shift,"renamed",0,9).get(0);
            store.stageAuthorizedFixtureCheckout(staff,shift,plan,fresh);
            require(store.expectedFixtureCash(staff,shift)==0,"stage debited cash");
            rejectStock(()->store.stageAuthorizedFixtureCheckout(staff,shift,plan));
            store.saveFixtureCustomer(owner,customer,2,"Synthetic renamed","Synthetic private",false);
            require(store.stageAuthorizedFixtureCheckout(staff,shift,plan,fresh).state.equals("PENDING"),"exact replay after archive");
            require(store.authorizedFixtureCheckoutCustomer(staff,plan.requestId).revision==2,"snapshot not retained");
            rejectStock(()->store.authorizedFixtureCheckoutCustomer(owner,plan.requestId));
            store.applyAuthorizedFixtureCheckout(staff,plan.requestId,plan.fingerprint());
            store.applyAuthorizedFixtureCheckout(staff,plan.requestId,plan.fingerprint());
            require(store.expectedFixtureCash(staff,shift)==250&&trainingCount(store,"SELECT COUNT(*) FROM checkout_sales")==1,"customer sale recorded twice");
            require(store.authorizedFixtureCheckoutCustomer(staff,plan.requestId).name.equals("Synthetic renamed"),"receipt attribution changed");
        }finally{java.util.Arrays.fill(ownerPass,'\0');java.util.Arrays.fill(staffPass,'\0');context.deleteDatabase(filename);}
    }
    private void verifyCustomerCheckoutUI(Activity activity){
        trainingLogin(activity,"catalog-cashier","Synthetic-Catalog-Cashier-01");awaitText(activity,"No open shift","customer checkout cashier login");
        trainingField(activity,R.id.cashier_opening,"0.00");trainingClick(activity,R.id.cashier_open_shift);
        awaitText(activity,"Shift open · Expected cash: PHP 0.00","customer shift");
        trainingClick(activity,R.id.cashier_new_checkout);awaitText(activity,"Choose training service","customer service selection");
        trainingSelect(activity,"Choose Synthetic UI configured");awaitText(activity,"New training checkout","customer checkout");
        trainingField(activity,R.id.cashier_checkout_pages,"3");trainingField(activity,R.id.cashier_checkout_copies,"2");
        trainingField(activity,R.id.cashier_cash,"25.00");trainingSpinner(activity,R.id.cashier_checkout_basis,CheckoutPlan.UsageBasis.PRODUCED_ITEM.ordinal());
        trainingClick(activity,R.id.cashier_review);awaitText(activity,"Review training checkout","customer review");
        trainingClick(activity,R.id.cashier_checkout_customer);awaitText(activity,"Select training customer","customer picker");
        trainingSelect(activity,"Select Synthetic Customer Updated");awaitText(activity,"Review training checkout","selected customer review");
        awaitText(activity,"Customer: Synthetic Customer Updated","customer review attribution");
        trainingClick(activity,R.id.cashier_stage);awaitText(activity,"Pending training checkout","linked pending");
        awaitText(activity,"Customer: Synthetic Customer Updated","customer pending attribution");
        trainingClick(activity,R.id.cashier_record);awaitText(activity,"Recorded training receipt","linked receipt");
        awaitText(activity,"Customer: Synthetic Customer Updated","customer receipt attribution");
        trainingClick(activity,R.id.cashier_return_home);awaitText(activity,"Shift open · Expected cash: PHP 21.00","named checkout total");
        trainingClick(activity,R.id.cashier_close_shift);awaitText(activity,"Close training shift","named close");
        trainingField(activity,R.id.cashier_counted,"21.00");trainingClick(activity,R.id.cashier_confirm_close);
        awaitText(activity,"Variance: PHP 0.00","named close variance");
        trainingClick(activity,R.id.cashier_return_home);awaitText(activity,"No open shift","named shift closed");
        trainingClick(activity,R.id.cashier_lock);awaitText(activity,"Training sign in","named cashier signout");
    }

    private void verifyCreditCheckoutUI(Activity activity,String fixtureId){
        trainingLogin(activity,"catalog-cashier","Synthetic-Catalog-Cashier-01");awaitText(activity,"No open shift","credit cashier login");
        trainingField(activity,R.id.cashier_opening,"0.00");trainingClick(activity,R.id.cashier_open_shift);
        awaitText(activity,"Shift open · Expected cash: PHP 0.00","credit shift");
        trainingClick(activity,R.id.cashier_new_checkout);awaitText(activity,"Choose training service","credit service");
        trainingSelect(activity,"Choose Synthetic UI configured");awaitText(activity,"New training checkout","credit checkout");
        trainingField(activity,R.id.cashier_checkout_pages,"3");trainingField(activity,R.id.cashier_checkout_copies,"2");
        trainingSpinner(activity,R.id.cashier_checkout_basis,CheckoutPlan.UsageBasis.PRODUCED_ITEM.ordinal());
        trainingSpinner(activity,R.id.cashier_payment_mode,1);trainingField(activity,R.id.cashier_cash,"5.00");
        trainingClick(activity,R.id.cashier_review);awaitText(activity,"Select training credit customer","credit picker");
        trainingSelect(activity,"Select Synthetic Customer Updated");awaitText(activity,"Review training credit checkout","credit review");
        awaitText(activity,"Original debt: PHP 16.00","credit review debt");
        trainingClick(activity,R.id.cashier_credit_stage);awaitText(activity,"Pending training credit checkout","credit stage");
        trainingClick(activity,R.id.cashier_return_home);awaitText(activity,"Recover pending credit checkout","credit recovery entry");
        trainingClick(activity,R.id.cashier_lock);awaitText(activity,"Training sign in","credit lock");
        trainingLogin(activity,"catalog-cashier","Synthetic-Catalog-Cashier-01");awaitText(activity,"Recover pending credit checkout","credit recovery login");
        trainingClick(activity,R.id.cashier_credit_pending);awaitText(activity,"Pending training credit checkout","credit recovered");
        awaitText(activity,"Customer: Synthetic Customer Updated","credit attribution recovered");
        trainingClick(activity,R.id.cashier_credit_record);awaitText(activity,"Recorded training credit receipt","credit receipt");
        awaitText(activity,"Cash paid: PHP 5.00","credit receipt cash");
        trainingClick(activity,R.id.cashier_return_home);awaitText(activity,"Shift open · Expected cash: PHP 5.00","credit cash only");
        trainingClick(activity,R.id.cashier_new_checkout);awaitText(activity,"Choose training service","unpaid service");
        trainingSelect(activity,"Choose Synthetic UI configured");awaitText(activity,"New training checkout","unpaid form");
        trainingField(activity,R.id.cashier_checkout_pages,"3");trainingField(activity,R.id.cashier_checkout_copies,"2");
        trainingSpinner(activity,R.id.cashier_checkout_basis,CheckoutPlan.UsageBasis.PRODUCED_ITEM.ordinal());
        trainingSpinner(activity,R.id.cashier_payment_mode,2);trainingField(activity,R.id.cashier_cash,"0.00");
        trainingClick(activity,R.id.cashier_review);awaitText(activity,"Select training credit customer","unpaid picker");
        trainingSelect(activity,"Select Synthetic Customer Updated");awaitText(activity,"Review training credit checkout","unpaid review");
        awaitText(activity,"Original debt: PHP 21.00","unpaid debt");
        trainingClick(activity,R.id.cashier_credit_stage);awaitText(activity,"Pending training credit checkout","unpaid pending");
        trainingField(activity,R.id.cashier_cancel_reason,"Synthetic unpaid cancel");trainingClick(activity,R.id.cashier_cancel);
        awaitText(activity,"Cancelled training credit checkout","unpaid cancelled");
        trainingClick(activity,R.id.cashier_return_home);awaitText(activity,"Shift open · Expected cash: PHP 5.00","cancel cash unchanged");
        trainingClick(activity,R.id.cashier_credit_history);awaitText(activity,"Recorded training credit receipts","credit history");
        trainingSelect(activity,"Synthetic UI configured · PHP 21.00");awaitText(activity,"Recorded training credit receipt","credit history receipt");
        trainingClick(activity,R.id.cashier_return_home);awaitText(activity,"Shift open · Expected cash: PHP 5.00","return from history");
        trainingClick(activity,R.id.cashier_close_shift);awaitText(activity,"Close training shift","credit close");
        trainingField(activity,R.id.cashier_counted,"5.00");trainingClick(activity,R.id.cashier_confirm_close);
        awaitText(activity,"Variance: PHP 0.00","credit exact close");
        trainingClick(activity,R.id.cashier_return_home);awaitText(activity,"No open shift","credit closed");
        trainingClick(activity,R.id.cashier_lock);awaitText(activity,"Training sign in","credit signout");
        try(CatalogStore fixture=CatalogStore.checkoutFixture(getTargetContext(),fixtureId)){
            require(trainingCount(fixture,"SELECT COUNT(*) FROM credit_receivable_sources WHERE total=2100 AND paid=500")==1,"credit UI source");
            require(trainingCount(fixture,"SELECT COUNT(*) FROM credit_checkout_requests WHERE state='CANCELLED' AND paid=0")==1,"unpaid UI cancel");
        }
    }

    private void verifyCollectionUI(Activity activity,String fixtureId,String ownerName,String ownerPassphrase){
        try(CatalogStore fixture=CatalogStore.checkoutFixture(getTargetContext(),fixtureId)){
            char[] password=ownerPassphrase.toCharArray();
            try{
                String owner=fixture.signInFixture(ownerName,password);
                CustomerJournal.Customer debtor=fixture.authorizedFixtureCustomers(owner,true,"Synthetic Customer Updated",0,9).get(0);
                fixture.saveFixtureCustomer(owner,debtor.id,debtor.revision,debtor.name,debtor.contact,false);
                fixture.signOutFixture(owner);
            }finally{java.util.Arrays.fill(password,'\0');}
        }
        trainingLogin(activity,"catalog-cashier","Synthetic-Catalog-Cashier-01");awaitText(activity,"No open shift","collection cashier login");
        trainingField(activity,R.id.cashier_opening,"0.00");trainingClick(activity,R.id.cashier_open_shift);
        awaitText(activity,"Shift open · Expected cash: PHP 0.00","collection shift");
        trainingClick(activity,R.id.cashier_collection_new);awaitText(activity,"Select training debtor","debtor picker");
        awaitText(activity,"Synthetic Customer Updated · Archived","archived debtor available");
        awaitText(activity,"Outstanding: PHP 16.00","debtor balance");
        trainingSelect(activity,"Collect from Synthetic Customer Updated");awaitText(activity,"New training debt collection","collection amount");
        trainingField(activity,R.id.cashier_collection_amount,"16.01");trainingClick(activity,R.id.cashier_collection_review);
        awaitText(activity,"Enter a collection amount no greater than the outstanding balance.","overpayment refused");
        trainingField(activity,R.id.cashier_collection_amount,"6.00");trainingClick(activity,R.id.cashier_collection_review);
        awaitText(activity,"Review training debt collection","collection review");
        awaitText(activity,"Outstanding after: PHP 10.00","allocation balance");
        trainingClick(activity,R.id.cashier_collection_stage);awaitText(activity,"Pending training debt collection","collection stage");
        trainingClick(activity,R.id.cashier_return_home);awaitText(activity,"Recover pending debt collection","collection pending dashboard");
        trainingClick(activity,R.id.cashier_lock);awaitText(activity,"Training sign in","collection lock");
        trainingLogin(activity,"catalog-cashier","Synthetic-Catalog-Cashier-01");awaitText(activity,"Recover pending debt collection","collection recovery login");
        trainingClick(activity,R.id.cashier_collection_pending);awaitText(activity,"Pending training debt collection","collection recovered");
        awaitText(activity,"Payment: PHP 6.00","recovered payment");
        trainingClick(activity,R.id.cashier_collection_record);awaitText(activity,"Recorded training debt collection","collection recorded");
        trainingClick(activity,R.id.cashier_return_home);awaitText(activity,"Shift open · Expected cash: PHP 6.00","collection cash");
        trainingClick(activity,R.id.cashier_collection_new);awaitText(activity,"Outstanding: PHP 10.00","remaining balance after settlement");
        trainingSelect(activity,"Collect from Synthetic Customer Updated");awaitText(activity,"New training debt collection","second collection");
        trainingField(activity,R.id.cashier_collection_amount,"1.00");trainingClick(activity,R.id.cashier_collection_review);
        awaitText(activity,"Review training debt collection","second review");
        trainingClick(activity,R.id.cashier_collection_stage);awaitText(activity,"Pending training debt collection","second pending");
        trainingField(activity,R.id.cashier_cancel_reason,"Synthetic cancelled collection");trainingClick(activity,R.id.cashier_cancel);
        awaitText(activity,"Cancelled training debt collection","collection cancelled");
        trainingClick(activity,R.id.cashier_return_home);awaitText(activity,"Shift open · Expected cash: PHP 6.00","cancel no cash");
        trainingClick(activity,R.id.cashier_collection_history);awaitText(activity,"Training collection history","collection history");
        trainingSelect(activity,"Synthetic Customer Updated · PHP 6.00");awaitText(activity,"Recorded training debt collection","history applied");
        trainingClick(activity,R.id.cashier_return_home);awaitText(activity,"Shift open · Expected cash: PHP 6.00","return collection history");
        trainingClick(activity,R.id.cashier_close_shift);awaitText(activity,"Close training shift","collection close");
        trainingField(activity,R.id.cashier_counted,"6.00");trainingClick(activity,R.id.cashier_confirm_close);
        awaitText(activity,"Variance: PHP 0.00","collection exact close");
        trainingClick(activity,R.id.cashier_return_home);awaitText(activity,"No open shift","collection closed");
        trainingClick(activity,R.id.cashier_lock);awaitText(activity,"Training sign in","collection signout");
        try(CatalogStore fixture=CatalogStore.checkoutFixture(getTargetContext(),fixtureId)){
            require(trainingCount(fixture,"SELECT COUNT(*) FROM credit_collection_requests WHERE state='APPLIED' AND amount=600")==1,"collection UI applied once");
            require(trainingCount(fixture,"SELECT COUNT(*) FROM credit_collection_requests WHERE state='CANCELLED' AND amount=100")==1,"collection UI cancelled");
            require(trainingCount(fixture,"SELECT COUNT(*) FROM credit_receivable_open WHERE remaining=1000")==1,"collection remaining exact");
        }
    }

    private void newQuotationUI(Activity a,boolean named){
        trainingClick(a,R.id.cashier_quote_new);awaitText(a,"Choose quotation service","quotation service");
        trainingSelect(a,"Choose Synthetic UI configured");awaitText(a,"New training quotation","quotation form");
        trainingField(a,R.id.cashier_checkout_pages,"3");trainingField(a,R.id.cashier_checkout_copies,"2");
        trainingSpinner(a,R.id.cashier_checkout_basis,CheckoutPlan.UsageBasis.PRODUCED_ITEM.ordinal());
        trainingClick(a,R.id.cashier_quote_review);awaitText(a,"Review training quotation","quotation review");
        awaitText(a,"Total: PHP 21.00","quotation amount");
        if(named){
            trainingClick(a,R.id.cashier_checkout_customer);awaitText(a,"Choose quotation customer","quotation customer");
            trainingSelect(a,"Select Synthetic Customer Updated");awaitText(a,"Customer: Synthetic Customer Updated","quotation attribution");
        }
        trainingClick(a,R.id.cashier_quote_save);awaitText(a,"Training quotation · Saved","quotation saved");
    }
    private void verifyQuotationUI(Activity a,String fixtureId,String ownerName,String ownerPassphrase){
        // Collection acceptance deliberately archives this customer. Verify that fence,
        // then restore the synthetic customer through the authorized owner API.
        try(CatalogStore fixture=CatalogStore.checkoutFixture(getTargetContext(),fixtureId)){
            char[] password=ownerPassphrase.toCharArray();
            try{
                String owner=fixture.signInFixture(ownerName,password);
                require(fixture.authorizedFixtureQuotationCustomers(owner,"Synthetic Customer Updated",0,9).isEmpty(),"archived quotation customer visible");
                CustomerJournal.Customer archived=fixture.authorizedFixtureCustomers(owner,false,"Synthetic Customer Updated",0,9).get(0);
                require(!archived.active,"collection fixture should archive customer");
                fixture.saveFixtureCustomer(owner,archived.id,archived.revision,archived.name,archived.contact,true);
                require(fixture.authorizedFixtureQuotationCustomers(owner,"Synthetic Customer Updated",0,9).size()==1,"restored quotation customer missing");
                fixture.signOutFixture(owner);
            }finally{java.util.Arrays.fill(password,'\0');}
        }
        trainingLogin(a,"catalog-cashier","Synthetic-Catalog-Cashier-01");awaitText(a,"No open shift","quotation login");
        newQuotationUI(a,false);
        trainingClick(a,R.id.cashier_quote_job);awaitText(a,"Create quotation production job","manual quote job create");
        trainingField(a,R.id.cashier_job_note,"Synthetic quote instructions");trainingClick(a,R.id.cashier_job_confirm);
        awaitText(a,"Quotation job · Quotation","quotation job created");
        trainingSelect(a,"Move to Awaiting approval");awaitText(a,"Confirm quotation job change","approval confirmation");
        trainingField(a,R.id.cashier_job_note,"Synthetic awaiting approval");trainingClick(a,R.id.cashier_job_confirm);
        awaitText(a,"Quotation job · Awaiting approval","approval stage");
        trainingClick(a,R.id.cashier_job_history);awaitText(a,"Quotation job event history","quote job history");
        awaitText(a,"Synthetic quote instructions","original note preserved");awaitText(a,"Synthetic awaiting approval","approval note preserved");
        trainingClick(a,R.id.cashier_job_reload);awaitText(a,"Quotation job · Awaiting approval","quote job reload");
        trainingClick(a,R.id.cashier_quote_reload);awaitText(a,"Training quotation · Saved","return quotation");
        trainingClick(a,R.id.cashier_quote_convert);awaitText(a,"Open your own shift and resolve pending requests before conversion.","shift fence");
        trainingClick(a,R.id.cashier_quote_reload);awaitText(a,"Training quotation · Saved","reload saved quote");
        trainingField(a,R.id.cashier_cancel_reason,"Synthetic quote cancellation");trainingClick(a,R.id.cashier_quote_cancel);
        awaitText(a,"Training quotation · Cancelled","quote cancelled");
        trainingClick(a,R.id.cashier_quote_job);awaitText(a,"Quotation job · Cancelled","quotation cancellation propagated");
        trainingClick(a,R.id.cashier_quote_jobs);awaitText(a,"Synthetic UI configured · Cancelled","quote job list");
        trainingSelect(a,"Synthetic UI configured · Cancelled");awaitText(a,"Quotation job · Cancelled","cancelled job recovery");
        trainingClick(a,R.id.cashier_back);awaitText(a,"No open shift","no payment from quote");
        newQuotationUI(a,true);
        trainingClick(a,R.id.cashier_quote_job);awaitText(a,"Create quotation production job","credit quote job create");
        trainingField(a,R.id.cashier_job_note,"Synthetic credit job");trainingClick(a,R.id.cashier_job_confirm);
        awaitText(a,"Quotation job · Quotation","credit quote job created");
        trainingClick(a,R.id.cashier_back);awaitText(a,"No open shift","saved quote no shift");
        trainingClick(a,R.id.cashier_open_shift);awaitText(a,"Shift open · Expected cash: PHP 0.00","quote conversion shift");
        trainingClick(a,R.id.cashier_quote_history);awaitText(a,"Saved training quotations","history");
        trainingSelect(a,"Synthetic UI configured · PHP 21.00 · Saved");awaitText(a,"Training quotation · Saved","history detail");
        trainingClick(a,R.id.cashier_quote_convert);awaitText(a,"Convert training quotation","conversion tender");
        trainingSpinner(a,R.id.cashier_payment_mode,1);trainingField(a,R.id.cashier_cash,"5.00");
        trainingClick(a,R.id.cashier_quote_review);awaitText(a,"Confirm quotation conversion","conversion review");
        awaitText(a,"Original debt: PHP 16.00","credit conversion debt");
        trainingClick(a,R.id.cashier_quote_confirm);awaitText(a,"Training quotation · Credit converted","converted link");
        trainingClick(a,R.id.cashier_quote_job);awaitText(a,"Quotation job · Converted","receipt handover");
        trainingClick(a,R.id.cashier_job_reload);awaitText(a,"Training job · Pending","linked receipt job");
        awaitText(a,"Outstanding: PHP 16.00","handover preserves credit balance");
        trainingClick(a,R.id.cashier_back);awaitText(a,"Shift open · Expected cash: PHP 5.00","handover cash effect");
        trainingClick(a,R.id.cashier_quote_history);awaitText(a,"Saved training quotations","converted quote recovery");
        trainingSelect(a,"Synthetic UI configured · PHP 21.00 · Credit converted");awaitText(a,"Training quotation · Credit converted","credit quote recovered");
        trainingClick(a,R.id.cashier_quote_receipt);awaitText(a,"Recorded training credit receipt","linked receipt");
        trainingClick(a,R.id.cashier_receipt_print);awaitText(a,"Reprint training receipt","credit reprint");
        awaitText(a,"Original outstanding: PHP 16.00","original debt in reprint");
        awaitText(a,ReceiptDocument.NOTICE,"non-tax notice");
        trainingClick(a,R.id.cashier_back);awaitText(a,"Shift open · Expected cash: PHP 5.00","cash effect once");
        newQuotationUI(a,false);
        trainingClick(a,R.id.cashier_quote_convert);awaitText(a,"Convert training quotation","cash conversion");
        trainingField(a,R.id.cashier_cash,"25.00");trainingClick(a,R.id.cashier_quote_review);
        awaitText(a,"Confirm quotation conversion","cash review");
        trainingClick(a,R.id.cashier_quote_confirm);awaitText(a,"Training quotation · Cash converted","cash link");
        trainingClick(a,R.id.cashier_quote_receipt);awaitText(a,"Recorded training receipt","cash receipt");
        trainingClick(a,R.id.cashier_receipt_print);awaitText(a,"Reprint training receipt","cash reprint");
        awaitText(a,"Change: PHP 4.00","original change in reprint");
        awaitText(a,"REPRINT - ORIGINAL RECORD","reprint notice");
        trainingClick(a,R.id.cashier_back);awaitText(a,"Shift open · Expected cash: PHP 26.00","cash reprint return");
        trainingClick(a,R.id.cashier_quote_history);awaitText(a,"Saved training quotations","cash quote recovery");
        trainingSelect(a,"Synthetic UI configured · PHP 21.00 · Cash converted");awaitText(a,"Training quotation · Cash converted","cash quote recovered");
        trainingClick(a,R.id.cashier_quote_receipt);awaitText(a,"Recorded training receipt","cash receipt recovered");
        trainingClick(a,R.id.cashier_job_receipt);awaitText(a,"Create training production job","create job screen");
        trainingField(a,R.id.cashier_job_note,"Synthetic print instructions");trainingClick(a,R.id.cashier_job_confirm);
        awaitText(a,"Training job · Pending","job created");
        for(String stage:new String[]{"In production","Quality checking","Ready for pickup","Completed"}){
            trainingSelect(a,"Move to "+stage);
            awaitText(a,stage.equals("Completed")?"Confirm job release":"Confirm production transition","job transition review");
            trainingField(a,R.id.cashier_job_note,"Synthetic "+stage);trainingClick(a,R.id.cashier_job_confirm);
            awaitText(a,"Training job · "+stage,"job stage");
        }
        trainingClick(a,R.id.cashier_job_history);awaitText(a,"Training job event history","job history");
        awaitText(a,"#5 · Completed","job final history");awaitText(a,"#1 · Pending","job creation history");
        trainingClick(a,R.id.cashier_job_reload);awaitText(a,"Training job · Completed","job return");
        trainingClick(a,R.id.cashier_back);awaitText(a,"Shift open · Expected cash: PHP 26.00","cash conversion effect once");
        trainingClick(a,R.id.cashier_close_shift);awaitText(a,"Close training shift","quote close");
        trainingField(a,R.id.cashier_counted,"26.00");trainingClick(a,R.id.cashier_confirm_close);
        awaitText(a,"Variance: PHP 0.00","quote exact close");
        trainingClick(a,R.id.cashier_return_home);awaitText(a,"No open shift","quote shift closed");
        trainingClick(a,R.id.cashier_lock);awaitText(a,"Training sign in","quote logout");
        try(CatalogStore f=CatalogStore.checkoutFixture(getTargetContext(),fixtureId)){
            require(trainingCount(f,"SELECT COUNT(*) FROM saved_quotations")==3,"quotation UI save count");
            require(trainingCount(f,"SELECT COUNT(*) FROM saved_quotation_cancellations")==1,"quotation UI cancellation count");
            require(trainingCount(f,"SELECT COUNT(*) FROM saved_quotation_cash_conversions")==1,"quotation UI cash link");
            require(trainingCount(f,"SELECT COUNT(*) FROM saved_quotation_credit_conversions")==1,"quotation UI credit link");
            require(trainingCount(f,"SELECT COUNT(*) FROM production_quote_events")==3,"quotation job UI event count");
            require(trainingCount(f,"SELECT COUNT(*) FROM production_quote_current WHERE effective_status='CANCELLED'")==1,"cancelled quote job count");
            require(trainingCount(f,"SELECT COUNT(*) FROM production_quote_current WHERE receipt_id IS NOT NULL")==1,"converted quote job count");
        }
    }

    private void verifyDatabaseLifecycleFence() throws java.io.IOException{
        android.content.Context context=getTargetContext();String fixture=stockId(),filename="checkout-fixture-"+fixture+".db";
        java.nio.file.Path path=context.getDatabasePath(filename).toPath();
        try{
            try(CatalogStore first=CatalogStore.checkoutFixture(context,fixture);CatalogStore second=CatalogStore.checkoutFixture(context,fixture)){
                first.getWritableDatabase();second.getReadableDatabase();
                try(DatabaseLifecycleLease ignored=DatabaseLifecycleLease.open(path,true)){throw new AssertionError("Restore lease while helpers open");}
                catch(java.io.IOException expected){}
            }
            try(DatabaseLifecycleLease exclusive=DatabaseLifecycleLease.open(path,true);CatalogStore blocked=CatalogStore.checkoutFixture(context,fixture)){
                rejectStock(blocked::getWritableDatabase);
                exclusive.requireExclusive(path);
            }
            try(CatalogStore reopened=CatalogStore.checkoutFixture(context,fixture)){
                require(reopened.getReadableDatabase().getVersion()==22,"Database reopen after lifecycle exclusion");
            }
        }finally{context.deleteDatabase(filename);}
    }

    private void verifyPreRestoreRecovery() throws java.io.IOException{
        android.content.Context context=getTargetContext();String fixture=stockId(),filename="checkout-fixture-"+fixture+".db";
        java.nio.file.Path path=context.getDatabasePath(filename).toPath(),recovery=null;
        char[] ownerPass="Synthetic-Recovery-Owner-01".toCharArray(),staffPass="Synthetic-Recovery-Staff-01".toCharArray(),
            archivePass="Synthetic-Recovery-Archive-01".toCharArray(),wrong="Synthetic-Recovery-Wrong-01".toCharArray();
        String owner,staff,shift=stockId();
        try{
            try(CatalogStore store=CatalogStore.checkoutFixture(context,fixture)){
                store.bootstrapDemoFixtureOwner("recovery-owner",ownerPass);owner=store.signInFixture("recovery-owner",ownerPass);
                store.createFixtureCashier(owner,"recovery-staff",staffPass);staff=store.signInFixture("recovery-staff",staffPass);
                store.openFixtureShift(owner,shift,0);
            }
            try(DatabaseLifecycleLease lease=DatabaseLifecycleLease.open(path,true)){
                try{NativePreRestoreRecovery.create(context,fixture,lease,staff,staffPass,archivePass);throw new AssertionError("Staff authorized recovery");}
                catch(IllegalStateException expected){}
                try{NativePreRestoreRecovery.create(context,fixture,lease,owner,wrong,archivePass);throw new AssertionError("Wrong owner proof accepted");}
                catch(IllegalStateException expected){}
                try{NativePreRestoreRecovery.create(context,fixture,lease,owner,ownerPass,archivePass);throw new AssertionError("Open shift recovery accepted");}
                catch(IllegalStateException expected){}
            }
            try(CatalogStore store=CatalogStore.checkoutFixture(context,fixture)){
                store.closeFixtureShift(owner,shift,0);
            }
            try(DatabaseLifecycleLease lease=DatabaseLifecycleLease.open(path,true)){
                recovery=NativePreRestoreRecovery.create(context,fixture,lease,owner,ownerPass,archivePass);
                require(java.nio.file.Files.isRegularFile(recovery),"Recovery archive not durable");
            }
            try(CatalogStore store=CatalogStore.checkoutFixture(context,fixture)){
                byte[] archive=java.nio.file.Files.readAllBytes(recovery);
                try{
                    NativeBackupSnapshot.Review review=store.reviewEncryptedFixtureBackup(owner,archive,archivePass);
                    require(review.schema==22&&review.openShifts==0&&review.pendingOperations==0,"Recovery contents invalid");
                    require(trainingCount(store,"SELECT COUNT(*) FROM cashier_sessions")>=2,"Recovery altered source sessions");
                    android.database.sqlite.SQLiteDatabase live=store.getWritableDatabase();
                    live.beginTransaction();
                    try{
                        NativeBackupSnapshot.reviewForRestore(live,context.getCacheDir(),archive,archivePass,owner);
                        rejectStock(()->NativeBackupSnapshot.reviewForRestore(live,context.getCacheDir(),archive,archivePass,staff));
                        live.setTransactionSuccessful();
                    }finally{live.endTransaction();}
                    store.createFixtureCashier(owner,"recovery-new-staff",staffPass);
                    live.beginTransaction();
                    try{
                        rejectStock(()->NativeBackupSnapshot.reviewForRestore(live,context.getCacheDir(),archive,archivePass,owner));
                        live.setTransactionSuccessful();
                    }finally{live.endTransaction();}
                    store.reviewEncryptedFixtureBackup(owner,archive,archivePass);
                }finally{java.util.Arrays.fill(archive,(byte)0);}
            }
        }finally{
            if(recovery!=null)java.nio.file.Files.deleteIfExists(recovery);
            java.util.Arrays.fill(ownerPass,'\0');java.util.Arrays.fill(staffPass,'\0');java.util.Arrays.fill(archivePass,'\0');java.util.Arrays.fill(wrong,'\0');
            context.deleteDatabase(filename);
        }
    }

    private void verifyNativeRestoreCoordinator() throws java.io.IOException{
        android.content.Context context=getTargetContext();String fixture=stockId(),filename="checkout-fixture-"+fixture+".db";
        java.nio.file.Path live=context.getDatabasePath(filename).toPath(),recovery=null;
        char[] ownerPass="Synthetic-Restore-Owner-01".toCharArray(),archivePass="Synthetic-Restore-Archive-01".toCharArray(),
            recoveryPass="Synthetic-Restore-Recovery-01".toCharArray();
        byte[] archive=null;String owner;String earlierId,laterId;
        try{
            try(CatalogStore store=CatalogStore.checkoutFixture(context,fixture)){
                store.bootstrapDemoFixtureOwner("restore-owner",ownerPass);owner=store.signInFixture("restore-owner",ownerPass);
                earlierId=store.createAuthorizedFixtureCatalog(owner,CatalogEntry.Kind.SERVICE,"Synthetic earlier","page","2.00").id;
                archive=store.encryptedFixtureBackup(owner,archivePass);
                laterId=store.createAuthorizedFixtureCatalog(owner,CatalogEntry.Kind.SERVICE,"Synthetic later","page","3.00").id;
            }
            recovery=NativeRestoreCoordinator.restoreFixture(context,fixture,owner,ownerPass,archive,archivePass,recoveryPass);
            require(java.nio.file.Files.isRegularFile(recovery),"Encrypted recovery missing");
            require(!NativeRestoreCoordinator.hasArtifacts(live),"Restore artifacts remain after acceptance");
            try(CatalogStore store=CatalogStore.checkoutFixture(context,fixture)){
                rejectStock(()->store.fixturePrincipal(owner));
                String fresh=store.signInFixture("restore-owner",ownerPass);
                require(fresh!=null,"Owner cannot sign in after restore");
                require(store.get(earlierId)!=null,"Original catalog missing");
                rejectStock(()->store.get(laterId));
                byte[] recoveryBytes=java.nio.file.Files.readAllBytes(recovery);
                try{store.reviewEncryptedFixtureBackup(fresh,recoveryBytes,recoveryPass);}
                finally{java.util.Arrays.fill(recoveryBytes,(byte)0);}
            }
            // Simulate interruption after atomic replacement but before acceptance.
            byte[] recoveryBytes=java.nio.file.Files.readAllBytes(recovery);
            try(DatabaseLifecycleLease lease=DatabaseLifecycleLease.open(live,true);
                BackupEnvelope.Decoded decoded=BackupEnvelope.open(recoveryBytes,22,recoveryPass)){
                try(android.database.sqlite.SQLiteDatabase db=android.database.sqlite.SQLiteDatabase.openDatabase(live.toString(),null,android.database.sqlite.SQLiteDatabase.OPEN_READWRITE)){
                    NativeRestoreCoordinator.checkpoint(db);
                }
                RestoreJournal journal=new RestoreJournal(live,lease);
                journal.prepare(decoded.snapshot);journal.replace();
            }finally{java.util.Arrays.fill(recoveryBytes,(byte)0);}
            // A normal helper open must automatically roll back the interrupted replacement.
            try(CatalogStore reopened=CatalogStore.checkoutFixture(context,fixture)){
                require(reopened.get(earlierId)!=null,"Startup rollback lost original catalog");
                require(!NativeRestoreCoordinator.hasArtifacts(live),"Startup rollback left artifacts");
                rejectStock(()->reopened.get(laterId));
            }
        }finally{
            if(archive!=null)java.util.Arrays.fill(archive,(byte)0);
            if(recovery!=null)java.nio.file.Files.deleteIfExists(recovery);
            java.util.Arrays.fill(ownerPass,'\0');java.util.Arrays.fill(archivePass,'\0');java.util.Arrays.fill(recoveryPass,'\0');
            if(!NativeRestoreCoordinator.hasArtifacts(live))context.deleteDatabase(filename);
        }
    }

    private void verifyNativeBackupSnapshot() throws java.io.IOException{
        android.content.Context context=getTargetContext();String fixture=stockId(),filename="checkout-fixture-"+fixture+".db";
        char[] pass="Synthetic-Backup-Owner-01".toCharArray(),backup="Synthetic-Archive-Password-01".toCharArray(),staffPass="Synthetic-Backup-Staff-01".toCharArray();
        java.io.File decoded=java.io.File.createTempFile("synthetic-backup-",".db",context.getCacheDir());
        try(CatalogStore store=CatalogStore.checkoutFixture(context,fixture)){
            store.setWriteAheadLoggingEnabled(true);
            store.bootstrapDemoFixtureOwner("backup-owner",pass);String owner=store.signInFixture("backup-owner",pass);
            store.createFixtureCashier(owner,"backup-staff",staffPass);String staff=store.signInFixture("backup-staff",staffPass);
            rejectStock(()->store.encryptedFixtureBackup(staff,backup));
            CatalogEntry service=store.createAuthorizedFixtureCatalog(owner,CatalogEntry.Kind.SERVICE,"Synthetic backup service","page","5.00");
            String shift=stockId();store.openFixtureShift(owner,shift,0);
            CheckoutPlan.Proposal plan=store.prepareCheckout(stockId(),service.id,1,0,0,new CheckoutPlan.Inputs(1,1,1,null,null,null,CheckoutPlan.UsageBasis.NONE,500));
            store.stageAuthorizedFixtureCheckout(owner,shift,plan);store.applyAuthorizedFixtureCheckout(owner,plan.requestId,plan.fingerprint());
            CheckoutPlan.Proposal pending=store.prepareCheckout(stockId(),service.id,1,0,0,new CheckoutPlan.Inputs(1,1,1,null,null,null,CheckoutPlan.UsageBasis.NONE,500));
            store.stageAuthorizedFixtureCheckout(owner,shift,pending);
            byte[] archive=store.encryptedFixtureBackup(owner,backup);
            java.io.ByteArrayOutputStream destination=new java.io.ByteArrayOutputStream();
            BackupFileTransfer.write(destination,archive);
            byte[] transferred=BackupFileTransfer.read(new java.io.ByteArrayInputStream(destination.toByteArray()));
            require(java.util.Arrays.equals(archive,transferred),"encrypted archive transfer changed bytes");
            java.util.Arrays.fill(transferred,(byte)0);
            NativeBackupSnapshot.Review review=store.reviewEncryptedFixtureBackup(owner,archive,backup);
            require(review.schema==22&&review.sales==1&&review.pendingSales==1&&review.openShifts==1,"backup review totals");
            require(review.pendingOperations==1&&review.currentOpenShifts==1&&review.currentPendingOperations==1,"restore readiness blockers missing");
            java.io.File residual=new java.io.File(context.getCacheDir(),"native-backup-123456789.db");
            try(java.io.FileOutputStream out=new java.io.FileOutputStream(residual)){out.write(1);}
            NativeBackupSnapshot.cleanup(context.getCacheDir());
            require(!residual.exists(),"legacy interrupted staging not cleaned");
            rejectStock(()->store.reviewEncryptedFixtureBackup(staff,archive,backup));
            char[] wrong="Synthetic-Wrong-Archive-Password".toCharArray();
            try{rejectStock(()->store.reviewEncryptedFixtureBackup(owner,archive,wrong));}finally{java.util.Arrays.fill(wrong,'\0');}
            byte[] bad=archive.clone();bad[bad.length-1]^=1;
            rejectStock(()->store.reviewEncryptedFixtureBackup(owner,bad,backup));
            byte[] malformed=BackupEnvelope.seal(new byte[]{1,2,3},22,backup);
            rejectStock(()->store.reviewEncryptedFixtureBackup(owner,malformed,backup));
            require(store.expectedFixtureCash(owner,shift)==500,"backup changed source cash");
            require(trainingCount(store,"SELECT COUNT(*) FROM cashier_sessions")>=2,"backup removed source sessions");
            try(BackupEnvelope.Decoded opened=BackupEnvelope.open(archive,22,backup)){
                require(opened.schema==22,"backup schema mismatch");
                try(java.io.FileOutputStream out=new java.io.FileOutputStream(decoded)){out.write(opened.snapshot);}
            }
            try(android.database.sqlite.SQLiteDatabase candidate=android.database.sqlite.SQLiteDatabase.openDatabase(decoded.getPath(),null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY)){
                NativeBackupSnapshot.validate(candidate);
                require(candidate.getVersion()==22,"snapshot version mismatch");
                try(android.database.Cursor c=candidate.rawQuery("SELECT COUNT(*) FROM checkout_requests WHERE state='APPLIED' OR state='PENDING'",null)){
                    require(c.moveToFirst()&&c.getLong(0)==2,"committed WAL sale or pending request missing");
                }
                try(android.database.Cursor c=candidate.rawQuery("SELECT expected_cash FROM cashier_drawer_current WHERE shift_id=?",new String[]{shift})){
                    require(c.moveToFirst()&&c.getLong(0)==500,"backup cash mismatch");
                }
            }
            java.util.Arrays.fill(archive,(byte)0);
        }finally{java.util.Arrays.fill(pass,'\0');java.util.Arrays.fill(backup,'\0');java.util.Arrays.fill(staffPass,'\0');android.database.sqlite.SQLiteDatabase.deleteDatabase(decoded);context.deleteDatabase(filename);}
    }

    private void verifyReceiptPdf() throws java.io.IOException{
        ReceiptDocument doc=ReceiptDocument.credit("synthetic-reference-123456789","Synthetic service with a long historical name","Synthetic customer with a long historical name",12500,2500);
        for(ReceiptDocument.Paper paper:ReceiptDocument.Paper.values()){
            ReceiptDocument.Geometry geometry=ReceiptDocument.geometry(paper,30,100);
            java.io.File file=java.io.File.createTempFile("synthetic-receipt-",".pdf",getTargetContext().getCacheDir());
            try{
                int pages;
                try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)){pages=ReceiptPdf.write(doc,true,geometry,out);}
                require(file.length()>100&&pages>=1,"PDF content missing");
                try(android.os.ParcelFileDescriptor fd=android.os.ParcelFileDescriptor.open(file,android.os.ParcelFileDescriptor.MODE_READ_ONLY);
                    android.graphics.pdf.PdfRenderer renderer=new android.graphics.pdf.PdfRenderer(fd)){
                    require(renderer.getPageCount()==pages,"PDF page count");
                    for(int n=0;n<pages;n++){
                        try(android.graphics.pdf.PdfRenderer.Page page=renderer.openPage(n)){
                            require(Math.abs(page.getWidth()-geometry.widthMm*72/25.4)<1,"PDF physical width");
                            require(Math.abs(page.getHeight()-geometry.heightMm*72/25.4)<1,"PDF physical height");
                            android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(page.getWidth(),page.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
                            try{
                                bitmap.eraseColor(android.graphics.Color.WHITE);
                                page.render(bitmap,null,null,android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
                                boolean visible=false;
                                for(int y=0;y<bitmap.getHeight()&&!visible;y++)for(int x=0;x<bitmap.getWidth();x++)
                                    if(android.graphics.Color.red(bitmap.getPixel(x,y))<128){visible=true;break;}
                                require(visible,"PDF page blank");
                            }finally{bitmap.recycle();}
                        }
                    }
                }
            }finally{require(file.delete(),"synthetic PDF cleanup");}
        }
    }

    private void verifyProductionJournal(String fixtureId){
        try(CatalogStore fixture=CatalogStore.checkoutFixture(getTargetContext(),fixtureId)){
            char[] pw="Synthetic-Catalog-Cashier-01".toCharArray();
            try{
                String token=fixture.signInFixture("catalog-cashier",pw);
                String cash,credit;
                try(android.database.Cursor c=fixture.getReadableDatabase().rawQuery("SELECT checkout_id FROM saved_quotation_cash_conversions",null)){
                    require(c.moveToFirst(),"cash conversion missing");cash=c.getString(0);}
                try(android.database.Cursor c=fixture.getReadableDatabase().rawQuery("SELECT checkout_id FROM saved_quotation_credit_conversions",null)){
                    require(c.moveToFirst(),"credit conversion missing");credit=c.getString(0);}
                require(fixture.authorizedFixtureJob(token,cash).status==ProductionWorkflow.Status.COMPLETED,"cash UI release");
                String event=java.util.UUID.randomUUID().toString();
                require(fixture.authorizedFixtureJob(token,credit).revision==1,"credit quote handover job");
                require(fixture.appendAuthorizedFixtureJob(token,credit,event,1,ProductionWorkflow.Status.IN_PRODUCTION,"Synthetic job").revision==2,"credit job stage");
                require(fixture.appendAuthorizedFixtureJob(token,credit,event,1,ProductionWorkflow.Status.IN_PRODUCTION,"Synthetic job").revision==2,"credit job replay");
                long revision=2;
                for(ProductionWorkflow.Status status:new ProductionWorkflow.Status[]{ProductionWorkflow.Status.QUALITY_CHECKING,ProductionWorkflow.Status.READY_FOR_PICKUP}){
                    ProductionJournal.Job job=fixture.appendAuthorizedFixtureJob(token,credit,java.util.UUID.randomUUID().toString(),revision++,status,"Synthetic transition");
                    require(job.status==status&&job.revision==revision,"job stage");
                }
                try{fixture.appendAuthorizedFixtureJob(token,credit,java.util.UUID.randomUUID().toString(),4,ProductionWorkflow.Status.COMPLETED,"Blocked unpaid release");throw new AssertionError("unpaid job released");}
                catch(IllegalStateException expected){}
                require(fixture.authorizedFixtureJob(token,credit).financial.balance==1600,"live job debt");
                require(fixture.authorizedFixtureJobs(token,0).size()==2,"job page");
                fixture.signOutFixture(token);
            }finally{java.util.Arrays.fill(pw,'\0');}
        }
    }

    private void verifyProductionUI(Activity a){
        trainingLogin(a,"catalog-cashier","Synthetic-Catalog-Cashier-01");awaitText(a,"No open shift","job login");
        trainingClick(a,R.id.cashier_jobs);awaitText(a,"Training production jobs","job page");
        trainingSelect(a,"Synthetic UI configured · Ready for pickup");awaitText(a,"Training job · Ready for pickup","credit job detail");
        awaitText(a,"Release blocked: collect outstanding payment first.","unpaid release fence");
        runOnMainChecked(()->require(a.findViewById(R.id.cashier_job_release)==null,"unpaid release action visible"));
        trainingClick(a,R.id.cashier_job_reload);awaitText(a,"Training job · Ready for pickup","reload job");
        trainingClick(a,R.id.cashier_jobs);awaitText(a,"Training production jobs","return job page");
        trainingSelect(a,"Synthetic UI configured · Completed");awaitText(a,"Training job · Completed","cash job detail");
        trainingClick(a,R.id.cashier_back);awaitText(a,"No open shift","job dashboard");
        trainingClick(a,R.id.cashier_lock);awaitText(a,"Training sign in","job logout");
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
                require(store.getReadableDatabase().getVersion()==22,"v4-to-v22 migration version wrong");
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
                        if(former>=8)CredentialJournal.upgrade(db);
                        char[] synthetic="Synthetic-Migration-Owner-01".toCharArray();
                        try{
                            long now=System.currentTimeMillis();
                            CashierJournal.Principal owner=CashierJournal.bootstrap(db,"migration-owner",synthetic,now);
                            migratedToken=CashierJournal.signIn(db,owner.username,synthetic,now);
                            shift=CashierJournal.openShift(db,owner,migratedShift,1000,now);
                            if(former>=8){
                                CashierJournal.Principal legacy=CashierJournal.createCashier(db,owner,"legacy-disabled",synthetic,now);
                                CashierJournal.createCashier(db,owner,"legacy-rotated",synthetic,now);
                                char[] changed="Synthetic-Migration-Rotated-02".toCharArray();
                                try{require(CashierJournal.resetCashierCredential(db,owner,synthetic,"legacy-rotated",changed,now),"v8 credential fixture failed");}
                                finally{java.util.Arrays.fill(changed,'\0');}
                                db.execSQL("UPDATE cashier_accounts SET active=0,revision=2 WHERE id=?",new Object[]{legacy.id});
                                if(former>=9){
                                    StaffJournal.upgrade(db);
                                    CashierJournal.Principal disabled=CashierJournal.createCashier(db,owner,"v9-disabled",synthetic,now);
                                    require(CashierJournal.disableCashier(db,owner,disabled.id,1,synthetic,"Synthetic v9 historical disable",now),"v9 disable fixture failed");
                                    if(former>=10){
                                        AdminJournal.upgrade(db);
                                        CatalogEntry audited=CatalogEntry.create(CatalogEntry.Kind.MATERIAL,"Synthetic v10 audited paper","sheet","1.00");
                                        db.execSQL("INSERT INTO catalog VALUES(?,?,?,?,?,?,?)",new Object[]{audited.id,"MATERIAL",audited.name,audited.unit,audited.centavos,1,0});
                                        db.execSQL("INSERT INTO catalog_history VALUES(?,?,?,?,?,?,?,?)",new Object[]{audited.id,1,"MATERIAL",audited.name,audited.unit,audited.centavos,0,"CREATE"});
                                        AdminJournal.append(db,owner,"CATALOG_CREATE",audited.id,1,null);
                                    }
                                }
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
                if(former>=11)DrawerJournal.upgrade(db);
                if(former>=12)RefundJournal.upgrade(db);
                if(former>=13)RecurringJournal.upgrade(db);
                if(former>=14)CustomerJournal.upgrade(db);
                if(former>=15)CustomerLinkJournal.upgrade(db);
                if(former>=16){ReceivableJournal.createCandidate(db);CreditCashJournal.createCandidate(db);CreditCheckoutJournal.createCandidate(db);}
                if(former>=17)QuotationJournal.upgrade(db);
                if(former>=18)QuotationJournal.upgradeConversion(db);
                if(former>=19)QuotationJournal.upgradeCreditConversion(db);
                if(former>=20)ProductionJournal.upgrade(db);
                if(former>=21)ProductionPolicy.upgrade(db);
                db.setVersion(former);
            }
            try(CatalogStore store=former>=7?CatalogStore.checkoutFixture(context,fixture):new CatalogStore(context,filename)){
                require(store.getReadableDatabase().getVersion()==22,"v5-v22 migration failed");
                require(store.stockBalance(material).quantity==1500&&store.stockHistory(material,0,10).size()==1,"v5 stock changed");
                require(store.pendingStock(material)!=null&&store.pendingStock(material).id.equals(pending),"v5 pending stock lost");
                require(store.get(material).centavos==199,"v5 catalog changed");
                require(trainingCount(store,"SELECT COUNT(*) FROM production_quote_events")==0,"migration invented quotation jobs");
                require(trainingCount(store,"SELECT COUNT(*) FROM production_service_policy")==0,"migration invented production settings");
                require(trainingCount(store,"SELECT COUNT(*) FROM saved_quotations")==0,"migration invented quotations");
                require(trainingCount(store,"SELECT COUNT(*) FROM cashier_admin_events")== (former>=10?1:0),"migration changed administrative audit");
                require(trainingCount(store,"SELECT COUNT(*) FROM cashier_refund_links")==0,"migration invented refund links");
                require(trainingCount(store,"SELECT COUNT(*) FROM cashier_checkout_customers")==0,"migration invented customer links");
                require(trainingCount(store,"SELECT COUNT(*) FROM cashier_customer_versions")==0,"migration invented customers");
                require(trainingCount(store,"SELECT COUNT(*) FROM cashier_recurring_versions")==0&&trainingCount(store,"SELECT COUNT(*) FROM cashier_recurring_occurrences")==0,"migration invented recurring expenses");
                require(trainingCount(store,"SELECT COUNT(*) FROM cashier_drawer_requests")==0&&trainingCount(store,"SELECT COUNT(*) FROM cashier_drawer_effects")==0,"migration invented drawer activity");
                require(trainingCount(store,"SELECT COUNT(*) FROM cashier_drawer_legacy_sales")== (former>=7?1:0),"migration legacy sale provenance wrong");
                if(former>=7){
                    rejectStock(()->store.applyStagedStock(pending));
                    require(store.applyAuthorizedFixtureStock(migratedToken,pending).quantity==1000,"authenticated v7 pending stock not recoverable");
                }else require(store.applyStagedStock(pending).quantity==1000,"v5 pending request not recoverable");
                try(android.database.Cursor c=store.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM checkout_requests",null)){
                    require(c.moveToFirst()&&c.getInt(0)==(former>=7?2:former==6?1:0),"migration changed checkout history");
                }
                require(CashierJournal.initialized(store.getReadableDatabase())==(former>=7),"migration changed account initialization");
                require(trainingCount(store,"SELECT COUNT(*) FROM cashier_credential_events")==(former>=8?1:0),"migration changed credential audit");
                require(trainingCount(store,"SELECT COUNT(*) FROM cashier_disable_events")== (former>=9?1:0),"migration changed disable events");
                require(trainingCount(store,"SELECT COUNT(*) FROM cashier_admin_events")== (former>=7?1:0)+(former>=10?1:0),"only post-migration authenticated resolution should be audited");
                if(former>=8){
                    require(trainingCount(store,"SELECT COUNT(*) FROM cashier_accounts WHERE username='legacy-disabled' AND active=0 AND revision=2")==1,"v8 disabled identity lost");
                    require(trainingCount(store,"SELECT COUNT(*) FROM cashier_accounts WHERE username='legacy-rotated' AND active=1 AND revision=2")==1,"v8 credential revision lost");
                }
                if(former>=6)require(CheckoutJournal.read(store.getReadableDatabase(),historic).state.equals("APPLIED"),"historic sale lost");
                if(former>=7){
                    CashierJournal.Principal owner=CashierJournal.requireSession(store.getReadableDatabase(),migratedToken,System.currentTimeMillis());
                    require(CashierJournal.activeShift(store.getReadableDatabase(),owner).id.equals(migratedShift),"v7 open shift lost");
                    require(CheckoutJournal.read(store.getReadableDatabase(),pendingCheckout).state.equals("PENDING"),"v7 pending checkout lost");
                    require(CashierJournal.expected(store.getReadableDatabase(),CashierJournal.shift(store.getReadableDatabase(),migratedShift))==1250,"v7 drawer changed");
                    CheckoutJournal.Record unresolved=store.authorizedFixtureCheckout(migratedToken,pendingCheckout);
                    require(store.applyAuthorizedFixtureCheckout(migratedToken,pendingCheckout,unresolved.fingerprint).state.equals("APPLIED"),"migrated pending checkout did not apply");
                    require(store.expectedFixtureCash(migratedToken,migratedShift)==1500&&trainingCount(store,"SELECT COUNT(*) FROM cashier_drawer_effects")==1,"migrated pending checkout did not add exactly one new effect");
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

    private void verifyRecurringExpense(){
        android.content.Context context=getTargetContext();String fixture=stockId(),filename="checkout-fixture-"+fixture+".db";
        char[] pass="Synthetic-Recurring-Owner-01".toCharArray(),staff="Synthetic-Recurring-Cashier-01".toCharArray();
        String schedule=stockId(),shift=stockId(),first=stockId(),retry=stockId();
        try(CatalogStore store=CatalogStore.checkoutFixture(context,fixture)){
            store.bootstrapDemoFixtureOwner("recurring-owner",pass);
            String owner=store.signInFixture("recurring-owner",pass);
            store.createFixtureCashier(owner,"recurring-cashier",staff);
            String cashier=store.signInFixture("recurring-cashier",staff);
            String today=RecurringMath.today(System.currentTimeMillis());
            RecurringMath.Cadence monthly=RecurringMath.Cadence.MONTHLY;
            rejectStock(()->store.saveFixtureRecurringDefinition(cashier,schedule,0,"Synthetic Rent","Rent",200,monthly,today,true));
            RecurringJournal.Definition d=store.saveFixtureRecurringDefinition(owner,schedule,0,"Synthetic Rent","Rent",200,monthly,today,true);
            require(d.revision==1&&d.nextIndex==0&&d.nextDue.equals(today),"schedule initial review");
            require(store.saveFixtureRecurringDefinition(owner,schedule,0,"Synthetic Rent","Rent",200,monthly,today,true).revision==1,"schedule retry duplicated version");
            store.openFixtureShift(owner,shift,1000);
            rejectStock(()->store.authorizedFixtureRecurringDefinitions(cashier,0,9));
            rejectStock(()->store.stageFixtureRecurringExpense(owner,shift,first,schedule,1,1,today,200,"Rent","Synthetic scheduled expense",1000));
            RecurringJournal.Occurrence pending=store.stageFixtureRecurringExpense(owner,shift,first,schedule,1,0,today,200,"Rent","Synthetic scheduled expense",1000);
            require(pending.drawer.state.equals("PENDING")&&store.expectedFixtureCash(owner,shift)==1000,"schedule stage moved cash");
            require(store.stageFixtureRecurringExpense(owner,shift,first,schedule,1,0,today,200,"Rent","Synthetic scheduled expense",1000).drawer.id.equals(first),"exact occurrence replay");
            rejectStock(()->store.resolveFixtureDrawer(owner,first,true,null));
            rejectStock(()->store.authorizedFixtureDrawer(owner,first));
            rejectStock(()->store.closeFixtureShift(owner,shift,1000));
            store.resolveFixtureRecurringExpense(owner,first,false,"Synthetic cancellation");
            require(store.authorizedFixtureRecurringDefinitions(owner,0,9).get(0).nextIndex==0,"cancel advanced due");
            store.stageFixtureRecurringExpense(owner,shift,retry,schedule,1,0,today,200,"Rent","Synthetic retry",1000);
            store.resolveFixtureRecurringExpense(owner,retry,true,null);store.resolveFixtureRecurringExpense(owner,retry,true,null);
            require(store.expectedFixtureCash(owner,shift)==800,"recurring effect duplicate or missing");
            RecurringJournal.Definition next=store.authorizedFixtureRecurringDefinitions(owner,0,9).get(0);
            require(next.nextIndex==1&&next.nextDue.equals(RecurringMath.due(today,monthly,1)),"next due wrong");
            require(store.authorizedFixtureRecurringHistory(owner,0,9).size()==2&&store.authorizedFixtureDrawerHistory(owner,0,9).isEmpty(),"linked expense history duplicated");
            require(store.authorizedFixtureSalesReport(owner,new ReportMath.Range(0,Long.MAX_VALUE)).totals.expenses.longValueExact()==200,"recurring expense report wrong");
            store.saveFixtureRecurringDefinition(owner,schedule,1,"Synthetic Rent","Rent",200,monthly,next.nextDue,false);
            require(!store.authorizedFixtureRecurringDefinitions(owner,0,9).get(0).active,"archive missing");
            require(store.closeFixtureShift(owner,shift,800).variance==0,"recurring close wrong");
        }finally{java.util.Arrays.fill(pass,'\0');java.util.Arrays.fill(staff,'\0');context.deleteDatabase(filename);}
    }

    private void verifyDrawerLedger(){
        android.content.Context context=getTargetContext();String fixture=stockId(),filename="checkout-fixture-"+fixture+".db";
        char[] owner="Synthetic-Drawer-Owner-01".toCharArray(),staff="Synthetic-Drawer-Cashier-02".toCharArray(),next="Synthetic-Drawer-Changed-03".toCharArray();
        String ownerShift=stockId(),cashierShift=stockId(),expense=stockId();
        ShiftMath.DrawerKind out=ShiftMath.DrawerKind.CASH_OUT,in=ShiftMath.DrawerKind.CASH_IN,exp=ShiftMath.DrawerKind.EXPENSE;
        try{
            try(CatalogStore ordinary=new CatalogStore(context)){
                rejectStock(()->ordinary.stageFixtureDrawer("bad",ownerShift,expense,exp,100,"Synthetic","Synthetic",1000));
                rejectStock(()->ordinary.authorizedFixtureDrawerHistory("bad",0,10));
            }
            try(CatalogStore store=CatalogStore.checkoutFixture(context,fixture)){
                store.bootstrapDemoFixtureOwner("drawer-owner",owner);
                String token=store.signInFixture("drawer-owner",owner);
                store.createFixtureCashier(token,"drawer-cashier",staff);
                String cashier=store.signInFixture("drawer-cashier",staff);
                store.openFixtureShift(token,ownerShift,1500);store.openFixtureShift(cashier,cashierShift,500);
                rejectStock(()->store.stageFixtureDrawer(cashier,ownerShift,expense,exp,200,"Synthetic","Synthetic",1500));
                rejectStock(()->store.stageFixtureDrawer(token,cashierShift,expense,exp,100,"Synthetic","Synthetic",500));
                rejectStock(()->store.stageFixtureDrawer(cashier,cashierShift,expense,in,200,null,"Synthetic",500));
                rejectStock(()->store.stageFixtureDrawer(cashier,cashierShift,expense,out,100,null,"Synthetic",500));
                rejectStock(()->store.stageFixtureDrawer(token,ownerShift,expense,exp,1501,"Synthetic","Synthetic",1500));
                rejectStock(()->store.stageFixtureDrawer(token,ownerShift,expense,exp,0,"Synthetic","Synthetic",1500));
                rejectStock(()->store.stageFixtureDrawer(token,ownerShift,expense,exp,200,"Synthetic","Synthetic",1400));
                CheckoutPlan.Proposal blocked=store.prepareDemoFixtureCheckout(token,ownerShift,stockId(),1,300);
                store.stageAuthorizedFixtureCheckout(token,ownerShift,blocked);
                rejectStock(()->store.stageFixtureDrawer(token,ownerShift,expense,exp,200,"Synthetic supplies","Synthetic expense",1500));
                store.cancelAuthorizedFixtureCheckout(token,blocked.requestId,blocked.fingerprint(),"Synthetic cancelled");
                CheckoutPlan.Proposal reviewed=store.prepareDemoFixtureCheckout(token,ownerShift,stockId(),1,300);
                DrawerJournal.Request original=store.stageFixtureDrawer(token,ownerShift,expense,exp,200,"Synthetic supplies","Synthetic expense",1500);
                require(original.after==1300&&original.state.equals("PENDING")&&store.expectedFixtureCash(token,ownerShift)==1500,"drawer stage changed cash");
                require(store.stageFixtureDrawer(token,ownerShift,expense,exp,200,"Synthetic supplies","Synthetic expense",1500).id.equals(expense),"drawer stage replay failed");
                rejectStock(()->store.stageFixtureDrawer(token,ownerShift,expense,exp,201,"Synthetic supplies","Synthetic expense",1500));
                rejectStock(()->store.stageFixtureDrawer(token,ownerShift,stockId(),exp,100,"Synthetic supplies","Synthetic competing",1500));
                rejectStock(()->store.stageAuthorizedFixtureCheckout(token,ownerShift,reviewed));
                rejectStock(()->store.closeFixtureShift(token,ownerShift,1500));
                rejectStock(()->store.resolveFixtureDrawer(cashier,expense,true,null));
                rejectStock(()->store.authorizedFixtureDrawer(cashier,expense));
                require(store.authorizedFixtureDrawerHistory(cashier,0,10).isEmpty(),"drawer history leaked cross operator");
                store.getWritableDatabase().execSQL("CREATE TRIGGER fault_drawer_native BEFORE INSERT ON cashier_drawer_effects BEGIN SELECT RAISE(ABORT,'synthetic drawer fault'); END");
                boolean fault=false;
                try{store.resolveFixtureDrawer(token,expense,true,null);}
                catch(android.database.sqlite.SQLiteException expected){fault=true;}
                finally{store.getWritableDatabase().execSQL("DROP TRIGGER fault_drawer_native");}
                require(fault&&store.pendingFixtureDrawer(token,ownerShift).id.equals(expense)&&store.expectedFixtureCash(token,ownerShift)==1500,"drawer fault stranded effect or state");
                store.changeFixturePassphrase(token,owner,next);
                rejectStock(()->store.resolveFixtureDrawer(token,expense,true,null));
                store.signOutFixture(cashier);
            }
            try(CatalogStore reopened=CatalogStore.checkoutFixture(context,fixture)){
                String token=reopened.signInFixture("drawer-owner",next),cashier=reopened.signInFixture("drawer-cashier",staff);
                require(reopened.pendingFixtureDrawer(token,ownerShift).id.equals(expense),"drawer recovery lost original identity");
                DrawerJournal.Request applied=reopened.resolveFixtureDrawer(token,expense,true,null);
                require(applied.state.equals("APPLIED")&&applied.stageRevision==1&&applied.resolvedRevision==2&&reopened.expectedFixtureCash(token,ownerShift)==1300,"drawer revision or cash effect wrong");
                require(reopened.resolveFixtureDrawer(token,expense,true,null).id.equals(expense),"drawer replay failed");
                rejectStock(()->reopened.resolveFixtureDrawer(token,expense,true,"Synthetic ignored reason"));
                rejectStock(()->reopened.resolveFixtureDrawer(token,expense,false,"Synthetic denied"));
                require(trainingCount(reopened,"SELECT COUNT(*) FROM cashier_drawer_effects")==1,"drawer replay duplicated cash");
                CheckoutPlan.Proposal plan=reopened.prepareDemoFixtureCheckout(token,ownerShift,stockId(),1,300);
                reopened.stageAuthorizedFixtureCheckout(token,ownerShift,plan);
                reopened.getWritableDatabase().execSQL("CREATE TRIGGER fault_sale_effect BEFORE INSERT ON cashier_drawer_effects BEGIN SELECT RAISE(ABORT,'synthetic checkout effect fault'); END");
                boolean fault=false;
                try{reopened.applyAuthorizedFixtureCheckout(token,plan.requestId,plan.fingerprint());}
                catch(android.database.sqlite.SQLiteException expected){fault=true;}
                finally{reopened.getWritableDatabase().execSQL("DROP TRIGGER fault_sale_effect");}
                require(fault&&reopened.authorizedFixtureCheckout(token,plan.requestId).state.equals("PENDING")&&reopened.expectedFixtureCash(token,ownerShift)==1300&&trainingCount(reopened,"SELECT COUNT(*) FROM checkout_sales")==0,"checkout effect fault changed cash or sale");
                require(reopened.stockBalance(plan.materials.get(0).materialId).quantity==50000,"checkout effect fault changed inventory");
                reopened.applyAuthorizedFixtureCheckout(token,plan.requestId,plan.fingerprint());
                require(reopened.expectedFixtureCash(token,ownerShift)==1550,"checkout after expense wrong");
                String cashIn=stockId(),cashOut=stockId(),cancel=stockId(),cashierExpense=stockId();
                reopened.stageFixtureDrawer(token,ownerShift,cashIn,in,500,null,"Synthetic incoming",1550);
                reopened.resolveFixtureDrawer(token,cashIn,true,null);
                reopened.stageFixtureDrawer(token,ownerShift,cashOut,out,300,null,"Synthetic outgoing",2050);
                reopened.resolveFixtureDrawer(token,cashOut,true,null);
                reopened.stageFixtureDrawer(token,ownerShift,cancel,out,100,null,"Synthetic cancellation",1750);
                reopened.resolveFixtureDrawer(token,cancel,false,"Synthetic unchanged");
                require(reopened.resolveFixtureDrawer(token,cancel,false,"Synthetic unchanged").state.equals("CANCELLED"),"cancel replay wrong");
                rejectStock(()->reopened.resolveFixtureDrawer(token,cancel,false,"Synthetic different"));
                rejectStock(()->reopened.resolveFixtureDrawer(token,cancel,true,null));
                require(reopened.expectedFixtureCash(token,ownerShift)==1750,"cancel changed drawer");
                reopened.stageFixtureDrawer(cashier,cashierShift,cashierExpense,exp,100,"Synthetic supplies","Synthetic cashier expense",500);
                reopened.resolveFixtureDrawer(cashier,cashierExpense,true,null);
                require(reopened.expectedFixtureCash(cashier,cashierShift)==400,"cashier own expense wrong");
                rejectStock(()->reopened.closeFixtureShift(token,ownerShift,1700,2050));
                require(reopened.closeFixtureShift(token,ownerShift,1700,1750).variance==-50,"owner close variance wrong");
                require(reopened.closeFixtureShift(cashier,cashierShift,400).variance==0,"cashier close variance wrong");
                require(reopened.resolveFixtureDrawer(token,expense,true,null).state.equals("APPLIED"),"closed-shift exact replay failed");
                rejectStock(()->reopened.stageFixtureDrawer(token,ownerShift,stockId(),exp,100,"Synthetic supplies","Closed shift",1750));
                require(reopened.authorizedFixtureDrawerHistory(token,0,10).size()==4&&reopened.authorizedFixtureDrawerHistory(cashier,0,10).size()==1,"drawer own history counts wrong");
                require(trainingCount(reopened,"SELECT COUNT(*) FROM cashier_drawer_effects")==5,"incorrect final cash effects");
                require(trainingCount(reopened,"SELECT COUNT(*) FROM cashier_drawer_requests WHERE state='CANCELLED'")==1,"cancelled audit missing");
                require(trainingCount(reopened,"SELECT COUNT(*) FROM checkout_sales")==1&&reopened.stockBalance(plan.materials.get(0).materialId).quantity==49000,"final sale or material wrong");
                String refundShift=stockId(),refund=stockId(),second=stockId();
                reopened.openFixtureShift(token,refundShift,500);
                RefundJournal.Review originalReceipt=reopened.reviewFixtureRefund(token,plan.requestId);
                require(originalReceipt.originalPaid==250&&originalReceipt.remaining()==250,"receipt compensation review wrong");
                rejectStock(()->reopened.reviewFixtureRefund(cashier,plan.requestId));
                rejectStock(()->reopened.stageFixtureRefund(token,refundShift,refund,plan.requestId,RefundJournal.Kind.VOID,250,0,100,"Synthetic invalid void",500));
                RefundJournal.Request compensation=reopened.stageFixtureRefund(token,refundShift,refund,plan.requestId,RefundJournal.Kind.REFUND,250,0,100,"Synthetic partial refund",500);
                require(compensation.drawer.after==400&&compensation.drawer.state.equals("PENDING"),"refund review wrong");
                require(reopened.stageFixtureRefund(token,refundShift,refund,plan.requestId,RefundJournal.Kind.REFUND,250,0,100,"Synthetic partial refund",500).drawer.id.equals(refund),"refund stage replay wrong");
                rejectStock(()->reopened.resolveFixtureDrawer(token,refund,true,null));
                rejectStock(()->reopened.stageFixtureDrawer(token,refundShift,refund,out,100,null,"Synthetic partial refund",500));
                rejectStock(()->reopened.resolveFixtureRefund(cashier,refund,true,null));
                rejectStock(()->reopened.closeFixtureShift(token,refundShift,500));
                require(reopened.authorizedFixtureRefund(token,refund).checkoutId.equals(plan.requestId),"refund recovery wrong");
                reopened.resolveFixtureRefund(token,refund,true,null);
                reopened.resolveFixtureRefund(token,refund,true,null);
                require(reopened.expectedFixtureCash(token,refundShift)==400&&reopened.reviewFixtureRefund(token,plan.requestId).remaining()==150,"refund apply or replay wrong");
                rejectStock(()->reopened.stageFixtureRefund(token,refundShift,second,plan.requestId,RefundJournal.Kind.REFUND,250,0,100,"Synthetic stale review",400));
                reopened.stageFixtureRefund(token,refundShift,second,plan.requestId,RefundJournal.Kind.REFUND,250,100,150,"Synthetic final review",400);
                reopened.resolveFixtureRefund(token,second,false,"Synthetic cancellation");
                require(reopened.expectedFixtureCash(token,refundShift)==400&&reopened.reviewFixtureRefund(token,plan.requestId).remaining()==150,"cancel changed cash or cap");
                require(trainingCount(reopened,"SELECT COUNT(*) FROM checkout_sales")==1&&reopened.stockBalance(plan.materials.get(0).materialId).quantity==49000,"refund changed original history");
                require(reopened.authorizedFixtureRefundReceipts(token,0,9).size()==1&&reopened.authorizedFixtureRefundReceipts(token,0,9).get(0).review.remaining()==150,"owner original receipt list wrong");
                require(reopened.authorizedFixtureRefundHistory(token,0,9).size()==2&&reopened.authorizedFixtureRefundHistory(token,1,1).size()==1,"owner linked history pagination wrong");
                require(reopened.authorizedFixtureDrawerHistory(token,0,9).size()==4,"refund duplicated ordinary drawer history");
                rejectStock(()->reopened.authorizedFixtureDrawer(token,refund));
                rejectStock(()->reopened.authorizedFixtureRefundReceipts(cashier,0,9));
                rejectStock(()->reopened.authorizedFixtureRefundHistory(cashier,0,9));
                require(reopened.closeFixtureShift(token,refundShift,400).variance==0,"refund close wrong");

            }
        }finally{java.util.Arrays.fill(owner,'\0');java.util.Arrays.fill(staff,'\0');java.util.Arrays.fill(next,'\0');context.deleteDatabase(filename);}
    }

    private void verifyCatalogSelectionAccess(){
        android.content.Context context=getTargetContext();String fixture=stockId(),filename="checkout-fixture-"+fixture+".db";
        char[] owner="Synthetic-Catalog-Owner-01".toCharArray(),staff="Synthetic-Catalog-Staff-02".toCharArray();
        try{
            try(CatalogStore ordinary=new CatalogStore(context)){
                rejectStock(()->ordinary.authorizedFixtureOwnerCatalog("bad",CatalogEntry.Kind.SERVICE,false,0));
                rejectStock(()->ordinary.authorizedFixtureCheckoutServices("bad",0));
            }
            try(CatalogStore store=CatalogStore.checkoutFixture(context,fixture)){
                store.bootstrapDemoFixtureOwner("catalog-owner",owner);
                String token=store.signInFixture("catalog-owner",owner);
                store.createFixtureCashier(token,"catalog-worker",staff);
                String cashier=store.signInFixture("catalog-worker",staff);
                rejectStock(()->store.authorizedFixtureOwnerCatalog(cashier,CatalogEntry.Kind.SERVICE,false,0));
                rejectStock(()->store.authorizedFixtureCheckoutServices("bad",0));
                rejectStock(()->store.authorizedFixtureOwnerCatalog(token,CatalogEntry.Kind.SERVICE,false,-8));
                rejectStock(()->store.authorizedFixtureOwnerCatalog(token,CatalogEntry.Kind.SERVICE,false,1));
                CatalogEntry seed=store.authorizedFixtureCheckoutServices(cashier,0).entries.get(0);
                CatalogEntry material=store.authorizedFixtureOwnerCatalog(token,CatalogEntry.Kind.MATERIAL,false,0).entries.get(0);
                rejectStock(()->store.authorizedFixtureCatalogDetail(cashier,seed.id));
                rejectStock(()->store.authorizedFixtureCheckoutSelection(cashier,material.id,material.revision));
                String stable=stockId();
                CatalogEntry item=store.createAuthorizedFixtureCatalog(token,stable,CatalogEntry.Kind.SERVICE,"Synthetic dynamic service","page","4.20");
                require(store.createAuthorizedFixtureCatalog(token,stable,CatalogEntry.Kind.SERVICE,"Synthetic dynamic service","page","4.20").id.equals(item.id),"create replay changed identity");
                rejectStock(()->store.createAuthorizedFixtureCatalog(token,stable,CatalogEntry.Kind.SERVICE,"Synthetic different","page","4.20"));
                rejectStock(()->store.createAuthorizedFixtureCatalog(cashier,stable,CatalogEntry.Kind.SERVICE,"Synthetic dynamic service","page","4.20"));
                require(trainingCount(store,"SELECT COUNT(*) FROM cashier_admin_events WHERE action='CATALOG_CREATE'")==1,"create replay duplicated audit");
                for(int i=0;i<7;i++)store.createAuthorizedFixtureCatalog(token,CatalogEntry.Kind.SERVICE,"Synthetic page "+i,"unit","1.00");
                CatalogStore.CatalogPage first=store.authorizedFixtureCheckoutServices(cashier,0),second=store.authorizedFixtureCheckoutServices(cashier,8);
                require(first.entries.size()==8&&first.more&&second.entries.size()==1&&!second.more,"checkout pagination wrong");
                require(first.entries.stream().noneMatch(e->e.id.equals(second.entries.get(0).id)),"checkout page duplicate");
                store.archiveAuthorizedFixtureCatalog(token,seed.id,seed.revision,true);
                require(store.authorizedFixtureCheckoutServices(cashier,0).entries.size()==8&&!store.authorizedFixtureCheckoutServices(cashier,0).more,"archive filter or overflow wrong");
                require(store.authorizedFixtureOwnerCatalog(token,CatalogEntry.Kind.SERVICE,true,0).entries.get(0).id.equals(seed.id),"owner archived view wrong");
                rejectStock(()->store.authorizedFixtureCheckoutSelection(cashier,seed.id,seed.revision));
                CatalogStore.CheckoutSelection selected=store.authorizedFixtureCheckoutSelection(cashier,item.id,1);
                String shift=stockId();store.openFixtureShift(cashier,shift,0);
                CheckoutPlan.Inputs simple=TrainingForms.checkout(selected.profile,selected.hasMaterials,"2",null,null,null,null,null,null,"10");
                rejectStock(()->store.prepareAuthorizedFixtureCheckout(token,shift,stockId(),selected,simple));
                require(store.prepareAuthorizedFixtureCheckout(cashier,shift,stockId(),selected,simple).totalCentavos==840,"dynamic basic selection wrong");
                CatalogEntry edited=store.editAuthorizedFixtureCatalog(token,item.id,1,"Synthetic revised service","page","4.50");
                rejectStock(()->store.prepareAuthorizedFixtureCheckout(cashier,shift,stockId(),selected,simple));
                rejectStock(()->store.createAuthorizedFixtureCatalog(token,stable,CatalogEntry.Kind.SERVICE,"Synthetic dynamic service","page","4.20"));
                CatalogStore.CheckoutSelection revised=store.authorizedFixtureCheckoutSelection(cashier,item.id,edited.revision);
                store.saveAuthorizedFixturePricing(token,item.id,edited.revision,0,PricingMath.Method.PER_PAGE,true,
                    java.util.Collections.singletonList(new PricingMath.Tier(500,350)));
                rejectStock(()->store.prepareAuthorizedFixtureCheckout(cashier,shift,stockId(),revised,simple));
                CatalogStore.CheckoutSelection priced=store.authorizedFixtureCheckoutSelection(cashier,item.id,edited.revision);
                store.setAuthorizedFixtureMaterialUsage(token,item.id,edited.revision,material.id,material.revision,0,500);
                CheckoutPlan.Inputs noRecipe=TrainingForms.checkout(priced.profile,false,null,"3","2",null,null,null,null,"25");
                rejectStock(()->store.prepareAuthorizedFixtureCheckout(cashier,shift,stockId(),priced,noRecipe));
                CatalogStore.CatalogAdminView detail=store.authorizedFixtureCatalogDetail(token,item.id);
                require(detail.profile.revision==1&&detail.recipeRevision==1&&detail.usage(material.id)==500,"coherent detail snapshot wrong");
                CatalogStore.CheckoutSelection ready=store.authorizedFixtureCheckoutSelection(cashier,item.id,edited.revision);
                CheckoutPlan.Inputs input=TrainingForms.checkout(ready.profile,true,null,"3","2",null,null,null,CheckoutPlan.UsageBasis.PRODUCED_ITEM,"25");
                CheckoutPlan.Proposal plan=store.prepareAuthorizedFixtureCheckout(cashier,shift,stockId(),ready,input);
                require(plan.totalCentavos==2100&&plan.changeCentavos==400&&plan.materials.get(0).quantityMilli==1000,"fresh configured checkout incorrect");
                require(trainingCount(store,"SELECT COUNT(*) FROM checkout_sales")==0&&store.stockBalance(material.id).quantity==50000,"prepare mutated financial state");
                store.signOutFixture(cashier);
                rejectStock(()->store.authorizedFixtureCheckoutServices(cashier,0));
                rejectStock(()->store.prepareAuthorizedFixtureCheckout(cashier,shift,stockId(),ready,input));
            }
        }finally{java.util.Arrays.fill(owner,'\0');java.util.Arrays.fill(staff,'\0');context.deleteDatabase(filename);}
    }

    private void verifyOwnerAdministration(){
        android.content.Context context=getTargetContext();
        String fixture=stockId(),filename="checkout-fixture-"+fixture+".db";
        char[] owner="Synthetic-Admin-Owner-01".toCharArray(),staff="Synthetic-Admin-Cashier-02".toCharArray();
        try{
            try(CatalogStore ordinary=new CatalogStore(context)){
                rejectStock(()->ordinary.createAuthorizedFixtureCatalog("invalid",CatalogEntry.Kind.MATERIAL,"Denied ordinary","sheet","1.00"));
                rejectStock(()->ordinary.authorizedFixtureInventory("invalid"));
                rejectStock(()->ordinary.authorizedFixtureAdminHistory("invalid",0,20));
                rejectStock(()->ordinary.applyAuthorizedFixtureStock("invalid",stockId()));
            }
            try(CatalogStore store=CatalogStore.checkoutFixture(context,fixture)){
                rejectStock(()->store.createAuthorizedFixtureCatalog(null,CatalogEntry.Kind.SERVICE,"Denied unauthenticated","page","2.50"));
                CatalogEntry service=store.create(CatalogEntry.Kind.SERVICE,"Synthetic pre-auth fixture","page","2.50");
                store.bootstrapFixtureOwner("admin-owner",owner);
                String token=store.signInFixture("admin-owner",owner);
                store.createFixtureCashier(token,"admin-cashier",staff);
                String cashier=store.signInFixture("admin-cashier",staff);
                require(store.authorizedFixtureAdminHistory(token,0,20).isEmpty(),"pre-auth history was misattributed");
                rejectStock(()->store.authorizedFixtureAdminHistory(cashier,0,20));
                rejectStock(()->store.authorizedFixtureAdminHistory(null,0,20));
                rejectStock(()->store.authorizedFixtureAdminHistory(token,-1,20));
                rejectStock(()->store.authorizedFixtureAdminHistory(token,0,51));
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
                require(store.authorizedFixtureAdminHistory(token,0,20).size()==1,"denials created audit events");
                store.getWritableDatabase().execSQL("CREATE TRIGGER fault_admin BEFORE INSERT ON cashier_admin_events BEGIN SELECT RAISE(ABORT,'synthetic audit fault'); END");
                boolean fault=false;
                try{store.editAuthorizedFixtureCatalog(token,serviceId,1,"Synthetic fault edit","page","2.50");}
                catch(android.database.sqlite.SQLiteException expected){fault=true;}
                finally{store.getWritableDatabase().execSQL("DROP TRIGGER fault_admin");}
                require(fault&&store.get(serviceId).revision==1&&store.authorizedFixtureAdminHistory(token,0,20).size()==1,"audit failure did not roll back catalog change");
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
                require(store.stageAuthorizedFixtureStock(token,request,materialId,1,0,5000,"Synthetic owner addition").id.equals(request),"stage replay wrong");
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
                rejectStock(()->store.authorizedFixtureAdminHistory(token,0,20));
                // A new authenticated owner, after database reopen, can resolve original identity.
                try(CatalogStore reopened=CatalogStore.checkoutFixture(context,fixture)){
                    String again=reopened.signInFixture("admin-owner",owner);
                    require(reopened.authorizedFixtureInventory(again).stream().anyMatch(i->i.pending!=null&&i.pending.id.equals(recovery)),"owner pending after reopen missing");
                    require(reopened.applyAuthorizedFixtureStock(again,recovery).quantity==5000,"original owner stock recovery failed");
                    require(reopened.applyAuthorizedFixtureStock(again,recovery).revision==3,"recovery replay duplicated movement");
                    require(trainingCount(reopened,"SELECT COUNT(*) FROM stock_movements")==3,"owner stock history wrong");
                    java.util.List<AdminJournal.Entry> all=reopened.authorizedFixtureAdminHistory(again,0,50);
                    require(all.size()==16&&all.get(0).action.equals("STOCK_APPLY")&&all.get(0).requestId.equals(recovery),"audit history count or recovery link wrong");
                    String[] actions={"STOCK_APPLY","STOCK_STAGE","STOCK_APPLY","STOCK_STAGE","STOCK_CANCEL","STOCK_STAGE","STOCK_APPLY","STOCK_STAGE","RECIPE_SAVE","PRICING_SAVE","PRICING_SAVE","CATALOG_RESTORE","CATALOG_ARCHIVE","CATALOG_CREATE","CATALOG_EDIT","CATALOG_CREATE"};
                    for(int i=0;i<all.size();i++)require(all.get(i).action.equals(actions[i])&&all.get(i).actorRevision==1&&all.get(i).actorName.equals("admin-owner"),"audit chronology or actor incorrect");
                    java.util.List<AdminJournal.Entry> first=reopened.authorizedFixtureAdminHistory(again,0,5);
                    java.util.List<AdminJournal.Entry> older=reopened.authorizedFixtureAdminHistory(again,first.get(4).eventNo,50);
                    require(first.size()==5&&older.size()==11&&older.get(0).eventNo==all.get(5).eventNo,"audit pagination overlap or loss");
                    char[] changed="Synthetic-Admin-Owner-Changed-03".toCharArray();
                    try{
                        reopened.changeFixturePassphrase(again,owner,changed);
                        rejectStock(()->reopened.authorizedFixtureAdminHistory(again,0,20));
                        String rotated=reopened.signInFixture("admin-owner",changed);
                        String last=stockId();
                        reopened.stageAuthorizedFixtureStock(rotated,last,materialId,1,3,1000,"Synthetic new credential");
                        reopened.cancelAuthorizedFixtureStock(rotated,last);
                        java.util.List<AdminJournal.Entry> latest=reopened.authorizedFixtureAdminHistory(rotated,0,3);
                        require(latest.size()==3&&latest.get(0).actorRevision==2&&latest.get(1).actorRevision==2&&latest.get(2).actorRevision==1,"audit actor revision changed historical identity");
                    }finally{java.util.Arrays.fill(changed,'\0');}
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
                require(store.getReadableDatabase().getVersion()==22,"v3-to-v22 migration version wrong");
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
