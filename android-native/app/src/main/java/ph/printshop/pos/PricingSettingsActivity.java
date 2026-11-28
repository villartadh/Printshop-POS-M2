package ph.printshop.pos;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Preview-only pricing preferences. No sale or inventory mutation. */
public final class PricingSettingsActivity extends Activity {
    private CatalogStore store;
    private String serviceId;
    private CatalogEntry service;
    private PricingProfile baseline;
    private final List<PricingMath.Tier> draft=new ArrayList<>();
    private Spinner method;
    private CheckBox pages;
    private TextView heading,status,revision;
    private EditText threshold,rate;
    private LinearLayout tierRows;
    private Button add,save,reload;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        store=new CatalogStore(this);serviceId=getIntent().getStringExtra("serviceId");
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(12),dp(16),dp(12));scroll.addView(root);setContentView(scroll);
        heading=label(root,"Service pricing",24);
        label(root,"LOCAL PREVIEW — pricing settings do not change stock or base selling price",14);
        revision=label(root,"",15);
        label(root,"Pricing method",16);
        method=new Spinner(this);method.setId(R.id.pricing_method);method.setMinimumHeight(dp(48));method.setSaveEnabled(false);
        ArrayList<String> methods=new ArrayList<>();
        for(PricingMath.Method m:PricingMath.Method.values())methods.add(PricingProfile.label(m));
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,methods);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);method.setAdapter(adapter);root.addView(method);
        pages=new CheckBox(this);pages.setId(R.id.pricing_pages);pages.setText("Use pages × copies");pages.setSaveEnabled(false);root.addView(pages);
        method.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> parent,View view,int position,long id){updatePages();}
            public void onNothingSelected(AdapterView<?> parent){updatePages();}
        });
        label(root,"Bulk discounts (optional)",20);
        label(root,"Highest qualifying quantity wins. Base service rate applies below all tiers.",14);
        label(root,"Minimum billable quantity",16);
        threshold=field(root,R.id.pricing_threshold,"Example: 10");
        label(root,"Price per billable unit (PHP)",16);
        rate=field(root,R.id.pricing_rate,"Example: 25.00");
        add=button(root,"Add tier",R.id.pricing_add,()->addTier());
        tierRows=new LinearLayout(this);tierRows.setOrientation(LinearLayout.VERTICAL);root.addView(tierRows);
        save=button(root,"Save pricing settings",R.id.pricing_save,()->save());
        reload=button(root,"Reload saved settings",R.id.pricing_reload,()->new android.app.AlertDialog.Builder(this)
            .setMessage("Discard unsaved pricing changes and reload?")
            .setNegativeButton("Cancel",null).setPositiveButton("Reload",(d,w)->load(null)).show());
        status=label(root,"",16);
        load(state);
    }
    private TextView label(LinearLayout parent,String message,int size){
        TextView v=new TextView(this);v.setText(message);v.setTextSize(size);v.setPadding(0,dp(6),0,dp(6));parent.addView(v);return v;
    }
    private EditText field(LinearLayout parent,int id,String hint){
        EditText v=new EditText(this);v.setId(id);v.setHint(hint);v.setSingleLine(true);v.setMinHeight(dp(48));
        v.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);v.setSaveEnabled(false);parent.addView(v);return v;
    }
    private Button button(LinearLayout parent,String text,int id,Runnable action){
        Button v=new Button(this);v.setText(text);if(id!=View.NO_ID)v.setId(id);v.setMinHeight(dp(48));parent.addView(v);
        v.setOnClickListener(w->action.run());return v;
    }
    private PricingMath.Method selectedMethod(){
        int i=method.getSelectedItemPosition();
        if(i<0||i>=PricingMath.Method.values().length)throw new IllegalArgumentException("Choose a pricing method");
        return PricingMath.Method.values()[i];
    }
    private void updatePages(){
        boolean allowed=method.getSelectedItemPosition()>=0&&PricingProfile.supportsPages(selectedMethod());
        pages.setEnabled(allowed&&baseline!=null);
        if(!allowed)pages.setChecked(false);
    }
    private void load(Bundle state){
        baseline=null;
        try{
            service=store.get(serviceId);
            if(service.archived||service.kind!=CatalogEntry.Kind.SERVICE)throw new IllegalStateException("Select an active service");
            baseline=store.pricing(serviceId);draft.clear();draft.addAll(baseline.tiers);
            method.setSelection(baseline.method.ordinal());pages.setChecked(baseline.pagesAndCopies);
            threshold.setText("");rate.setText("");
            if(state!=null&&state.getLong("serviceRevision",-1)==service.revision&&state.getLong("profileRevision",-1)==baseline.revision){
                int savedMethod=state.getInt("method",-1);
                long[] limits=state.getLongArray("limits"),rates=state.getLongArray("rates");
                if(savedMethod>=0&&savedMethod<PricingMath.Method.values().length&&limits!=null&&rates!=null&&limits.length==rates.length&&limits.length<=100){
                    List<PricingMath.Tier> restored=new ArrayList<>();
                    for(int i=0;i<limits.length;i++)restored.add(new PricingMath.Tier(limits[i],rates[i]));
                    PricingProfile saved=new PricingProfile(baseline.revision,PricingMath.Method.values()[savedMethod],state.getBoolean("pages"),restored);
                    draft.clear();draft.addAll(saved.tiers);method.setSelection(saved.method.ordinal());pages.setChecked(saved.pagesAndCopies);
                    threshold.setText(state.getString("threshold",""));rate.setText(state.getString("rate",""));
                }
            }
            heading.setText(service.name+" · Pricing");revision.setText("Saved revision "+baseline.revision+" · Base rate "+QuoteMath.display(service.centavos)+" / "+service.unit);
            status.setText(state==null?"":"Pricing settings restored or reloaded.");
            setControls(true);updatePages();renderTiers();
        }catch(RuntimeException ex){
            status.setText("Pricing settings unavailable. Reload before editing.");setControls(false);
        }
    }
    private void setControls(boolean enabled){
        method.setEnabled(enabled);pages.setEnabled(enabled);threshold.setEnabled(enabled);rate.setEnabled(enabled);
        save.setEnabled(enabled);add.setEnabled(enabled);
    }
    private void renderTiers(){
        tierRows.removeAllViews();
        if(draft.isEmpty())label(tierRows,"No bulk tiers configured.",16);
        for(PricingMath.Tier tier:new ArrayList<>(draft)){
            String quantity=PricingMath.displayQuantity(tier.minQuantityHundredths);
            label(tierRows,quantity+"+ units · "+QuoteMath.display(tier.rateCentavos)+" per unit",16);
            button(tierRows,"Remove tier "+quantity,View.NO_ID,()->{draft.remove(tier);renderTiers();status.setText("Tier removed from draft. Save to apply.");});
        }
    }
    private void addTier(){
        try{
            List<PricingMath.Tier> next=new ArrayList<>(draft);
            next.add(new PricingMath.Tier(PricingProfile.parseThreshold(threshold.getText().toString()),QuoteMath.parseCentavos(rate.getText().toString())));
            PricingProfile validated=new PricingProfile(baseline.revision,selectedMethod(),pages.isChecked(),next);
            draft.clear();draft.addAll(validated.tiers);threshold.setText("");rate.setText("");renderTiers();
            status.setText("Tier added to draft. Save to apply.");
        }catch(IllegalArgumentException|IllegalStateException ex){status.setText(ex.getMessage());}
    }
    private void save(){
        try{
            if(threshold.length()!=0||rate.length()!=0)throw new IllegalArgumentException("Add the entered tier or clear its fields before saving");
            baseline=store.savePricing(serviceId,service.revision,baseline.revision,selectedMethod(),pages.isChecked(),draft);
            revision.setText("Saved revision "+baseline.revision+" · Base rate "+QuoteMath.display(service.centavos)+" / "+service.unit);
            status.setText("Pricing settings saved.");
        }catch(IllegalArgumentException|IllegalStateException ex){status.setText(ex.getMessage());}
        catch(RuntimeException ex){status.setText("Save confirmation unavailable. Reload saved settings before retrying.");}
    }
    @Override protected void onSaveInstanceState(Bundle state){
        if(baseline!=null){
            state.putLong("serviceRevision",service.revision);state.putLong("profileRevision",baseline.revision);
            state.putInt("method",method.getSelectedItemPosition());state.putBoolean("pages",pages.isChecked());
            long[] limits=new long[draft.size()],rates=new long[draft.size()];
            for(int i=0;i<draft.size();i++){limits[i]=draft.get(i).minQuantityHundredths;rates[i]=draft.get(i).rateCentavos;}
            state.putLongArray("limits",limits);state.putLongArray("rates",rates);
            state.putString("threshold",threshold.getText().toString());state.putString("rate",rate.getText().toString());
        }
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy(){if(store!=null)store.close();super.onDestroy();}
}
