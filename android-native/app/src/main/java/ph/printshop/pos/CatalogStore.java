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
    CatalogStore(Context context, String filename) { super(context, filename, null, 5); }
    @Override public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE catalog (id TEXT PRIMARY KEY NOT NULL, kind TEXT NOT NULL CHECK(kind IN ('SERVICE','MATERIAL')), name TEXT NOT NULL, unit TEXT NOT NULL, centavos INTEGER NOT NULL CHECK(centavos BETWEEN 0 AND 99999999999), revision INTEGER NOT NULL CHECK(revision >= 1), archived INTEGER NOT NULL CHECK(archived IN (0,1)))");
        db.execSQL("CREATE TABLE catalog_history (id TEXT NOT NULL REFERENCES catalog(id), revision INTEGER NOT NULL, kind TEXT NOT NULL, name TEXT NOT NULL, unit TEXT NOT NULL, centavos INTEGER NOT NULL, archived INTEGER NOT NULL, action TEXT NOT NULL CHECK(action IN ('CREATE','EDIT','ARCHIVE','RESTORE')), PRIMARY KEY(id,revision))");
        createStockLedger(db);
        createRecipeSchema(db);
        createPricingSchema(db);
        createStockRequests(db);
    }
    private static void createStockLedger(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE stock_movements (request_id TEXT PRIMARY KEY NOT NULL, material_id TEXT NOT NULL REFERENCES catalog(id), catalog_revision INTEGER NOT NULL CHECK(catalog_revision>=1), stock_revision INTEGER NOT NULL CHECK(stock_revision>=1), unit TEXT NOT NULL, reason TEXT NOT NULL, delta INTEGER NOT NULL CHECK(delta BETWEEN -999999999999 AND 999999999999 AND delta<>0), before_quantity INTEGER NOT NULL CHECK(before_quantity BETWEEN 0 AND 999999999999), after_quantity INTEGER NOT NULL CHECK(after_quantity BETWEEN 0 AND 999999999999 AND after_quantity=before_quantity+delta), created_ms INTEGER NOT NULL, UNIQUE(material_id,stock_revision))");
    }
    private static void createRecipeSchema(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE service_materials (service_id TEXT NOT NULL REFERENCES catalog(id), material_id TEXT NOT NULL REFERENCES catalog(id), quantity_milli INTEGER NOT NULL CHECK(quantity_milli BETWEEN 1 AND 999999999999), unit TEXT NOT NULL, PRIMARY KEY(service_id,material_id))");
        db.execSQL("CREATE TABLE recipe_history (service_id TEXT NOT NULL REFERENCES catalog(id), revision INTEGER NOT NULL CHECK(revision>=1), material_id TEXT NOT NULL REFERENCES catalog(id), quantity_milli INTEGER NOT NULL CHECK(quantity_milli BETWEEN 0 AND 999999999999), unit TEXT NOT NULL, material_revision INTEGER NOT NULL, service_revision INTEGER NOT NULL, created_ms INTEGER NOT NULL, PRIMARY KEY(service_id,revision))");
    }
    private static void createPricingSchema(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE pricing_profile_versions (service_id TEXT NOT NULL REFERENCES catalog(id), revision INTEGER NOT NULL CHECK(revision>=1), method TEXT NOT NULL CHECK(method IN ('FIXED','PER_UNIT','PER_PAGE','PER_COPY','PER_SHEET','PER_SQFT','PER_LINEAR_FT','PER_METER','TIERED_BULK')), pages_copies INTEGER NOT NULL CHECK(pages_copies IN (0,1) AND (pages_copies=0 OR method IN ('PER_PAGE','PER_COPY','PER_SHEET'))), service_revision INTEGER NOT NULL CHECK(service_revision>=1), catalog_unit TEXT NOT NULL, created_ms INTEGER NOT NULL, PRIMARY KEY(service_id,revision))");
        db.execSQL("CREATE TABLE pricing_tier_versions (service_id TEXT NOT NULL, profile_revision INTEGER NOT NULL, threshold_hundredths INTEGER NOT NULL CHECK(threshold_hundredths BETWEEN 1 AND 10000000), rate_centavos INTEGER NOT NULL CHECK(rate_centavos BETWEEN 0 AND 99999999999), PRIMARY KEY(service_id,profile_revision,threshold_hundredths), FOREIGN KEY(service_id,profile_revision) REFERENCES pricing_profile_versions(service_id,revision))");
    }
    private static void createStockRequests(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE stock_requests (request_id TEXT PRIMARY KEY NOT NULL, material_id TEXT NOT NULL REFERENCES catalog(id), catalog_revision INTEGER NOT NULL CHECK(catalog_revision>=1), expected_stock_revision INTEGER NOT NULL CHECK(expected_stock_revision>=0 AND expected_stock_revision<9223372036854775807), delta INTEGER NOT NULL CHECK(delta BETWEEN -999999999999 AND 999999999999 AND delta<>0), reason TEXT NOT NULL CHECK(length(reason) BETWEEN 1 AND 160), created_ms INTEGER NOT NULL, state TEXT NOT NULL CHECK(state IN ('PENDING','APPLIED','CANCELLED')), resolved_ms INTEGER, CHECK((state='PENDING' AND resolved_ms IS NULL) OR (state<>'PENDING' AND resolved_ms IS NOT NULL)))");
        db.execSQL("CREATE UNIQUE INDEX one_pending_stock_request ON stock_requests(material_id) WHERE state='PENDING'");
        db.execSQL("CREATE TRIGGER stock_request_no_delete BEFORE DELETE ON stock_requests BEGIN SELECT RAISE(ABORT,'Stock request audit cannot be deleted'); END");
        db.execSQL("CREATE TRIGGER stock_request_initial_state BEFORE INSERT ON stock_requests WHEN NEW.state<>'PENDING' BEGIN SELECT RAISE(ABORT,'Stock request must begin pending'); END");
        db.execSQL("CREATE TRIGGER stock_request_resolution BEFORE UPDATE ON stock_requests WHEN OLD.state<>'PENDING' OR NEW.state NOT IN ('APPLIED','CANCELLED') OR NEW.request_id IS NOT OLD.request_id OR NEW.material_id IS NOT OLD.material_id OR NEW.catalog_revision IS NOT OLD.catalog_revision OR NEW.expected_stock_revision IS NOT OLD.expected_stock_revision OR NEW.delta IS NOT OLD.delta OR NEW.reason IS NOT OLD.reason OR NEW.created_ms IS NOT OLD.created_ms OR (NEW.state='APPLIED' AND NOT EXISTS (SELECT 1 FROM stock_movements m WHERE m.request_id=OLD.request_id AND m.material_id=OLD.material_id AND m.catalog_revision=OLD.catalog_revision AND m.stock_revision=OLD.expected_stock_revision+1 AND m.delta=OLD.delta AND m.reason=OLD.reason)) OR (NEW.state='CANCELLED' AND EXISTS (SELECT 1 FROM stock_movements m WHERE m.request_id=OLD.request_id)) BEGIN SELECT RAISE(ABORT,'Invalid stock request transition'); END");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (newVersion == 5 && oldVersion >= 1 && oldVersion <= 4) {
            if (oldVersion < 2) createStockLedger(db);
            if (oldVersion < 3) createRecipeSchema(db);
            if (oldVersion < 4) createPricingSchema(db);
            createStockRequests(db); return;
        }
        throw new IllegalStateException("No migration path; existing catalog preserved");
    }
    private static PricingProfile readPricing(SQLiteDatabase db,String serviceId) {
        long revision;
        PricingMath.Method method;
        boolean pages;
        try (Cursor c=db.query("pricing_profile_versions",new String[]{"revision","method","pages_copies"},"service_id=?",new String[]{serviceId},null,null,"revision DESC","1")) {
            if(!c.moveToFirst())return PricingProfile.defaults();
            revision=c.getLong(0);method=PricingMath.Method.valueOf(c.getString(1));pages=c.getInt(2)==1;
        }
        List<PricingMath.Tier> tiers=new ArrayList<>();
        try(Cursor c=db.query("pricing_tier_versions",new String[]{"threshold_hundredths","rate_centavos"},"service_id=? AND profile_revision=?",new String[]{serviceId,Long.toString(revision)},null,null,"threshold_hundredths")) {
            while(c.moveToNext())tiers.add(new PricingMath.Tier(c.getLong(0),c.getLong(1)));
        }
        return new PricingProfile(revision,method,pages,tiers);
    }
    /** Immutable snapshots make an earlier configured rate reproducible without changing catalog price history. */
    public PricingProfile pricing(String serviceId) {
        SQLiteDatabase db=getReadableDatabase();
        if(read(db,serviceId).kind!=CatalogEntry.Kind.SERVICE)throw new IllegalArgumentException("Not a service");
        return readPricing(db,serviceId);
    }
    public PricingProfile savePricing(String serviceId,long serviceRevision,long expectedPricingRevision,
                                      PricingMath.Method method,boolean pagesAndCopies,List<PricingMath.Tier> tiers) {
        PricingProfile next=new PricingProfile(Math.addExact(expectedPricingRevision,1),method,pagesAndCopies,tiers);
        if(expectedPricingRevision<0)throw new IllegalArgumentException("Invalid expected pricing revision");
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try {
            CatalogEntry service=read(db,serviceId);
            if(service.kind!=CatalogEntry.Kind.SERVICE||service.archived)throw new IllegalStateException("Select an active service");
            PricingProfile prior=readPricing(db,serviceId);
            if(service.revision!=serviceRevision||prior.revision!=expectedPricingRevision)
                throw new IllegalStateException("Service or pricing changed; reload before editing");
            if(prior.sameConfiguration(next))throw new IllegalArgumentException("No pricing change");
            ContentValues profile=new ContentValues();
            profile.put("service_id",serviceId);profile.put("revision",next.revision);
            profile.put("method",next.method.name());profile.put("pages_copies",next.pagesAndCopies?1:0);
            profile.put("service_revision",serviceRevision);profile.put("catalog_unit",service.unit);
            profile.put("created_ms",System.currentTimeMillis());
            db.insertOrThrow("pricing_profile_versions",null,profile);
            for(PricingMath.Tier tier:next.tiers){
                ContentValues row=new ContentValues();
                row.put("service_id",serviceId);row.put("profile_revision",next.revision);
                row.put("threshold_hundredths",tier.minQuantityHundredths);row.put("rate_centavos",tier.rateCentavos);
                db.insertOrThrow("pricing_tier_versions",null,row);
            }
            db.setTransactionSuccessful();return next;
        } finally {db.endTransaction();}
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
                if (pendingStock(db,id)!=null) throw new IllegalStateException("Resolve the pending stock movement before editing this material");
                StockMath.Balance stock = stockBalance(db, id);
                if (stock.revision > 0 && !current.unit.equals(next.unit)) throw new IllegalStateException("Material unit cannot change after stock movement history exists");
                if (!current.unit.equals(next.unit) && hasReference(db,"recipe_history",id)) throw new IllegalStateException("Material unit cannot change after recipe history exists");
                if (next.archived && hasReference(db,"service_materials",id)) throw new IllegalStateException("Remove material from linked service recipes before archiving");
                if (next.archived && stock.quantity != 0) throw new IllegalStateException("Reduce material stock to zero with a reason before archiving");
            }
            if (current.kind == CatalogEntry.Kind.SERVICE && !current.unit.equals(next.unit)
                    && (recipeRevision(db,id)>0 || readPricing(db,id).revision>0))
                throw new IllegalStateException("Service unit cannot change after material or pricing history exists; create a new service");
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
    public static final class RecipeBinding {
        public final String materialId, unit;
        public final long quantityMilli;
        RecipeBinding(String id,String unit,long quantity){this.materialId=id;this.unit=unit;this.quantityMilli=quantity;}
    }
    public List<RecipeBinding> recipeBindings(String serviceId) {
        SQLiteDatabase db=getReadableDatabase();
        if(read(db,serviceId).kind!=CatalogEntry.Kind.SERVICE)throw new IllegalArgumentException("Not a service");
        List<RecipeBinding> result=new ArrayList<>();
        try(Cursor c=db.query("service_materials",new String[]{"material_id","unit","quantity_milli"},"service_id=?",new String[]{serviceId},null,null,"material_id")){
            while(c.moveToNext())result.add(new RecipeBinding(c.getString(0),c.getString(1),c.getLong(2)));
        }
        return result;
    }
    public MaterialCost.Estimate estimateMaterials(String serviceId,int quantity) {
        QuoteMath.total(0,quantity);
        return estimateMaterialsByHundredths(serviceId,(long)quantity*100);
    }
    public MaterialCost.Estimate estimateMaterialsByHundredths(String serviceId,long unitsHundredths) {
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
            MaterialCost.Estimate result=MaterialCost.estimateFractional(components,unitsHundredths);
            db.setTransactionSuccessful();return result;
        } finally {db.endTransaction();}
    }
    /** Read-only confirmation plan from one consistent database snapshot.
     * Expected revisions are supplied by the reviewed quotation. No ledger/payment writes.
     * A future commit operation must perform the same checks inside its write transaction.
     */
    public CheckoutPlan.Proposal prepareCheckout(String requestId,String serviceId,long expectedServiceRevision,
                                                  long expectedPricingRevision,long expectedRecipeRevision,
                                                  CheckoutPlan.Inputs inputs) {
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransactionNonExclusive();
        try {
            CatalogEntry service=read(db,serviceId);
            if(service.kind!=CatalogEntry.Kind.SERVICE||service.archived)
                throw new IllegalArgumentException("Select an active service");
            PricingProfile profile=readPricing(db,serviceId);
            long recipe=recipeRevision(db,serviceId);
            if(service.revision!=expectedServiceRevision||profile.revision!=expectedPricingRevision||recipe!=expectedRecipeRevision)
                throw new IllegalStateException("Service, pricing or recipe changed; review a fresh quotation");
            List<CheckoutPlan.Material> materials=new ArrayList<>();
            try(Cursor c=db.query("service_materials",new String[]{"material_id","quantity_milli","unit"},
                                  "service_id=?",new String[]{serviceId},null,null,"material_id")){
                while(c.moveToNext()){
                    CatalogEntry material=read(db,c.getString(0));
                    if(!material.unit.equals(c.getString(2)))throw new IllegalStateException("Recipe unit changed; review before checkout");
                    StockMath.Balance balance=stockBalance(db,material.id);
                    materials.add(new CheckoutPlan.Material(material,c.getLong(1),balance.revision,balance.quantity,pendingStock(db,material.id)!=null));
                }
            }
            CheckoutPlan.Proposal proposal=CheckoutPlan.prepare(requestId,service,profile,recipe,inputs,materials);
            db.setTransactionSuccessful();return proposal;
        }finally{db.endTransaction();}
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
    /** The request payload remains immutable; the only transition is pending to applied or explicitly cancelled. */
    public static final class StockRequest {
        public final String id, materialId, reason, state;
        public final long catalogRevision, stockRevision, delta;
        private StockRequest(Cursor c) {
            id=c.getString(0);materialId=c.getString(1);catalogRevision=c.getLong(2);
            stockRevision=c.getLong(3);delta=c.getLong(4);reason=c.getString(5);state=c.getString(6);
        }
        private void verify(String material,long catalog,long stock,long amount,String note) {
            if(!materialId.equals(material)||catalogRevision!=catalog||stockRevision!=stock||delta!=amount||!reason.equals(note))
                throw new IllegalStateException("Movement identity already used for different request");
        }
    }
    private static StockRequest readRequest(SQLiteDatabase db,String where,String value) {
        try(Cursor c=db.query("stock_requests",new String[]{"request_id","material_id","catalog_revision","expected_stock_revision","delta","reason","state"},where,new String[]{value},null,null,null,"1")) {
            return c.moveToFirst()?new StockRequest(c):null;
        }
    }
    private static StockRequest pendingStock(SQLiteDatabase db,String materialId) {
        return readRequest(db,"material_id=? AND state='PENDING'",materialId);
    }
    public StockRequest pendingStock(String materialId) {
        return pendingStock(getReadableDatabase(),materialId);
    }
    public StockRequest stockRequest(String requestId) {
        StockMath.validateRequestId(requestId);
        return readRequest(getReadableDatabase(),"request_id=?",requestId);
    }
    private static void validateMovement(String requestId,long catalogRevision,long stockRevision) {
        StockMath.validateRequestId(requestId);
        if(catalogRevision<1||stockRevision<0||stockRevision==Long.MAX_VALUE)
            throw new IllegalArgumentException("Invalid movement revision.");
    }
    private static StockMath.Balance previousMovement(SQLiteDatabase db,String requestId,String id,long catalogRevision,long expectedStockRevision,long delta,String reason) {
        try(Cursor c=db.query("stock_movements",new String[]{"material_id","catalog_revision","stock_revision","delta","reason","after_quantity"},"request_id=?",new String[]{requestId},null,null,null)) {
            if(!c.moveToFirst())return null;
            if(!id.equals(c.getString(0))||catalogRevision!=c.getLong(1)||expectedStockRevision+1!=c.getLong(2)||delta!=c.getLong(3)||!reason.equals(c.getString(4)))
                throw new IllegalStateException("Movement identity already used for different request");
            return new StockMath.Balance(c.getLong(5),c.getLong(2));
        }
    }
    private static CatalogEntry eligibleMaterial(SQLiteDatabase db,String id,long catalogRevision,long expectedStockRevision,long delta) {
        CatalogEntry material=read(db,id);
        if(material.kind!=CatalogEntry.Kind.MATERIAL||material.archived)throw new IllegalStateException("Only active materials can have stock movements");
        if(material.revision!=catalogRevision)throw new IllegalStateException("Material changed; reload before adjusting stock");
        StockMath.Balance before=stockBalance(db,id);
        if(before.revision!=expectedStockRevision)throw new IllegalStateException("Stock changed; reload before adjusting");
        StockMath.apply(before.quantity,delta);
        return material;
    }
    /** Commit the identity and exact payload before attempting any stock adjustment. */
    public StockRequest stageStock(String requestId,String id,long catalogRevision,long expectedStockRevision,long delta,String note) {
        validateMovement(requestId,catalogRevision,expectedStockRevision);
        String reason=StockMath.reason(note);
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            StockRequest prior=readRequest(db,"request_id=?",requestId);
            if(prior!=null){
                prior.verify(id,catalogRevision,expectedStockRevision,delta,reason);
                db.setTransactionSuccessful();return prior;
            }
            if(previousMovement(db,requestId,id,catalogRevision,expectedStockRevision,delta,reason)!=null)
                throw new IllegalStateException("Movement identity already recorded in legacy ledger");
            if(pendingStock(db,id)!=null)throw new IllegalStateException("Resolve the existing pending stock movement first");
            eligibleMaterial(db,id,catalogRevision,expectedStockRevision,delta);
            ContentValues row=new ContentValues();
            row.put("request_id",requestId);row.put("material_id",id);row.put("catalog_revision",catalogRevision);
            row.put("expected_stock_revision",expectedStockRevision);row.put("delta",delta);row.put("reason",reason);
            row.put("created_ms",System.currentTimeMillis());row.put("state","PENDING");
            db.insertOrThrow("stock_requests",null,row);
            StockRequest staged=readRequest(db,"request_id=?",requestId);
            if(staged==null)throw new IllegalStateException("Staged movement unavailable");
            db.setTransactionSuccessful();return staged;
        }finally{db.endTransaction();}
    }
    private static StockMath.Balance appendMovement(SQLiteDatabase db,String requestId,String id,long catalogRevision,long expectedStockRevision,long delta,String reason) {
        CatalogEntry material=eligibleMaterial(db,id,catalogRevision,expectedStockRevision,delta);
        StockMath.Balance before=stockBalance(db,id);
        long after=StockMath.apply(before.quantity,delta),revision=Math.addExact(before.revision,1);
        ContentValues values=new ContentValues();
        values.put("request_id",requestId);values.put("material_id",id);values.put("catalog_revision",catalogRevision);
        values.put("stock_revision",revision);values.put("unit",material.unit);values.put("reason",reason);values.put("delta",delta);
        values.put("before_quantity",before.quantity);values.put("after_quantity",after);values.put("created_ms",System.currentTimeMillis());
        db.insertOrThrow("stock_movements",null,values);
        return new StockMath.Balance(after,revision);
    }
    private static void resolveRequest(SQLiteDatabase db,String requestId,String status) {
        ContentValues row=new ContentValues();row.put("state",status);row.put("resolved_ms",System.currentTimeMillis());
        if(db.update("stock_requests",row,"request_id=? AND state='PENDING'",new String[]{requestId})!=1)
            throw new IllegalStateException("Pending movement changed; reload");
    }
    /** Ledger insert and APPLIED state commit together, or neither commits. Repeat returns original result. */
    public StockMath.Balance applyStagedStock(String requestId) {
        StockMath.validateRequestId(requestId);
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            StockRequest r=readRequest(db,"request_id=?",requestId);
            if(r==null||"CANCELLED".equals(r.state))throw new IllegalStateException("No applicable staged movement");
            StockMath.Balance prior=previousMovement(db,r.id,r.materialId,r.catalogRevision,r.stockRevision,r.delta,r.reason);
            if("APPLIED".equals(r.state)){
                if(prior==null)throw new IllegalStateException("Applied request missing ledger row; review required");
                db.setTransactionSuccessful();return prior;
            }
            if(prior!=null)throw new IllegalStateException("Pending request already has a ledger row; review required");
            StockMath.Balance applied=appendMovement(db,r.id,r.materialId,r.catalogRevision,r.stockRevision,r.delta,r.reason);
            resolveRequest(db,requestId,"APPLIED");
            db.setTransactionSuccessful();return applied;
        }finally{db.endTransaction();}
    }
    /** Explicit cancellation is allowed only if the movement has not been recorded. Keep the audit record. */
    public void cancelStagedStock(String requestId) {
        StockMath.validateRequestId(requestId);
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            StockRequest r=readRequest(db,"request_id=?",requestId);
            if(r==null||"APPLIED".equals(r.state))throw new IllegalStateException("Applied or unknown movement cannot be cancelled");
            if(previousMovement(db,r.id,r.materialId,r.catalogRevision,r.stockRevision,r.delta,r.reason)!=null)
                throw new IllegalStateException("Recorded movement cannot be cancelled");
            if("PENDING".equals(r.state))resolveRequest(db,requestId,"CANCELLED");
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    /** Legacy exact replay remains valid. New direct mutations cannot bypass a pending request. */
    public StockMath.Balance moveStock(String requestId,String id,long catalogRevision,long expectedStockRevision,long delta,String note) {
        validateMovement(requestId,catalogRevision,expectedStockRevision);
        String reason=StockMath.reason(note);
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            if(readRequest(db,"request_id=?",requestId)!=null)throw new IllegalStateException("Use staged movement recovery for this identity");
            StockMath.Balance prior=previousMovement(db,requestId,id,catalogRevision,expectedStockRevision,delta,reason);
            if(prior!=null){db.setTransactionSuccessful();return prior;}
            if(pendingStock(db,id)!=null)throw new IllegalStateException("Resolve the pending stock movement first");
            StockMath.Balance result=appendMovement(db,requestId,id,catalogRevision,expectedStockRevision,delta,reason);
            db.setTransactionSuccessful();return result;
        }finally{db.endTransaction();}
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
