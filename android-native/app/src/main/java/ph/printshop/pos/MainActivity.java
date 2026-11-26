package ph.printshop.pos;

import android.app.Activity;
import android.content.Intent;
import android.widget.Button;
import android.widget.Toast;
import android.os.Bundle;
import android.graphics.Color;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.Editable;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Native offline calculator only. No sales, tax issuance, network, or shop data. */
public final class MainActivity extends Activity {
    private EditText price, quantity, received;
    private TextView total, change, materialCost, linkedService;
    private CatalogStore store;
    private String selectedServiceId;
    private int dp(int x) { return Math.round(x * getResources().getDisplayMetrics().density); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new CatalogStore(this);
        selectedServiceId = state == null ? null : state.getString("serviceId");
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));
        root.setBackgroundColor(Color.rgb(247,249,252));
        scroll.addView(root);
        setContentView(scroll);
        label(root, "Printshop POS", 28);
        label(root, "Native Android foundation · offline quote calculator", 16);
        label(root, "PREVIEW ONLY — NOT A SALE OR TAX DOCUMENT", 14);
        Button catalog = new Button(this);
        catalog.setId(R.id.open_catalog);
        catalog.setText("Open service and material catalog");
        catalog.setMinHeight(dp(48));
        root.addView(catalog);
        catalog.setOnClickListener(v -> startActivityForResult(new Intent(this, CatalogActivity.class), 41));
        price = field(root, "Unit price (PHP)", R.id.quote_price, true);
        quantity = field(root, "Quantity", R.id.quote_quantity, false);
        received = field(root, "Amount received (optional)", R.id.quote_received, true);
        total = label(root, "Total —", 24);
        change = label(root, "Change —", 20);
        linkedService = label(root, "No service linked", 16);
        materialCost = label(root, "Material cost — select a service", 16);
        Button clear = new Button(this);
        clear.setText("Clear service link");
        clear.setMinHeight(dp(48));
        root.addView(clear);
        clear.setOnClickListener(v -> { selectedServiceId = null; calculate(); });
        TextWatcher watcher = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { calculate(); }
            public void afterTextChanged(Editable e) {}
        };
        price.addTextChangedListener(watcher);
        quantity.addTextChangedListener(watcher);
        received.addTextChangedListener(watcher);
        calculate();
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != 41 || resultCode != RESULT_OK || data == null) return;
        long centavos = data.getLongExtra("centavos", -1);
        if (centavos < 0 || centavos > 99_999_999_999L) return;
        selectedServiceId = data.getStringExtra("serviceId");
        price.setText(java.math.BigDecimal.valueOf(centavos, 2).toPlainString());
        calculate();
        Toast.makeText(this, "Service price copied to editable quotation", Toast.LENGTH_SHORT).show();
    }
    private TextView label(LinearLayout root, String text, int size) {
        TextView v = new TextView(this);
        v.setText(text); v.setTextSize(size); v.setTextColor(Color.rgb(27,39,58));
        v.setPadding(0, dp(8), 0, dp(8));
        root.addView(v, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return v;
    }
    private EditText field(LinearLayout root, String name, int id, boolean decimal) {
        TextView label = label(root, name, 16);
        label.setLabelFor(id);
        EditText v = new EditText(this);
        v.setId(id);
        v.setSingleLine(true);
        v.setTextSize(22);
        v.setMinHeight(dp(56));
        v.setInputType(InputType.TYPE_CLASS_NUMBER | (decimal ? InputType.TYPE_NUMBER_FLAG_DECIMAL : 0));
        root.addView(v, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return v;
    }
    private void calculate() {
        updateMaterialEstimate();
        change.setText("Change —");
        try {
            long amount = QuoteMath.total(QuoteMath.parseCentavos(price.getText().toString()), Integer.parseInt(quantity.getText().toString()));
            total.setText("Total: " + QuoteMath.display(amount));
            if (received.length() != 0) {
                try { change.setText("Change: " + QuoteMath.display(QuoteMath.change(amount, QuoteMath.parseCentavos(received.getText().toString())))); }
                catch (IllegalArgumentException ex) { change.setText(ex.getMessage()); }
            }
        } catch (IllegalArgumentException ex) { total.setText("Total — enter valid price and quantity"); }
    }
    private void updateMaterialEstimate() {
        if (selectedServiceId == null) {
            linkedService.setText("No service linked");
            materialCost.setText("Material cost — select a service");
            return;
        }
        try {
            linkedService.setText("Linked service — unavailable");
            CatalogEntry selected = store.get(selectedServiceId);
            if (selected.archived || selected.kind != CatalogEntry.Kind.SERVICE) {
                materialCost.setText("Material cost — selected service unavailable");
                return;
            }
            linkedService.setText("Linked service: " + selected.name + " (editable selling price)");
            int count = Integer.parseInt(quantity.getText().toString());
            QuoteMath.total(0, count);
            MaterialCost.Estimate estimate = store.estimateMaterials(selectedServiceId, count);
            materialCost.setText(estimate.componentCount == 0
                ? "Material cost — no materials configured"
                : "Material cost: " + QuoteMath.display(estimate.costCentavos)
                    + (estimate.stockSufficient ? " · Stock sufficient" : " · Insufficient stock"));
        } catch (NumberFormatException ex) {
            materialCost.setText("Material cost — enter valid quantity");
        } catch (RuntimeException ex) {
            materialCost.setText("Material cost — estimate unavailable");
        }
    }
    @Override protected void onResume() {
        super.onResume();
        if (materialCost != null) calculate();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("serviceId", selectedServiceId);
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        if (store != null) store.close();
        super.onDestroy();
    }
}
