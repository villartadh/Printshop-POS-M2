package ph.printshop.pos;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

/** Exercises the real installed native activity, not a WebView or mock. */
public final class NativeQuoteSmoke extends Instrumentation {
    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        start();
    }
    @Override public void onStart() {
        Bundle evidence = new Bundle();
        Activity activity = null;
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName(getTargetContext().getPackageName(), MainActivity.class.getName());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity = startActivitySync(intent);
            final Activity screen = activity;
            runOnMainSync(() -> {
                EditText price = screen.findViewById(R.id.quote_price);
                EditText quantity = screen.findViewById(R.id.quote_quantity);
                EditText received = screen.findViewById(R.id.quote_received);
                require(price != null && quantity != null && received != null, "native input widgets missing");
                View root = screen.getWindow().getDecorView();
                require(hasText(root, "PREVIEW ONLY — NOT A SALE OR TAX DOCUMENT"), "preview warning missing");
                price.setText("1.99");
                quantity.setText("3");
                received.setText("10.00");
                require(hasText(root, "Total: PHP 5.97"), "total mismatch");
                require(hasText(root, "Change: PHP 4.03"), "change mismatch");
                received.setText("5.96");
                require(hasText(root, "Insufficient cash."), "underpayment not rejected");
                price.setText("1.999");
                require(hasText(root, "Total — enter valid price and quantity"), "fractional centavo accepted");
                require(hasText(root, "Change —"), "stale change");
                price.setText("0.01");
                quantity.setText("1");
                received.setText("0.01");
                require(hasText(root, "Total: PHP 0.01"), "centavo total mismatch");
                require(hasText(root, "Change: PHP 0.00"), "exact tender mismatch");
            });
            verifyCatalog();
            verifyCatalogScreen(screen);
            verifyStockLedger();
            evidence.putString("stream", "NATIVE_QUOTE_SMOKE_PASS\nNATIVE_CATALOG_STORE_PASS\nNATIVE_CATALOG_SCREEN_PASS\nNATIVE_STOCK_LEDGER_PASS\n");
            finish(Activity.RESULT_OK, evidence);
        } catch (Throwable error) {
            evidence.putString("stream", "NATIVE_QUOTE_SMOKE_FAIL: " + error + "\n");
            finish(Activity.RESULT_CANCELED, evidence);
        } finally {
            if (activity != null) {
                final Activity screen = activity;
                runOnMainSync(screen::finish);
            }
        }
    }
    private static void rejectStock(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("invalid stock operation accepted");
    }
    private void verifyStockLedger() {
        android.content.Context context = getTargetContext();
        String filename = "stock-migration-" + java.util.UUID.randomUUID() + ".db";
        String id = java.util.UUID.randomUUID().toString();
        String request = java.util.UUID.randomUUID().toString();
        try {
            // Fixture uses the exact former v1 schema, not a destructive reset.
            try (android.database.sqlite.SQLiteDatabase db = context.openOrCreateDatabase(filename, 0, null)) {
        db.execSQL("CREATE TABLE catalog (id TEXT PRIMARY KEY NOT NULL, kind TEXT NOT NULL CHECK(kind IN ('SERVICE','MATERIAL')), name TEXT NOT NULL, unit TEXT NOT NULL, centavos INTEGER NOT NULL CHECK(centavos BETWEEN 0 AND 99999999999), revision INTEGER NOT NULL CHECK(revision >= 1), archived INTEGER NOT NULL CHECK(archived IN (0,1)))");
        db.execSQL("CREATE TABLE catalog_history (id TEXT NOT NULL REFERENCES catalog(id), revision INTEGER NOT NULL, kind TEXT NOT NULL, name TEXT NOT NULL, unit TEXT NOT NULL, centavos INTEGER NOT NULL, archived INTEGER NOT NULL, action TEXT NOT NULL CHECK(action IN ('CREATE','EDIT','ARCHIVE','RESTORE')), PRIMARY KEY(id,revision))");
                db.execSQL("INSERT INTO catalog VALUES(?,?,?,?,?,?,?)", new Object[]{id,"MATERIAL","Synthetic paper","sheet",199,1,0});
                db.execSQL("INSERT INTO catalog_history VALUES(?,?,?,?,?,?,?,?)", new Object[]{id,1,"MATERIAL","Synthetic paper","sheet",199,0,"CREATE"});
                db.setVersion(1);
            }
            try (CatalogStore store = new CatalogStore(context,filename)) {
                require(store.get(id).centavos == 199 && store.get(id).revision == 1, "v1 catalog migration changed record");
                require(store.stockBalance(id).quantity == 0 && store.stockBalance(id).revision == 0, "initial stock wrong");
                StockMath.Balance added = store.moveStock(request,id,1,0,1500,"Synthetic receipt");
                require(added.quantity == 1500 && added.revision == 1, "stock receipt wrong");
                require(store.moveStock(request,id,1,0,1500,"Synthetic receipt").revision == 1, "exact replay wrong");
                rejectStock(() -> store.moveStock(request,id,1,0,1501,"Synthetic receipt"));
                rejectStock(() -> store.moveStock(java.util.UUID.randomUUID().toString(),id,1,0,1,"Stale"));
                rejectStock(() -> store.moveStock(java.util.UUID.randomUUID().toString(),id,1,1,-1501,"Overdraw"));
                rejectStock(() -> store.archive(id,1,true));
                rejectStock(() -> store.edit(id,1,"Synthetic paper","ream","1.99"));
                require(store.stockBalance(id).quantity == 1500 && store.stockBalance(id).revision == 1, "rejected movement changed stock");
                store.moveStock(java.util.UUID.randomUUID().toString(),id,1,1,-1500,"Synthetic usage");
                store.archive(id,1,true);
                require(store.moveStock(request,id,1,0,1500,"Synthetic receipt").quantity == 1500, "historical replay after archive wrong");
                require(store.stockBalance(id).quantity == 0 && store.stockBalance(id).revision == 2, "historical replay changed current balance");
            }
            try (CatalogStore reopened = new CatalogStore(context,filename)) {
                require(reopened.get(id).archived && reopened.stockBalance(id).revision == 2, "stock persistence wrong");
                try (android.database.Cursor c = reopened.getReadableDatabase().rawQuery("SELECT count(*) FROM stock_movements WHERE material_id=?",new String[]{id})) {
                    require(c.moveToFirst() && c.getInt(0)==2,"stock movement history count wrong");
                }
                try (android.database.Cursor c = reopened.getReadableDatabase().rawQuery("SELECT count(*) FROM catalog_history WHERE id=?",new String[]{id})) {
                    require(c.moveToFirst() && c.getInt(0)==2,"catalog migration history count wrong");
                }
            }
        } finally { context.deleteDatabase(filename); }
    }
    private void verifyCatalogScreen(Activity screen) {
        android.content.Context context = getTargetContext();
        try (CatalogStore store = new CatalogStore(context)) {
            require(store.list(true).isEmpty(), "catalog UI test requires a fresh preview installation");
            CatalogEntry fixture = store.create(CatalogEntry.Kind.SERVICE, "Synthetic service", "page", "3.25");
            Activity catalog = null;
            ActivityMonitor monitor = addMonitor(CatalogActivity.class.getName(), null, false);
            try {
                runOnMainSync(() -> screen.findViewById(R.id.open_catalog).performClick());
                catalog = waitForMonitorWithTimeout(monitor, 10000);
                require(catalog != null, "catalog Activity did not open");
                final Activity shown = catalog;
                runOnMainSync(() -> {
                    View root = shown.getWindow().getDecorView();
                    require(hasText(root, "Services & Materials"), "catalog heading missing");
                    require(hasText(root, "Synthetic service · SERVICE"), "catalog fixture missing");
                    require(hasText(root, "PHP 3.25 / page · Revision 1"), "catalog unit price missing");
                    View choice = findText(root, "Use in quotation");
                    require(choice != null && choice.performClick(), "service selection failed");
                });
                waitForIdleSync();
                runOnMainSync(() -> {
                    EditText price = screen.findViewById(R.id.quote_price);
                    require(price.getText().toString().equals("3.25"), "catalog quotation price not copied");
                });
            } finally {
                removeMonitor(monitor);
                if (catalog != null) {
                    final Activity shown = catalog;
                    runOnMainSync(shown::finish);
                }
                CatalogEntry current = store.get(fixture.id);
                if (!current.archived) store.archive(current.id, current.revision, true);
            }
        }
    }
    private static View findText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView)view).getText())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i=0; i<group.getChildCount(); i++) {
                View match = findText(group.getChildAt(i), text);
                if (match != null) return match;
            }
        }
        return null;
    }
    private void verifyCatalog() {
        android.content.Context context = getTargetContext();
        String filename = "catalog-test-" + java.util.UUID.randomUUID() + ".db";
        try {
            String id;
            try (CatalogStore store = new CatalogStore(context, filename)) {
                CatalogEntry entry = store.create(CatalogEntry.Kind.SERVICE, "Synthetic print", "page", "1.99");
                id = entry.id;
                require(store.list(false).size() == 1, "catalog create missing");
                CatalogEntry edited = store.edit(id, 1, "Synthetic color", "page", "2.01");
                require(edited.revision == 2 && edited.centavos == 201, "catalog update mismatch");
                try {
                    store.edit(id, 1, "Stale", "page", "1");
                    throw new AssertionError("stale catalog edit accepted");
                } catch (IllegalStateException expected) { }
                require(store.get(id).revision == 2, "stale edit changed record");
                store.archive(id, 2, true);
                require(store.list(false).isEmpty() && store.list(true).size() == 1, "archive visibility mismatch");
            }
            try (CatalogStore reopened = new CatalogStore(context, filename)) {
                CatalogEntry archived = reopened.get(id);
                require(archived.archived && archived.revision == 3 && archived.centavos == 201, "catalog persistence mismatch");
                reopened.archive(id, 3, false);
                CatalogEntry restored = reopened.get(id);
                require(!restored.archived && restored.revision == 4 && restored.name.equals("Synthetic color"), "restore mismatch");
                try (android.database.Cursor cursor = reopened.getReadableDatabase().rawQuery("SELECT count(*) FROM catalog_history WHERE id=?", new String[]{id})) {
                    require(cursor.moveToFirst() && cursor.getInt(0) == 4, "catalog history mismatch");
                }
            }
        } finally {
            context.deleteDatabase(filename);
        }
    }
    private static boolean hasText(View view, String value) {
        if (view instanceof TextView && value.contentEquals(((TextView)view).getText())) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); i++) if (hasText(group.getChildAt(i), value)) return true;
        }
        return false;
    }
    private static void require(boolean ok, String description) {
        if (!ok) throw new AssertionError(description);
    }
}
