package ph.printshop.pos;

import android.app.Activity;
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
    private TextView total, change;
    private int dp(int x) { return Math.round(x * getResources().getDisplayMetrics().density); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
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
        price = field(root, "Unit price (PHP)", 1001, true);
        quantity = field(root, "Quantity", 1002, false);
        received = field(root, "Amount received (optional)", 1003, true);
        total = label(root, "Total —", 24);
        change = label(root, "Change —", 20);
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
}
