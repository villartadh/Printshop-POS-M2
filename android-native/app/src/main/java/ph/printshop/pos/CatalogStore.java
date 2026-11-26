package ph.printshop.pos;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

/** App-private preview catalog. Checkout writes are isolated to explicit synthetic fixtures. */
public final class CatalogStore extends SQLiteOpenHelper {
    private final boolean fixtureCheckoutWrites;
    public CatalogStore(Context context) { this(context, "preview-catalog.db"); }
    CatalogStore(Context context, String filename) { this(context,filename,false); }
    private CatalogStore(Context context,String filename,boolean fixtureCheckoutWrites) {
        super(context,filename,null,10);this.fixtureCheckoutWrites=fixtureCheckoutWrites;
    }
    /** No production/ordinary preview API can record sales or consume stock through checkout.
     * Only a separately identified synthetic fixture database opts in to transaction experiments.
     */
    static CatalogStore checkoutFixture(Context context,String fixtureId) {
        StockMath.validateRequestId(fixtureId);
        return new CatalogStore(context,"checkout-fixture-"+fixtureId+".db",true);
    }
    private void requireCheckoutFixture(){
        if(!fixtureCheckoutWrites)throw new IllegalStateException("Checkout recording disabled until native authentication and shift acceptance");
    }
    /** Called inside the write transaction, so session revocation/initialization cannot race a write. */
    private CashierJournal.Principal authorizeCatalogWrite(SQLiteDatabase db,String token){
        if(token!=null)return requireFixtureOwner(db,token);
        if(CashierJournal.initialized(db))
            throw new IllegalStateException("Use owner-authorized training administration");
        return null;
    }
    private CashierJournal.Principal requireFixtureOwner(SQLiteDatabase db,String token){
        requireCheckoutFixture();
        if(token==null)throw new IllegalStateException("Sign in required");
        CashierJournal.Principal actor=CashierJournal.requireSession(db,token,System.currentTimeMillis());
        CashierSecurity.requireOwner(actor.role);return actor;
    }
    private String ownerToken(String token){
        requireCheckoutFixture();
        if(token==null)throw new IllegalStateException("Sign in required");
        return token;
    }
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
        CheckoutJournal.create(db);
        CashierJournal.create(db);
        CredentialJournal.upgrade(db);
        StaffJournal.upgrade(db);
        AdminJournal.upgrade(db);
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
        if (newVersion == 10 && oldVersion >= 1 && oldVersion <= 9) {
            if (oldVersion < 2) createStockLedger(db);
            if (oldVersion < 3) createRecipeSchema(db);
            if (oldVersion < 4) createPricingSchema(db);
            if(oldVersion < 5) createStockRequests(db);
            if(oldVersion<6) CheckoutJournal.create(db);
            if(oldVersion<7) CashierJournal.create(db);
            if(oldVersion<8) CredentialJournal.upgrade(db);
            if(oldVersion<9) StaffJournal.upgrade(db);
            AdminJournal.upgrade(db); return;
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
        return savePricing(null,serviceId,serviceRevision,expectedPricingRevision,method,pagesAndCopies,tiers);
    }
    PricingProfile saveAuthorizedFixturePricing(String token,String serviceId,long serviceRevision,long expectedPricingRevision,
                                      PricingMath.Method method,boolean pagesAndCopies,List<PricingMath.Tier> tiers) {
        return savePricing(ownerToken(token),serviceId,serviceRevision,expectedPricingRevision,method,pagesAndCopies,tiers);
    }
    private PricingProfile savePricing(String token,String serviceId,long serviceRevision,long expectedPricingRevision,
                                      PricingMath.Method method,boolean pagesAndCopies,List<PricingMath.Tier> tiers) {
        PricingProfile next=new PricingProfile(Math.addExact(expectedPricingRevision,1),method,pagesAndCopies,tiers);
        if(expectedPricingRevision<0)throw new IllegalArgumentException("Invalid expected pricing revision");
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try {
            CashierJournal.Principal actor=authorizeCatalogWrite(db,token);
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
            AdminJournal.append(db,actor,"PRICING_SAVE",serviceId,next.revision,null);
            db.setTransactionSuccessful();return next;
        } finally {db.endTransaction();}
    }
    public CatalogEntry create(CatalogEntry.Kind kind, String name, String unit, String price) {
        return create(null,kind,name,unit,price);
    }
    CatalogEntry createAuthorizedFixtureCatalog(String token,CatalogEntry.Kind kind, String name, String unit, String price) {
        return create(ownerToken(token),kind,name,unit,price);
    }
    private CatalogEntry create(String token,CatalogEntry.Kind kind, String name, String unit, String price) {
        return create(token,CatalogEntry.create(kind,name,unit,price),false);
    }
    /** One stable identity per native creation form; exact replay never creates a second item. */
    CatalogEntry createAuthorizedFixtureCatalog(String token,String id,CatalogEntry.Kind kind,String name,String unit,String price){
        return create(ownerToken(token),new CatalogEntry(id,kind,name,unit,QuoteMath.parseCentavos(price),1,false),true);
    }
    private CatalogEntry create(String token,CatalogEntry entry,boolean replay){
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            CashierJournal.Principal actor=authorizeCatalogWrite(db,token);
            if(replay){
                try(Cursor c=db.query("catalog",null,"id=?",new String[]{entry.id},null,null,null)){
                    if(c.moveToFirst()){
                        CatalogEntry existing=entry(c);
                        boolean exact=existing.revision==1&&!existing.archived&&existing.kind==entry.kind&&
                            existing.name.equals(entry.name)&&existing.unit.equals(entry.unit)&&existing.centavos==entry.centavos;
                        try(Cursor audit=db.rawQuery("SELECT 1 FROM cashier_admin_events WHERE action='CATALOG_CREATE' AND subject_id=? AND revision=1 AND actor_id=?",new String[]{entry.id,actor.id})){
                            if(!exact||!audit.moveToFirst())throw new IllegalStateException("Creation already recorded or changed; reload catalog");
                        }
                        db.setTransactionSuccessful();return existing;
                    }
                }
            }
            db.insertOrThrow("catalog", null, values(entry));
            history(db, entry, "CREATE");
            AdminJournal.append(db,actor,"CATALOG_CREATE",entry.id,entry.revision,null);
            db.setTransactionSuccessful();
            return entry;
        } finally { db.endTransaction(); }
    }
    public CatalogEntry edit(String id, long expectedRevision, String name, String unit, String price) {
        return mutate(null,id, expectedRevision, current -> current.edit(name, unit, price), "EDIT");
    }
    CatalogEntry editAuthorizedFixtureCatalog(String token,String id,long expectedRevision,String name,String unit,String price){
        return mutate(ownerToken(token),id,expectedRevision,current->current.edit(name,unit,price),"EDIT");
    }
    public CatalogEntry archive(String id, long expectedRevision, boolean archived) {
        return mutate(null,id, expectedRevision, current -> current.archive(archived), archived ? "ARCHIVE" : "RESTORE");
    }
    CatalogEntry archiveAuthorizedFixtureCatalog(String token,String id,long expectedRevision,boolean archived){
        return mutate(ownerToken(token),id,expectedRevision,current->current.archive(archived),archived?"ARCHIVE":"RESTORE");
    }
    private interface Change { CatalogEntry apply(CatalogEntry current); }
    private CatalogEntry mutate(String token,String id, long expectedRevision, Change change, String action) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            CashierJournal.Principal actor=authorizeCatalogWrite(db,token);
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
            AdminJournal.append(db,actor,"CATALOG_"+action,next.id,next.revision,null);
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
        return setMaterialUsage(null,serviceId,serviceRevision,materialId,materialRevision,expectedRecipeRevision,quantityMilli);
    }
    long setAuthorizedFixtureMaterialUsage(String token,String serviceId,long serviceRevision,String materialId,long materialRevision,long expectedRecipeRevision,long quantityMilli) {
        return setMaterialUsage(ownerToken(token),serviceId,serviceRevision,materialId,materialRevision,expectedRecipeRevision,quantityMilli);
    }
    private long setMaterialUsage(String token,String serviceId,long serviceRevision,String materialId,long materialRevision,long expectedRecipeRevision,long quantityMilli) {
        if(quantityMilli<0||quantityMilli>StockMath.MAX)throw new IllegalArgumentException("Invalid recipe quantity");
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try {
            CashierJournal.Principal actor=authorizeCatalogWrite(db,token);
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
            AdminJournal.append(db,actor,"RECIPE_SAVE",serviceId,revision,null);
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
        return readRecipeBindings(db,serviceId);
    }
    private static List<RecipeBinding> readRecipeBindings(SQLiteDatabase db,String serviceId){
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
    /** Read-only confirmation plan from one consistent database snapshot. */
    public CheckoutPlan.Proposal prepareCheckout(String requestId,String serviceId,long expectedServiceRevision,
                                                  long expectedPricingRevision,long expectedRecipeRevision,
                                                  CheckoutPlan.Inputs inputs) {
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransactionNonExclusive();
        try {
            CheckoutPlan.Proposal p=readCheckoutPlan(db,requestId,serviceId,expectedServiceRevision,expectedPricingRevision,expectedRecipeRevision,inputs);
            db.setTransactionSuccessful();return p;
        }finally{db.endTransaction();}
    }
    private static CheckoutPlan.Proposal readCheckoutPlan(SQLiteDatabase db,String requestId,String serviceId,
                             long expectedServiceRevision,long expectedPricingRevision,long expectedRecipeRevision,CheckoutPlan.Inputs inputs){
        CatalogEntry service=read(db,serviceId);
        if(service.kind!=CatalogEntry.Kind.SERVICE||service.archived)throw new IllegalArgumentException("Select an active service");
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
        return CheckoutPlan.prepare(requestId,service,profile,recipe,inputs,materials);
    }
    private interface FixtureAction<T>{T run(SQLiteDatabase db);}
    private <T> T fixtureTransaction(FixtureAction<T> action){
        requireCheckoutFixture();SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{T result=action.run(db);db.setTransactionSuccessful();return result;}
        finally{db.endTransaction();}
    }
    boolean fixtureInitialized(){
        return fixtureTransaction(CashierJournal::initialized);
    }
    /** UI training setup: create the owner and deterministic-scope demonstration records together.
     * The fixture database must be empty; never import or overwrite ordinary preview catalog data.
     */
    void bootstrapDemoFixtureOwner(String username,char[] password){
        fixtureTransaction(db->{
            try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM catalog",null)){
                if(!c.moveToFirst()||c.getLong(0)!=0)throw new IllegalStateException("Training catalog must be empty");
            }
            CashierJournal.bootstrap(db,username,password,System.currentTimeMillis());
            CatalogEntry service=CatalogEntry.create(CatalogEntry.Kind.SERVICE,"Training document print","page","2.50");
            CatalogEntry paper=CatalogEntry.create(CatalogEntry.Kind.MATERIAL,"Training paper","sheet","1.00");
            db.insertOrThrow("catalog",null,values(service));history(db,service,"CREATE");
            db.insertOrThrow("catalog",null,values(paper));history(db,paper,"CREATE");
            appendMovement(db,java.util.UUID.randomUUID().toString(),paper.id,1,0,50000,"Synthetic training initial stock");
            ContentValues binding=new ContentValues();binding.put("service_id",service.id);binding.put("material_id",paper.id);
            binding.put("quantity_milli",1000L);binding.put("unit",paper.unit);db.insertOrThrow("service_materials",null,binding);
            ContentValues audit=new ContentValues();audit.put("service_id",service.id);audit.put("revision",1L);audit.put("material_id",paper.id);
            audit.put("quantity_milli",1000L);audit.put("unit",paper.unit);audit.put("material_revision",1L);audit.put("service_revision",1L);
            audit.put("created_ms",System.currentTimeMillis());db.insertOrThrow("recipe_history",null,audit);
            return null;
        });
    }
    private static CatalogEntry demoService(SQLiteDatabase db){
        try(Cursor c=db.query("catalog",null,"kind='SERVICE' AND archived=0 AND name='Training document print'",null,null,null,"id")){
            if(!c.moveToFirst())throw new IllegalStateException("Training service unavailable");
            CatalogEntry service=entry(c);
            if(c.moveToNext())throw new IllegalStateException("Training service ambiguous");
            return service;
        }
    }
    /** Bounded, deterministic catalog browsing; no unrestricted catalog reads from training UI. */
    static final class CatalogPage {
        final List<CatalogEntry> entries;
        final boolean more;
        CatalogPage(List<CatalogEntry> entries,boolean more){
            this.entries=java.util.Collections.unmodifiableList(new ArrayList<>(entries));this.more=more;
        }
    }
    private static CatalogPage catalogPage(SQLiteDatabase db,CatalogEntry.Kind kind,boolean archived,int offset){
        TrainingForms.validatePageOffset(offset);
        if(kind==null)throw new IllegalArgumentException("Select a catalog kind");
        List<CatalogEntry> entries=new ArrayList<>();
        try(Cursor c=db.rawQuery("SELECT * FROM catalog WHERE kind=? AND archived=? ORDER BY name COLLATE NOCASE,id LIMIT ? OFFSET ?",
            new String[]{kind.name(),archived?"1":"0",Integer.toString(TrainingForms.PAGE_SIZE+1),Integer.toString(offset)})){
            while(c.moveToNext())entries.add(entry(c));
        }
        boolean more=entries.size()>TrainingForms.PAGE_SIZE;
        if(more)entries.remove(entries.size()-1);
        return new CatalogPage(entries,more);
    }
    CatalogPage authorizedFixtureOwnerCatalog(String token,CatalogEntry.Kind kind,boolean archived,int offset){
        return fixtureTransaction(db->{requireFixtureOwner(db,token);return catalogPage(db,kind,archived,offset);});
    }
    CatalogPage authorizedFixtureCheckoutServices(String token,int offset){
        return fixtureTransaction(db->{
            CashierJournal.requireSession(db,token,System.currentTimeMillis());
            return catalogPage(db,CatalogEntry.Kind.SERVICE,false,offset);
        });
    }
    static final class CatalogAdminView {
        final CatalogEntry item;
        final PricingProfile profile;
        final long recipeRevision;
        final List<RecipeBinding> bindings;
        final List<CatalogEntry> materials;
        final StockMath.Balance stock;
        CatalogAdminView(CatalogEntry item,PricingProfile profile,long revision,List<RecipeBinding> bindings,List<CatalogEntry> materials,StockMath.Balance stock){
            this.item=item;this.profile=profile;recipeRevision=revision;
            this.bindings=java.util.Collections.unmodifiableList(new ArrayList<>(bindings));
            this.materials=java.util.Collections.unmodifiableList(new ArrayList<>(materials));this.stock=stock;
        }
        long usage(String materialId){
            for(RecipeBinding b:bindings)if(b.materialId.equals(materialId))return b.quantityMilli;
            return 0;
        }
    }
    CatalogAdminView authorizedFixtureCatalogDetail(String token,String id){
        return fixtureTransaction(db->{
            requireFixtureOwner(db,token);CatalogEntry item=read(db,id);
            if(item.kind==CatalogEntry.Kind.MATERIAL)return new CatalogAdminView(item,null,0,java.util.Collections.emptyList(),java.util.Collections.emptyList(),stockBalance(db,id));
            List<RecipeBinding> bindings=readRecipeBindings(db,id);
            List<CatalogEntry> materials=new ArrayList<>();
            for(RecipeBinding b:bindings)materials.add(read(db,b.materialId));
            return new CatalogAdminView(item,readPricing(db,id),recipeRevision(db,id),bindings,materials,null);
        });
    }
    static final class CheckoutSelection {
        final CatalogEntry service;final PricingProfile profile;final long recipeRevision;final boolean hasMaterials;
        CheckoutSelection(CatalogEntry service,PricingProfile profile,long revision,boolean hasMaterials){
            this.service=service;this.profile=profile;recipeRevision=revision;this.hasMaterials=hasMaterials;
        }
    }
    CheckoutSelection authorizedFixtureCheckoutSelection(String token,String id,long expectedRevision){
        return fixtureTransaction(db->{
            CashierJournal.requireSession(db,token,System.currentTimeMillis());
            CatalogEntry service=read(db,id);
            if(service.kind!=CatalogEntry.Kind.SERVICE||service.archived||service.revision!=expectedRevision)
                throw new IllegalStateException("Service changed; reload and select again");
            return new CheckoutSelection(service,readPricing(db,id),recipeRevision(db,id),!readRecipeBindings(db,id).isEmpty());
        });
    }
    CheckoutPlan.Proposal prepareAuthorizedFixtureCheckout(String token,String shiftId,String requestId,CheckoutSelection selected,CheckoutPlan.Inputs inputs){
        return fixtureTransaction(db->{
            CashierJournal.Principal actor=CashierJournal.requireSession(db,token,System.currentTimeMillis());
            CashierJournal.requireShift(db,actor,shiftId,true);
            if(selected==null)throw new IllegalArgumentException("Select an active service");
            return readCheckoutPlan(db,requestId,selected.service.id,selected.service.revision,selected.profile.revision,selected.recipeRevision,inputs);
        });
    }
    CatalogEntry authorizedDemoService(String token){
        return fixtureTransaction(db->{CashierJournal.requireSession(db,token,System.currentTimeMillis());return demoService(db);});
    }
    CheckoutPlan.Proposal prepareDemoFixtureCheckout(String token,String shiftId,String requestId,int count,long cash){
        return fixtureTransaction(db->{
            CashierJournal.Principal actor=CashierJournal.requireSession(db,token,System.currentTimeMillis());
            CashierJournal.requireShift(db,actor,shiftId,true);
            CatalogEntry service=demoService(db);PricingProfile profile=readPricing(db,service.id);
            if(profile.method!=PricingMath.Method.PER_UNIT||profile.pagesAndCopies||!profile.tiers.isEmpty())
                throw new IllegalStateException("Training service pricing unexpectedly changed");
            long revision=recipeRevision(db,service.id);
            CheckoutPlan.Inputs input=new CheckoutPlan.Inputs(count,1,1,null,null,null,CheckoutPlan.UsageBasis.BILLABLE_UNIT,cash);
            return readCheckoutPlan(db,requestId,service.id,service.revision,profile.revision,revision,input);
        });
    }
    List<CheckoutJournal.Record> recentAuthorizedFixtureReceipts(String token,int limit){
        return fixtureTransaction(db->{
            if(limit<1||limit>50)throw new IllegalArgumentException("Invalid receipt limit");
            CashierJournal.Principal actor=CashierJournal.requireSession(db,token,System.currentTimeMillis());
            List<CheckoutJournal.Record> records=new ArrayList<>();
            try(Cursor c=db.rawQuery("SELECT r.request_id FROM cashier_shift_checkouts l JOIN checkout_requests r ON l.request_id=r.request_id WHERE l.operator_id=? AND r.state='APPLIED' ORDER BY r.resolved_ms DESC,r.request_id LIMIT ?",new String[]{actor.id,Integer.toString(limit)})){
                while(c.moveToNext())records.add(CheckoutJournal.read(db,c.getString(0)));
            }
            return records;
        });
    }

    // Explicit synthetic setup only. No default credentials or real UI path.
    CashierJournal.Principal bootstrapFixtureOwner(String username,char[] password){
        return fixtureTransaction(db->CashierJournal.bootstrap(db,username,password,System.currentTimeMillis()));
    }
    String signInFixture(String username,char[] password){
        String token=fixtureTransaction(db->CashierJournal.signIn(db,username,password,System.currentTimeMillis()));
        if(token==null)throw new IllegalStateException("Sign in denied");
        return token;
    }
    CashierJournal.Principal fixturePrincipal(String token){
        return fixtureTransaction(db->CashierJournal.requireSession(db,token,System.currentTimeMillis()));
    }
    void signOutFixture(String token){
        fixtureTransaction(db->{CashierJournal.signOut(db,token,System.currentTimeMillis());return null;});
    }
    CashierJournal.Principal createFixtureCashier(String token,String username,char[] password){
        return fixtureTransaction(db->CashierJournal.createCashier(db,CashierJournal.requireSession(db,token,System.currentTimeMillis()),username,password,System.currentTimeMillis()));
    }

    void changeFixturePassphrase(String token,char[] current,char[] replacement){
        boolean changed=fixtureTransaction(db->{
            long now=System.currentTimeMillis();
            return CashierJournal.changeOwnCredential(db,CashierJournal.requireSession(db,token,now),current,replacement,now);
        });
        if(!changed)throw new IllegalStateException("Credential confirmation denied");
    }
    void resetFixtureCashierPassphrase(String token,char[] current,String username,char[] replacement){
        boolean changed=fixtureTransaction(db->{
            long now=System.currentTimeMillis();
            return CashierJournal.resetCashierCredential(db,CashierJournal.requireSession(db,token,now),current,username,replacement,now);
        });
        if(!changed)throw new IllegalStateException("Credential confirmation denied");
    }
    List<AdminJournal.Entry> authorizedFixtureAdminHistory(String token,long before,int limit){
        return fixtureTransaction(db->{requireFixtureOwner(db,token);return AdminJournal.list(db,before,limit);});
    }
    List<CashierJournal.StaffSummary> authorizedFixtureStaff(String token){
        return fixtureTransaction(db->CashierJournal.staff(db,CashierJournal.requireSession(db,token,System.currentTimeMillis())));
    }
    void disableFixtureCashier(String token,String id,long expectedRevision,char[] current,String reason){
        boolean disabled=fixtureTransaction(db->{
            long now=System.currentTimeMillis();
            return CashierJournal.disableCashier(db,CashierJournal.requireSession(db,token,now),id,expectedRevision,current,reason,now);
        });
        if(!disabled)throw new IllegalStateException("Credential confirmation denied");
    }
    CashierJournal.Shift openFixtureShift(String token,String id,long opening){
        return fixtureTransaction(db->CashierJournal.openShift(db,CashierJournal.requireSession(db,token,System.currentTimeMillis()),id,opening,System.currentTimeMillis()));
    }
    CashierJournal.Shift activeFixtureShift(String token){
        return fixtureTransaction(db->CashierJournal.activeShift(db,CashierJournal.requireSession(db,token,System.currentTimeMillis())));
    }
    long expectedFixtureCash(String token,String shiftId){
        return fixtureTransaction(db->CashierJournal.expected(db,CashierJournal.requireShift(db,CashierJournal.requireSession(db,token,System.currentTimeMillis()),shiftId,false)));
    }
    CashierJournal.Shift closeFixtureShift(String token,String shiftId,long counted){
        return fixtureTransaction(db->CashierJournal.closeShift(db,CashierJournal.requireSession(db,token,System.currentTimeMillis()),shiftId,counted,System.currentTimeMillis()));
    }

    /** v6 compatibility experiment only: disabled the moment an account is initialized.
     * This preserves prior synthetic fixture tests, not a real checkout authorization path.
     */
    CheckoutJournal.Record stageFixtureCheckout(CheckoutPlan.Proposal reviewed) {
        return fixtureTransaction(db->{CashierJournal.requireLegacyFixture(db);return stageCheckout(db,reviewed);});
    }
    CheckoutJournal.Record fixtureCheckout(String id){
        return fixtureTransaction(db->{CashierJournal.requireLegacyFixture(db);StockMath.validateRequestId(id);return CheckoutJournal.read(db,id);});
    }
    List<CheckoutJournal.Record> pendingFixtureCheckouts(int limit){
        return fixtureTransaction(db->{CashierJournal.requireLegacyFixture(db);return CheckoutJournal.pending(db,limit);});
    }
    CheckoutJournal.Record applyFixtureCheckout(String id,String fingerprint){
        return fixtureTransaction(db->{CashierJournal.requireLegacyFixture(db);return applyCheckout(db,id,fingerprint);});
    }
    CheckoutJournal.Record cancelFixtureCheckout(String id,String fingerprint,String reason){
        return fixtureTransaction(db->{CashierJournal.requireLegacyFixture(db);return cancelCheckout(db,id,fingerprint,reason);});
    }

    /** Each authorized request is linked atomically to the operator's reviewed open shift. */
    CheckoutJournal.Record stageAuthorizedFixtureCheckout(String token,String shiftId,CheckoutPlan.Proposal reviewed){
        return fixtureTransaction(db->{
            CashierJournal.Principal p=CashierJournal.requireSession(db,token,System.currentTimeMillis());
            if(reviewed==null)throw new IllegalArgumentException("Checkout proposal required");
            CheckoutJournal.Record prior=CheckoutJournal.read(db,reviewed.requestId);
            if(prior!=null){
                CashierJournal.Shift linked=CashierJournal.requireCheckoutLink(db,p,prior.id,"PENDING".equals(prior.state));
                if(!linked.id.equals(shiftId))throw new IllegalStateException("Checkout assigned to another shift");
                prior.verifyFingerprint(reviewed.fingerprint());return prior;
            }
            CashierJournal.Shift shift=CashierJournal.requireShift(db,p,shiftId,true);
            CheckoutJournal.Record staged=stageCheckout(db,reviewed);
            CashierJournal.link(db,staged.id,shift);return staged;
        });
    }
    CheckoutJournal.Record authorizedFixtureCheckout(String token,String id){
        return fixtureTransaction(db->{
            StockMath.validateRequestId(id);
            CashierJournal.Principal p=CashierJournal.requireSession(db,token,System.currentTimeMillis());
            CashierJournal.requireCheckoutLink(db,p,id,false);
            return CheckoutJournal.read(db,id);
        });
    }
    List<CheckoutJournal.Record> pendingAuthorizedFixtureCheckouts(String token,String shiftId,int limit){
        return fixtureTransaction(db->{
            if(limit<1||limit>50)throw new IllegalArgumentException("Invalid checkout limit");
            CashierJournal.Principal p=CashierJournal.requireSession(db,token,System.currentTimeMillis());
            CashierJournal.requireShift(db,p,shiftId,false);List<CheckoutJournal.Record> result=new ArrayList<>();
            try(Cursor c=db.rawQuery("SELECT r.request_id FROM cashier_shift_checkouts l JOIN checkout_requests r ON l.request_id=r.request_id WHERE l.shift_id=? AND r.state='PENDING' ORDER BY r.created_ms,r.request_id LIMIT ?",new String[]{shiftId,Integer.toString(limit)})){
                while(c.moveToNext())result.add(CheckoutJournal.read(db,c.getString(0)));
            }
            return result;
        });
    }
    CheckoutJournal.Record applyAuthorizedFixtureCheckout(String token,String id,String fingerprint){
        return fixtureTransaction(db->{
            StockMath.validateRequestId(id);CashierJournal.Principal p=CashierJournal.requireSession(db,token,System.currentTimeMillis());
            CheckoutJournal.Record r=CheckoutJournal.read(db,id);if(r==null)throw new IllegalStateException("Unknown checkout request");
            CashierJournal.Shift shift=CashierJournal.requireCheckoutLink(db,p,id,"PENDING".equals(r.state));
            if("PENDING".equals(r.state))ShiftMath.addPayment(CashierJournal.expected(db,shift),r.total);
            return applyCheckout(db,id,fingerprint);
        });
    }
    CheckoutJournal.Record cancelAuthorizedFixtureCheckout(String token,String id,String fingerprint,String reason){
        return fixtureTransaction(db->{
            StockMath.validateRequestId(id);CashierJournal.Principal p=CashierJournal.requireSession(db,token,System.currentTimeMillis());
            CheckoutJournal.Record r=CheckoutJournal.read(db,id);if(r==null)throw new IllegalStateException("Unknown checkout request");
            CashierJournal.requireCheckoutLink(db,p,id,"PENDING".equals(r.state));
            return cancelCheckout(db,id,fingerprint,reason);
        });
    }
    private static CheckoutJournal.Record stageCheckout(SQLiteDatabase db,CheckoutPlan.Proposal reviewed){
        if(reviewed==null)throw new IllegalArgumentException("Checkout proposal required");
        CheckoutJournal.Record prior=CheckoutJournal.read(db,reviewed.requestId);
        if(prior!=null){prior.verifyFingerprint(reviewed.fingerprint());return prior;}
        CheckoutPlan.Proposal current=readCheckoutPlan(db,reviewed.requestId,reviewed.serviceId,reviewed.serviceRevision,reviewed.pricingRevision,reviewed.recipeRevision,reviewed.inputs);
        reviewed.requireSameSnapshot(current);CheckoutJournal.stage(db,current);
        CheckoutJournal.Record result=CheckoutJournal.read(db,current.requestId);
        if(result==null)throw new IllegalStateException("Staged checkout unavailable");return result;
    }
    private static CheckoutJournal.Record applyCheckout(SQLiteDatabase db,String id,String fingerprint){
        StockMath.validateRequestId(id);
        CheckoutJournal.Record r=CheckoutJournal.read(db,id);
        if(r==null)throw new IllegalStateException("Unknown checkout request");
        r.verifyFingerprint(fingerprint);
        if("CANCELLED".equals(r.state))throw new IllegalStateException("Cancelled checkout cannot be applied");
        if("APPLIED".equals(r.state))return r;
        CheckoutPlan.Proposal current=readCheckoutPlan(db,id,r.serviceId,r.serviceRevision,r.pricingRevision,r.recipeRevision,r.inputs);
        r.verifyFingerprint(current.fingerprint());CheckoutJournal.saleAndCash(db,r);
        for(CheckoutPlan.Consumption m:current.materials){
            String movementId=java.util.UUID.randomUUID().toString();
            if(readRequest(db,"request_id=?",movementId)!=null)throw new IllegalStateException("Stock operation ID collision");
            StockMath.Balance result=appendMovement(db,movementId,m.materialId,m.catalogRevision,m.stockRevision,-m.quantityMilli,"Preview checkout "+id);
            if(result.quantity!=m.afterMilli||result.revision!=m.stockRevision+1)throw new IllegalStateException("Stock result differs from confirmed snapshot");
            CheckoutJournal.link(db,id,m.materialId,movementId);
        }
        CheckoutJournal.resolve(db,id,"APPLIED",null);
        CheckoutJournal.Record applied=CheckoutJournal.read(db,id);
        if(applied==null||!"APPLIED".equals(applied.state))throw new IllegalStateException("Checkout result unavailable");
        return applied;
    }
    private static CheckoutJournal.Record cancelCheckout(SQLiteDatabase db,String id,String fingerprint,String reason){
        StockMath.validateRequestId(id);String note=StockMath.reason(reason);
        CheckoutJournal.Record r=CheckoutJournal.read(db,id);
        if(r==null)throw new IllegalStateException("Unknown checkout request");
        r.verifyFingerprint(fingerprint);
        if("APPLIED".equals(r.state))throw new IllegalStateException("Recorded checkout cannot be cancelled");
        if("CANCELLED".equals(r.state)){
            if(!note.equals(r.cancelReason))throw new IllegalStateException("Cancellation reason differs from audit");return r;
        }
        CheckoutJournal.resolve(db,id,"CANCELLED",note);
        CheckoutJournal.Record cancelled=CheckoutJournal.read(db,id);
        if(cancelled==null||!"CANCELLED".equals(cancelled.state))throw new IllegalStateException("Cancellation confirmation unavailable");
        return cancelled;
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
    static final class InventoryItem {
        final CatalogEntry material;
        final StockMath.Balance balance;
        final StockRequest pending;
        InventoryItem(CatalogEntry m,StockMath.Balance b,StockRequest p){material=m;balance=b;pending=p;}
    }
    List<InventoryItem> authorizedFixtureInventory(String token){
        return fixtureTransaction(db->{
            requireFixtureOwner(db,token);
            List<InventoryItem> result=new ArrayList<>();
            try(Cursor c=db.query("catalog",null,"kind='MATERIAL' AND archived=0",null,null,null,"name COLLATE NOCASE,id")){
                while(c.moveToNext()){
                    CatalogEntry item=entry(c);
                    result.add(new InventoryItem(item,stockBalance(db,item.id),pendingStock(db,item.id)));
                }
            }
            return result;
        });
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
        return stageStock(null,requestId,id,catalogRevision,expectedStockRevision,delta,note);
    }
    StockRequest stageAuthorizedFixtureStock(String token,String requestId,String id,long catalogRevision,long expectedStockRevision,long delta,String note) {
        return stageStock(ownerToken(token),requestId,id,catalogRevision,expectedStockRevision,delta,note);
    }
    private StockRequest stageStock(String token,String requestId,String id,long catalogRevision,long expectedStockRevision,long delta,String note) {
        validateMovement(requestId,catalogRevision,expectedStockRevision);
        String reason=StockMath.reason(note);
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            CashierJournal.Principal actor=authorizeCatalogWrite(db,token);
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
            AdminJournal.append(db,actor,"STOCK_STAGE",id,expectedStockRevision,requestId);
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
        return applyStagedStock(null,requestId);
    }
    StockMath.Balance applyAuthorizedFixtureStock(String token,String requestId) {
        return applyStagedStock(ownerToken(token),requestId);
    }
    private StockMath.Balance applyStagedStock(String token,String requestId) {
        StockMath.validateRequestId(requestId);
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            CashierJournal.Principal actor=authorizeCatalogWrite(db,token);
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
            AdminJournal.append(db,actor,"STOCK_APPLY",r.materialId,applied.revision,r.id);
            db.setTransactionSuccessful();return applied;
        }finally{db.endTransaction();}
    }
    /** Explicit cancellation is allowed only if the movement has not been recorded. Keep the audit record. */
    public void cancelStagedStock(String requestId) {
        cancelStagedStock(null,requestId);
    }
    void cancelAuthorizedFixtureStock(String token,String requestId) {
        cancelStagedStock(ownerToken(token),requestId);
    }
    private void cancelStagedStock(String token,String requestId) {
        StockMath.validateRequestId(requestId);
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            CashierJournal.Principal actor=authorizeCatalogWrite(db,token);
            StockRequest r=readRequest(db,"request_id=?",requestId);
            if(r==null||"APPLIED".equals(r.state))throw new IllegalStateException("Applied or unknown movement cannot be cancelled");
            if(previousMovement(db,r.id,r.materialId,r.catalogRevision,r.stockRevision,r.delta,r.reason)!=null)
                throw new IllegalStateException("Recorded movement cannot be cancelled");
            if("PENDING".equals(r.state)){
                resolveRequest(db,requestId,"CANCELLED");
                AdminJournal.append(db,actor,"STOCK_CANCEL",r.materialId,r.stockRevision,r.id);
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    /** Legacy exact replay remains valid. New direct mutations cannot bypass a pending request. */
    public StockMath.Balance moveStock(String requestId,String id,long catalogRevision,long expectedStockRevision,long delta,String note) {
        validateMovement(requestId,catalogRevision,expectedStockRevision);
        String reason=StockMath.reason(note);
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            authorizeCatalogWrite(db,null);
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
