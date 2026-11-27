package ph.printshop.pos;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.math.BigDecimal;

/** Native preview catalog; records are local, not live stock or sales. */
public final class CatalogActivity extends Activity {
    private CatalogStore store;
    private LinearLayout rows;
    private CheckBox archived;
    private int dp(int x) { return Math.round(x * getResources().getDisplayMetrics().density); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new CatalogStore(this);
        LinearLayout root = column();
        root.setPadding(dp(16), dp(12), dp(16), dp(12));
        setContentView(root);
        label(root, "Services & Materials", 24);
        label(root, "LOCAL PREVIEW — no live inventory or sales", 14);
        LinearLayout actions = new LinearLayout(this);
        root.addView(actions);
        button(actions, "New service", () -> editor(CatalogEntry.Kind.SERVICE, null));
        button(actions, "New material", () -> editor(CatalogEntry.Kind.MATERIAL, null));
        archived = new CheckBox(this);
        archived.setText("Show archived");
        root.addView(archived);
        archived.setOnCheckedChangeListener((view, checked) -> refresh());
        ScrollView scroll = new ScrollView(this);
        rows = column();
        scroll.addView(rows);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        refresh();
    }
    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }
    private void label(LinearLayout parent, String text, int size) {
        TextView view = new TextView(this);
        view.setText(text); view.setTextSize(size); view.setPadding(0, dp(8), 0, dp(4));
        parent.addView(view);
    }
    private void button(LinearLayout parent, String text, Runnable action) {
        Button button = new Button(this);
        button.setText(text); button.setMinHeight(dp(48));
        if (parent.getOrientation() == LinearLayout.HORIZONTAL) parent.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
        else parent.addView(button, new LinearLayout.LayoutParams(-1, -2));
        button.setOnClickListener(v -> action.run());
    }
    private void refresh() {
        rows.removeAllViews();
        try {
            java.util.List<CatalogEntry> entries = store.list(archived.isChecked());
            if (entries.isEmpty()) label(rows, "No catalog entries. Add a service or material to begin.", 16);
            for (CatalogEntry entry : entries) {
                label(rows, entry.name + " · " + entry.kind.name() + (entry.archived ? " · Archived" : ""), 18);
                label(rows, QuoteMath.display(entry.centavos) + " / " + entry.unit + " · Revision " + entry.revision, 15);
                LinearLayout actions = new LinearLayout(this);
                rows.addView(actions);
                if (!entry.archived) button(actions, "Edit", () -> editor(entry.kind, entry));
                button(actions, entry.archived ? "Restore" : "Archive", () -> {
                    new AlertDialog.Builder(this).setMessage((entry.archived ? "Restore " : "Archive ") + entry.name + "?")
                        .setNegativeButton("Cancel", null).setPositiveButton("Confirm", (d, w) -> {
                            try { store.archive(entry.id, entry.revision, !entry.archived); refresh(); }
                            catch (RuntimeException ex) { error(ex); refresh(); }
                        }).show();
                });
                if (entry.kind == CatalogEntry.Kind.MATERIAL) {
                    StockMath.Balance stock = store.stockBalance(entry.id);
                    label(rows, "On hand: " + StockMath.display(stock.quantity) + " " + entry.unit, 16);
                    button(rows, "Open stock movements", () -> {
                        Intent intent = new Intent(this, StockActivity.class);
                        intent.putExtra("materialId", entry.id);
                        startActivity(intent);
                    });
                }
                if (!entry.archived && entry.kind == CatalogEntry.Kind.SERVICE) {
                    PricingProfile profile=store.pricing(entry.id);
                    label(rows,"Pricing: "+PricingProfile.label(profile.method)+" · "+profile.tiers.size()+" tier(s)",15);
                    button(rows,"Configure service pricing",()->{
                        Intent intent=new Intent(this,PricingSettingsActivity.class);
                        intent.putExtra("serviceId",entry.id);
                        startActivity(intent);
                    });
                    button(rows, "Configure service materials", () -> {
                        Intent intent=new Intent(this,RecipeActivity.class);
                        intent.putExtra("serviceId",entry.id);
                        startActivity(intent);
                    });
                    button(rows, "Use in quotation", () -> {
                        try {
                            CatalogEntry current = store.get(entry.id);
                            if (current.archived || current.revision != entry.revision) throw new IllegalStateException("Service changed; reload and select again");
                            PricingProfile currentProfile=store.pricing(current.id);
                            if(currentProfile.revision!=profile.revision)throw new IllegalStateException("Pricing settings changed; reload and select again");
                            Intent result = new Intent();
                            result.putExtra("pricingRevision",currentProfile.revision);
                            result.putExtra("centavos", current.centavos);
                            result.putExtra("serviceName", current.name);
                            result.putExtra("serviceId", current.id);
                            setResult(RESULT_OK, result);
                            finish();
                        } catch (RuntimeException ex) { error(ex); refresh(); }
                    });
                }
            }
        } catch (RuntimeException ex) { label(rows, "Catalog unavailable. No data was deleted.", 16); error(ex); }
    }
    private void editor(CatalogEntry.Kind kind, CatalogEntry entry) {
        LinearLayout fields = column();
        fields.setPadding(dp(20), 0, dp(20), 0);
        EditText name = field(fields, "Name", false, entry == null ? "" : entry.name);
        EditText unit = field(fields, "Unit (e.g. page)", false, entry == null ? "" : entry.unit);
        EditText price = field(fields, kind == CatalogEntry.Kind.MATERIAL ? "Material cost (PHP)" : "Service price (PHP)", true,
            entry == null ? "" : BigDecimal.valueOf(entry.centavos, 2).toPlainString());
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(entry == null ? "New " + kind.name().toLowerCase(java.util.Locale.ROOT) : "Edit " + entry.name)
            .setView(fields).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                if (entry == null) store.create(kind, name.getText().toString(), unit.getText().toString(), price.getText().toString());
                else store.edit(entry.id, entry.revision, name.getText().toString(), unit.getText().toString(), price.getText().toString());
                dialog.dismiss(); refresh();
            } catch (RuntimeException ex) { error(ex); }
        }));
        dialog.show();
    }
    private EditText field(LinearLayout parent, String hint, boolean money, String value) {
        EditText view = new EditText(this);
        view.setHint(hint); view.setSingleLine(true); view.setMinHeight(dp(48));
        view.setInputType(money ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        view.setText(value); parent.addView(view);
        return view;
    }
    private void error(RuntimeException ex) {
        String message = ex instanceof IllegalArgumentException || ex instanceof IllegalStateException ? ex.getMessage() : "Catalog operation failed; no confirmation was recorded.";
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
    @Override protected void onResume() {
        super.onResume();
        if (rows != null) refresh();
    }
    @Override protected void onDestroy() {
        if (store != null) store.close();
        super.onDestroy();
    }
}
