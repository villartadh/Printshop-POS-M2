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
    CatalogStore(Context context, String filename) { super(context, filename, null, 3); }
    @Override public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE catalog (id TEXT PRIMARY KEY NOT NULL, kind TEXT NOT NULL CHECK(kind IN ('SERVICE','MATERIAL')), name TEXT NOT NULL, unit TEXT NOT NULL, centavos INTEGER NOT NULL CHECK(centavos BETWEEN 0 AND 99999999999), revision INTEGER NOT NULL CHECK(revision >= 1), archived INTEGER NOT NULL CHECK(archived IN (0,1)))");
        db.execSQL("CREATE TABLE catalog_history (id TEXT NOT NULL REFERENCES catalog(id), revision INTEGER NOT NULL, kind TEXT NOT NULL, name TEXT NOT NULL, unit TEXT NOT NULL, centavos INTEGER NOT NULL, archived INTEGER NOT NULL, action TEXT NOT NULL CHECK(action IN ('CREATE','EDIT','ARCHIVE','RESTORE')), PRIMARY KEY(id,revision))");
        createStockLedger(db);
        createRecipeSchema(db);
    }
    private static void createStockLedger(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE stock_movements (request_id TEXT PRIMARY KEY NOT NULL, material_id TEXT NOT NULL REFERENCES catalog(id), catalog_revision INTEGER NOT NULL CHECK(catalog_revision>=1), stock_revision INTEGER NOT NULL CHECK(stock_revision>=1), unit TEXT NOT NULL, reason TEXT NOT NULL, delta INTEGER NOT NULL CHECK(delta BETWEEN -999999999999 AND 999999999999 AND delta<>0), before_quantity INTEGER NOT NULL CHECK(before_quantity BETWEEN 0 AND 999999999999), after_quantity INTEGER NOT NULL CHECK(after_quantity BETWEEN 0 AND 999999999999 AND after_quantity=before_quantity+delta), created_ms INTEGER NOT NULL, UNIQUE(material_id,stock_revision))");
    }
    private static void createRecipeSchema(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE service_materials (service_id TEXT NOT NULL REFERENCES catalog(id), material_id TEXT NOT NULL REFERENCES catalog(id), quantity_milli INTEGER NOT NULL CHECK(quantity_milli BETWEEN 1 AND 999999999999), unit TEXT NOT NULL, PRIMARY KEY(service_id,material_id))");
        db.execSQL("CREATE TABLE recipe_history (service_id TEXT NOT NULL REFERENCES catalog(id), revision INTEGER NOT NULL CHECK(revision>=1), material_id TEXT NOT NULL REFERENCES catalog(id), quantity_milli INTEGER NOT NULL CHECK(quantity_milli BETWEEN 0 AND 999999999999), unit TEXT NOT NULL, material_revision INTEGER NOT NULL, service_revision INTEGER NOT NULL, created_ms INTEGER NOT NULL, PRIMARY KEY(service_id,revision))");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (newVersion == 3 && (oldVersion == 1 || oldVersion == 2)) {
            if (oldVersion == 1) createStockLedger(db);
            createRecipeSchema(db); return;
        }
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
            if (current.kind == CatalogEntry.Kind.MATERIAL) {
                StockMath.Balance stock = stockBalance(db, id);
                if (stock.revision > 0 && !current.unit.equals(next.unit)) throw new IllegalStateException("Material unit cannot change after stock movement history exists");
                if (!current.unit.equals(next.unit) && hasReference(db,"recipe_history",id)) throw new IllegalStateException("Material unit cannot change after recipe history exists");
                if (next.archived && hasReference(db,"service_materials",id)) throw new IllegalStateException("Remove material from linked service recipes before archiving");
                if (next.archived && stock.quantity != 0) throw new IllegalStateException("Reduce material stock to zero with a reason before archiving");
            }
            int rows = db.update("catalog", values(next), "id=? AND revision=?", new String[]{id, Long.toString(expectedRevision)});
            if (rows != 1) throw new IllegalStateException("Catalog entry changed; reload before editing");
            history(db, next, action);
            db.setTransactionSuccessful();
            return next;
        } finally { db.endTransaction(); }
    }
    private static boolean hasReference(SQLiteDatabase db,String table,String materialId) {
        try(Cursor c=db.query(table,new String[]{"material_id"},"material_id=?",new String[]{materialId},null,null,null,"1")) {return c.moveToFirst();}
    }
    private static long recipeRevision(SQLiteDatabase db,String serviceId) {
        try(Cursor c=db.rawQuery("SELECT COALESCE(MAX(revision),0) FROM recipe_history WHERE service_id=?",new String[]{serviceId})) {
            if(!c.moveToFirst())throw new IllegalStateException("Cannot read recipe revision");
            return c.getLong(0);
        }
    }
    public long recipeRevision(String serviceId) {
        SQLiteDatabase db=getReadableDatabase();
        if(read(db,serviceId).kind!=CatalogEntry.Kind.SERVICE)throw new IllegalArgumentException("Not a service");
        return recipeRevision(db,serviceId);
    }
    public long setMaterialUsage(String serviceId,long serviceRevision,String materialId,long materialRevision,long expectedRecipeRevision,long quantityMilli) {
        if(quantityMilli<0||quantityMilli>StockMath.MAX)throw new IllegalArgumentException("Invalid recipe quantity");
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try {
            CatalogEntry service=read(db,serviceId), material=read(db,materialId);
            if(service.kind!=CatalogEntry.Kind.SERVICE||material.kind!=CatalogEntry.Kind.MATERIAL||service.archived||material.archived)
                throw new IllegalStateException("Only active services and materials may be linked");
            if(service.revision!=serviceRevision||material.revision!=materialRevision||recipeRevision(db,serviceId)!=expectedRecipeRevision)
                throw new IllegalStateException("Catalog or recipe changed; reload before editing");
            String[] pair={serviceId,materialId};
            long prior=0;
            try(Cursor c=db.query("service_materials",new String[]{"quantity_milli"},"service_id=? AND material_id=?",pair,null,null,null)) {
                if(c.moveToFirst())prior=c.getLong(0);
            }
            if(prior==quantityMilli)throw new IllegalArgumentException("No recipe change");
            if(prior==0 && quantityMilli>0) {
                try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM service_materials WHERE service_id=?",new String[]{serviceId})) {
                    if(!c.moveToFirst()||c.getInt(0)>=100)throw new IllegalArgumentException("Maximum 100 material bindings");
                }
            }
            if(quantityMilli==0) {
                if(db.delete("service_materials","service_id=? AND material_id=?",pair)!=1)throw new IllegalStateException("Recipe changed");
            } else {
                ContentValues binding=new ContentValues();
                binding.put("service_id",serviceId);binding.put("material_id",materialId);binding.put("quantity_milli",quantityMilli);binding.put("unit",material.unit);
                if(prior==0)db.insertOrThrow("service_materials",null,binding);
                else if(db.update("service_materials",binding,"service_id=? AND material_id=?",pair)!=1)throw new IllegalStateException("Recipe changed");
            }
            long revision=Math.addExact(expectedRecipeRevision,1);
            ContentValues log=new ContentValues();
            log.put("service_id",serviceId);log.put("material_id",materialId);log.put("revision",revision);log.put("quantity_milli",quantityMilli);log.put("unit",material.unit);
            log.put("material_revision",materialRevision);log.put("service_revision",serviceRevision);log.put("created_ms",System.currentTimeMillis());
            db.insertOrThrow("recipe_history",null,log);
            db.setTransactionSuccessful();return revision;
        } finally {db.endTransaction();}
    }
    public MaterialCost.Estimate estimateMaterials(String serviceId,int quantity) {
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransactionNonExclusive();
        try {
            CatalogEntry service=read(db,serviceId);
            if(service.kind!=CatalogEntry.Kind.SERVICE||service.archived)throw new IllegalArgumentException("Select an active service");
            List<MaterialCost.Component> components=new ArrayList<>();
            try(Cursor c=db.query("service_materials",new String[]{"material_id","quantity_milli","unit"},"service_id=?",new String[]{serviceId},null,null,"material_id")) {
                while(c.moveToNext()) {
                    CatalogEntry material=read(db,c.getString(0));
                    if(material.archived||material.kind!=CatalogEntry.Kind.MATERIAL||!material.unit.equals(c.getString(2)))
                        throw new IllegalStateException("Recipe contains unavailable material");
                    components.add(new MaterialCost.Component(material.centavos,c.getLong(1),stockBalance(db,material.id).quantity));
                }
            }
            MaterialCost.Estimate result=MaterialCost.estimate(components,quantity);
            db.setTransactionSuccessful();return result;
        } finally {db.endTransaction();}
    }
    public StockMath.Balance stockBalance(String id) {
        SQLiteDatabase db = getReadableDatabase();
        if (read(db,id).kind != CatalogEntry.Kind.MATERIAL) throw new IllegalArgumentException("Stock is available only for materials");
        return stockBalance(db,id);
    }
    private static StockMath.Balance stockBalance(SQLiteDatabase db,String id) {
        try (Cursor c = db.query("stock_movements",new String[]{"after_quantity","stock_revision"},"material_id=?",new String[]{id},null,null,"stock_revision DESC","1")) {
            return c.moveToFirst() ? new StockMath.Balance(c.getLong(0),c.getLong(1)) : new StockMath.Balance(0,0);
        }
    }
    public StockMath.Balance moveStock(String requestId,String id,long catalogRevision,long expectedStockRevision,long delta,String note) {
        StockMath.validateRequestId(requestId);
        String reason = StockMath.reason(note);
        if (catalogRevision < 1 || expectedStockRevision < 0 || expectedStockRevision == Long.MAX_VALUE)
            throw new IllegalArgumentException("Invalid movement revision.");
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            try (Cursor c = db.query("stock_movements",new String[]{"material_id","catalog_revision","stock_revision","delta","reason","after_quantity"},"request_id=?",new String[]{requestId},null,null,null)) {
                if (c.moveToFirst()) {
                    if (!id.equals(c.getString(0)) || catalogRevision != c.getLong(1) || expectedStockRevision+1 != c.getLong(2) || delta != c.getLong(3) || !reason.equals(c.getString(4)))
                        throw new IllegalStateException("Movement identity already used for different request");
                    StockMath.Balance original = new StockMath.Balance(c.getLong(5),c.getLong(2));
                    db.setTransactionSuccessful();
                    return original;
                }
            }
            CatalogEntry material = read(db,id);
            if (material.kind != CatalogEntry.Kind.MATERIAL || material.archived) throw new IllegalStateException("Only active materials can have stock movements");
            if (material.revision != catalogRevision) throw new IllegalStateException("Material changed; reload before adjusting stock");
            StockMath.Balance before = stockBalance(db,id);
            if (before.revision != expectedStockRevision) throw new IllegalStateException("Stock changed; reload before adjusting");
            long after = StockMath.apply(before.quantity,delta);
            long revision = Math.addExact(before.revision,1);
            ContentValues values = new ContentValues();
            values.put("request_id",requestId); values.put("material_id",id); values.put("catalog_revision",catalogRevision);
            values.put("stock_revision",revision); values.put("unit",material.unit); values.put("reason",reason); values.put("delta",delta);
            values.put("before_quantity",before.quantity); values.put("after_quantity",after); values.put("created_ms",System.currentTimeMillis());
            db.insertOrThrow("stock_movements",null,values);
            db.setTransactionSuccessful();
            return new StockMath.Balance(after,revision);
        } finally { db.endTransaction(); }
    }
    public static final class StockMovement {
        public final long revision, delta, before, after, createdMs;
        public final String reason, unit;
        StockMovement(long revision,long delta,long before,long after,long createdMs,String reason,String unit) {
            this.revision=revision; this.delta=delta; this.before=before; this.after=after; this.createdMs=createdMs; this.reason=reason; this.unit=unit;
        }
    }
    public List<StockMovement> stockHistory(String id,long beforeRevision,int limit) {
        if (beforeRevision<0 || limit<1 || limit>50) throw new IllegalArgumentException("Invalid history page.");
        SQLiteDatabase db=getReadableDatabase();
        if (read(db,id).kind!=CatalogEntry.Kind.MATERIAL) throw new IllegalArgumentException("Stock history is only available for materials.");
        List<StockMovement> result=new ArrayList<>();
        String selection=beforeRevision==0?"material_id=?":"material_id=? AND stock_revision<?";
        String[] args=beforeRevision==0?new String[]{id}:new String[]{id,Long.toString(beforeRevision)};
        try (Cursor c=db.query("stock_movements",new String[]{"stock_revision","delta","before_quantity","after_quantity","created_ms","reason","unit"},selection,args,null,null,"stock_revision DESC",Integer.toString(limit))) {
            while(c.moveToNext()) result.add(new StockMovement(c.getLong(0),c.getLong(1),c.getLong(2),c.getLong(3),c.getLong(4),c.getString(5),c.getString(6)));
        }
        return result;
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
