package ph.printshop.pos;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Original native training UI. It cannot operate the ordinary preview catalog or take real payments. */
public final class CashierPreviewActivity extends Activity {
    static final String PREFS="cashier-training-fixture";
    static final String FIXTURE_KEY="fixture_id";
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable expiry=this::lockAndReload;
    private CatalogStore store;
    // Worker confined, never serialized, never placed in a View or preferences.
    private String workerToken;
    private LinearLayout page;
    private TextView status;
    private ScrollView scroll;
    private boolean active,busy,signedIn;
    private int epoch;
    private String mode="";
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        scroll=new ScrollView(this);scroll.setFillViewport(true);
        page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16),dp(12),dp(16),dp(12));page.setBackgroundColor(Color.rgb(247,249,252));
        scroll.addView(page);setContentView(scroll);
        screen("Training cashier","Preparing the isolated training workspace");
        try{
            SharedPreferences preferences=getSharedPreferences(PREFS,MODE_PRIVATE);
            String id=preferences.getString(FIXTURE_KEY,null);
            if(id==null){
                id=UUID.randomUUID().toString();
                if(!preferences.edit().putString(FIXTURE_KEY,id).commit())throw new IllegalStateException("Fixture identity not saved");
            }
            StockMath.validateRequestId(id);
            store=CatalogStore.checkoutFixture(this,id);
        }catch(RuntimeException ex){
            screen("Training unavailable","The isolated fixture could not be opened. No replacement or reset was attempted.");
        }
    }
    @Override protected void onStart(){
        super.onStart();active=true;
        if(store!=null)loadEntry();
        else screen("Training unavailable","The isolated fixture could not be opened. No replacement or reset was attempted.");
    }
    @Override protected void onStop(){
        active=false;epoch++;busy=false;signedIn=false;handler.removeCallbacks(expiry);
        // Do not keep credentials, pending proposal or receipt Views visible while backgrounded.
        page.removeAllViews();
        if(store!=null)worker.execute(this::logoutWorker);
        super.onStop();
    }
    @Override protected void onDestroy(){
        handler.removeCallbacks(expiry);
        if(store!=null)worker.execute(()->{logoutWorker();store.close();});
        worker.shutdown();super.onDestroy();
    }
    private void logoutWorker(){
        String token=workerToken;workerToken=null;
        if(token!=null)try{store.signOutFixture(token);}catch(RuntimeException ignored){/* Expired/revoked token is discarded. */}
    }
    private String session(){
        if(workerToken==null)throw new IllegalStateException("Sign in required");
        return workerToken;
    }
    private interface Work<T>{T run();}
    private boolean submit(Work<?> task,Consumer<Object> result){
        if(!active||busy||store==null)return false;
        busy=true;int currentEpoch=epoch;List<View> paused=new ArrayList<>();pauseControls(page,paused);
        status.setText("Working…");
        worker.execute(()->{
            Object value=null;RuntimeException failed=null;
            try{value=task.run();}catch(RuntimeException ex){failed=ex;}
            final Object output=value;final RuntimeException failure=failed;
            runOnUiThread(()->{
                if(!active||epoch!=currentEpoch)return;
                busy=false;for(View v:paused)v.setEnabled(true);status.setText("");
                if(failure==null){result.accept(output);return;}
                String message=failure.getMessage();
                if("Sign in required".equals(message)||"Session changed".equals(message)){
                    lockAndReload();return;
                }
                status.setText(safeFailure(message));
            });
        });
        return true;
    }
    private static String safeFailure(String message){
        if(message==null)return "Operation refused. Review the training state before retrying.";
        switch(message){
            case "Sign in denied":return "Sign in denied. Check your synthetic credentials or wait for the lockout.";
            case "Insufficient cash.":return "Insufficient training cash.";
            case "Resolve pending checkout before closing shift":return "Resolve the pending training checkout before closing the shift.";
            case "Insufficient stock or quantity limit exceeded.":return "Insufficient training stock.";
            case "Username unavailable":return "Training username unavailable.";
            case "Credential confirmation denied":return "Credential confirmation denied. Check your current synthetic passphrase or wait for lockout.";
            case "Active cashier required":return "Select an active training cashier. The owner account is protected.";
            case "Close the cashier shift before disabling the account":return "Close the cashier's open shift and resolve pending checkouts before disabling.";
            case "Cashier account changed; reload before disabling":return message;
            case "Choose a different synthetic passphrase":return message;
            case "Use 3–32 letters, digits, dots, underscores or hyphens":return message;
            case "Choose a password with 12–128 characters":return message;
            case "Password cannot be blank":return message;
            case "Password contains a control character":return message;
            case "Enter a nonnegative amount with up to two decimal places.":return message;
            case "Invalid stock movement.":return message;
            case "Use up to three decimal places.":return message;
            case "Enter a printable reason (1–160 characters).":return message;
            case "Owner authorization required":return message;
            case "Resolve the existing pending stock movement first":return message;
            case "Invalid quantity":case "Price or quantity outside supported range.":return "Enter a whole training page count from 1 to 100000.";
            default:return "Operation refused or confirmation unavailable. Review the same pending request or history before retrying.";
        }
    }
    private void pauseControls(View v,List<View> paused){
        if((v instanceof Button||v instanceof EditText)&&v.isEnabled()){v.setEnabled(false);paused.add(v);}
        if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++)pauseControls(group.getChildAt(i),paused);}
    }
    private void screen(String title,String description){
        page.removeAllViews();scroll.scrollTo(0,0);
        label("PRINTSHOP POS · TRAINING",14,Color.rgb(31,95,124));
        label(title,26,Color.rgb(27,39,58));
        label("SYNTHETIC ONLY — NO REAL CASH OR TAX DOCUMENT",14,Color.rgb(156,58,22));
        if(!description.isEmpty())label(description,16,Color.rgb(55,65,81));
        status=label("",15,Color.rgb(156,58,22));status.setId(R.id.cashier_status);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    }
    private TextView label(String text,int size,int color){
        TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(color);
        v.setPadding(0,dp(3),0,dp(3));page.addView(v);return v;
    }
    private TextView label(String text){return label(text,16,Color.rgb(27,39,58));}
    private Button button(String title,int id,Runnable action){
        Button v=new Button(this);v.setText(title);v.setAllCaps(false);v.setMinimumHeight(dp(48));
        if(id!=0)v.setId(id);page.addView(v,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        v.setOnClickListener(w->{if(active&&!busy)action.run();});return v;
    }
    private EditText field(String title,int id,int inputType,int max,String initial){
        TextView titleView=label(title);titleView.setLabelFor(id);
        EditText v=new EditText(this);v.setId(id);v.setSingleLine(true);v.setMinimumHeight(dp(48));v.setTextSize(18);
        v.setInputType(inputType);v.setFilters(new InputFilter[]{new InputFilter.LengthFilter(max)});
        v.setSaveEnabled(false);v.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        if(initial!=null)v.setText(initial);page.addView(v);return v;
    }
    private EditText username(){return field("Synthetic username",R.id.cashier_username,InputType.TYPE_CLASS_TEXT,32,null);}
    private EditText password(){return field("Synthetic passphrase (12+ characters)",R.id.cashier_password,
        InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD,128,null);}
    private char[] takePassword(EditText field){
        android.text.Editable value=field.getText();char[] result=new char[value.length()];
        for(int i=0;i<result.length;i++)result[i]=value.charAt(i);value.clear();return result;
    }
    private EditText cashField(String title,int id,String initial){
        return field(title,id,InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL,12,initial);
    }
    private static int quantity(String text){
        if(text==null||!text.matches("[0-9]{1,6}"))throw new IllegalArgumentException("Invalid quantity");
        int value=Integer.parseInt(text);QuoteMath.total(0,value);return value;
    }
    private void loadEntry(){
        signedIn=false;mode="entry";screen("Training cashier","Checking the isolated workspace");
        submit(()->store.fixtureInitialized(),v->{if((Boolean)v)showLogin("");else showSetup();});
    }
    private void showSetup(){
        signedIn=false;mode="setup";screen("Set up training owner","Create a synthetic-only login. Do not use your real shop credentials. Training paper and a training print service will be created.");
        EditText name=username(),secret=password();
        CheckBox consent=new CheckBox(this);consent.setId(R.id.cashier_consent);consent.setText("I will use synthetic credentials and data only.");consent.setSaveEnabled(false);page.addView(consent);
        button("Create training owner",R.id.cashier_setup,()->{
            if(!consent.isChecked()){status.setText("Confirm synthetic-only use before setup.");return;}
            String user=name.getText().toString();char[] pass=takePassword(secret);
            if(!submit(()->{try{store.bootstrapDemoFixtureOwner(user,pass);return null;}finally{Arrays.fill(pass,'\0');}},
                v->showLogin("Training owner created. Sign in to continue.")))Arrays.fill(pass,'\0');
        });
    }
    private void showLogin(String notice){
        signedIn=false;mode="login";screen("Training sign in","Use only the synthetic credentials created in this training fixture.");
        if(!notice.isEmpty())status.setText(notice);
        EditText name=username(),secret=password();
        button("Sign in",R.id.cashier_login,()->{
            String user=name.getText().toString();char[] pass=takePassword(secret);
            if(!submit(()->{try{logoutWorker();workerToken=store.signInFixture(user,pass);return null;}finally{Arrays.fill(pass,'\0');}},v->{
                signedIn=true;handler.removeCallbacks(expiry);handler.postDelayed(expiry,CashierSecurity.SESSION_MS);refreshDashboard();
            }))Arrays.fill(pass,'\0');
        });
    }
    private void lockAndReload(){
        if(!active||store==null)return;
        epoch++;busy=false;signedIn=false;handler.removeCallbacks(expiry);
        screen("Training locked","Signing out…");
        worker.execute(this::logoutWorker);loadEntry();
    }
    private static final class Dashboard {
        final CashierJournal.Principal actor;
        final CashierJournal.Shift shift;
        final long expected;
        final int pending;
        final CatalogEntry service;
        Dashboard(CashierJournal.Principal a,CashierJournal.Shift s,long e,int p,CatalogEntry service){actor=a;shift=s;expected=e;pending=p;this.service=service;}
    }
    private Dashboard dashboard(){
        String token=session();CashierJournal.Principal actor=store.fixturePrincipal(token);
        CashierJournal.Shift shift=store.activeFixtureShift(token);
        long expected=shift==null?0:store.expectedFixtureCash(token,shift.id);
        int pending=shift==null?0:store.pendingAuthorizedFixtureCheckouts(token,shift.id,50).size();
        return new Dashboard(actor,shift,expected,pending,store.authorizedDemoService(token));
    }
    private void refreshDashboard(){
        submit(this::dashboard,v->showDashboard((Dashboard)v));
    }
    private void showDashboard(Dashboard d){
        signedIn=true;mode="home";screen("Training cashier",d.actor.username+" · "+d.actor.role.name());
        if(d.shift==null){
            label("No open shift");
            EditText opening=cashField("Training opening cash (PHP)",R.id.cashier_opening,"0.00");
            button("Open training shift",R.id.cashier_open_shift,()->{
                String raw=opening.getText().toString();String id=UUID.randomUUID().toString();
                submit(()->store.openFixtureShift(session(),id,QuoteMath.parseCentavos(raw)),v->refreshDashboard());
            });
        }else{
            label("Shift open · Expected cash: "+QuoteMath.display(d.expected),18,Color.rgb(27,39,58));
            label(d.pending==0?"No pending training checkouts":"Unresolved training requests: "+d.pending+(d.pending==50?"+":""));
            Button next=button("New training checkout",R.id.cashier_new_checkout,()->showCheckout(d));
            next.setEnabled(d.pending==0);
            button("Recover pending checkouts",R.id.cashier_pending,()->showPendingList(d.shift.id));
            Button close=button("Close training shift",R.id.cashier_close_shift,()->showClose(d));close.setEnabled(d.pending==0);
        }
        button("Recorded training receipts",R.id.cashier_history,this::showHistory);
        button("Change my training passphrase",R.id.cashier_change_pass,()->showCredentialChange(false));
        if(d.actor.role==CashierSecurity.Role.OWNER){
            button("Create training cashier",R.id.cashier_add_staff,this::showStaff);
            button("Manage training cashiers",R.id.cashier_manage_staff,this::showManageStaff);
            button("Training inventory",R.id.cashier_inventory,this::showInventory);
            button("Reset training cashier passphrase",R.id.cashier_reset_pass,()->showCredentialChange(true));
        }
        button("Lock / sign out",R.id.cashier_lock,this::lockAndReload);
    }
    private void showStaff(){
        mode="staff";screen("Create training cashier","Owner-only creation. No real shop credentials.");
        EditText name=username(),secret=password();
        button("Create cashier",R.id.cashier_confirm_staff,()->{
            String user=name.getText().toString();char[] pass=takePassword(secret);
            if(!submit(()->{try{return store.createFixtureCashier(session(),user,pass);}finally{Arrays.fill(pass,'\0');}},v->refreshDashboard()))Arrays.fill(pass,'\0');
        });back();
    }

    private void showManageStaff(){
        submit(()->store.authorizedFixtureStaff(session()),v->{
            mode="manage-staff";screen("Manage training cashiers","Owner-only access. Disabled accounts remain in the historical audit; no training records are deleted.");
            @SuppressWarnings("unchecked") List<CashierJournal.StaffSummary> staff=(List<CashierJournal.StaffSummary>)v;
            if(staff.isEmpty())label("No training cashiers");
            for(CashierJournal.StaffSummary person:staff){
                label(person.username+" · "+(!person.active?"Disabled":person.openShift?"Open shift — cannot disable":"Active"));
                if(person.active&&!person.openShift)button("Disable "+person.username,0,()->showDisableStaff(person));
                if(!person.active)label(person.disableReason==null?"Legacy disabled account — no v9 disable event":"Reason: "+person.disableReason);
            }
            button("Refresh cashier list",R.id.cashier_staff_refresh,this::showManageStaff);
            back();
        });
    }
    private void showDisableStaff(CashierJournal.StaffSummary target){
        mode="disable-staff";
        screen("Confirm permanent training disable","Account: "+target.username+". This prevents all future sign-ins, revokes existing sessions and preserves all existing shifts, receipts and audit records. There is no reactivation in this training version.");
        EditText reason=field("Synthetic reason (do not include private information)",R.id.cashier_disable_reason,InputType.TYPE_CLASS_TEXT,160,null);
        EditText password=credentialField("Current owner synthetic passphrase",R.id.cashier_disable_pass);
        button("Permanently disable training cashier",R.id.cashier_disable_confirm,()->{
            String note=reason.getText().toString();char[] proof=takePassword(password);
            if(!submit(()->{
                try{store.disableFixtureCashier(session(),target.id,target.revision,proof,note);return null;}
                finally{Arrays.fill(proof,'\0');}
            },v->{
                mode="staff-disabled";screen("Training cashier disabled",target.username+" cannot sign in. Historical records remain intact.");
                button("Return to cashier management",R.id.cashier_staff_refresh,this::showManageStaff);back();
            }))Arrays.fill(proof,'\0');
        });
        button("Return to cashier management",R.id.cashier_staff_refresh,this::showManageStaff);back();
    }

    private void showInventory(){
        submit(()->store.authorizedFixtureInventory(session()),v->{
            mode="inventory";screen("Training inventory","Owner-only synthetic stock. Saving a pending adjustment never changes stock.");
            @SuppressWarnings("unchecked") List<CatalogStore.InventoryItem> items=(List<CatalogStore.InventoryItem>)v;
            if(items.isEmpty())label("No active training materials");
            for(CatalogStore.InventoryItem item:items){
                label(item.material.name+" · "+StockMath.display(item.balance.quantity)+" "+item.material.unit);
                if(item.pending==null)button("Adjust "+item.material.name,0,()->showInventoryAdjustment(item));
                else button("Recover "+item.material.name+" adjustment",0,()->showInventoryPending(item.material,item.pending));
            }
            back();
        });
    }
    private void showInventoryAdjustment(CatalogStore.InventoryItem item){
        mode="inventory-adjust";screen("Prepare training stock adjustment",item.material.name+" · Current: "+StockMath.display(item.balance.quantity)+" "+item.material.unit);
        EditText count=field("Quantity ("+item.material.unit+", up to three decimals)",R.id.cashier_inventory_quantity,
            InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL,13,null);
        EditText reason=field("Synthetic movement reason",R.id.cashier_inventory_reason,InputType.TYPE_CLASS_TEXT,160,null);
        // Reuse one request ID on this screen after an uncertain response; never immediately alter stock.
        String id=UUID.randomUUID().toString();
        button("Save pending addition",R.id.cashier_inventory_add,()->stageInventory(item,id,count,reason,1));
        button("Save pending removal",R.id.cashier_inventory_remove,()->stageInventory(item,id,count,reason,-1));
        button("Return to training inventory",R.id.cashier_inventory_back,this::showInventory);
        back();
    }
    private void stageInventory(CatalogStore.InventoryItem item,String id,EditText count,EditText reason,int sign){
        String quantity=count.getText().toString(),note=reason.getText().toString();
        submit(()->store.stageAuthorizedFixtureStock(session(),id,item.material.id,item.material.revision,
                item.balance.revision,sign*StockMath.parse(quantity),note),v->{
            CatalogStore.StockRequest r=(CatalogStore.StockRequest)v;
            if("PENDING".equals(r.state))showInventoryPending(item.material,r);
            else showInventory();
        });
    }
    private void showInventoryPending(CatalogEntry material,CatalogStore.StockRequest r){
        mode="inventory-pending";screen("Pending training stock adjustment","Review the original saved movement before applying. Cancellation leaves stock unchanged.");
        label(material.name+" · "+(r.delta>0?"Add ":"Remove ")+StockMath.display(Math.abs(r.delta))+" "+material.unit);
        label("Reason: "+r.reason);label("Request: "+r.id);
        button("Apply saved stock adjustment",R.id.cashier_inventory_apply,()->submit(()->store.applyAuthorizedFixtureStock(session(),r.id),v->showInventory()));
        button("Cancel saved stock adjustment",R.id.cashier_inventory_cancel,()->submit(()->{store.cancelAuthorizedFixtureStock(session(),r.id);return null;},v->showInventory()));
        button("Return to training inventory",R.id.cashier_inventory_back,this::showInventory);
        back();
    }

    private EditText credentialField(String label,int id){
        return field(label,id,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD,128,null);
    }
    private void showCredentialChange(boolean reset){
        mode="credential";
        screen(reset?"Reset training cashier passphrase":"Change training passphrase",
            reset?"Owner confirmation is required. The cashier keeps their existing shift, requests and receipts; old sessions are revoked. Never enter real credentials.":
            "Confirm your current synthetic passphrase. All your sessions will be revoked. Sign in again with the new passphrase; existing training records stay intact.");
        EditText target=reset?username():null;
        EditText current=credentialField(reset?"Owner current synthetic passphrase":"Current synthetic passphrase",R.id.cashier_current_pass);
        EditText next=credentialField("New synthetic passphrase (12+ characters)",R.id.cashier_new_pass);
        EditText confirmation=credentialField("Repeat new synthetic passphrase",R.id.cashier_confirm_pass);
        button(reset?"Confirm cashier reset":"Confirm passphrase change",R.id.cashier_rotate_pass,()->{
            String username=target==null?null:target.getText().toString();
            char[] old=takePassword(current),replacement=takePassword(next),repeat=takePassword(confirmation);
            boolean matches=Arrays.equals(replacement,repeat);Arrays.fill(repeat,'\0');
            if(!matches){Arrays.fill(old,'\0');Arrays.fill(replacement,'\0');status.setText("New synthetic passphrases do not match.");return;}
            if(!submit(()->{
                try{
                    if(reset)store.resetFixtureCashierPassphrase(session(),old,username,replacement);
                    else{store.changeFixturePassphrase(session(),old,replacement);workerToken=null;}
                    return null;
                }finally{Arrays.fill(old,'\0');Arrays.fill(replacement,'\0');}
            },v->{
                if(reset){screen("Training cashier passphrase reset","The cashier must sign in again. Their existing training records are preserved.");back();}
                else lockAndReload();
            })){Arrays.fill(old,'\0');Arrays.fill(replacement,'\0');}
        });back();
    }
    private void showCheckout(Dashboard d){
        mode="checkout";screen("New training checkout",d.service.name+" · "+QuoteMath.display(d.service.centavos)+" / "+d.service.unit);
        EditText count=field("Number of pages",R.id.cashier_count,InputType.TYPE_CLASS_NUMBER,6,"1");
        EditText cash=cashField("Training cash tendered (PHP)",R.id.cashier_cash,null);
        button("Review training checkout",R.id.cashier_review,()->{
            String q=count.getText().toString(),amount=cash.getText().toString();String id=UUID.randomUUID().toString();
            submit(()->store.prepareDemoFixtureCheckout(session(),d.shift.id,id,quantity(q),QuoteMath.parseCentavos(amount)),
                v->showReview(d.shift.id,(CheckoutPlan.Proposal)v));
        });back();
    }
    private void showReview(String shift,CheckoutPlan.Proposal plan){
        mode="review";screen("Review training checkout","No sale, payment or inventory change has been recorded.");
        label(CheckoutPreview.render(plan));
        for(CheckoutPlan.Consumption m:plan.materials)label("Proposed use: "+StockMath.display(m.quantityMilli)+" "+m.unit+" ("+m.name+")");
        button("Save pending request",R.id.cashier_stage,()->submit(()->store.stageAuthorizedFixtureCheckout(session(),shift,plan),v->{
            CheckoutJournal.Record record=(CheckoutJournal.Record)v;
            if("APPLIED".equals(record.state))showReceipt(record);
            else if("CANCELLED".equals(record.state)){
                screen("Training request cancelled","This original request was cancelled and cannot be recorded.");back();
            }else showPendingRecord(record);
        }));back();
    }
    private void showPendingList(String shift){
        submit(()->store.pendingAuthorizedFixtureCheckouts(session(),shift,20),v->{
            mode="pending-list";screen("Pending training checkouts","Select the original request. Never create a replacement after uncertain confirmation.");
            @SuppressWarnings("unchecked") List<CheckoutJournal.Record> records=(List<CheckoutJournal.Record>)v;
            if(records.isEmpty())label("No pending training checkouts");
            for(CheckoutJournal.Record r:records)button(r.serviceName+" · "+QuoteMath.display(r.total),0,()->showPendingRecord(r));
            back();
        });
    }
    private void showPendingRecord(CheckoutJournal.Record r){
        mode="pending";screen("Pending training checkout","The reviewed request is saved. Training cash and stock change only after explicit recording.");
        label(r.serviceName+" · "+PricingMath.displayQuantity(r.billableHundredths)+" units");
        label("Total: "+QuoteMath.display(r.total),20,Color.rgb(27,39,58));
        label("Training cash: "+QuoteMath.display(r.received)+" · Change: "+QuoteMath.display(r.change));
        label("Request: "+r.id);
        button("Record synthetic checkout",R.id.cashier_record,()->submit(()->store.applyAuthorizedFixtureCheckout(session(),r.id,r.fingerprint),v->showReceipt((CheckoutJournal.Record)v)));
        EditText reason=field("Cancellation reason (only for unrecorded request)",R.id.cashier_cancel_reason,InputType.TYPE_CLASS_TEXT,160,null);
        button("Cancel unrecorded request",R.id.cashier_cancel,()->{
            String note=reason.getText().toString();
            submit(()->store.cancelAuthorizedFixtureCheckout(session(),r.id,r.fingerprint,note),v->refreshDashboard());
        });back();
    }
    private void showReceipt(CheckoutJournal.Record r){
        mode="receipt";screen("Recorded training receipt","Historical synthetic record only. No tax receipt or physical printing.");
        label(r.previewReceipt(),18,Color.rgb(27,39,58));
        button("Return to training cashier",R.id.cashier_return_home,this::refreshDashboard);
    }
    private void showHistory(){
        submit(()->store.recentAuthorizedFixtureReceipts(session(),20),v->{
            mode="history";screen("Recorded training receipts","Your most recent recorded synthetic transactions.");
            @SuppressWarnings("unchecked") List<CheckoutJournal.Record> records=(List<CheckoutJournal.Record>)v;
            if(records.isEmpty())label("No recorded training receipts");
            for(CheckoutJournal.Record r:records)button(r.serviceName+" · "+QuoteMath.display(r.total),0,()->
                submit(()->store.authorizedFixtureCheckout(session(),r.id),x->showReceipt((CheckoutJournal.Record)x)));
            back();
        });
    }
    private void showClose(Dashboard d){
        mode="close";screen("Close training shift","Expected cash: "+QuoteMath.display(d.expected)+". Pending requests must be resolved.");
        EditText counted=cashField("Counted training cash (PHP)",R.id.cashier_counted,null);
        button("Confirm training shift close",R.id.cashier_confirm_close,()->{
            String raw=counted.getText().toString();
            submit(()->store.closeFixtureShift(session(),d.shift.id,QuoteMath.parseCentavos(raw)),v->{
                CashierJournal.Shift s=(CashierJournal.Shift)v;
                mode="closed";screen("Training shift closed","Audited cash reconciliation");
                label("Expected: "+QuoteMath.display(s.expectedCash));
                label("Counted: "+QuoteMath.display(s.countedCash));
                label("Variance: "+QuoteMath.display(s.variance),20,Color.rgb(27,39,58));
                button("Return to training cashier",R.id.cashier_return_home,this::refreshDashboard);
            });
        });back();
    }
    private void back(){button("Back to cashier",R.id.cashier_back,this::refreshDashboard);}
    @Override public void onBackPressed(){
        if(busy)return;
        if(signedIn&&!"home".equals(mode)){refreshDashboard();return;}
        super.onBackPressed();
    }
}
