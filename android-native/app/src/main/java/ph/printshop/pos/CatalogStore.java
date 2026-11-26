package ph.printshop.pos;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

/** App-private preview catalog. No cross-platform import or sales. */
public final class CatalogStore extends SQLiteOpenHelper {
    public CatalogStore(Context context) { this(context, "preview-catalog.db"); }
    CatalogStore(Context context, String filename) { super(context, filename, null, 1); }
    @Override public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE catalog (id TEXT PRIMARY KEY NOT NULL, kind TEXT NOT NULL CHECK(kind IN ('SERVICE','MATERIAL')), name TEXT NOT NULL, unit TEXT NOT NULL, centavos INTEGER NOT NULL CHECK(centavos BETWEEN 0 AND 99999999999), revision INTEGER NOT NULL CHECK(revision >= 1), archived INTEGER NOT NULL CHECK(archived IN (0,1)))");
        db.execSQL("CREATE TABLE catalog_history (id TEXT NOT NULL REFERENCES catalog(id), revision INTEGER NOT NULL, kind TEXT NOT NULL, name TEXT NOT NULL, unit TEXT NOT NULL, centavos INTEGER NOT NULL, archived INTEGER NOT NULL, action TEXT NOT NULL CHECK(action IN ('CREATE','EDIT','ARCHIVE','RESTORE')), PRIMARY KEY(id,revision))");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("No migration path; existing catalog preserved");
    }
    public CatalogEntry create(CatalogEntry.Kind kind, String name, String unit, String price) {
        CatalogEntry entry = CatalogEntry.create(kind, name, unit, price);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.insertOrThrow("catalog", null, values(entry));
            history(db, entry, "CREATE");
            db.setTransactionSuccessful();
            return entry;
        } finally { db.endTransaction(); }
    }
    public CatalogEntry edit(String id, long expectedRevision, String name, String unit, String price) {
        return mutate(id, expectedRevision, current -> current.edit(name, unit, price), "EDIT");
    }
    public CatalogEntry archive(String id, long expectedRevision, boolean archived) {
        return mutate(id, expectedRevision, current -> current.archive(archived), archived ? "ARCHIVE" : "RESTORE");
    }
    private interface Change { CatalogEntry apply(CatalogEntry current); }
    private CatalogEntry mutate(String id, long expectedRevision, Change change, String action) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            CatalogEntry current = read(db, id);
            if (current.revision != expectedRevision) throw new IllegalStateException("Catalog entry changed; reload before editing");
            CatalogEntry next = change.apply(current);
            int rows = db.update("catalog", values(next), "id=? AND revision=?", new String[]{id, Long.toString(expectedRevision)});
            if (rows != 1) throw new IllegalStateException("Catalog entry changed; reload before editing");
            history(db, next, action);
            db.setTransactionSuccessful();
            return next;
        } finally { db.endTransaction(); }
    }
    public List<CatalogEntry> list(boolean includeArchived) {
        List<CatalogEntry> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("catalog", null, includeArchived ? null : "archived=0", null, null, null, "kind,name COLLATE NOCASE,id")) {
            while (cursor.moveToNext()) result.add(entry(cursor));
        }
        return result;
    }
    public CatalogEntry get(String id) { return read(getReadableDatabase(), id); }
    private static CatalogEntry read(SQLiteDatabase db, String id) {
        try (Cursor cursor = db.query("catalog", null, "id=?", new String[]{id}, null, null, null)) {
            if (!cursor.moveToFirst()) throw new IllegalArgumentException("Catalog entry not found");
            return entry(cursor);
        }
    }
    private static CatalogEntry entry(Cursor c) {
        return new CatalogEntry(c.getString(c.getColumnIndexOrThrow("id")), CatalogEntry.Kind.valueOf(c.getString(c.getColumnIndexOrThrow("kind"))),
            c.getString(c.getColumnIndexOrThrow("name")), c.getString(c.getColumnIndexOrThrow("unit")), c.getLong(c.getColumnIndexOrThrow("centavos")),
            c.getLong(c.getColumnIndexOrThrow("revision")), c.getInt(c.getColumnIndexOrThrow("archived")) == 1);
    }
    private static ContentValues values(CatalogEntry entry) {
        ContentValues v = new ContentValues();
        v.put("id", entry.id); v.put("kind", entry.kind.name()); v.put("name", entry.name); v.put("unit", entry.unit);
        v.put("centavos", entry.centavos); v.put("revision", entry.revision); v.put("archived", entry.archived ? 1 : 0);
        return v;
    }
    private static void history(SQLiteDatabase db, CatalogEntry entry, String action) {
        ContentValues record = values(entry);
        record.put("action", action);
        db.insertOrThrow("catalog_history", null, record);
    }
}
