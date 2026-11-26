package ph.printshop.pos;

import android.app.Activity;
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
    private Button receive, consume, retry, older, latest;
    private LinearLayout history;
    private long cursor;
    private Bundle pending;
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        materialId=getIntent().getStringExtra("materialId");
        store=new CatalogStore(this);
        pending=state==null?null:state.getBundle("pending");
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
        receive=button(root,"Receive stock",R.id.stock_receive,()->prepare(true));
        consume=button(root,"Consume stock",R.id.stock_consume,()->prepare(false));
        retry=button(root,"Retry same movement",R.id.stock_retry,this::runPending);
        status=label(root,"",16);
        label(root,"Movement history",20);
        history=new LinearLayout(this); history.setOrientation(LinearLayout.VERTICAL); root.addView(history);
        latest=button(root,"Latest movements",R.id.stock_latest,()->{cursor=0;refresh();});
        older=button(root,"Older movements",R.id.stock_older,()->{
            List<CatalogStore.StockMovement> list=store.stockHistory(materialId,cursor,21);
            if(list.size()>20){cursor=list.get(19).revision;refresh();}
        });
        refresh();
        if(pending!=null) status.setText("Unconfirmed movement. Retry the same request or review history before leaving.");
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
        boolean valid=false;
        history.removeAllViews();
        try {
            material=store.get(materialId);
            if(material.kind!=CatalogEntry.Kind.MATERIAL)throw new IllegalArgumentException("Not a material");
            balance=store.stockBalance(materialId);
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
            valid=!material.archived;
        } catch(RuntimeException ex) {
            status.setText("Stock unavailable; no changes made.");
            latest.setVisibility(android.view.View.GONE);older.setVisibility(android.view.View.GONE);
        }
        receive.setEnabled(valid&&pending==null);consume.setEnabled(valid&&pending==null);
        quantity.setEnabled(valid&&pending==null);reason.setEnabled(valid&&pending==null);
        retry.setVisibility(pending==null?android.view.View.GONE:android.view.View.VISIBLE);
    }
    private void prepare(boolean positive) {
        if(pending!=null||material==null||balance==null)return;
        try {
            long delta=StockMath.parse(quantity.getText().toString());
            if(!positive)delta=-delta;
            StockMath.apply(balance.quantity,delta);
            String note=StockMath.reason(reason.getText().toString());
            pending=new Bundle();
            pending.putString("id",UUID.randomUUID().toString());
            pending.putLong("catalogRevision",material.revision);
            pending.putLong("stockRevision",balance.revision);
            pending.putLong("delta",delta);pending.putString("reason",note);
            runPending();
        } catch(IllegalArgumentException ex){status.setText(ex.getMessage());}
    }
    private void runPending() {
        if(pending==null)return;
        receive.setEnabled(false);consume.setEnabled(false);retry.setEnabled(false);
        try {
            store.moveStock(pending.getString("id"),materialId,pending.getLong("catalogRevision"),pending.getLong("stockRevision"),pending.getLong("delta"),pending.getString("reason"));
            pending=null;quantity.setText("");reason.setText("");cursor=0;
            status.setText("Movement recorded.");
        } catch(IllegalArgumentException|IllegalStateException ex){
            pending=null;status.setText(ex.getMessage()+" Review the current balance before trying again.");
        } catch(RuntimeException ex){
            status.setText("Confirmation unavailable. Retry the same request; do not create a new movement.");
        } finally {retry.setEnabled(true);refresh();}
    }
    @Override protected void onSaveInstanceState(Bundle state){
        super.onSaveInstanceState(state);if(pending!=null)state.putBundle("pending",pending);
    }
    @Override protected void onDestroy(){if(store!=null)store.close();super.onDestroy();}
}
