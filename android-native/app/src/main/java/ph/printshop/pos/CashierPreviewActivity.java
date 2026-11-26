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
        final boolean pendingRefund;
        Dashboard(CashierJournal.Principal a,CashierJournal.Shift s,long e,int p,DrawerJournal.Request drawer,boolean refund){actor=a;shift=s;expected=e;pending=p;pendingDrawer=drawer;pendingRefund=refund;}
    }
    private Dashboard dashboard(){
        String token=session();CashierJournal.Principal actor=store.fixturePrincipal(token);
        CashierJournal.Shift shift=store.activeFixtureShift(token);
        long expected=shift==null?0:store.expectedFixtureCash(token,shift.id);
        int pending=shift==null?0:store.pendingAuthorizedFixtureCheckouts(token,shift.id,50).size();
        DrawerJournal.Request drawer=shift==null?null:store.pendingFixtureDrawer(token,shift.id);
        return new Dashboard(actor,shift,expected,pending,drawer,drawer!=null&&store.isFixtureRefund(token,drawer.id));
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
            next.setEnabled(d.pending==0&&d.pendingDrawer==null);
            button("Recover pending checkouts",R.id.cashier_pending,()->showPendingList(d.shift.id));
            if(d.pendingDrawer!=null)label(d.pendingRefund?"Unresolved training refund/void":"Unresolved training drawer activity");
            Button drawer=button(d.pendingDrawer==null?"Training drawer activity":d.pendingRefund?"Recover pending refund/void":"Recover pending drawer activity",R.id.cashier_drawer,()->{
                if(d.pendingDrawer==null)showDrawerOptions(d);else if(d.pendingRefund)openRefundRecord(d.pendingDrawer.id);else openDrawerRequest(d.pendingDrawer.id);
            });
            drawer.setEnabled(d.pending==0);
            Button close=button("Close training shift",R.id.cashier_close_shift,()->showClose(d));close.setEnabled(d.pending==0&&d.pendingDrawer==null);
        }
        button("Recorded training receipts",R.id.cashier_history,this::showHistory);
        button("Training drawer history",R.id.cashier_drawer_history,()->showDrawerHistory(0));
        button("Change my training passphrase",R.id.cashier_change_pass,()->showCredentialChange(false));
        if(d.actor.role==CashierSecurity.Role.OWNER){
            button("Training sale refunds / voids",R.id.cashier_refund_receipts,()->showRefundReceipts(0));
            button("Refund / void history",R.id.cashier_refund_history,()->showRefundHistory(0));
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
        submit(()->store.authorizedFixtureSalesReport(session(),ReportMath.range(period,System.currentTimeMillis())),v->showSalesReport(period,(ReportJournal.Report)v,0));
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
        label("Expenses: "+reportMoney(total.expenses));label("Other cash in: "+reportMoney(total.cashIn));label("Other cash out: "+reportMoney(total.cashOut));
        label("Net drawer movement: "+reportMoney(total.movement()));
        label("Closed shift reconciliation (whole shifts closed in period)");
        label("Closed shifts: "+report.closedShifts);label("Expected: "+reportMoney(report.closedExpected));
        label("Counted: "+reportMoney(report.closedCounted));label("Variance: "+reportMoney(report.closedVariance));
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
            boolean available=d.shift!=null&&d.pending==0&&d.pendingDrawer==null&&review.remaining()>0;
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
        EditText cash=cashField("Training cash tendered (PHP)",R.id.cashier_cash,null);
        button("Review training checkout",R.id.cashier_review,()->{
            String quantity=text(count),p=text(pages),c=text(copies),w=text(width),h=text(height),amount=text(cash),id=UUID.randomUUID().toString();
            PricingMath.DimensionUnit unit=units==null?null:PricingMath.DimensionUnit.values()[units.getSelectedItemPosition()];
            CheckoutPlan.UsageBasis usage=basis==null?null:CheckoutPlan.UsageBasis.values()[basis.getSelectedItemPosition()];
            submit(()->store.prepareAuthorizedFixtureCheckout(session(),shift,id,selected,
                TrainingForms.checkout(profile,selected.hasMaterials,quantity,p,c,w,h,unit,usage,amount)),v->showReview(shift,(CheckoutPlan.Proposal)v));
        });
        button("Reload service selection",R.id.cashier_checkout_choose,()->showCheckoutChoices(shift,0));back();
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
