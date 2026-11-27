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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
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
            case "Resolve pending credit checkout before close":return "Resolve the pending training credit checkout before closing the shift.";
            case "Resolve pending collection before close":return "Resolve the pending training collection before closing the shift.";
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
            case "Original receipt changed or amount is invalid; review again":
            case "Authorized original cash receipt unavailable":
            case "Invalid customer details":return message;
            case "Collection exceeds outstanding balance":return "Enter a collection amount no greater than the outstanding balance.";
            case "Invalid collection":return "Enter a positive collection amount.";
            case "Collection changed; review again":return "Collection changed. Reload the balance or cancel the saved request.";
            case "Payment mode and cash amount do not match":return "Partial cash needs a positive amount; unpaid needs PHP 0.00.";
            case "Partial payment requires customer and positive tender below total":return "Partial cash must be greater than zero and less than the sale total.";
            case "Credit checkout changed; review again":return "Credit checkout changed. Cancel the saved request or review a new checkout.";
            case "Checkout customer selection changed":
            case "Customer changed; reload":
            case "Invalid expense date":
            case "Invalid recurring expense":
            case "Recurring expense changed; reload":
            case "Recurring expense changed or not yet due":
            case "Use recurring expense review":
            case "Use receipt compensation review":
            case "Resolve pending drawer activity first":
            case "Resolve pending checkout before drawer activity":
            case "Drawer changed; reload and review again":
            case "Drawer request identity already used for different review":
            case "Drawer request already resolved differently":
            case "Drawer request belongs to another operator or is unavailable":
            case "Drawer amount must be positive":
            case "Insufficient expected drawer cash":
            case "Drawer amount outside supported range":
            case "Enter an expense category (1–80 printable characters)":
            case "Category is only for expenses":
            case "Operator's open shift required":
                return message;
            case "Creation already recorded or changed; reload catalog":
            case "Catalog entry changed; reload before editing":
            case "Catalog or recipe changed; reload before editing":
            case "Service or pricing changed; reload before editing":
            case "Service changed; reload and select again":
            case "Service, pricing or recipe changed; review a fresh quotation":
            case "Restore archived entry before editing":
            case "Entry already has requested archive state":
            case "Resolve the pending stock movement before editing this material":
            case "Material unit cannot change after stock movement history exists":
            case "Material unit cannot change after recipe history exists":
            case "Remove material from linked service recipes before archiving":
            case "Reduce material stock to zero with a reason before archiving":
            case "Service unit cannot change after material or pricing history exists; create a new service":
            case "Name must contain 1 to 80 printable characters":
            case "Unit must contain 1 to 24 printable characters":
            case "No pricing change":case "No recipe change":
            case "Duplicate tier threshold":case "Maximum 100 material bindings":
            case "Page and copy inputs require a document pricing method":
            case "Enter a positive tier quantity with up to two decimals":
            case "Tier quantity outside supported range":
            case "Choose the material usage basis":
            case "Enter a positive dimension with up to three decimal places":
            case "Dimensions must be positive":
            case "Billable quantity outside supported range":
            case "Insufficient material stock":
            case "Recipe demand is not representable in exact stock milliunits":
            case "Resolve pending inventory movement before checkout":
            case "Zero-value checkout requires a separate policy":
            case "Enter a positive quantity; use Remove to unlink":
            case "Only active services and materials may be linked":
            case "Job changed; reload":
            case "Job unavailable":
            case "Job already exists or invalid initial status":
            case "Production event identity changed":
            case "Resolve pending receipt compensation first":
            case "Invalid production transition or outstanding payment":
            case "Quotation cannot be converted":
            case "Quotation terms changed; save a new quotation":
            case "Quotation customer changed; save a new quotation":
            case "Quotation already converted":
            case "Quotation already cancelled":
            case "Quotation unavailable":
            case "Quotation changed; review again":
            case "Service changed; review quotation again":
            case "Credit conversion requires a named quotation customer":
            case "Select an active service":
                return message;
            case "Resolve the existing pending stock movement first":return message;
            case "Invalid quantity":case "Price or quantity outside supported range.":return "Enter a whole training page count from 1 to 100000.";
            default:return "Operation refused or confirmation unavailable. Review the same pending request or history before retrying.";
        }
    }
    private void pauseControls(View v,List<View> paused){
        if((v instanceof Button||v instanceof EditText||v instanceof Spinner)&&v.isEnabled()){v.setEnabled(false);paused.add(v);}
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
    private Spinner choices(String title,int id,List<String> labels,int selected){
        TextView titleView=label(title);titleView.setLabelFor(id);
        Spinner spinner=new Spinner(this);spinner.setId(id);spinner.setMinimumHeight(dp(48));spinner.setSaveEnabled(false);
        setChoices(spinner,labels,selected);page.addView(spinner);return spinner;
    }
    private void setChoices(Spinner spinner,List<String> labels,int selected){
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spinner.setAdapter(adapter);
        if(!labels.isEmpty())spinner.setSelection(Math.max(0,Math.min(selected,labels.size()-1)));
    }
    private static String text(EditText field){return field==null?null:field.getText().toString();}
    private void catalogPages(int offset,boolean more,Runnable previous,Runnable next){
        if(offset>0)button("Previous page",R.id.cashier_catalog_previous,previous);
        if(more)button("Next page",R.id.cashier_catalog_next,next);
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
        final DrawerJournal.Request pendingDrawer;
        final boolean pendingRefund,pendingRecurring;
        final CreditCheckoutJournal.Record pendingCredit;
        final ReceivableJournal.Record pendingCollection;
        boolean clear(){return pending==0&&pendingDrawer==null&&pendingCredit==null&&pendingCollection==null;}
        Dashboard(CashierJournal.Principal a,CashierJournal.Shift s,long e,int p,DrawerJournal.Request drawer,boolean refund,boolean recurring,CreditCheckoutJournal.Record credit,ReceivableJournal.Record collection){actor=a;shift=s;expected=e;pending=p;pendingDrawer=drawer;pendingRefund=refund;pendingRecurring=recurring;pendingCredit=credit;pendingCollection=collection;}
    }
    private Dashboard dashboard(){
        String token=session();CashierJournal.Principal actor=store.fixturePrincipal(token);
        CashierJournal.Shift shift=store.activeFixtureShift(token);
        long expected=shift==null?0:store.expectedFixtureCash(token,shift.id);
        int pending=shift==null?0:store.pendingAuthorizedFixtureCheckouts(token,shift.id,50).size();
        DrawerJournal.Request drawer=shift==null?null:store.pendingFixtureDrawer(token,shift.id);
        return new Dashboard(actor,shift,expected,pending,drawer,drawer!=null&&store.isFixtureRefund(token,drawer.id),drawer!=null&&store.isFixtureRecurring(token,drawer.id),shift==null?null:store.pendingAuthorizedFixtureCreditCheckout(token,shift.id),shift==null?null:store.pendingAuthorizedFixtureCollection(token,shift.id));
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
            Button next=button("New training checkout",R.id.cashier_new_checkout,()->showCheckoutChoices(d.shift.id,0));
            next.setEnabled(d.clear());
            if(d.pendingCredit!=null)button("Recover pending credit checkout",R.id.cashier_credit_pending,()->showCreditRecord(d.pendingCredit));
            if(d.pendingCollection!=null)button("Recover pending debt collection",R.id.cashier_collection_pending,()->showCollectionRecord(d.pendingCollection,0));
            Button collect=button("Collect customer debt",R.id.cashier_collection_new,()->showDebtors(d.shift.id,"",0));
            collect.setEnabled(d.clear());
            button("Recover pending checkouts",R.id.cashier_pending,()->showPendingList(d.shift.id));
            if(d.pendingDrawer!=null)label(d.pendingRefund?"Unresolved training refund/void":d.pendingRecurring?"Unresolved training recurring expense":"Unresolved training drawer activity");
            Button drawer=button(d.pendingDrawer==null?"Training drawer activity":d.pendingRefund?"Recover pending refund/void":d.pendingRecurring?"Recover pending recurring expense":"Recover pending drawer activity",R.id.cashier_drawer,()->{
                if(d.pendingDrawer==null)showDrawerOptions(d);else if(d.pendingRefund)openRefundRecord(d.pendingDrawer.id);else if(d.pendingRecurring)openRecurringRecord(d.pendingDrawer.id);else openDrawerRequest(d.pendingDrawer.id);
            });
            drawer.setEnabled(d.pending==0&&d.pendingCredit==null&&d.pendingCollection==null);
            Button close=button("Close training shift",R.id.cashier_close_shift,()->showClose(d));close.setEnabled(d.clear());
        }
        button("New training quotation",R.id.cashier_quote_new,()->showQuoteServices(0));
        button("Saved training quotations",R.id.cashier_quote_history,()->showQuoteHistory(0));
        button("Training production jobs",R.id.cashier_jobs,()->showJobs(0));
        button("Recorded training receipts",R.id.cashier_history,this::showHistory);
        button("Recorded training credit receipts",R.id.cashier_credit_history,this::showCreditHistory);
        button("Training collection history",R.id.cashier_collection_history,()->showCollectionHistory(0));
        button("Training drawer history",R.id.cashier_drawer_history,()->showDrawerHistory(0));
        button("Change my training passphrase",R.id.cashier_change_pass,()->showCredentialChange(false));
        if(d.actor.role==CashierSecurity.Role.OWNER){
            button("Training sale refunds / voids",R.id.cashier_refund_receipts,()->showRefundReceipts(0));
            button("Refund / void history",R.id.cashier_refund_history,()->showRefundHistory(0));
            button("Training customers",R.id.cashier_customers,()->showCustomers(true,"",0));
            button("Recurring expenses",R.id.cashier_recurring,()->showRecurringDefinitions(0));
            button("Training sales reports",R.id.cashier_sales_report,()->showReportPeriod(ReportMath.Period.DAY));
            button("Create training cashier",R.id.cashier_add_staff,this::showStaff);
            button("Manage training cashiers",R.id.cashier_manage_staff,this::showManageStaff);
            button("Manage training services",R.id.cashier_catalog_services,()->showOwnerCatalog(CatalogEntry.Kind.SERVICE,false,0));
            button("Manage training materials",R.id.cashier_catalog_materials,()->showOwnerCatalog(CatalogEntry.Kind.MATERIAL,false,0));
            button("Training inventory",R.id.cashier_inventory,this::showInventory);
            button("Administrative history",R.id.cashier_admin_history,()->showAdminHistory(0));
            button("Reset training cashier passphrase",R.id.cashier_reset_pass,()->showCredentialChange(true));
        }
        button("Lock / sign out",R.id.cashier_lock,this::lockAndReload);
    }
    private static String drawerLabel(ShiftMath.DrawerKind kind){
        switch(kind){case EXPENSE:return "Expense";case CASH_IN:return "Cash in";case CASH_OUT:return "Cash out";default:throw new IllegalArgumentException("Select a drawer activity");}
    }
    private void showDrawerOptions(Dashboard dashboard){
        mode="drawer-options";screen("Training drawer activity","Expected cash: "+QuoteMath.display(dashboard.expected)+". Record synthetic expenses on your own open shift. Owner-only cash-in/out adjustments.");
        button("New training expense",R.id.cashier_drawer_expense,()->showDrawerEntry(dashboard,ShiftMath.DrawerKind.EXPENSE));
        if(dashboard.actor.role==CashierSecurity.Role.OWNER){
            button("Training cash in",R.id.cashier_drawer_in,()->showDrawerEntry(dashboard,ShiftMath.DrawerKind.CASH_IN));
            button("Training cash out",R.id.cashier_drawer_out,()->showDrawerEntry(dashboard,ShiftMath.DrawerKind.CASH_OUT));
        }
        button("Review drawer history",R.id.cashier_drawer_history,()->showDrawerHistory(0));back();
    }
    private static final class DrawerDraft {
        final String id,shift,category,reason;
        final ShiftMath.DrawerKind kind;
        final long amount,before,after;
        DrawerDraft(String id,String shift,ShiftMath.DrawerKind kind,long amount,String category,String reason,long before){
            this.id=id;this.shift=shift;this.kind=kind;this.amount=amount;this.category=ShiftMath.category(kind,category);
            this.reason=StockMath.reason(reason);this.before=before;after=ShiftMath.drawerAfter(before,kind,amount);
        }
    }
    private void showDrawerEntry(Dashboard dashboard,ShiftMath.DrawerKind kind){
        mode="drawer-entry";screen("Prepare training "+drawerLabel(kind).toLowerCase(java.util.Locale.ROOT),
            "Expected cash: "+QuoteMath.display(dashboard.expected)+". No actual cash is moved.");
        EditText amount=cashField("Amount (PHP)",R.id.cashier_drawer_amount,null);
        EditText category=kind==ShiftMath.DrawerKind.EXPENSE?field("Synthetic expense category",R.id.cashier_drawer_category,InputType.TYPE_CLASS_TEXT,80,null):null;
        EditText reason=field("Synthetic reason",R.id.cashier_drawer_reason,InputType.TYPE_CLASS_TEXT,160,null);
        String id=UUID.randomUUID().toString();
        button("Review drawer activity",R.id.cashier_drawer_review,()->{
            try{showDrawerReview(new DrawerDraft(id,dashboard.shift.id,kind,QuoteMath.parseCentavos(text(amount)),text(category),text(reason),dashboard.expected));}
            catch(IllegalArgumentException|IllegalStateException ex){status.setText(safeFailure(ex.getMessage()));}
        });
        button("Discard draft",R.id.cashier_drawer_return,this::refreshDashboard);back();
    }
    private void showDrawerReview(DrawerDraft draft){
        mode="drawer-review";screen("Review training drawer activity","No financial change has been recorded. Save the exact reviewed request before applying.");
        label(drawerLabel(draft.kind)+" · "+QuoteMath.display(draft.amount));
        if(draft.category!=null)label("Category: "+draft.category);
        label("Reason: "+draft.reason);label("Before: "+QuoteMath.display(draft.before));label("Proposed after: "+QuoteMath.display(draft.after));
        button("Save pending drawer request",R.id.cashier_drawer_stage,()->submit(()->store.stageFixtureDrawer(session(),draft.shift,draft.id,draft.kind,
            draft.amount,draft.category,draft.reason,draft.before),v->showDrawerRecord((DrawerJournal.Request)v)));
        button("Discard unsaved review",R.id.cashier_drawer_return,this::refreshDashboard);back();
    }
    private void openDrawerRequest(String id){
        submit(()->store.authorizedFixtureDrawer(session(),id),v->showDrawerRecord((DrawerJournal.Request)v));
    }
    private void showDrawerRecord(DrawerJournal.Request request){
        mode="drawer-record";boolean pending="PENDING".equals(request.state),applied="APPLIED".equals(request.state);
        screen(pending?"Pending training drawer activity":applied?"Recorded training drawer activity":"Cancelled training drawer activity",
            pending?"Review the original saved request. Apply once or cancel without affecting cash.":
                applied?"Immutable synthetic cash-effect record.":"Original reviewed request retained. Cancellation did not change drawer cash.");
        label(drawerLabel(request.kind)+" · "+QuoteMath.display(request.amount));
        if(request.category!=null)label("Category: "+request.category);
        label("Reason: "+request.reason);label("Before: "+QuoteMath.display(request.before));
        label((applied?"After: ":"Proposed after: ")+QuoteMath.display(request.after));
        label("Request: "+request.id);label("Shift: "+request.shiftId);
        label("Operator: "+request.operatorId+" · Stage account revision "+request.stageRevision);
        label("Staged: "+new java.util.Date(request.createdMs));
        if(request.resolvedRevision!=null)label("Resolved account revision "+request.resolvedRevision+" · "+new java.util.Date(request.resolvedMs));
        if(request.cancelReason!=null)label("Cancellation reason: "+request.cancelReason);
        if(pending){
            button("Apply saved drawer activity",R.id.cashier_drawer_apply,()->submit(()->store.resolveFixtureDrawer(session(),request.id,true,null),
                v->showDrawerRecord((DrawerJournal.Request)v)));
            EditText reason=field("Cancellation reason",R.id.cashier_drawer_cancel_reason,InputType.TYPE_CLASS_TEXT,160,null);
            button("Cancel saved drawer activity",R.id.cashier_drawer_cancel,()->{
                String note=text(reason);submit(()->store.resolveFixtureDrawer(session(),request.id,false,note),v->showDrawerRecord((DrawerJournal.Request)v));
            });
        }
        button("Return to training cashier",R.id.cashier_drawer_return,this::refreshDashboard);
        button("Review drawer history",R.id.cashier_drawer_history,()->showDrawerHistory(0));back();
    }
    private void showDrawerHistory(int offset){
        submit(()->store.authorizedFixtureDrawerHistory(session(),offset,9),v->{
            @SuppressWarnings("unchecked") List<DrawerJournal.Request> rows=(List<DrawerJournal.Request>)v;
            mode="drawer-history";screen("Training drawer history","Your own synthetic expenses and adjustments across shifts. Pending, applied and cancelled records are retained.");
            if(rows.isEmpty())label("No drawer activity on this page");
            int count=Math.min(8,rows.size());
            for(int i=0;i<count;i++){
                DrawerJournal.Request request=rows.get(i);
                label(request.state+" · "+drawerLabel(request.kind)+" · "+QuoteMath.display(request.amount)+(request.category==null?"":" · "+request.category));
                label(new java.util.Date(request.createdMs)+" · "+request.id.substring(0,8));
                button("Review "+request.id.substring(0,8),0,()->openDrawerRequest(request.id));
            }
            catalogPages(offset,rows.size()>8,()->showDrawerHistory(offset-8),()->showDrawerHistory(offset+8));
            button("Reload drawer history",R.id.cashier_drawer_history,()->showDrawerHistory(0));back();
        });
    }
    private void showReportPeriod(ReportMath.Period period){
        submit(()->store.authorizedFixtureSalesReport(session(),period),v->showSalesReport(period,(ReportJournal.Report)v,0));
    }
    private static String reportMoney(java.math.BigInteger amount){return ReportMath.display(amount);}
    private void showSalesReport(ReportMath.Period period,ReportJournal.Report report,int offset){
        mode="sales-report";screen("Training sales reports","Owner-only · Asia/Manila shop time. Sales use receipt time; refunds/voids use actual resolution date. Net sales is not profit.");
        java.text.SimpleDateFormat date=new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.ROOT);
        date.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Manila"));
        label("Report period: "+period.name());
        label(date.format(new java.util.Date(report.range.start))+" to "+date.format(new java.util.Date(report.range.end-1)));
        button("Today",R.id.cashier_report_day,()->showReportPeriod(ReportMath.Period.DAY));
        button("This week (Monday–Sunday)",R.id.cashier_report_week,()->showReportPeriod(ReportMath.Period.WEEK));
        button("This month",R.id.cashier_report_month,()->showReportPeriod(ReportMath.Period.MONTH));
        ReportMath.Totals total=report.totals;
        label("Gross sales: "+reportMoney(total.gross));label("Receipts: "+total.sales);
        label("Cash refunds: "+reportMoney(total.refunds));label("Cash voids: "+reportMoney(total.voids));
        label("Net sales: "+reportMoney(total.net()),22,Color.rgb(31,95,124));
        label("Cash paid at checkout: "+reportMoney(total.upfrontCash));
        label("Debt created: "+reportMoney(total.debtCreated));label("Debt collected: "+reportMoney(total.debtCollected));
        label("Net sales cash received: "+reportMoney(total.cashCollected()));
        label("Debt movement this period: "+reportMoney(total.debtMovement())+" (not outstanding balance)");
        label("Expenses: "+reportMoney(total.expenses));label("Other cash in: "+reportMoney(total.cashIn));label("Other cash out: "+reportMoney(total.cashOut));
        label("Net drawer movement: "+reportMoney(total.movement()));
        label("Closed shift reconciliation (whole shifts closed in period)");
        label("Closed shifts: "+report.closedShifts);label("Expected: "+reportMoney(report.closedExpected));
        label("Counted: "+reportMoney(report.closedCounted));label("Variance: "+reportMoney(report.closedVariance));
        button("Net sales chart",R.id.cashier_report_chart,()->showReportChart(period,report,0));
        button("Report transactions",R.id.cashier_report_events,()->showReportEvents(period,report,0));
        label("By service (original sold name)",20,Color.rgb(27,39,58));
        if(report.services.isEmpty())label("No sales or compensation in this period");
        for(int i=offset;i<Math.min(offset+8,report.services.size());i++){
            ReportJournal.Service service=report.services.get(i);
            label(service.name+" · "+service.id);label("Gross "+reportMoney(service.totals.gross)+" · Refund "+reportMoney(service.totals.refunds)+" · Void "+reportMoney(service.totals.voids));
            label("Net "+reportMoney(service.totals.net())+" · Receipts "+service.totals.sales);
        }
        catalogPages(offset,offset+8<report.services.size(),()->showSalesReport(period,report,offset-8),()->showSalesReport(period,report,offset+8));
        button("Refresh report",R.id.cashier_sales_report,()->showReportPeriod(period));back();
    }
    private static final class ReportBar extends View {
        private final int signed;
        private final android.graphics.Paint paint=new android.graphics.Paint();
        ReportBar(android.content.Context context,int signed){super(context);this.signed=signed;setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(android.graphics.Canvas canvas){
            super.onDraw(canvas);
            float center=getWidth()/2f,top=3f,bottom=Math.max(top,getHeight()-3f);
            paint.setColor(Color.rgb(233,237,242));canvas.drawRect(0,top,getWidth(),bottom,paint);
            paint.setColor(Color.rgb(95,105,115));canvas.drawRect(center-1,0,center+1,getHeight(),paint);
            if(signed!=0){
                float edge=center+center*(signed/1000f);
                if(Math.abs(edge-center)<1f)edge=center+Math.signum(signed);
                paint.setColor(signed<0?Color.rgb(170,57,45):Color.rgb(31,125,105));
                canvas.drawRect(Math.min(center,edge),top,Math.max(center,edge),bottom,paint);
            }
        }
    }
    private void showReportChart(ReportMath.Period period,ReportJournal.Report report,int offset){
        mode="report-chart";screen("Training net sales chart","Philippine shop time · Negative values extend left, positive right. All pages share the same scale. Amounts below are exact.");
        ReportMath.Series series=report.chart;
        if(series==null){label("Chart unavailable for this range");back();return;}
        java.math.BigInteger max=series.maxAbs();
        label("Scale on either side: "+reportMoney(max));label("Period net: "+reportMoney(report.totals.net()));
        java.text.SimpleDateFormat date=new java.text.SimpleDateFormat(period==ReportMath.Period.DAY?"HH:mm":"MMM dd, EEE",java.util.Locale.ROOT);
        date.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Manila"));
        for(int i=offset;i<Math.min(offset+8,series.buckets.size());i++){
            ReportMath.Bucket bucket=series.buckets.get(i);
            label(date.format(new java.util.Date(bucket.start))+" · Net "+reportMoney(bucket.totals.net()));
            page.addView(new ReportBar(this,ReportMath.bar(bucket.totals.net(),max)),new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(20)));
            label("Gross "+reportMoney(bucket.totals.gross)+" · Refund "+reportMoney(bucket.totals.refunds)+" · Void "+reportMoney(bucket.totals.voids));
        }
        catalogPages(offset,offset+8<series.buckets.size(),()->showReportChart(period,report,offset-8),()->showReportChart(period,report,offset+8));
        button("Back to report",R.id.cashier_report_return,()->showSalesReport(period,report,0));back();
    }
    private static String eventLabel(String kind){
        switch(kind){case "SALE":return "Sale";case "CREDIT_SALE":return "Credit sale";case "COLLECTION":return "Debt collection";case "REFUND":return "Refund";case "VOID":return "Void";case "EXPENSE":return "Expense";case "CASH_IN":return "Other cash in";case "CASH_OUT":return "Other cash out";default:return "Unknown";}
    }
    private static String eventDate(long at){
        java.text.SimpleDateFormat date=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",java.util.Locale.ROOT);
        date.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Manila"));return date.format(new java.util.Date(at));
    }
    private void showReportEvents(ReportMath.Period period,ReportJournal.Report report,int offset){
        submit(()->store.authorizedFixtureReportEvents(session(),report.range,offset,9),v->{
            @SuppressWarnings("unchecked") List<ReportJournal.Event> events=(List<ReportJournal.Event>)v;
            mode="report-events";screen("Training report transactions","Applied events only · newest first. Live history for the selected report dates; refresh the report to reconcile newly recorded activity.");
            if(events.isEmpty())label("No applied transactions on this page");
            for(int i=0;i<Math.min(8,events.size());i++){
                ReportJournal.Event event=events.get(i);
                label(eventDate(event.recordedMs)+" · "+eventLabel(event.kind)+" · "+QuoteMath.display(event.amount));
                if(event.serviceName!=null)label(event.serviceName);
                button("Inspect "+eventLabel(event.kind)+" "+event.id,0,()->showReportEvent(period,report,offset,event));
            }
            catalogPages(offset,events.size()>8,()->showReportEvents(period,report,offset-8),()->showReportEvents(period,report,offset+8));
            button("Back to report",R.id.cashier_report_return,()->showSalesReport(period,report,0));back();
        });
    }
    private void showReportEvent(ReportMath.Period period,ReportJournal.Report report,int offset,ReportJournal.Event event){
        mode="report-event";screen("Training report transaction","Read-only original applied event. No actual cash or fiscal document.");
        label(eventLabel(event.kind)+" · "+QuoteMath.display(event.amount));label("Recorded: "+eventDate(event.recordedMs));
        label("Event: "+event.id);label("Shift: "+event.shiftId);label("Operator: "+event.operatorId);
        if(event.receiptId!=null)label("Original receipt: "+event.receiptId);
        if(event.serviceName!=null)label("Original service: "+event.serviceName+" · "+event.serviceId);
        if(event.customerId!=null)label("Customer: "+event.customerId+" · Revision "+event.customerRevision);
        if("CREDIT_SALE".equals(event.kind)){label("Cash paid: "+QuoteMath.display(event.upfrontCash));label("Original debt: "+QuoteMath.display(event.amount-event.upfrontCash));}
        if("COLLECTION".equals(event.kind))label("Debt settlement only · Not new sales revenue");
        if(event.reason!=null)label("Reason: "+event.reason);
        if(event.recurringSchedule!=null){label("Recurring expense: "+event.recurringName);label("Schedule: "+event.recurringSchedule+" · Revision "+event.recurringRevision);label("Due: "+event.recurringDue);}
        button("Back to transactions",R.id.cashier_report_events,()->showReportEvents(period,report,offset));
        button("Back to report",R.id.cashier_report_return,()->showSalesReport(period,report,0));back();
    }
    private static String refundLabel(RefundJournal.Kind kind){return kind==RefundJournal.Kind.VOID?"Void":"Refund";}
    private void showRefundReceipts(int offset){
        submit(()->store.authorizedFixtureRefundReceipts(session(),offset,9),v->{
            @SuppressWarnings("unchecked") List<RefundJournal.Receipt> rows=(List<RefundJournal.Receipt>)v;
            mode="refund-receipts";screen("Training sale refunds / voids","Owner-only. Original receipts remain unchanged. Select a synthetic cash receipt. No automatic stock return or external refund.");
            if(rows.isEmpty())label("No eligible cash receipts on this page");
            for(int i=0;i<Math.min(8,rows.size());i++){
                RefundJournal.Receipt receipt=rows.get(i);
                label(receipt.serviceName+" · Paid "+QuoteMath.display(receipt.review.originalPaid));
                label("Receipt: "+receipt.review.checkoutId+" · "+new java.util.Date(receipt.recordedMs));
                label("Remaining: "+QuoteMath.display(receipt.review.remaining()));
                Button choose=button("Select "+receipt.serviceName+" · "+QuoteMath.display(receipt.review.originalPaid),0,()->openRefundReview(receipt));
                choose.setEnabled(receipt.review.remaining()>0);
            }
            catalogPages(offset,rows.size()>8,()->showRefundReceipts(offset-8),()->showRefundReceipts(offset+8));
            button("Reload original receipts",R.id.cashier_refund_receipts,()->showRefundReceipts(0));
            button("Refund / void history",R.id.cashier_refund_history,()->showRefundHistory(0));back();
        });
    }
    private void openRefundReview(RefundJournal.Receipt receipt){
        submit(()->new Object[]{dashboard(),store.reviewFixtureRefund(session(),receipt.review.checkoutId)},v->{
            Object[] values=(Object[])v;
            Dashboard d=(Dashboard)values[0];RefundJournal.Review review=(RefundJournal.Review)values[1];
            mode="refund-select";screen("Original training cash receipt",receipt.serviceName);
            label("Receipt: "+review.checkoutId);label("Original shift: "+receipt.shiftId);
            label("Original paid: "+QuoteMath.display(review.originalPaid));
            label("Previously compensated: "+QuoteMath.display(review.compensated));
            label("Remaining: "+QuoteMath.display(review.remaining()));
            boolean available=d.shift!=null&&d.clear()&&review.remaining()>0;
            if(!available)label("Open your own training shift and resolve pending activity before compensation.");
            if(d.shift!=null)label("Expected current drawer cash: "+QuoteMath.display(d.expected));
            button("Prepare cash refund",R.id.cashier_refund_new,()->showRefundEntry(d,review,RefundJournal.Kind.REFUND)).setEnabled(available);
            button("Prepare full cash void",R.id.cashier_void_new,()->showRefundEntry(d,review,RefundJournal.Kind.VOID)).setEnabled(available&&review.compensated==0);
            button("Back to original receipts",R.id.cashier_refund_receipts,()->showRefundReceipts(0));back();
        });
    }
    private void showRefundEntry(Dashboard d,RefundJournal.Review review,RefundJournal.Kind kind){
        mode="refund-entry";screen("Prepare training "+refundLabel(kind).toLowerCase(java.util.Locale.ROOT),
            "Compensation only. Original sale and inventory remain unchanged. No actual cash or external payment is moved.");
        label("Receipt: "+review.checkoutId);label("Remaining: "+QuoteMath.display(review.remaining()));
        EditText amount=kind==RefundJournal.Kind.REFUND?cashField("Refund amount (PHP)",R.id.cashier_refund_amount,null):null;
        if(amount==null)label("Full void amount: "+QuoteMath.display(review.originalPaid));
        EditText reason=field("Synthetic reason",R.id.cashier_refund_reason,InputType.TYPE_CLASS_TEXT,160,null);
        String id=UUID.randomUUID().toString();
        button("Review compensation",R.id.cashier_refund_review,()->{
            try{
                long value=amount==null?review.originalPaid:QuoteMath.parseCentavos(text(amount));
                if(value<=0||value>review.remaining())throw new IllegalArgumentException("Original receipt changed or amount is invalid; review again");
                long after=ShiftMath.drawerAfter(d.expected,ShiftMath.DrawerKind.CASH_OUT,value);
                showRefundConfirmation(d,review,kind,id,value,StockMath.reason(text(reason)),after);
            }catch(IllegalArgumentException|IllegalStateException ex){status.setText(safeFailure(ex.getMessage()));}
        });
        button("Discard draft",R.id.cashier_refund_return,this::refreshDashboard);back();
    }
    private void showRefundConfirmation(Dashboard d,RefundJournal.Review review,RefundJournal.Kind kind,String id,long amount,String reason,long after){
        mode="refund-review";screen("Review training compensation","Confirm exact original receipt and current drawer balance. Saving does not move cash. No automatic stock return.");
        label(refundLabel(kind)+" · "+QuoteMath.display(amount));label("Receipt: "+review.checkoutId);
        label("Original paid: "+QuoteMath.display(review.originalPaid));label("Previously compensated: "+QuoteMath.display(review.compensated));
        label("Reason: "+reason);label("Before: "+QuoteMath.display(d.expected));label("Proposed after: "+QuoteMath.display(after));
        button("Save pending compensation",R.id.cashier_refund_stage,()->submit(()->store.stageFixtureRefund(session(),d.shift.id,id,review.checkoutId,kind,review.originalPaid,review.compensated,amount,reason,d.expected),v->showRefundRecord((RefundJournal.Request)v)));
        button("Discard unsaved review",R.id.cashier_refund_return,this::refreshDashboard);back();
    }
    private void openRefundRecord(String id){
        submit(()->store.authorizedFixtureRefund(session(),id),v->showRefundRecord((RefundJournal.Request)v));
    }
    private void showRefundRecord(RefundJournal.Request request){
        DrawerJournal.Request d=request.drawer;
        boolean pending="PENDING".equals(d.state),applied="APPLIED".equals(d.state);
        mode="refund-record";screen(pending?"Pending training compensation":applied?"Recorded training compensation":"Cancelled training compensation",
            "Immutable original receipt link. No automatic stock return or external refund.");
        label(refundLabel(request.kind)+" · "+QuoteMath.display(d.amount));label("Receipt: "+request.checkoutId);
        label("Original paid: "+QuoteMath.display(request.originalPaid));label("Previously compensated: "+QuoteMath.display(request.priorPaid));
        label("Reason: "+d.reason);label("Before: "+QuoteMath.display(d.before));label((applied?"After: ":"Proposed after: ")+QuoteMath.display(d.after));
        label("Request: "+d.id);label("Resolution shift: "+d.shiftId);label("Operator: "+d.operatorId+" · Stage account revision "+d.stageRevision);
        label("Staged: "+new java.util.Date(d.createdMs));
        if(d.resolvedRevision!=null)label("Resolved account revision "+d.resolvedRevision+" · "+new java.util.Date(d.resolvedMs));
        if(d.cancelReason!=null)label("Cancellation reason: "+d.cancelReason);
        if(pending){
            button("Apply saved compensation",R.id.cashier_refund_apply,()->submit(()->store.resolveFixtureRefund(session(),d.id,true,null),v->showRefundRecord((RefundJournal.Request)v)));
            EditText reason=field("Cancellation reason",R.id.cashier_refund_cancel_reason,InputType.TYPE_CLASS_TEXT,160,null);
            button("Cancel saved compensation",R.id.cashier_refund_cancel,()->{
                String note=text(reason);submit(()->store.resolveFixtureRefund(session(),d.id,false,note),v->showRefundRecord((RefundJournal.Request)v));
            });
        }
        button("Return to training cashier",R.id.cashier_refund_return,this::refreshDashboard);
        button("Refund / void history",R.id.cashier_refund_history,()->showRefundHistory(0));back();
    }
    private void showRefundHistory(int offset){
        submit(()->store.authorizedFixtureRefundHistory(session(),offset,9),v->{
            @SuppressWarnings("unchecked") List<RefundJournal.Request> rows=(List<RefundJournal.Request>)v;
            mode="refund-history";screen("Training refund / void history","Owner-only linked compensation records. Pending, applied and cancelled requests are retained separately from ordinary drawer adjustments.");
            if(rows.isEmpty())label("No compensation on this page");
            for(int i=0;i<Math.min(8,rows.size());i++){
                RefundJournal.Request request=rows.get(i);
                label(request.drawer.state+" · "+refundLabel(request.kind)+" · "+QuoteMath.display(request.drawer.amount));
                label("Receipt: "+request.checkoutId+" · "+new java.util.Date(request.drawer.createdMs));
                button("Review "+request.drawer.id.substring(0,8),0,()->openRefundRecord(request.drawer.id));
            }
            catalogPages(offset,rows.size()>8,()->showRefundHistory(offset-8),()->showRefundHistory(offset+8));
            button("Reload compensation history",R.id.cashier_refund_history,()->showRefundHistory(0));back();
        });
    }
    private void showCustomers(boolean active,String search,int offset){
        submit(()->store.authorizedFixtureCustomers(session(),active,search,offset,9),v->{
            @SuppressWarnings("unchecked") List<CustomerJournal.Customer> rows=(List<CustomerJournal.Customer>)v;
            mode="customers";screen("Training customers","Owner-only synthetic directory. Do not enter real customer information. No balances or credit sales yet.");
            button(active?"Show archived customers":"Show active customers",R.id.cashier_customer_filter,()->showCustomers(!active,search,0));
            EditText query=field("Search customer name",R.id.cashier_customer_search,InputType.TYPE_CLASS_TEXT,80,search);
            button("Search",R.id.cashier_customer_find,()->showCustomers(active,text(query),0));
            button("New training customer",R.id.cashier_customer_new,()->showCustomerEditor(null,true));
            if(rows.isEmpty())label("No matching customers");
            for(int i=0;i<Math.min(8,rows.size());i++){
                CustomerJournal.Customer c=rows.get(i);
                label(c.name+" · "+(c.active?"Active":"Archived"));
                label("Customer ID: "+c.id);
                button("Review "+c.name,0,()->showCustomerDetail(c));
            }
            catalogPages(offset,rows.size()>8,()->showCustomers(active,search,offset-8),()->showCustomers(active,search,offset+8));back();
        });
    }
    private void showCustomerDetail(CustomerJournal.Customer c){
        mode="customer-detail";screen(c.name,"Synthetic customer · Revision "+c.revision+" · "+(c.active?"Active":"Archived"));
        label("Customer ID: "+c.id);label("Contact: "+(c.contact.isEmpty()?"Not recorded":c.contact));
        button("Edit customer",R.id.cashier_customer_edit,()->showCustomerEditor(c,c.active));
        button(c.active?"Archive customer":"Restore customer",R.id.cashier_customer_archive,()->showCustomerEditor(c,!c.active));
        button("Customer revision history",R.id.cashier_customer_history,()->showCustomerHistory(c,0));
        button("Back to customer list",R.id.cashier_customers,()->showCustomers(c.active,"",0));back();
    }
    private void showCustomerEditor(CustomerJournal.Customer c,boolean active){
        boolean stateChange=c!=null&&c.active!=active;
        mode="customer-editor";screen(c==null?"New training customer":stateChange?(active?"Restore training customer":"Archive training customer"):"Edit training customer","Original revisions are retained. Use synthetic details only.");
        String id=c==null?UUID.randomUUID().toString():c.id;
        EditText name=field("Customer name",R.id.cashier_customer_name,InputType.TYPE_CLASS_TEXT,80,c==null?null:c.name);
        EditText contact=field("Optional synthetic contact",R.id.cashier_customer_contact,InputType.TYPE_CLASS_TEXT,120,c==null?null:c.contact);
        if(stateChange){name.setEnabled(false);contact.setEnabled(false);}
        button("Save customer revision",R.id.cashier_customer_save,()->{
            String n=text(name),contactValue=text(contact);
            submit(()->store.saveFixtureCustomer(session(),id,c==null?0:c.revision,n,contactValue,active),v->showCustomerDetail((CustomerJournal.Customer)v));
        });
        button("Discard unsaved changes",R.id.cashier_customers,()->showCustomers(c==null||c.active,"",0));back();
    }
    private void showCustomerHistory(CustomerJournal.Customer c,int offset){
        submit(()->store.authorizedFixtureCustomerHistory(session(),c.id,offset,9),v->{
            @SuppressWarnings("unchecked") List<CustomerJournal.Customer> rows=(List<CustomerJournal.Customer>)v;
            mode="customer-history";screen("Training customer revision history","Owner-only immutable revisions of "+c.id);
            for(int i=0;i<Math.min(8,rows.size());i++){
                CustomerJournal.Customer r=rows.get(i);
                label("Revision "+r.revision+" · "+r.name+" · "+(r.active?"Active":"Archived"));
                label("Contact: "+(r.contact.isEmpty()?"Not recorded":r.contact));
                label("Actor: "+r.actorId+" · "+eventDate(r.recordedMs));
            }
            catalogPages(offset,rows.size()>8,()->showCustomerHistory(c,offset-8),()->showCustomerHistory(c,offset+8));
            button("Back to customer",R.id.cashier_customer_history,()->showCustomerDetail(c));back();
        });
    }
    private void showRecurringDefinitions(int offset){
        submit(()->store.authorizedFixtureRecurringDefinitions(session(),offset,9),v->{
            @SuppressWarnings("unchecked") List<RecurringJournal.Definition> rows=(List<RecurringJournal.Definition>)v;
            mode="recurring-list";screen("Training recurring expenses","Owner only. Philippine calendar. No automatic debit; review and record each due expense.");
            String today=RecurringMath.today(System.currentTimeMillis());
            button("New recurring expense",R.id.cashier_recurring_new,()->showRecurringEditor(null,false));
            if(rows.isEmpty())label("No recurring expense definitions");
            for(int i=0;i<Math.min(8,rows.size());i++){
                RecurringJournal.Definition d=rows.get(i);
                label(d.name+" · "+QuoteMath.display(d.amount)+" · "+d.cadence);
                label(!d.active?"Archived":d.nextDue==null?"No supported next date":(d.nextDue.compareTo(today)<=0?"Due: ":"Next due: ")+d.nextDue);
                button("Review "+d.name,0,()->showRecurringDetail(d));
            }
            catalogPages(offset,rows.size()>8,()->showRecurringDefinitions(offset-8),()->showRecurringDefinitions(offset+8));
            button("Recurring expense history",R.id.cashier_recurring_history,()->showRecurringHistory(0));back();
        });
    }
    private void showRecurringDetail(RecurringJournal.Definition d){
        submit(this::dashboard,v->{
            Dashboard h=(Dashboard)v;
            mode="recurring-detail";screen(d.name,"Training schedule · Revision "+d.revision);
            label(d.category+" · "+QuoteMath.display(d.amount)+" · "+d.cadence);
            label("First due: "+d.firstDue+" · Next due: "+(d.nextDue==null?"Unavailable":d.nextDue));
            boolean due=d.active&&d.nextDue!=null&&d.nextDue.compareTo(RecurringMath.today(System.currentTimeMillis()))<=0;
            boolean ready=due&&h.shift!=null&&h.clear();
            if(due&&!ready)label("Open your own shift and resolve all pending activity first.");
            button("Prepare due expense",R.id.cashier_recurring_due,()->showRecurringEntry(h,d)).setEnabled(ready);
            button(d.active?"Edit schedule":"Reactivate schedule",R.id.cashier_recurring_edit,()->showRecurringEditor(d,false));
            if(d.active)button("Archive schedule",R.id.cashier_recurring_archive,()->showRecurringEditor(d,true));
            button("Back to recurring expenses",R.id.cashier_recurring,()->showRecurringDefinitions(0));back();
        });
    }
    private void showRecurringEditor(RecurringJournal.Definition d,boolean archive){
        mode="recurring-editor";screen(archive?"Archive training schedule":d==null?"New training schedule":"Revise training schedule",
            "Saving a revision supersedes old unpaid dates. Enter an effective first due date after every previously paid occurrence. Historical records remain; no cash is moved.");
        String id=d==null?UUID.randomUUID().toString():d.id;
        EditText name=field("Name",R.id.cashier_recurring_name,InputType.TYPE_CLASS_TEXT,80,d==null?null:d.name);
        EditText category=field("Category",R.id.cashier_recurring_category,InputType.TYPE_CLASS_TEXT,80,d==null?null:d.category);
        EditText amount=cashField("Amount (PHP)",R.id.cashier_recurring_amount,d==null?null:java.math.BigDecimal.valueOf(d.amount,2).toPlainString());
        Spinner cadence=choices("Frequency",R.id.cashier_recurring_cadence,Arrays.asList("Daily","Weekly","Monthly"),d==null?2:d.cadence.ordinal());
        EditText date=field("Effective first due (YYYY-MM-DD)",R.id.cashier_recurring_date,InputType.TYPE_CLASS_TEXT,10,d==null?RecurringMath.today(System.currentTimeMillis()):d.nextDue);
        if(archive){name.setEnabled(false);category.setEnabled(false);amount.setEnabled(false);cadence.setEnabled(false);}
        button(archive?"Confirm archive":"Save schedule revision",R.id.cashier_recurring_save,()->{
            String n=text(name),c=text(category),a=text(amount),f=text(date);
            RecurringMath.Cadence frequency=RecurringMath.Cadence.values()[cadence.getSelectedItemPosition()];
            submit(()->store.saveFixtureRecurringDefinition(session(),id,d==null?0:d.revision,n,c,(archive?d.amount:QuoteMath.parseCentavos(a)),frequency,f,!archive),v->showRecurringDefinitions(0));
        });
        button("Discard unsaved changes",R.id.cashier_recurring,()->showRecurringDefinitions(0));back();
    }
    private void showRecurringEntry(Dashboard h,RecurringJournal.Definition d){
        mode="recurring-entry";screen("Prepare due training expense",d.name+" · Due "+d.nextDue);
        label(d.category+" · "+QuoteMath.display(d.amount));
        EditText reason=field("Synthetic payment reason",R.id.cashier_recurring_reason,InputType.TYPE_CLASS_TEXT,160,null);
        String id=UUID.randomUUID().toString();
        button("Review due expense",R.id.cashier_recurring_review,()->{
            try{
                long after=ShiftMath.drawerAfter(h.expected,ShiftMath.DrawerKind.EXPENSE,d.amount);
                showRecurringConfirmation(h,d,id,StockMath.reason(text(reason)),after);
            }catch(IllegalArgumentException|IllegalStateException ex){status.setText(safeFailure(ex.getMessage()));}
        });back();
    }
    private void showRecurringConfirmation(Dashboard h,RecurringJournal.Definition d,String id,String reason,long after){
        mode="recurring-review";screen("Review due training expense","Saving does not move cash. Apply the saved occurrence explicitly.");
        label(d.name+" · Due "+d.nextDue+" · Revision "+d.revision);
        label(d.category+" · "+QuoteMath.display(d.amount));label("Reason: "+reason);
        label("Before: "+QuoteMath.display(h.expected));label("Proposed after: "+QuoteMath.display(after));
        button("Save pending recurring expense",R.id.cashier_recurring_stage,()->submit(()->store.stageFixtureRecurringExpense(session(),h.shift.id,id,d.id,d.revision,d.nextIndex,d.nextDue,d.amount,d.category,reason,h.expected),v->showRecurringRecord((RecurringJournal.Occurrence)v)));
        back();
    }
    private void openRecurringRecord(String id){
        submit(()->store.authorizedFixtureRecurringExpense(session(),id),v->showRecurringRecord((RecurringJournal.Occurrence)v));
    }
    private void showRecurringRecord(RecurringJournal.Occurrence occurrence){
        DrawerJournal.Request d=occurrence.drawer;boolean pending="PENDING".equals(d.state);
        mode="recurring-record";screen("Training recurring expense · "+d.state,"Original saved schedule and due date. Cancellation does not debit cash.");
        label(occurrence.name+" · Due "+occurrence.dueDate+" · Revision "+occurrence.revision);
        label("Schedule: "+occurrence.scheduleId+" · Occurrence "+occurrence.index);
        label(d.category+" · "+QuoteMath.display(d.amount));label("Reason: "+d.reason);
        label("Before: "+QuoteMath.display(d.before));label(("APPLIED".equals(d.state)?"After: ":"Proposed after: ")+QuoteMath.display(d.after));
        label("Request: "+d.id+" · Shift: "+d.shiftId);label("Staged: "+eventDate(d.createdMs));
        if(d.resolvedMs!=null)label("Resolved: "+eventDate(d.resolvedMs));
        if(d.cancelReason!=null)label("Cancellation reason: "+d.cancelReason);
        if(pending){
            button("Apply saved recurring expense",R.id.cashier_recurring_apply,()->submit(()->store.resolveFixtureRecurringExpense(session(),d.id,true,null),v->showRecurringRecord((RecurringJournal.Occurrence)v)));
            EditText reason=field("Cancellation reason",R.id.cashier_recurring_cancel_reason,InputType.TYPE_CLASS_TEXT,160,null);
            button("Cancel saved recurring expense",R.id.cashier_recurring_cancel,()->{
                String note=text(reason);submit(()->store.resolveFixtureRecurringExpense(session(),d.id,false,note),v->showRecurringRecord((RecurringJournal.Occurrence)v));
            });
        }
        button("Recurring expense history",R.id.cashier_recurring_history,()->showRecurringHistory(0));back();
    }
    private void showRecurringHistory(int offset){
        submit(()->store.authorizedFixtureRecurringHistory(session(),offset,9),v->{
            @SuppressWarnings("unchecked") List<RecurringJournal.Occurrence> rows=(List<RecurringJournal.Occurrence>)v;
            mode="recurring-history";screen("Training recurring expense history","Original pending, applied and cancelled occurrences. Live history.");
            if(rows.isEmpty())label("No recurring expense occurrences");
            for(int i=0;i<Math.min(8,rows.size());i++){
                RecurringJournal.Occurrence occurrence=rows.get(i);
                label(occurrence.drawer.state+" · "+occurrence.name+" · "+QuoteMath.display(occurrence.drawer.amount));
                label("Due "+occurrence.dueDate+" · Revision "+occurrence.revision);
                button("Review "+occurrence.drawer.id,0,()->openRecurringRecord(occurrence.drawer.id));
            }
            catalogPages(offset,rows.size()>8,()->showRecurringHistory(offset-8),()->showRecurringHistory(offset+8));
            button("Back to recurring expenses",R.id.cashier_recurring,()->showRecurringDefinitions(0));back();
        });
    }
    private void showOwnerCatalog(CatalogEntry.Kind kind,boolean archived,int offset){
        submit(()->store.authorizedFixtureOwnerCatalog(session(),kind,archived,offset),v->{
            CatalogStore.CatalogPage result=(CatalogStore.CatalogPage)v;
            mode="owner-catalog";boolean services=kind==CatalogEntry.Kind.SERVICE;
            screen(services?"Training services":"Training materials",archived?"Archived records — restore without deleting history.":"Active synthetic records — changes are recorded in administrative history.");
            button(archived?"Show active entries":"Show archived entries",R.id.cashier_catalog_filter,()->showOwnerCatalog(kind,!archived,0));
            if(!archived)button(services?"New training service":"New training material",R.id.cashier_catalog_create,()->showCatalogEditor(kind,null));
            if(result.entries.isEmpty())label(archived?"No archived entries on this page":"No active entries on this page");
            for(CatalogEntry item:result.entries){
                label(item.name+" · "+QuoteMath.display(item.centavos)+" / "+item.unit);
                label("Revision "+item.revision+" · ID "+item.id.substring(0,8));
                button("Manage "+item.name,0,()->showOwnerDetail(item.id));
            }
            catalogPages(offset,result.more,()->showOwnerCatalog(kind,archived,offset-TrainingForms.PAGE_SIZE),
                ()->showOwnerCatalog(kind,archived,offset+TrainingForms.PAGE_SIZE));
            button("Reload catalog",R.id.cashier_catalog_reload,()->showOwnerCatalog(kind,archived,0));
            back();
        });
    }
    private void showOwnerDetail(String id){
        submit(()->store.authorizedFixtureCatalogDetail(session(),id),v->{
            CatalogStore.CatalogAdminView detail=(CatalogStore.CatalogAdminView)v;CatalogEntry item=detail.item;
            mode="catalog-detail";screen("Training catalog item",item.name+" · "+(item.archived?"Archived":"Active"));
            label(QuoteMath.display(item.centavos)+" / "+item.unit+" · Revision "+item.revision);
            label("Item: "+item.id);
            if(detail.stock!=null)label("Stock: "+StockMath.display(detail.stock.quantity)+" "+item.unit);
            if(!item.archived){
                button("Edit training item",R.id.cashier_catalog_edit,()->showCatalogEditor(item.kind,item));
                if(detail.profile!=null){
                    label("Pricing: "+PricingProfile.label(detail.profile.method)+" · revision "+detail.profile.revision+" · "+detail.profile.tiers.size()+" tier(s)");
                    button("Configure training pricing",R.id.cashier_catalog_pricing,()->showOwnerPricing(detail));
                    label("Recipe: "+detail.bindings.size()+" linked material(s) · revision "+detail.recipeRevision);
                    button("Configure training recipe",R.id.cashier_catalog_recipe,()->showOwnerRecipe(detail,0));
                    label("Production jobs: "+(detail.production.enabled?"Automatic":"Manual")+" · revision "+detail.production.revision);
                    button("Configure production jobs",R.id.cashier_production_policy,()->{
                        mode="production-policy";screen("Production job setting","Applies only to future successful sales. Existing receipts and jobs are unchanged.");
                        boolean enabled=!detail.production.enabled;
                        button(enabled?"Enable automatic jobs":"Use manual jobs",R.id.cashier_production_policy_save,()->submit(
                            ()->store.setAuthorizedFixtureProductionPolicy(session(),item.id,item.revision,detail.production.revision,enabled),v2->showOwnerDetail(item.id)));
                        button("Keep current setting",R.id.cashier_catalog_return,()->showOwnerDetail(item.id));back();
                    });
                }
            }
            button(item.archived?"Restore training item":"Archive training item",R.id.cashier_catalog_archive,()->showCatalogArchive(item));
            button("Reload training item",R.id.cashier_catalog_reload,()->showOwnerDetail(id));
            button("Return to catalog",R.id.cashier_catalog_return,()->showOwnerCatalog(item.kind,item.archived,0));
            back();
        });
    }
    private void showCatalogEditor(CatalogEntry.Kind kind,CatalogEntry original){
        mode="catalog-edit";boolean service=kind==CatalogEntry.Kind.SERVICE;
        screen(original==null?(service?"Create training service":"Create training material"):"Edit training catalog item",
            "Synthetic only. Save creates an audited catalog revision. Review saved records before another attempt.");
        EditText name=field("Name",R.id.cashier_catalog_name,InputType.TYPE_CLASS_TEXT,80,original==null?null:original.name);
        EditText unit=field("Unit",R.id.cashier_catalog_unit,InputType.TYPE_CLASS_TEXT,24,original==null?null:original.unit);
        EditText price=cashField(service?"Base selling price (PHP)":"Unit material cost (PHP)",R.id.cashier_catalog_price,
            original==null?null:TrainingForms.money(original.centavos));
        String creationId=original==null?UUID.randomUUID().toString():null;
        button("Save training item",R.id.cashier_catalog_save,()->{
            String n=text(name),u=text(unit),p=text(price);
            submit(()->original==null?store.createAuthorizedFixtureCatalog(session(),creationId,kind,n,u,p):
                store.editAuthorizedFixtureCatalog(session(),original.id,original.revision,n,u,p),
                v->showOwnerDetail(((CatalogEntry)v).id));
        });
        button("Discard draft",R.id.cashier_catalog_return,()->{
            if(original==null)showOwnerCatalog(kind,false,0);else showOwnerDetail(original.id);
        });back();
    }
    private void showCatalogArchive(CatalogEntry item){
        mode="catalog-archive";screen(item.archived?"Confirm training restore":"Confirm training archive",
            item.name+" · Revision "+item.revision+". Historical records are preserved. Pending movements and linked stock protection still apply.");
        button(item.archived?"Confirm restore":"Confirm archive",R.id.cashier_catalog_confirm_archive,()->submit(
            ()->store.archiveAuthorizedFixtureCatalog(session(),item.id,item.revision,!item.archived),v->showOwnerDetail(item.id)));
        button("Cancel and reload item",R.id.cashier_catalog_return,()->showOwnerDetail(item.id));back();
    }
    private void showOwnerPricing(CatalogStore.CatalogAdminView detail){
        CatalogEntry service=detail.item;PricingProfile saved=detail.profile;
        mode="catalog-pricing";screen("Configure training pricing",service.name+" · Saved pricing revision "+saved.revision+" · Base "+QuoteMath.display(service.centavos));
        List<String> methods=new ArrayList<>();for(PricingMath.Method m:PricingMath.Method.values())methods.add(PricingProfile.label(m));
        Spinner method=choices("Pricing method",R.id.cashier_pricing_method,methods,saved.method.ordinal());
        CheckBox pages=new CheckBox(this);pages.setId(R.id.cashier_pricing_pages);pages.setText("Use pages × copies (document methods only)");
        pages.setSaveEnabled(false);pages.setChecked(saved.pagesAndCopies);pages.setEnabled(PricingProfile.supportsPages(saved.method));page.addView(pages);
        method.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> p,View view,int position,long id){
                boolean allowed=position>=0&&position<PricingMath.Method.values().length&&PricingProfile.supportsPages(PricingMath.Method.values()[position]);
                if(!allowed)pages.setChecked(false);pages.setEnabled(allowed&&!busy);
            }
            public void onNothingSelected(AdapterView<?> p){pages.setChecked(false);pages.setEnabled(false);}
        });
        label("Optional bulk tiers. Highest qualifying quantity wins; base selling price otherwise.");
        List<PricingMath.Tier> draft=new ArrayList<>(saved.tiers);
        EditText threshold=field("Minimum billable quantity",R.id.cashier_pricing_threshold,InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL,9,null);
        EditText rate=cashField("Tier rate per billable unit (PHP)",R.id.cashier_pricing_rate,null);
        Spinner tiers=choices("Draft tiers",R.id.cashier_pricing_tiers,tierLabels(draft),0);
        final Button[] remove={null};
        button("Add tier to draft",R.id.cashier_pricing_add,()->{
            try{
                List<PricingMath.Tier> next=TrainingForms.addTier(PricingMath.Method.values()[method.getSelectedItemPosition()],pages.isChecked(),draft,text(threshold),text(rate));
                draft.clear();draft.addAll(next);threshold.setText("");rate.setText("");setChoices(tiers,tierLabels(draft),0);
                remove[0].setEnabled(true);status.setText("Tier added to draft. Save pricing to apply.");
            }catch(IllegalArgumentException|IllegalStateException ex){status.setText(safeFailure(ex.getMessage()));}
        });
        remove[0]=button("Remove selected draft tier",R.id.cashier_pricing_remove,()->{
            int index=tiers.getSelectedItemPosition();
            if(index>=0&&index<draft.size()){draft.remove(index);setChoices(tiers,tierLabels(draft),0);remove[0].setEnabled(!draft.isEmpty());status.setText("Tier removed from draft. Save pricing to apply.");}
        });
        remove[0].setEnabled(!draft.isEmpty());
        button("Save training pricing",R.id.cashier_pricing_save,()->{
            if(threshold.length()!=0||rate.length()!=0){status.setText("Add the entered tier or clear both fields before saving.");return;}
            PricingMath.Method selected=PricingMath.Method.values()[method.getSelectedItemPosition()];
            boolean usePages=pages.isChecked();List<PricingMath.Tier> copy=new ArrayList<>(draft);
            submit(()->store.saveAuthorizedFixturePricing(session(),service.id,service.revision,saved.revision,selected,usePages,copy),v->showOwnerDetail(service.id));
        });
        button("Discard draft and reload item",R.id.cashier_catalog_return,()->showOwnerDetail(service.id));back();
    }
    private static List<String> tierLabels(List<PricingMath.Tier> draft){
        List<String> rows=new ArrayList<>();
        for(PricingMath.Tier t:draft)rows.add(PricingMath.displayQuantity(t.minQuantityHundredths)+"+ units · "+QuoteMath.display(t.rateCentavos));
        if(rows.isEmpty())rows.add("No bulk tiers");
        return rows;
    }
    private void showOwnerRecipe(CatalogStore.CatalogAdminView detail,int offset){
        submit(()->store.authorizedFixtureOwnerCatalog(session(),CatalogEntry.Kind.MATERIAL,false,offset),v->{
            CatalogStore.CatalogPage result=(CatalogStore.CatalogPage)v;
            mode="catalog-recipe";screen("Configure training recipe",detail.item.name+" · Saved recipe revision "+detail.recipeRevision+". Configure material usage; stock is not consumed by editing.");
            label(detail.bindings.size()+" linked material(s). Select a material to set or remove its usage.");
            if(result.entries.isEmpty())label("No active materials on this page");
            for(CatalogEntry m:result.entries){
                long usage=detail.usage(m.id);
                label(m.name+" · "+QuoteMath.display(m.centavos)+" / "+m.unit+" · ID "+m.id.substring(0,8));
                label(usage>0?"Linked: "+StockMath.display(usage)+" "+m.unit+" per selected usage basis":"Not linked");
                button("Set usage for "+m.name,0,()->showRecipeUsage(detail,m,usage));
            }
            catalogPages(offset,result.more,()->showOwnerRecipe(detail,offset-TrainingForms.PAGE_SIZE),()->showOwnerRecipe(detail,offset+TrainingForms.PAGE_SIZE));
            button("Reload service",R.id.cashier_catalog_return,()->showOwnerDetail(detail.item.id));back();
        });
    }
    private void showRecipeUsage(CatalogStore.CatalogAdminView detail,CatalogEntry material,long prior){
        CatalogEntry service=detail.item;mode="recipe-usage";
        screen("Training material usage",service.name+" · "+material.name+" ("+material.unit+"). Quantity per selected usage basis; no stock movement on save.");
        EditText usage=field("Material quantity (up to three decimals)",R.id.cashier_recipe_usage,
            InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL,13,prior==0?null:StockMath.display(prior));
        button("Save training usage",R.id.cashier_recipe_save,()->{
            String raw=text(usage);
            submit(()->{
                long qty=StockMath.parse(raw);
                if(qty==0)throw new IllegalArgumentException("Enter a positive quantity; use Remove to unlink");
                return store.setAuthorizedFixtureMaterialUsage(session(),service.id,service.revision,material.id,material.revision,detail.recipeRevision,qty);
            },v->showOwnerDetail(service.id));
        });
        if(prior>0)button("Remove material from recipe",R.id.cashier_recipe_remove,()->{
            screen("Confirm training recipe removal",material.name+" will be unlinked from "+service.name+". Historical records and stock remain unchanged.");
            button("Confirm recipe removal",R.id.cashier_recipe_confirm_remove,()->submit(()->store.setAuthorizedFixtureMaterialUsage(
                session(),service.id,service.revision,material.id,material.revision,detail.recipeRevision,0),v->showOwnerDetail(service.id)));
            button("Cancel and reload service",R.id.cashier_catalog_return,()->showOwnerDetail(service.id));back();
        });
        button("Discard draft and reload service",R.id.cashier_catalog_return,()->showOwnerDetail(service.id));back();
    }
    private void showAdminHistory(long before){
        submit(()->store.authorizedFixtureAdminHistory(session(),before,20),v->{
            mode="admin-history";screen("Training administrative history","Owner-only history of authenticated manual changes. Earlier preview and migrated records are not attributed retrospectively.");
            @SuppressWarnings("unchecked") List<AdminJournal.Entry> events=(List<AdminJournal.Entry>)v;
            if(events.isEmpty())label("No administrative events on this page");
            for(AdminJournal.Entry e:events){
                label("#"+e.eventNo+" · "+e.action.replace('_',' ')+" · "+e.actorName+" (account revision "+e.actorRevision+")");
                label("Current item: "+e.subjectName+" · linked revision "+e.revision+" · "+e.subjectId);
                label(new java.util.Date(e.recordedMs).toString());
                if(e.requestId!=null)label("Request: "+e.requestId+" · "+e.reason);
            }
            if(events.size()==20)button("Older administrative events",R.id.cashier_admin_older,()->showAdminHistory(events.get(events.size()-1).eventNo));
            if(before!=0)button("Latest administrative events",R.id.cashier_admin_latest,()->showAdminHistory(0));
            back();
        });
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
    private String jobStatus(ProductionWorkflow.Status status){
        switch(status){
            case IN_PRODUCTION:return "In production";
            case QUALITY_CHECKING:return "Quality checking";
            case READY_FOR_PICKUP:return "Ready for pickup";
            case COMPLETED:return "Completed";
            case CANCELLED:return "Cancelled";
            case AWAITING_APPROVAL:return "Awaiting approval";
            case QUOTATION:return "Quotation";
            default:return "Pending";
        }
    }
    private void openOrCreateJob(String receipt,String service,long total){
        submit(()->store.findAuthorizedFixtureJob(session(),receipt),v->{
            if(v!=null){showJob((ProductionJournal.Job)v);return;}
            mode="job-create";screen("Create training production job","Only create a job for work requiring production. This does not change payment or inventory.");
            label(service);label("Receipt: "+receipt);label("Total: "+QuoteMath.display(total));
            EditText note=field("Production instructions",R.id.cashier_job_note,InputType.TYPE_CLASS_TEXT,160,null);
            String event=UUID.randomUUID().toString();
            button("Create job",R.id.cashier_job_confirm,()->{
                String raw=text(note);
                submit(()->store.appendAuthorizedFixtureJob(session(),receipt,event,0,ProductionWorkflow.Status.PENDING,raw),x->showJob((ProductionJournal.Job)x));
            });
            button("Reload original receipt job",R.id.cashier_job_reload,()->openOrCreateJob(receipt,service,total));back();
        });
    }
    private void showJobs(int offset){
        submit(()->store.authorizedFixtureJobs(session(),offset),v->{
            @SuppressWarnings("unchecked") List<ProductionJournal.Job> rows=(List<ProductionJournal.Job>)v;
            mode="jobs";screen("Training production jobs","Select a job to review the current stage, payment and history.");
            button("Quotation production jobs",R.id.cashier_quote_jobs,()->showQuoteJobs(0));
            if(rows.isEmpty())label("No recorded-sale production jobs. Create one from a receipt or quotation.");
            for(int i=0;i<Math.min(8,rows.size());i++){
                ProductionJournal.Job j=rows.get(i);
                button(j.service+" · "+jobStatus(j.status),0,()->openJob(j.receipt));
            }
            catalogPages(offset,rows.size()>8,()->showJobs(offset-8),()->showJobs(offset+8));back();
        });
    }
    private void openJob(String receipt){
        submit(()->store.authorizedFixtureJob(session(),receipt),v->showJob((ProductionJournal.Job)v));
    }
    private void showJob(ProductionJournal.Job job){
        mode="job-detail";screen("Training job · "+jobStatus(job.status),"Payment is read from the original financial ledger.");
        label(job.service);label("Receipt: "+job.receipt);label("Revision: "+job.revision);
        label("Total: "+QuoteMath.display(job.financial.total));label("Outstanding: "+QuoteMath.display(job.financial.balance));
        label("Payment: "+job.financial.payment);label("Last note: "+job.note);
        if(job.pendingCompensation)label("Resolve pending receipt compensation before advancing.");
        else if(job.financial.reversed())label("Receipt reversed; production progression blocked.");
        else{
            if(job.status==ProductionWorkflow.Status.READY_FOR_PICKUP&&job.financial.balance>0)label("Release blocked: collect outstanding payment first.");
            for(ProductionWorkflow.Status next:ProductionWorkflow.allowed(job.status,job.financial))
                button("Move to "+jobStatus(next),next==ProductionWorkflow.Status.COMPLETED?R.id.cashier_job_release:0,()->showJobTransition(job,next));
        }
        button("Job event history",R.id.cashier_job_history,()->showJobHistory(job.receipt,0));
        button("Reload job",R.id.cashier_job_reload,()->openJob(job.receipt));
        button("Production jobs",R.id.cashier_jobs,()->showJobs(0));back();
    }
    private void showJobTransition(ProductionJournal.Job job,ProductionWorkflow.Status next){
        mode="job-transition";screen(next==ProductionWorkflow.Status.COMPLETED?"Confirm job release":"Confirm production transition","Review current status. The ledger and revision are checked again at confirmation.");
        label(job.service);label(jobStatus(job.status)+" → "+jobStatus(next));
        label("Outstanding: "+QuoteMath.display(job.financial.balance));
        EditText note=field("Transition note",R.id.cashier_job_note,InputType.TYPE_CLASS_TEXT,160,null);
        String event=UUID.randomUUID().toString();
        button("Confirm transition",R.id.cashier_job_confirm,()->{
            String raw=text(note);
            submit(()->store.appendAuthorizedFixtureJob(session(),job.receipt,event,job.revision,next,raw),v->showJob((ProductionJournal.Job)v));
        });
        button("Reload job",R.id.cashier_job_reload,()->openJob(job.receipt));back();
    }
    private void showJobHistory(String receipt,int offset){
        submit(()->store.authorizedFixtureJobHistory(session(),receipt,offset),v->{
            @SuppressWarnings("unchecked") List<ProductionJournal.Event> rows=(List<ProductionJournal.Event>)v;
            mode="job-history";screen("Training job event history","Original status changes and actor revisions are retained.");
            for(int i=0;i<Math.min(8,rows.size());i++){
                ProductionJournal.Event e=rows.get(i);
                label("#"+e.revision+" · "+jobStatus(ProductionWorkflow.Status.valueOf(e.status)));
                label(e.actor+" · Account revision "+e.actorRevision+" · "+new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",java.util.Locale.ROOT).format(new java.util.Date(e.created)));
                label(e.note);
            }
            catalogPages(offset,rows.size()>8,()->showJobHistory(receipt,offset-8),()->showJobHistory(receipt,offset+8));
            button("Return to job",R.id.cashier_job_reload,()->openJob(receipt));back();
        });
    }
    private void showQuoteServices(int offset){
        submit(()->store.authorizedFixtureCheckoutServices(session(),offset),v->{
            CatalogStore.CatalogPage page=(CatalogStore.CatalogPage)v;
            mode="quote-services";screen("Choose quotation service","Active training services only; no stock reservation.");
            if(page.entries.isEmpty())label("No active training services");
            for(CatalogEntry item:page.entries)button("Choose "+item.name,0,()->
                submit(()->store.authorizedFixtureCheckoutSelection(session(),item.id,item.revision),x->showQuoteForm((CatalogStore.CheckoutSelection)x)));
            catalogPages(offset,page.more,()->showQuoteServices(offset-TrainingForms.PAGE_SIZE),()->showQuoteServices(offset+TrainingForms.PAGE_SIZE));back();
        });
    }
    private void showQuoteForm(CatalogStore.CheckoutSelection selected){
        CatalogEntry service=selected.service;PricingProfile profile=selected.profile;
        mode="quote-form";screen("New training quotation",service.name+" · "+QuoteMath.display(service.centavos)+" / "+service.unit);
        label("Pricing: "+PricingProfile.label(profile.method)+" · "+profile.tiers.size()+" tier(s)");
        EditText count=profile.pagesAndCopies?null:field("Quantity ("+service.unit+")",R.id.cashier_count,InputType.TYPE_CLASS_NUMBER,6,"1");
        EditText pages=profile.pagesAndCopies?field("Pages",R.id.cashier_checkout_pages,InputType.TYPE_CLASS_NUMBER,6,"1"):null;
        EditText copies=profile.pagesAndCopies?field("Copies",R.id.cashier_checkout_copies,InputType.TYPE_CLASS_NUMBER,6,"1"):null;
        boolean measured=TrainingForms.measured(profile.method);
        int decimals=InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL;
        EditText width=measured?field("Width",R.id.cashier_checkout_width,decimals,9,null):null;
        EditText height=measured?field("Height",R.id.cashier_checkout_height,decimals,9,null):null;
        Spinner units=measured?choices("Measurement unit",R.id.cashier_checkout_unit,Arrays.asList("Feet","Inches","Centimeters","Meters"),0):null;
        Spinner basis=selected.hasMaterials&&profile.needsAdvancedQuote()?choices("Material usage basis",R.id.cashier_checkout_basis,
            Arrays.asList("Choose usage basis","Per billable unit","Per produced item"),0):null;
        if(selected.hasMaterials&&!profile.needsAdvancedQuote())label("Material usage: per billable unit");
        if(!selected.hasMaterials)label("No material recipe configured");
        button("Review quotation",R.id.cashier_quote_review,()->{
            String quantity=text(count),p=text(pages),c=text(copies),w=text(width),h=text(height),id=UUID.randomUUID().toString();
            PricingMath.DimensionUnit unit=units==null?null:PricingMath.DimensionUnit.values()[units.getSelectedItemPosition()];
            CheckoutPlan.UsageBasis usage=basis==null?null:CheckoutPlan.UsageBasis.values()[basis.getSelectedItemPosition()];
            submit(()->store.prepareAuthorizedFixtureQuotation(session(),id,selected,
                TrainingForms.checkout(profile,selected.hasMaterials,quantity,p,c,w,h,unit,usage,"0.00")),
                v->showQuoteReview(selected,(SavedQuotation)v,null));
        });
        button("Change service",R.id.cashier_checkout_choose,()->showQuoteServices(0));back();
    }
    private void quoteTerms(String name,long quantity,long rate,long total,long cost,PricingMath.Method method,CheckoutPlan.Inputs inputs){
        label(name+" · "+PricingProfile.label(method));
        label("Billable quantity: "+PricingMath.displayQuantity(quantity)+" · Rate: "+QuoteMath.display(rate));
        label("Total: "+QuoteMath.display(total),20,Color.rgb(27,39,58));
        label("Estimated material cost: "+QuoteMath.display(cost));
        label("Quantity: "+inputs.count+" · Pages: "+inputs.pages+" · Copies: "+inputs.copies);
        if(inputs.dimensionUnit!=null)label("Dimensions: "+inputs.width+(inputs.height==null?"":" × "+inputs.height)+" "+inputs.dimensionUnit);
        label("Material basis: "+inputs.basis);
    }
    private void quoteFlags(boolean sufficient,boolean pending,boolean convertible){
        label(sufficient?"Stock estimate: sufficient":"Stock estimate: insufficient");
        if(pending)label("Stock movements pending at review");
        if(!convertible)label("Not convertible under current checkout precision or value rules");
        label("Stock is not reserved; conversion requires a fresh check.");
    }
    private String quoteUsage(long hundredthMilli){
        return java.math.BigDecimal.valueOf(hundredthMilli,5).stripTrailingZeros().toPlainString();
    }
    private void showQuoteReview(CatalogStore.CheckoutSelection selected,SavedQuotation q,CustomerLinkJournal.Selection customer){
        mode="quote-review";screen("Review training quotation","No payment, sale, debt or stock movement.");
        quoteTerms(q.serviceName,q.quantityHundredths,q.rateCentavos,q.totalCentavos,q.materialCostCentavos,q.method,q.inputs);
        for(SavedQuotation.Material m:q.materials)label("Estimated use: "+quoteUsage(m.requiredHundredthMilli)+" "+m.unit+" ("+m.name+")");
        quoteFlags(q.stockSufficientAtReview,q.pendingStockAtReview,q.representableForCheckout);
        checkoutCustomerLabel(customer);
        button("Choose customer",R.id.cashier_checkout_customer,()->showQuoteCustomers(selected,q,customer,"",0));
        if(customer!=null)button("Clear customer",R.id.cashier_checkout_customer_clear,()->showQuoteReview(selected,q,null));
        button("Save quotation",R.id.cashier_quote_save,()->submit(()->store.saveAuthorizedFixtureQuotation(session(),selected,q,customer),v->openQuote(((QuotationJournal.Record)v).id)));
        button("Edit inputs",R.id.cashier_checkout_choose,()->showQuoteForm(selected));back();
    }
    private void showQuoteCustomers(CatalogStore.CheckoutSelection selected,SavedQuotation q,CustomerLinkJournal.Selection prior,String search,int offset){
        submit(()->store.authorizedFixtureQuotationCustomers(session(),search,offset,9),v->{
            @SuppressWarnings("unchecked") List<CustomerLinkJournal.Selection> rows=(List<CustomerLinkJournal.Selection>)v;
            mode="quote-customers";screen("Choose quotation customer","Optional active named customer; no debt is created.");
            EditText query=field("Search customer name",R.id.cashier_customer_search,InputType.TYPE_CLASS_TEXT,80,search);
            button("Search",R.id.cashier_customer_find,()->showQuoteCustomers(selected,q,prior,text(query),0));
            if(rows.isEmpty())label("No matching customers");
            for(int i=0;i<Math.min(8,rows.size());i++){
                CustomerLinkJournal.Selection c=rows.get(i);label(c.id);button("Select "+c.name,0,()->showQuoteReview(selected,q,c));
            }
            catalogPages(offset,rows.size()>8,()->showQuoteCustomers(selected,q,prior,search,offset-8),()->showQuoteCustomers(selected,q,prior,search,offset+8));
            button("Back to review",R.id.cashier_checkout_customer,()->showQuoteReview(selected,q,prior));back();
        });
    }
    private String quoteState(QuotationJournal.Record q){
        return q.cancelled?"Cancelled":q.cashCheckout!=null?"Cash converted":q.creditCheckout!=null?"Credit converted":"Saved";
    }
    private void showQuoteHistory(int offset){
        submit(()->store.authorizedFixtureQuotationHistory(session(),offset),v->{
            QuotationJournal.Page page=(QuotationJournal.Page)v;
            mode="quote-history";screen("Saved training quotations","Select an original record, including after an interrupted confirmation.");
            if(page.rows.isEmpty())label("No saved quotations");
            for(QuotationJournal.Record q:page.rows)button(q.historical().serviceName+" · "+QuoteMath.display(q.total)+" · "+quoteState(q),0,()->openQuote(q.id));
            catalogPages(offset,page.more,()->showQuoteHistory(offset-TrainingForms.PAGE_SIZE),()->showQuoteHistory(offset+TrainingForms.PAGE_SIZE));back();
        });
    }
    private void openQuote(String id){
        submit(()->store.authorizedFixtureQuotation(session(),id),v->showQuoteDetail((QuotationJournal.Record)v));
    }
    private void showQuoteDetail(QuotationJournal.Record q){
        SavedQuotation.Historical h=q.historical();
        mode="quote-detail";screen("Training quotation · "+quoteState(q),"Historical synthetic quotation, not a tax receipt.");
        label("Quotation: "+q.id);quoteTerms(h.serviceName,h.quantityHundredths,h.rate,h.total,h.materialCost,h.method,h.inputs);
        label("Service / pricing / recipe revisions: "+h.serviceRevision+" / "+h.pricingRevision+" / "+h.recipeRevision);
        label(q.customer==null?"Customer: Walk-in / unassigned":"Customer: "+q.customerName+" · Revision "+q.customerRevision);
        for(SavedQuotation.HistoricalMaterial m:h.materials)label("Estimated use: "+quoteUsage(m.requiredHundredthMilli)+" "+m.unit+" ("+m.name+")");
        quoteFlags(q.stockSufficient,q.stockPending,q.convertible);
        if(q.cancelled)label("Cancellation: "+q.cancelReason);
        else if(q.cashCheckout!=null){
            label("Linked cash receipt: "+q.cashCheckout);
            button("Open my cash receipt",R.id.cashier_quote_receipt,()->submit(()->store.authorizedFixtureCheckout(session(),q.cashCheckout),v->showReceipt((CheckoutJournal.Record)v)));
        }else if(q.creditCheckout!=null){
            label("Linked credit receipt: "+q.creditCheckout);
            button("Open my credit receipt",R.id.cashier_quote_receipt,()->submit(()->store.authorizedFixtureCreditCheckout(session(),q.creditCheckout),v->showCreditRecord((CreditCheckoutJournal.Record)v)));
        }else{
            if(q.convertible)button("Review conversion",R.id.cashier_quote_convert,()->submit(this::dashboard,v->showQuoteTender(q,(Dashboard)v)));
            EditText reason=field("Cancellation reason",R.id.cashier_cancel_reason,InputType.TYPE_CLASS_TEXT,160,null);
            button("Cancel quotation",R.id.cashier_quote_cancel,()->{String note=text(reason);submit(()->store.cancelAuthorizedFixtureQuotation(session(),q.id,note),v->showQuoteDetail((QuotationJournal.Record)v));});
        }
        button("Quotation production job",R.id.cashier_quote_job,()->openQuoteJob(q));
        button("Reload quotation",R.id.cashier_quote_reload,()->openQuote(q.id));
        button("Quotation history",R.id.cashier_quote_history,()->showQuoteHistory(0));back();
    }
    private void showQuoteJobs(int offset){
        submit(()->store.authorizedFixtureQuoteJobs(session(),offset),v->{
            @SuppressWarnings("unchecked") List<QuoteProductionJournal.Job> rows=(List<QuoteProductionJournal.Job>)v;
            mode="quote-jobs";screen("Quotation production jobs","Pre-sale work requests and original conversion links.");
            if(rows.isEmpty())label("No quotation production jobs");
            for(int i=0;i<Math.min(8,rows.size());i++){
                QuoteProductionJournal.Job j=rows.get(i);button(j.service+" · "+(j.receipt==null?jobStatus(ProductionWorkflow.Status.valueOf(j.status)):"Converted"),0,()->reloadQuoteJob(j.quote));
            }
            catalogPages(offset,rows.size()>8,()->showQuoteJobs(offset-8),()->showQuoteJobs(offset+8));
            button("Recorded-sale jobs",R.id.cashier_jobs,()->showJobs(0));back();
        });
    }
    private void openQuoteJob(QuotationJournal.Record q){
        submit(()->store.authorizedFixtureQuoteJob(session(),q.id),v->{
            if(v!=null){showQuoteJob((QuoteProductionJournal.Job)v);return;}
            mode="quote-job-create";screen("Quotation production job","No payment or inventory changes. Production begins after conversion.");
            if(q.cancelled||q.cashCheckout!=null||q.creditCheckout!=null)label("No pre-sale job for this resolved quotation.");
            else quoteJobConfirmation(q.id,0,ProductionWorkflow.Status.QUOTATION);
            button("Return to quotation",R.id.cashier_quote_reload,()->openQuote(q.id));back();
        });
    }
    private void reloadQuoteJob(String quote){
        submit(()->store.authorizedFixtureQuoteJob(session(),quote),v->{if(v==null){openQuote(quote);return;}showQuoteJob((QuoteProductionJournal.Job)v);});
    }
    private void showQuoteJob(QuoteProductionJournal.Job job){
        mode="quote-job-detail";screen("Quotation job · "+(job.receipt==null?jobStatus(ProductionWorkflow.Status.valueOf(job.status)):"Converted"),"The saved quotation and original notes are retained.");
        label(job.service);label("Quotation: "+job.quote);label("Estimated total: "+QuoteMath.display(job.total));label("Last note: "+job.note);
        if(job.receipt!=null){label("Linked receipt: "+job.receipt);if(!"CANCELLED".equals(job.status))button("Open recorded-sale job",R.id.cashier_job_reload,()->openJob(job.receipt));}
        else if(!"CANCELLED".equals(job.status)){
            label("No recorded payment. Convert quotation before starting production.");
            ProductionWorkflow.Status next="QUOTATION".equals(job.status)?ProductionWorkflow.Status.AWAITING_APPROVAL:ProductionWorkflow.Status.QUOTATION;
            for(ProductionWorkflow.Status target:new ProductionWorkflow.Status[]{next,ProductionWorkflow.Status.CANCELLED})
                button("Move to "+jobStatus(target),0,()->{
                    mode="quote-job-transition";screen("Confirm quotation job change",jobStatus(target));quoteJobConfirmation(job.quote,job.revision,target);back();
                });
        }
        button("Quotation job history",R.id.cashier_job_history,()->showQuoteJobHistory(job.quote,0));
        button("Return to quotation",R.id.cashier_quote_reload,()->openQuote(job.quote));
        button("Quotation production jobs",R.id.cashier_quote_jobs,()->showQuoteJobs(0));back();
    }
    private void quoteJobConfirmation(String quote,long prior,ProductionWorkflow.Status target){
        EditText note=field("Job note",R.id.cashier_job_note,InputType.TYPE_CLASS_TEXT,160,null);String event=UUID.randomUUID().toString();
        button(prior==0?"Create quotation job":"Confirm change",R.id.cashier_job_confirm,()->{
            String raw=text(note);submit(()->store.appendAuthorizedFixtureQuoteJob(session(),quote,event,prior,target,raw),v->showQuoteJob((QuoteProductionJournal.Job)v));
        });
    }
    private void showQuoteJobHistory(String quote,int offset){
        submit(()->store.authorizedFixtureQuoteJobHistory(session(),quote,offset),v->{
            @SuppressWarnings("unchecked") List<ProductionJournal.Event> rows=(List<ProductionJournal.Event>)v;
            mode="quote-job-history";screen("Quotation job event history","Immutable pre-sale notes and operator revisions.");
            for(int i=0;i<Math.min(8,rows.size());i++){ProductionJournal.Event e=rows.get(i);label("#"+e.revision+" · "+e.status+" · "+e.actor+" · Account revision "+e.actorRevision);label(e.note);}
            catalogPages(offset,rows.size()>8,()->showQuoteJobHistory(quote,offset-8),()->showQuoteJobHistory(quote,offset+8));
            button("Return to quotation job",R.id.cashier_job_reload,()->reloadQuoteJob(quote));back();
        });
    }
    private void showQuoteTender(QuotationJournal.Record q,Dashboard d){
        mode="quote-tender";screen("Convert training quotation","Current commercial terms, stock and shift are checked again.");
        label("Total: "+QuoteMath.display(q.total));
        if(d.shift==null||!d.clear())label("Open your own shift and resolve pending requests before conversion.");
        else{
            Spinner payment=choices("Payment mode",R.id.cashier_payment_mode,q.customer==null?Arrays.asList("Full cash"):Arrays.asList("Full cash","Partial or unpaid credit"),0);
            EditText cash=cashField("Actual training cash (PHP)",R.id.cashier_cash,null);
            button("Review payment",R.id.cashier_quote_review,()->{
                String raw=text(cash),id=UUID.randomUUID().toString(),shift=d.shift.id;
                boolean credit=payment.getSelectedItemPosition()==1;
                submit(()->{
                    long amount=QuoteMath.parseCentavos(raw);
                    if(credit)return store.prepareAuthorizedFixtureQuotationCredit(session(),shift,q.id,id,amount);
                    return store.prepareAuthorizedFixtureQuotationCash(session(),shift,q.id,id,amount);
                },v->showQuoteConversionReview(q,shift,v));
            });
        }
        button("Back to quotation",R.id.cashier_quote_reload,()->openQuote(q.id));back();
    }
    private void showQuoteConversionReview(QuotationJournal.Record q,String shift,Object plan){
        mode="quote-conversion-review";screen("Confirm quotation conversion","Record synthetic sale once. After uncertainty, reload the original quotation.");
        label("Quotation: "+q.id);label("Customer: "+(q.customerName==null?"Walk-in / unassigned":q.customerName));
        if(plan instanceof CheckoutPlan.Proposal){
            CheckoutPlan.Proposal cash=(CheckoutPlan.Proposal)plan;label(CheckoutPreview.render(cash));
            button("Record cash conversion",R.id.cashier_quote_confirm,()->submit(()->store.convertAuthorizedFixtureQuotationCash(session(),shift,q.id,cash),v->openQuote(q.id)));
        }else{
            CreditCheckoutPlan credit=(CreditCheckoutPlan)plan;creditAmounts(credit.total,credit.paid);
            button("Record credit conversion",R.id.cashier_quote_confirm,()->submit(()->store.convertAuthorizedFixtureQuotationCredit(session(),shift,q.id,credit),v->openQuote(q.id)));
        }
        button("Reload original quotation",R.id.cashier_quote_reload,()->openQuote(q.id));back();
    }
    private void showCheckoutChoices(String shift,int offset){
        submit(()->store.authorizedFixtureCheckoutServices(session(),offset),v->{
            CatalogStore.CatalogPage result=(CatalogStore.CatalogPage)v;
            if(offset==0&&!result.more&&result.entries.size()==1){selectCheckout(shift,result.entries.get(0));return;}
            mode="checkout-service";screen("Choose training service","Only active services can be used. Pricing and recipe are loaded from saved records.");
            if(result.entries.isEmpty())label("No active training services on this page. Ask an owner to configure or restore a service.");
            for(CatalogEntry item:result.entries){
                label(item.name+" · "+QuoteMath.display(item.centavos)+" / "+item.unit+" · ID "+item.id.substring(0,8));
                button("Choose "+item.name,0,()->selectCheckout(shift,item));
            }
            catalogPages(offset,result.more,()->showCheckoutChoices(shift,offset-TrainingForms.PAGE_SIZE),()->showCheckoutChoices(shift,offset+TrainingForms.PAGE_SIZE));
            button("Reload services",R.id.cashier_catalog_reload,()->showCheckoutChoices(shift,0));back();
        });
    }
    private void selectCheckout(String shift,CatalogEntry service){
        submit(()->store.authorizedFixtureCheckoutSelection(session(),service.id,service.revision),v->showCheckout(shift,(CatalogStore.CheckoutSelection)v));
    }
    private void showCheckout(String shift,CatalogStore.CheckoutSelection selected){
        CatalogEntry service=selected.service;PricingProfile profile=selected.profile;
        mode="checkout";screen("New training checkout",service.name+" · "+QuoteMath.display(service.centavos)+" / "+service.unit);
        label("Pricing: "+PricingProfile.label(profile.method)+" · "+profile.tiers.size()+" tier(s)");
        EditText count=profile.pagesAndCopies?null:field("Quantity ("+service.unit+")",R.id.cashier_count,InputType.TYPE_CLASS_NUMBER,6,"1");
        EditText pages=profile.pagesAndCopies?field("Pages",R.id.cashier_checkout_pages,InputType.TYPE_CLASS_NUMBER,6,"1"):null;
        EditText copies=profile.pagesAndCopies?field("Copies",R.id.cashier_checkout_copies,InputType.TYPE_CLASS_NUMBER,6,"1"):null;
        boolean measured=TrainingForms.measured(profile.method);
        int decimals=InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL;
        EditText width=measured?field("Width",R.id.cashier_checkout_width,decimals,9,null):null;
        EditText height=measured?field("Height",R.id.cashier_checkout_height,decimals,9,null):null;
        Spinner units=measured?choices("Measurement unit",R.id.cashier_checkout_unit,Arrays.asList("Feet","Inches","Centimeters","Meters"),0):null;
        Spinner basis=selected.hasMaterials&&profile.needsAdvancedQuote()?choices("Material usage basis",R.id.cashier_checkout_basis,
            Arrays.asList("Choose usage basis","Per billable unit","Per produced item"),0):null;
        if(selected.hasMaterials&&!profile.needsAdvancedQuote())label("Material usage: per billable unit");
        if(!selected.hasMaterials)label("No material recipe configured");
        Spinner payment=choices("Payment mode",R.id.cashier_payment_mode,Arrays.asList("Full cash","Partial cash (customer credit)","Unpaid (customer credit)"),0);
        label("For unpaid credit, enter 0.00; for partial credit, enter only actual cash paid.");
        EditText cash=cashField("Training cash tendered (PHP)",R.id.cashier_cash,null);
        button("Review training checkout",R.id.cashier_review,()->{
            String quantity=text(count),p=text(pages),c=text(copies),w=text(width),h=text(height),amount=text(cash),id=UUID.randomUUID().toString();
            PricingMath.DimensionUnit unit=units==null?null:PricingMath.DimensionUnit.values()[units.getSelectedItemPosition()];
            CheckoutPlan.UsageBasis usage=basis==null?null:CheckoutPlan.UsageBasis.values()[basis.getSelectedItemPosition()];
            int paymentMode=payment.getSelectedItemPosition();
            submit(()->{
                CheckoutPlan.Inputs inputs=TrainingForms.checkout(profile,selected.hasMaterials,quantity,p,c,w,h,unit,usage,amount);
                if(paymentMode==0)return store.prepareAuthorizedFixtureCheckout(session(),shift,id,selected,inputs);
                if((paymentMode==1&&inputs.receivedCentavos<=0)||(paymentMode==2&&inputs.receivedCentavos!=0))
                    throw new IllegalArgumentException("Payment mode and cash amount do not match");
                return inputs;
            },v->{if(v instanceof CheckoutPlan.Proposal)showReview(shift,(CheckoutPlan.Proposal)v);
                else showCreditCustomerSelection(shift,id,selected,(CheckoutPlan.Inputs)v,"",0);});
        });
        button("Reload service selection",R.id.cashier_checkout_choose,()->showCheckoutChoices(shift,0));back();
    }
    private void showCreditCustomerSelection(String shift,String id,CatalogStore.CheckoutSelection selected,CheckoutPlan.Inputs inputs,String search,int offset){
        submit(()->store.authorizedFixtureCheckoutCustomers(session(),shift,search,offset,9),v->{
            @SuppressWarnings("unchecked") List<CustomerLinkJournal.Selection> rows=(List<CustomerLinkJournal.Selection>)v;
            mode="credit-customer";screen("Select training credit customer","Credit requires an active named customer. No contact details are displayed.");
            EditText query=field("Search customer name",R.id.cashier_customer_search,InputType.TYPE_CLASS_TEXT,80,search);
            button("Search",R.id.cashier_customer_find,()->showCreditCustomerSelection(shift,id,selected,inputs,text(query),0));
            if(rows.isEmpty())label("No matching active customers");
            for(int i=0;i<Math.min(8,rows.size());i++){
                CustomerLinkJournal.Selection c=rows.get(i);
                label(c.id);button("Select "+c.name,0,()->submit(()->store.prepareAuthorizedFixtureCreditCheckout(session(),shift,id,selected,c,inputs),x->showCreditReview(shift,selected,c,(CreditCheckoutPlan)x)));
            }
            catalogPages(offset,rows.size()>8,()->showCreditCustomerSelection(shift,id,selected,inputs,search,offset-8),()->showCreditCustomerSelection(shift,id,selected,inputs,search,offset+8));
            button("Back to checkout",R.id.cashier_checkout_choose,()->showCheckout(shift,selected));back();
        });
    }
    private void creditAmounts(long total,long paid){
        label(paid==0?"Payment mode: Unpaid":"Payment mode: Partial cash");
        label("Total: "+QuoteMath.display(total),20,Color.rgb(27,39,58));
        label("Cash paid: "+QuoteMath.display(paid));label("Original debt: "+QuoteMath.display(total-paid));label("Change: PHP 0.00");
    }
    private void showCreditReview(String shift,CatalogStore.CheckoutSelection selected,CustomerLinkJournal.Selection customer,CreditCheckoutPlan plan){
        mode="credit-review";screen("Review training credit checkout","No sale, debt, cash or inventory change has been recorded.");
        label(selected.service.name+" · "+PricingMath.displayQuantity(plan.billableHundredths)+" billable units");checkoutCustomerLabel(customer);
        creditAmounts(plan.total,plan.paid);
        for(CheckoutPlan.Consumption m:plan.materials)label("Proposed use: "+StockMath.display(m.quantityMilli)+" "+m.unit+" ("+m.name+")");
        button("Change customer",R.id.cashier_checkout_customer,()->showCreditCustomerSelection(shift,plan.id,selected,plan.actualInputs,"",0));
        button("Save pending credit request",R.id.cashier_credit_stage,()->submit(()->store.stageAuthorizedFixtureCreditCheckout(session(),shift,plan),v->showCreditRecord((CreditCheckoutJournal.Record)v)));back();
    }
    private void showCreditRecord(CreditCheckoutJournal.Record r){
        boolean pending="PENDING".equals(r.state),applied="APPLIED".equals(r.state);
        mode="credit-record";screen(pending?"Pending training credit checkout":applied?"Recorded training credit receipt":"Cancelled training credit checkout",
            pending?"Review the saved request. Record once or cancel with no financial effect.":"Historical synthetic record only. No tax receipt or physical printing.");
        label(r.serviceName);label("Customer: "+r.customerName);label("Customer ID: "+r.customerId+" · Revision "+r.customerRevision);
        creditAmounts(r.total,r.paid);label("Request: "+r.id);
        for(String material:r.materialDetails)label((applied?"Recorded use: ":"Proposed use: ")+material);
        if(pending){
            button("Record synthetic credit checkout",R.id.cashier_credit_record,()->submit(()->store.applyAuthorizedFixtureCreditCheckout(session(),r.id,r.fingerprint),v->showCreditRecord((CreditCheckoutJournal.Record)v)));
            EditText reason=field("Cancellation reason",R.id.cashier_cancel_reason,InputType.TYPE_CLASS_TEXT,160,null);
            button("Cancel unrecorded credit request",R.id.cashier_cancel,()->{String note=text(reason);submit(()->store.cancelAuthorizedFixtureCreditCheckout(session(),r.id,r.fingerprint,note),v->showCreditRecord((CreditCheckoutJournal.Record)v));});
        }else if(r.cancelReason!=null)label("Cancellation: "+r.cancelReason);
        if(applied)button("Production job",R.id.cashier_job_receipt,()->openOrCreateJob(r.id,r.serviceName,r.total));
        button("Return to training cashier",R.id.cashier_return_home,this::refreshDashboard);
    }
    private void showDebtors(String shift,String search,int offset){
        submit(()->store.authorizedFixtureDebtors(session(),shift,search,offset,9),v->{
            @SuppressWarnings("unchecked") List<ReceivableJournal.Debtor> rows=(List<ReceivableJournal.Debtor>)v;
            mode="debtors";screen("Select training debtor","Only customers with outstanding debt, including archived customers. Balances are live; no contact details.");
            EditText query=field("Search customer name",R.id.cashier_customer_search,InputType.TYPE_CLASS_TEXT,80,search);
            button("Search",R.id.cashier_customer_find,()->showDebtors(shift,text(query),0));
            if(rows.isEmpty())label("No outstanding debt on this page");
            for(int i=0;i<Math.min(8,rows.size());i++){
                ReceivableJournal.Debtor debtor=rows.get(i);
                label(debtor.name+(debtor.active?"":" · Archived"));
                label("Outstanding: "+reportMoney(debtor.outstanding));label(debtor.id);
                button("Collect from "+debtor.name,0,()->showCollectionAmount(shift,debtor));
            }
            catalogPages(offset,rows.size()>8,()->showDebtors(shift,search,offset-8),()->showDebtors(shift,search,offset+8));
            back();
        });
    }
    private void showCollectionAmount(String shift,ReceivableJournal.Debtor debtor){
        mode="collection-amount";screen("New training debt collection","Record actual cash only. The oldest outstanding sales are settled first.");
        label("Customer: "+debtor.name+(debtor.active?"":" · Archived"));
        label("Outstanding: "+reportMoney(debtor.outstanding));
        EditText amount=cashField("Cash received (PHP)",R.id.cashier_collection_amount,null);
        button("Review collection",R.id.cashier_collection_review,()->{
            String raw=text(amount),id=UUID.randomUUID().toString();
            submit(()->store.prepareAuthorizedFixtureCollection(session(),shift,id,debtor.id,debtor.revision,QuoteMath.parseCentavos(raw)),v->showCollectionReview(debtor,(ReceivableJournal.Review)v,0));
        });
        button("Reload debtors",R.id.cashier_collection_new,()->showDebtors(shift,"",0));back();
    }
    private void allocationLine(String debt,long before,long amount,long after){
        label("Original receipt: "+debt);
        label("Before "+QuoteMath.display(before)+" · Payment "+QuoteMath.display(amount)+" · After "+QuoteMath.display(after));
    }
    private void showCollectionReview(ReceivableJournal.Debtor debtor,ReceivableJournal.Review review,int offset){
        mode="collection-review";screen("Review training debt collection","No cash or debt changes yet. Review the exact FIFO allocations.");
        ReceivableMath.Collection c=review.collection;
        label("Customer: "+debtor.name);label("Payment: "+QuoteMath.display(c.amount),20,Color.rgb(27,39,58));
        label("Outstanding before: "+reportMoney(c.outstandingBefore));label("Outstanding after: "+reportMoney(c.outstandingAfter));
        label("Drawer before: "+QuoteMath.display(c.drawerBefore));label("Drawer after: "+QuoteMath.display(c.drawerAfter));
        for(int i=offset;i<Math.min(offset+8,c.allocations.size());i++){
            ReceivableMath.Allocation a=c.allocations.get(i);allocationLine(a.debtId,a.before,a.amount,a.after);
        }
        catalogPages(offset,offset+8<c.allocations.size(),()->showCollectionReview(debtor,review,offset-8),()->showCollectionReview(debtor,review,offset+8));
        button("Save pending collection",R.id.cashier_collection_stage,()->submit(()->store.stageAuthorizedFixtureCollection(session(),review),v->showCollectionRecord((ReceivableJournal.Record)v,0)));
        button("Edit collection amount",R.id.cashier_collection_amount_edit,()->showCollectionAmount(review.shift,debtor));back();
    }
    private void showCollectionRecord(ReceivableJournal.Record record,int offset){
        boolean pending="PENDING".equals(record.state),applied="APPLIED".equals(record.state);
        mode="collection-record";screen(pending?"Pending training debt collection":applied?"Recorded training debt collection":"Cancelled training debt collection",
            pending?"Review the original saved payment. Confirm once or cancel without changing cash or debt.":"Historical collection only · Not sales revenue or a fiscal receipt.");
        label("Customer: "+record.customerName);label("Customer ID: "+record.customer+" · Revision "+record.customerRevision);
        label("Payment: "+QuoteMath.display(record.amount),20,Color.rgb(27,39,58));label("Request: "+record.id);
        label("Reviewed drawer before: "+QuoteMath.display(record.before));label("Reviewed drawer after: "+QuoteMath.display(record.after));
        for(int i=offset;i<Math.min(offset+8,record.allocations.size());i++){
            ReceivableJournal.AllocationRow a=record.allocations.get(i);allocationLine(a.debt,a.before,a.amount,a.after);
        }
        catalogPages(offset,offset+8<record.allocations.size(),()->showCollectionRecord(record,offset-8),()->showCollectionRecord(record,offset+8));
        if(pending){
            button("Record debt collection",R.id.cashier_collection_record,()->submit(()->store.applyAuthorizedFixtureCollection(session(),record.id,record.fingerprint),v->showCollectionRecord((ReceivableJournal.Record)v,0)));
            EditText reason=field("Cancellation reason",R.id.cashier_cancel_reason,InputType.TYPE_CLASS_TEXT,160,null);
            button("Cancel unrecorded collection",R.id.cashier_cancel,()->{String note=text(reason);submit(()->store.cancelAuthorizedFixtureCollection(session(),record.id,record.fingerprint,note),v->showCollectionRecord((ReceivableJournal.Record)v,0));});
        }else if(record.reason!=null)label("Cancellation: "+record.reason);
        button("Return to training cashier",R.id.cashier_return_home,this::refreshDashboard);
    }
    private void showCollectionHistory(int offset){
        submit(()->store.authorizedFixtureCollectionHistory(session(),offset,9),v->{
            @SuppressWarnings("unchecked") List<ReceivableJournal.Record> rows=(List<ReceivableJournal.Record>)v;
            mode="collection-history";screen("Training collection history","Your applied and cancelled debt collections. Pending payments are recovered from the dashboard.");
            if(rows.isEmpty())label("No collections on this page");
            for(int i=0;i<Math.min(8,rows.size());i++){
                ReceivableJournal.Record row=rows.get(i);label(row.state+" · "+eventDate(row.recordedMs));
                button(row.customerName+" · "+QuoteMath.display(row.amount),0,()->submit(()->store.authorizedFixtureCollection(session(),row.id),x->showCollectionRecord((ReceivableJournal.Record)x,0)));
            }
            catalogPages(offset,rows.size()>8,()->showCollectionHistory(offset-8),()->showCollectionHistory(offset+8));back();
        });
    }
    private void showCreditHistory(){
        submit(()->store.recentAuthorizedFixtureCreditReceipts(session(),20),v->{
            mode="credit-history";screen("Recorded training credit receipts","Your most recent synthetic credit transactions.");
            @SuppressWarnings("unchecked") List<CreditCheckoutJournal.Record> records=(List<CreditCheckoutJournal.Record>)v;
            if(records.isEmpty())label("No recorded training credit receipts");
            for(CreditCheckoutJournal.Record r:records)button(r.serviceName+" · "+QuoteMath.display(r.total),0,()->submit(()->store.authorizedFixtureCreditCheckout(session(),r.id),x->showCreditRecord((CreditCheckoutJournal.Record)x)));
            back();
        });
    }
    private void showReview(String shift,CheckoutPlan.Proposal plan){showReview(shift,plan,null);}
    private void showReview(String shift,CheckoutPlan.Proposal plan,CustomerLinkJournal.Selection customer){
        mode="review";screen("Review training checkout","No sale, payment or inventory change has been recorded.");
        label(CheckoutPreview.render(plan));
        checkoutCustomerLabel(customer);
        button("Choose customer",R.id.cashier_checkout_customer,()->showCheckoutCustomerSelection(shift,plan,customer,"",0));
        if(customer!=null)button("Clear customer",R.id.cashier_checkout_customer_clear,()->showReview(shift,plan,null));
        for(CheckoutPlan.Consumption m:plan.materials)label("Proposed use: "+StockMath.display(m.quantityMilli)+" "+m.unit+" ("+m.name+")");
        button("Save pending request",R.id.cashier_stage,()->submit(()->store.stageAuthorizedFixtureCheckout(session(),shift,plan,customer),v->{
            CheckoutJournal.Record record=(CheckoutJournal.Record)v;
            if("APPLIED".equals(record.state))showReceipt(record);
            else if("CANCELLED".equals(record.state)){
                screen("Training request cancelled","This original request was cancelled and cannot be recorded.");back();
            }else showPendingRecord(record);
        }));back();
    }
    private void showCheckoutCustomerSelection(String shift,CheckoutPlan.Proposal plan,CustomerLinkJournal.Selection prior,String search,int offset){
        submit(()->store.authorizedFixtureCheckoutCustomers(session(),shift,search,offset,9),v->{
            @SuppressWarnings("unchecked") List<CustomerLinkJournal.Selection> rows=(List<CustomerLinkJournal.Selection>)v;
            mode="checkout-customer";screen("Select training customer","Active names only. No contact details, credit or balance changes.");
            EditText query=field("Search customer name",R.id.cashier_customer_search,InputType.TYPE_CLASS_TEXT,80,search);
            button("Search",R.id.cashier_customer_find,()->showCheckoutCustomerSelection(shift,plan,prior,text(query),0));
            if(rows.isEmpty())label("No matching customers");
            for(int i=0;i<Math.min(8,rows.size());i++){
                CustomerLinkJournal.Selection c=rows.get(i);
                label(c.id);button("Select "+c.name,0,()->showReview(shift,plan,c));
            }
            catalogPages(offset,rows.size()>8,()->showCheckoutCustomerSelection(shift,plan,prior,search,offset-8),()->showCheckoutCustomerSelection(shift,plan,prior,search,offset+8));
            button("Back to checkout review",R.id.cashier_checkout_customer,()->showReview(shift,plan,prior));back();
        });
    }
    private void checkoutCustomerLabel(CustomerLinkJournal.Selection c){
        label(c==null?"Customer: Walk-in / unassigned":"Customer: "+c.name);
        if(c!=null)label("Customer ID: "+c.id+" · Revision "+c.revision);
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
        submit(()->store.authorizedFixtureCheckoutCustomer(session(),r.id),v->renderPendingRecord(r,(CustomerLinkJournal.Selection)v));
    }
    private void renderPendingRecord(CheckoutJournal.Record r,CustomerLinkJournal.Selection customer){
        mode="pending";screen("Pending training checkout","The reviewed request is saved. Training cash and stock change only after explicit recording.");
        label(r.serviceName+" · "+PricingMath.displayQuantity(r.billableHundredths)+" units");
        label("Total: "+QuoteMath.display(r.total),20,Color.rgb(27,39,58));
        label("Training cash: "+QuoteMath.display(r.received)+" · Change: "+QuoteMath.display(r.change));
        label("Request: "+r.id);checkoutCustomerLabel(customer);
        button("Record synthetic checkout",R.id.cashier_record,()->submit(()->store.applyAuthorizedFixtureCheckout(session(),r.id,r.fingerprint),v->showReceipt((CheckoutJournal.Record)v)));
        EditText reason=field("Cancellation reason (only for unrecorded request)",R.id.cashier_cancel_reason,InputType.TYPE_CLASS_TEXT,160,null);
        button("Cancel unrecorded request",R.id.cashier_cancel,()->{
            String note=reason.getText().toString();
            submit(()->store.cancelAuthorizedFixtureCheckout(session(),r.id,r.fingerprint,note),v->refreshDashboard());
        });back();
    }
    private void showReceipt(CheckoutJournal.Record r){
        submit(()->store.authorizedFixtureCheckoutCustomer(session(),r.id),v->renderReceipt(r,(CustomerLinkJournal.Selection)v));
    }
    private void renderReceipt(CheckoutJournal.Record r,CustomerLinkJournal.Selection customer){
        mode="receipt";screen("Recorded training receipt","Historical synthetic record only. No tax receipt or physical printing.");
        label(r.previewReceipt(),18,Color.rgb(27,39,58));checkoutCustomerLabel(customer);
        button("Production job",R.id.cashier_job_receipt,()->openOrCreateJob(r.id,r.serviceName,r.total));
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
            submit(()->store.closeFixtureShift(session(),d.shift.id,QuoteMath.parseCentavos(raw),d.expected),v->{
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

