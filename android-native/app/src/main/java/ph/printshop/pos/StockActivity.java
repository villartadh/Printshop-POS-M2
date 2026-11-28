package ph.printshop.pos;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;
import java.util.UUID;

/** Preview-only stock controls. Movements cannot record sales or financial payments. */
public final class StockActivity extends Activity {
    private CatalogStore store;
    private String materialId;
    private CatalogEntry material;
    private StockMath.Balance balance;
    private TextView heading, onHand, status;
    private EditText quantity, reason;
    private Button receive, consume, retry, cancel, older, latest;
    private TextView pendingDetail;
    private LinearLayout history;
    private long cursor;
    private CatalogStore.StockRequest pending;
    private String attemptedId;
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        materialId=getIntent().getStringExtra("materialId");
        store=new CatalogStore(this);
        attemptedId=state==null?null:state.getString("attemptedId");
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(12),dp(16),dp(12));
        scroll.addView(root);
        setContentView(scroll);
        heading=label(root,"Material stock",24);
        label(root,"LOCAL PREVIEW — not live inventory",14);
        onHand=label(root,"On hand —",22);
        quantity=field(root,"Quantity (up to three decimals)",R.id.stock_quantity,true);
        reason=field(root,"Movement reason",R.id.stock_reason,false);
        quantity.setSaveEnabled(false);reason.setSaveEnabled(false);
        receive=button(root,"Receive stock",R.id.stock_receive,()->prepare(true));
        consume=button(root,"Consume stock",R.id.stock_consume,()->prepare(false));
        pendingDetail=label(root,"",16);
        retry=button(root,"Retry same movement",R.id.stock_retry,this::runPending);
        cancel=button(root,"Cancel unrecorded movement",R.id.stock_cancel,this::confirmCancel);
        status=label(root,"",16);
        label(root,"Movement history",20);
        history=new LinearLayout(this); history.setOrientation(LinearLayout.VERTICAL); root.addView(history);
        latest=button(root,"Latest movements",R.id.stock_latest,()->{cursor=0;refresh();});
        older=button(root,"Older movements",R.id.stock_older,()->{
            List<CatalogStore.StockMovement> list=store.stockHistory(materialId,cursor,21);
            if(list.size()>20){cursor=list.get(19).revision;refresh();}
        });
        refresh();

    }
    private TextView label(LinearLayout parent,String text,int size) {
        TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setPadding(0,dp(6),0,dp(6));parent.addView(v);return v;
    }
    private EditText field(LinearLayout parent,String hint,int id,boolean decimal) {
        EditText v=new EditText(this);v.setHint(hint);v.setId(id);v.setSingleLine(true);v.setMinHeight(dp(48));
        v.setInputType(decimal?InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL:InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        parent.addView(v);return v;
    }
    private Button button(LinearLayout parent,String text,int id,Runnable action) {
        Button v=new Button(this);v.setText(text);v.setId(id);v.setMinHeight(dp(48));parent.addView(v);
        v.setOnClickListener(w->action.run());return v;
    }
    private void refresh() {
        boolean valid=false,readable=false;
        pending=null;
        history.removeAllViews();
        try {
            material=store.get(materialId);
            if(material.kind!=CatalogEntry.Kind.MATERIAL)throw new IllegalArgumentException("Not a material");
            balance=store.stockBalance(materialId);
            pending=store.pendingStock(materialId);
            if(attemptedId!=null){
                CatalogStore.StockRequest attempted=store.stockRequest(attemptedId);
                if(attempted!=null&&"APPLIED".equals(attempted.state)){
                    quantity.setText("");reason.setText("");cursor=0;status.setText("Movement recorded. Verified from the request ledger.");
                }else if(attempted!=null&&"CANCELLED".equals(attempted.state)){
                    quantity.setText("");reason.setText("");status.setText("Unrecorded movement cancelled; stock unchanged.");
                }
                if(attempted==null||!"PENDING".equals(attempted.state))attemptedId=null;
            }
            heading.setText(material.name+" · Stock");
            onHand.setText("On hand: "+StockMath.display(balance.quantity)+" "+material.unit);
            List<CatalogStore.StockMovement> list=store.stockHistory(materialId,cursor,21);
            for(int i=0;i<Math.min(20,list.size());i++){
                CatalogStore.StockMovement m=list.get(i);
                label(history,"#"+m.revision+" "+(m.delta>0?"+":"")+StockMath.display(m.delta)+" "+m.unit+" · "+m.reason,16);
                label(history,StockMath.display(m.before)+" → "+StockMath.display(m.after)+" "+m.unit,14);
            }
            if(list.isEmpty())label(history,"No movements yet.",16);
            latest.setVisibility(cursor==0?android.view.View.GONE:android.view.View.VISIBLE);
            older.setVisibility(list.size()>20?android.view.View.VISIBLE:android.view.View.GONE);
            readable=true;valid=!material.archived;
        } catch(RuntimeException ex) {
            status.setText("Stock or confirmation unavailable. Reopen this screen to review; do not submit a replacement.");
            latest.setVisibility(android.view.View.GONE);older.setVisibility(android.view.View.GONE);
        }
        receive.setEnabled(valid&&pending==null);consume.setEnabled(valid&&pending==null);
        quantity.setEnabled(valid&&pending==null);reason.setEnabled(valid&&pending==null);
        boolean recoverable=readable&&pending!=null;
        pendingDetail.setVisibility(recoverable?android.view.View.VISIBLE:android.view.View.GONE);
        retry.setVisibility(recoverable?android.view.View.VISIBLE:android.view.View.GONE);
        cancel.setVisibility(recoverable?android.view.View.VISIBLE:android.view.View.GONE);
        retry.setEnabled(recoverable);cancel.setEnabled(recoverable);
        if(recoverable)pendingDetail.setText("Pending movement: "+(pending.delta>0?"+":"")+StockMath.display(pending.delta)+" "+material.unit+" · "+pending.reason+"\nRequest "+pending.id+"\nRetry the same request, or explicitly cancel it if unrecorded.");
    }
    private void prepare(boolean positive) {
        if(pending!=null||material==null||balance==null)return;
        try {
            long delta=StockMath.parse(quantity.getText().toString());
            if(!positive)delta=-delta;
            StockMath.apply(balance.quantity,delta);
            String note=StockMath.reason(reason.getText().toString());
            attemptedId=UUID.randomUUID().toString();
            pending=store.stageStock(attemptedId,materialId,material.revision,balance.revision,delta,note);
            runPending();
        } catch(IllegalArgumentException|IllegalStateException ex){
            status.setText(ex.getMessage()+" Review the current balance.");refresh();
        } catch(RuntimeException ex){
            status.setText("Could not confirm staging. Review pending movement and history before any new request.");refresh();
        }
    }
    private void runPending() {
        if(pending==null)return;
        String id=pending.id;attemptedId=id;
        receive.setEnabled(false);consume.setEnabled(false);retry.setEnabled(false);cancel.setEnabled(false);
        try {
            store.applyStagedStock(id);
            quantity.setText("");reason.setText("");cursor=0;
            status.setText("Movement recorded.");
        } catch(RuntimeException ex){
            status.setText("Confirmation unavailable or movement blocked. Review history and retry the same request; never submit a replacement.");
        } finally {refresh();}
    }
    private void confirmCancel() {
        if(pending==null)return;
        String id=pending.id;attemptedId=id;
        new AlertDialog.Builder(this).setTitle("Cancel pending movement?")
            .setMessage("This cancels only the unrecorded request. It cannot reverse a recorded stock movement. The request remains in the audit history.")
            .setNegativeButton("Keep pending",null)
            .setPositiveButton("Cancel request",(dialog,which)->{
                try{
                    store.cancelStagedStock(id);
                    quantity.setText("");reason.setText("");status.setText("Unrecorded movement cancelled; stock unchanged.");
                }catch(RuntimeException ex){status.setText("Cancellation not confirmed. Review history and pending request.");}
                finally{refresh();}
            }).show();
    }
    @Override protected void onResume(){super.onResume();refresh();}
    @Override protected void onSaveInstanceState(Bundle state){
        state.putString("attemptedId",attemptedId);super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy(){if(store!=null)store.close();super.onDestroy();}
}
