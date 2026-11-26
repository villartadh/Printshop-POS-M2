package ph.printshop.pos;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

/** Native offline quotation preview. No sale, tax issuance, stock adjustment or network. */
public final class MainActivity extends Activity {
    private EditText price,quantity,received,pages,copies,width,height;
    private TextView total,change,materialCost,linkedService,pricingSummary;
    private LinearLayout countSection,documentSection,dimensionSection,basisSection;
    private Spinner dimensionUnit,materialBasis;
    private CatalogStore store;
    private String selectedServiceId;
    private long selectedPricingRevision=-1;
    private boolean ready,updating;
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        store=new CatalogStore(this);
        selectedServiceId=state==null?null:state.getString("serviceId");
        selectedPricingRevision=state==null?-1:state.getLong("pricingRevision",-1);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20),dp(16),dp(20),dp(16));root.setBackgroundColor(Color.rgb(247,249,252));scroll.addView(root);setContentView(scroll);
        label(root,"Printshop POS",28);
        label(root,"Native Android foundation · offline quote calculator",16);
        label(root,"PREVIEW ONLY — NOT A SALE OR TAX DOCUMENT",14);
        Button catalog=new Button(this);catalog.setId(R.id.open_catalog);
        catalog.setText("Open service and material catalog");catalog.setMinHeight(dp(48));root.addView(catalog);
        catalog.setOnClickListener(v->startActivityForResult(new Intent(this,CatalogActivity.class),41));
        linkedService=label(root,"No service linked",16);
        price=field(root,"Base unit price (PHP)",R.id.quote_price,true);
        countSection=section(root);
        quantity=field(countSection,"Quantity / number of items",R.id.quote_quantity,false);
        documentSection=section(root);
        pages=field(documentSection,"Pages per copy",R.id.quote_pages,false);pages.setText("1");
        copies=field(documentSection,"Number of copies",R.id.quote_copies,false);copies.setText("1");
        dimensionSection=section(root);
        width=field(dimensionSection,"Width",R.id.quote_width,true);
        height=field(dimensionSection,"Height",R.id.quote_height,true);
        label(dimensionSection,"Dimension unit",16);
        dimensionUnit=spinner(dimensionSection,R.id.quote_dimension_unit,new String[]{"Feet (ft)","Inches (in)","Centimeters (cm)","Meters (m)"});
        pricingSummary=label(root,"Billable —",16);
        received=field(root,"Amount received (optional)",R.id.quote_received,true);
        total=label(root,"Total —",24);change=label(root,"Change —",20);
        basisSection=section(root);
        label(basisSection,"Material usage basis",18);
        label(basisSection,"Match the saved service recipe: per billable unit or per produced item/copy. Never changes inventory.",14);
        materialBasis=spinner(basisSection,R.id.quote_material_basis,new String[]{"Choose usage basis","Per billable unit","Per produced item / copy"});
        materialCost=label(root,"Material cost — select a service",16);
        Button clear=new Button(this);clear.setText("Clear service link");clear.setMinHeight(dp(48));root.addView(clear);
        clear.setOnClickListener(v->{selectedServiceId=null;selectedPricingRevision=-1;materialBasis.setSelection(0);calculate();});
        TextWatcher watcher=new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){calculate();}
            public void afterTextChanged(Editable e){}
        };
        for(EditText input:new EditText[]{price,quantity,received,pages,copies,width,height})input.addTextChangedListener(watcher);
        AdapterView.OnItemSelectedListener selection=new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> parent,View view,int position,long id){calculate();}
            public void onNothingSelected(AdapterView<?> parent){calculate();}
        };
        dimensionUnit.setOnItemSelectedListener(selection);materialBasis.setOnItemSelectedListener(selection);
        ready=true;calculate();
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=41||resultCode!=RESULT_OK||data==null)return;
        long centavos=data.getLongExtra("centavos",-1),revision=data.getLongExtra("pricingRevision",-1);
        String id=data.getStringExtra("serviceId");
        if(centavos<0||centavos>99_999_999_999L||id==null||revision<0)return;
        updating=true;
        try{
            selectedServiceId=id;selectedPricingRevision=revision;
            price.setText(java.math.BigDecimal.valueOf(centavos,2).toPlainString());
            pages.setText("1");copies.setText("1");width.setText("");height.setText("");
            dimensionUnit.setSelection(0);materialBasis.setSelection(0);
        }finally{updating=false;}
        calculate();
        Toast.makeText(this,"Service price copied to editable quotation",Toast.LENGTH_SHORT).show();
    }
    private LinearLayout section(LinearLayout parent){
        LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);parent.addView(v);return v;
    }
    private TextView label(LinearLayout root,String text,int size){
        TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(Color.rgb(27,39,58));
        v.setPadding(0,dp(6),0,dp(6));root.addView(v,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));return v;
    }
    private EditText field(LinearLayout root,String name,int id,boolean decimal){
        TextView label=label(root,name,16);label.setLabelFor(id);
        EditText v=new EditText(this);v.setId(id);v.setSingleLine(true);v.setTextSize(20);v.setMinHeight(dp(48));
        v.setInputType(InputType.TYPE_CLASS_NUMBER|(decimal?InputType.TYPE_NUMBER_FLAG_DECIMAL:0));
        root.addView(v,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));return v;
    }
    private Spinner spinner(LinearLayout root,int id,String[] labels){
        Spinner v=new Spinner(this);v.setId(id);v.setMinimumHeight(dp(48));
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);v.setAdapter(adapter);root.addView(v);return v;
    }
    private boolean measured(PricingMath.Method m){
        return m==PricingMath.Method.PER_SQFT||m==PricingMath.Method.PER_LINEAR_FT||m==PricingMath.Method.PER_METER;
    }
    private void showInputs(PricingProfile profile){
        countSection.setVisibility(profile.pagesAndCopies?View.GONE:View.VISIBLE);
        documentSection.setVisibility(profile.pagesAndCopies?View.VISIBLE:View.GONE);
        dimensionSection.setVisibility(measured(profile.method)?View.VISIBLE:View.GONE);
        basisSection.setVisibility(selectedServiceId!=null&&profile.needsAdvancedQuote()?View.VISIBLE:View.GONE);
    }
    private static String billedUnit(PricingMath.Method method){
        switch(method){
            case PER_SQFT:return "sq ft";
            case PER_LINEAR_FT:return "linear ft";
            case PER_METER:return "linear m";
            default:return "units";
        }
    }
    private void calculate(){
        if(!ready||updating)return;
        change.setText("Change —");pricingSummary.setText("Billable —");
        PricingProfile profile=PricingProfile.defaults();
        if(selectedServiceId!=null){
            try{
                CatalogEntry entry=store.get(selectedServiceId);
                if(entry.archived||entry.kind!=CatalogEntry.Kind.SERVICE)throw new IllegalStateException("Service unavailable");
                profile=store.pricing(selectedServiceId);
                linkedService.setText("Linked service: "+entry.name+" (editable selling price)");
                showInputs(profile);
                if(profile.revision!=selectedPricingRevision){
                    total.setText("Total — pricing settings changed; reselect service");
                    materialCost.setText("Material cost — pricing settings changed; reselect service");
                    basisSection.setVisibility(View.GONE);return;
                }
            }catch(RuntimeException ex){
                linkedService.setText("Linked service — unavailable");total.setText("Total — selected service unavailable");
                materialCost.setText("Material cost — selected service unavailable");showInputs(PricingProfile.defaults());return;
            }
        }else{
            linkedService.setText("No service linked");materialCost.setText("Material cost — select a service");showInputs(profile);
        }
        long billable;
        int produced;
        try{
            int count=profile.pagesAndCopies?1:Integer.parseInt(quantity.getText().toString());
            int pageCount=profile.pagesAndCopies?Integer.parseInt(pages.getText().toString()):1;
            int copyCount=profile.pagesAndCopies?Integer.parseInt(copies.getText().toString()):1;
            int unit=dimensionUnit.getSelectedItemPosition();
            if(unit<0||unit>=PricingMath.DimensionUnit.values().length)unit=0;
            billable=PricingMath.effectiveQuantity(profile.method,count,profile.pagesAndCopies,pageCount,copyCount,
                width.getText().toString(),height.getText().toString(),PricingMath.DimensionUnit.values()[unit]);
            produced=profile.pagesAndCopies?copyCount:count;
        }catch(IllegalArgumentException ex){
            total.setText("Total — enter valid price and quantity");
            if(selectedServiceId!=null)materialCost.setText("Material cost — enter valid quantity or dimensions");
            return;
        }
        updateMaterialEstimate(profile,billable,produced);
        try{
            long base=QuoteMath.parseCentavos(price.getText().toString());
            PricingMath.Result result=PricingMath.quote(base,billable,profile.tiers);
            String units=PricingMath.displayQuantity(result.quantityHundredths)+" "+billedUnit(profile.method);
            pricingSummary.setText("Billable: "+units+" · Rate: "+QuoteMath.display(result.rateCentavos)
                +(profile.tiers.isEmpty()?"":" · Bulk tiers apply"));
            total.setText("Total: "+QuoteMath.display(result.totalCentavos));
            if(received.length()!=0){
                try{change.setText("Change: "+QuoteMath.display(QuoteMath.change(result.totalCentavos,QuoteMath.parseCentavos(received.getText().toString()))));}
                catch(IllegalArgumentException ex){change.setText(ex.getMessage());}
            }
        }catch(IllegalArgumentException ex){total.setText("Total — enter valid price and quantity");}
    }
    private void updateMaterialEstimate(PricingProfile profile,long billable,int produced){
        if(selectedServiceId==null)return;
        try{
            if(store.recipeBindings(selectedServiceId).isEmpty()){
                basisSection.setVisibility(View.GONE);
                materialCost.setText("Material cost — no materials configured");return;
            }
            long materialQuantity=billable;
            if(profile.needsAdvancedQuote()){
                basisSection.setVisibility(View.VISIBLE);
                int basis=materialBasis.getSelectedItemPosition();
                if(basis!=1&&basis!=2){
                    materialCost.setText("Material cost — choose usage basis");return;
                }
                if(basis==2)materialQuantity=(long)produced*100;
            }
            MaterialCost.Estimate estimate=store.estimateMaterialsByHundredths(selectedServiceId,materialQuantity);
            materialCost.setText(estimate.componentCount==0?"Material cost — no materials configured":
                "Material cost: "+QuoteMath.display(estimate.costCentavos)+(estimate.stockSufficient?" · Stock sufficient":" · Insufficient stock"));
        }catch(RuntimeException ex){materialCost.setText("Material cost — estimate unavailable");}
    }
    @Override protected void onResume(){super.onResume();calculate();}
    @Override protected void onSaveInstanceState(Bundle state){
        state.putString("serviceId",selectedServiceId);state.putLong("pricingRevision",selectedPricingRevision);super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy(){if(store!=null)store.close();super.onDestroy();}
}
