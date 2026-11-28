package ph.printshop.pos;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Preview-only service material configuration; never consumes inventory. */
public final class RecipeActivity extends Activity {
    private CatalogStore store;
    private String serviceId;
    private CatalogEntry service;
    private List<CatalogEntry> materials=new ArrayList<>();
    private List<CatalogStore.RecipeBinding> bindings=new ArrayList<>();
    private long recipeRevision;
    private Spinner chooser;
    private EditText usage;
    private TextView heading, estimate, status;
    private LinearLayout linked;
    private Button save, remove;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        store=new CatalogStore(this);serviceId=getIntent().getStringExtra("serviceId");
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(12),dp(16),dp(12));scroll.addView(root);setContentView(scroll);
        heading=label(root,"Service materials",24);
        label(root,"LOCAL PREVIEW — costs are not selling prices",14);
        estimate=label(root,"Estimated cost —",20);
        label(root,"Choose a material",16);
        chooser=new Spinner(this);chooser.setId(R.id.recipe_material);chooser.setMinimumHeight(dp(48));root.addView(chooser);
        label(root,"Material quantity per service unit",16);
        usage=new EditText(this);usage.setId(R.id.recipe_usage);usage.setSingleLine(true);usage.setHint("Example: 0.5");
        usage.setMinHeight(dp(48));usage.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);root.addView(usage);
        save=button(root,"Save material usage",R.id.recipe_save,()->change(false));
        remove=button(root,"Remove selected material",R.id.recipe_remove,()->change(true));
        status=label(root,"",16);
        label(root,"Linked materials",20);
        linked=new LinearLayout(this);linked.setOrientation(LinearLayout.VERTICAL);root.addView(linked);
        chooser.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> p,View v,int position,long id){showUsage(position);}
            public void onNothingSelected(AdapterView<?> p){usage.setText("");}
        });
        refresh();
    }
    private TextView label(LinearLayout p,String text,int size){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setPadding(0,dp(6),0,dp(6));p.addView(v);return v;}
    private Button button(LinearLayout p,String text,int id,Runnable action){Button v=new Button(this);v.setText(text);v.setId(id);v.setMinHeight(dp(48));p.addView(v);v.setOnClickListener(w->action.run());return v;}
    private void refresh(){
        linked.removeAllViews();materials.clear();
        try{
            service=store.get(serviceId);
            if(service.kind!=CatalogEntry.Kind.SERVICE||service.archived)throw new IllegalStateException("Select an active service");
            recipeRevision=store.recipeRevision(serviceId);bindings=store.recipeBindings(serviceId);
            heading.setText(service.name+" · Materials");
            ArrayList<String> labels=new ArrayList<>();
            for(CatalogEntry item:store.list(false))if(item.kind==CatalogEntry.Kind.MATERIAL){
                materials.add(item);labels.add(item.name+" · "+item.unit+" · "+QuoteMath.display(item.centavos));
            }
            ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,labels);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);chooser.setAdapter(adapter);
            for(CatalogStore.RecipeBinding b:bindings){
                CatalogEntry item=store.get(b.materialId);
                label(linked,item.name+" · "+StockMath.display(b.quantityMilli)+" "+b.unit+" / "+service.unit,16);
            }
            if(bindings.isEmpty())label(linked,"No materials configured.",16);
            MaterialCost.Estimate cost=store.estimateMaterials(serviceId,1);
            estimate.setText(cost.componentCount==0?"Estimated cost — no materials configured":"Estimated cost per unit: "+QuoteMath.display(cost.costCentavos)+(cost.stockSufficient?" · Stock sufficient":" · Insufficient stock"));
            save.setEnabled(!materials.isEmpty());remove.setEnabled(!materials.isEmpty());usage.setEnabled(!materials.isEmpty());chooser.setEnabled(!materials.isEmpty());
            if(!materials.isEmpty())showUsage(chooser.getSelectedItemPosition());
        }catch(RuntimeException ex){
            estimate.setText("Material configuration unavailable.");save.setEnabled(false);remove.setEnabled(false);usage.setEnabled(false);
        }
    }
    private void showUsage(int position){
        if(position<0||position>=materials.size())return;
        String id=materials.get(position).id;
        for(CatalogStore.RecipeBinding b:bindings)if(b.materialId.equals(id)){usage.setText(StockMath.display(b.quantityMilli));remove.setEnabled(true);return;}
        usage.setText("");remove.setEnabled(false);
    }
    private void change(boolean unlink){
        int i=chooser.getSelectedItemPosition();
        if(i<0||i>=materials.size())return;
        CatalogEntry m=materials.get(i);
        try{
            long qty=unlink?0:StockMath.parse(usage.getText().toString());
            if(!unlink&&qty==0)throw new IllegalArgumentException("Enter a positive quantity; use Remove to unlink");
            store.setMaterialUsage(service.id,service.revision,m.id,m.revision,recipeRevision,qty);
            status.setText(unlink?"Material removed.":"Material usage saved.");refresh();
        }catch(IllegalArgumentException|IllegalStateException ex){status.setText(ex.getMessage());refresh();}
        catch(RuntimeException ex){status.setText("Confirmation unavailable. Review linked materials before retrying.");refresh();}
    }
    @Override protected void onDestroy(){if(store!=null)store.close();super.onDestroy();}
}
