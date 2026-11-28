package ph.printshop.pos;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;

/** Additive local preview journal. Only the isolated checkout fixture API may write it until auth/shift gates exist.
 * The caller owns every write transaction. No external payments, tax receipt, or real shop sales.
 */
final class CheckoutJournal {
    private CheckoutJournal() {}
    static void create(SQLiteDatabase db){
        db.execSQL("CREATE TABLE checkout_requests (request_id TEXT PRIMARY KEY NOT NULL, fingerprint_version INTEGER NOT NULL CHECK(fingerprint_version=1), fingerprint TEXT NOT NULL CHECK(length(fingerprint)=64 AND fingerprint NOT GLOB '*[^0-9a-f]*'), service_id TEXT NOT NULL REFERENCES catalog(id), service_name TEXT NOT NULL, service_unit TEXT NOT NULL, service_revision INTEGER NOT NULL CHECK(service_revision>=1), pricing_revision INTEGER NOT NULL CHECK(pricing_revision>=0), recipe_revision INTEGER NOT NULL CHECK(recipe_revision>=0), method TEXT NOT NULL CHECK(method IN ('FIXED','PER_UNIT','PER_PAGE','PER_COPY','PER_SHEET','PER_SQFT','PER_LINEAR_FT','PER_METER','TIERED_BULK')), basis TEXT NOT NULL CHECK(basis IN ('NONE','BILLABLE_UNIT','PRODUCED_ITEM')), input_count INTEGER NOT NULL CHECK(input_count BETWEEN 1 AND 100000), pages INTEGER NOT NULL CHECK(pages BETWEEN 1 AND 100000), copies INTEGER NOT NULL CHECK(copies BETWEEN 1 AND 100000), width TEXT, height TEXT, dimension_unit TEXT CHECK(dimension_unit IN ('FT','IN','CM','M')), billable_hundredths INTEGER NOT NULL CHECK(billable_hundredths BETWEEN 1 AND 10000000), produced_items INTEGER NOT NULL CHECK(produced_items BETWEEN 1 AND 100000), base_rate INTEGER NOT NULL CHECK(base_rate BETWEEN 0 AND 99999999999), rate INTEGER NOT NULL CHECK(rate BETWEEN 0 AND 99999999999), total INTEGER NOT NULL CHECK(total BETWEEN 1 AND 9999999999900000 AND total=(rate*billable_hundredths+50)/100), material_cost INTEGER NOT NULL CHECK(material_cost BETWEEN 0 AND 9999999999900000), received INTEGER NOT NULL CHECK(received BETWEEN total AND 9999999999900000), change_due INTEGER NOT NULL CHECK(change_due=received-total), material_count INTEGER NOT NULL CHECK(material_count BETWEEN 0 AND 100 AND ((material_count=0 AND basis='NONE') OR (material_count>0 AND basis<>'NONE' AND recipe_revision>=1))), created_ms INTEGER NOT NULL, state TEXT NOT NULL CHECK(state IN ('PENDING','APPLIED','CANCELLED')), resolved_ms INTEGER, cancel_reason TEXT, CHECK((state='PENDING' AND resolved_ms IS NULL AND cancel_reason IS NULL) OR (state='APPLIED' AND resolved_ms IS NOT NULL AND cancel_reason IS NULL) OR (state='CANCELLED' AND resolved_ms IS NOT NULL AND cancel_reason IS NOT NULL AND length(cancel_reason) BETWEEN 1 AND 160)), CHECK(typeof(fingerprint_version)='integer' AND typeof(service_revision)='integer' AND typeof(pricing_revision)='integer' AND typeof(recipe_revision)='integer' AND typeof(input_count)='integer' AND typeof(pages)='integer' AND typeof(copies)='integer' AND typeof(billable_hundredths)='integer' AND typeof(produced_items)='integer' AND typeof(base_rate)='integer' AND typeof(rate)='integer' AND typeof(total)='integer' AND typeof(material_cost)='integer' AND typeof(received)='integer' AND typeof(change_due)='integer' AND typeof(material_count)='integer' AND typeof(created_ms)='integer' AND (resolved_ms IS NULL OR typeof(resolved_ms)='integer')))");
        db.execSQL("CREATE TABLE checkout_materials (request_id TEXT NOT NULL REFERENCES checkout_requests(request_id), material_id TEXT NOT NULL REFERENCES catalog(id), name TEXT NOT NULL, unit TEXT NOT NULL, catalog_revision INTEGER NOT NULL CHECK(catalog_revision>=1), stock_revision INTEGER NOT NULL CHECK(stock_revision>=1 AND stock_revision<9223372036854775807), per_unit_milli INTEGER NOT NULL CHECK(per_unit_milli BETWEEN 1 AND 999999999999), quantity_milli INTEGER NOT NULL CHECK(quantity_milli BETWEEN 1 AND 999999999999), before_milli INTEGER NOT NULL CHECK(before_milli BETWEEN quantity_milli AND 999999999999), after_milli INTEGER NOT NULL CHECK(after_milli=before_milli-quantity_milli), unit_cost INTEGER NOT NULL CHECK(unit_cost BETWEEN 0 AND 99999999999), PRIMARY KEY(request_id,material_id), CHECK(typeof(catalog_revision)='integer' AND typeof(stock_revision)='integer' AND typeof(per_unit_milli)='integer' AND typeof(quantity_milli)='integer' AND typeof(before_milli)='integer' AND typeof(after_milli)='integer' AND typeof(unit_cost)='integer'))");
        db.execSQL("CREATE TABLE checkout_sales (request_id TEXT PRIMARY KEY NOT NULL REFERENCES checkout_requests(request_id), recorded_ms INTEGER NOT NULL, CHECK(typeof(recorded_ms)='integer'))");
        db.execSQL("CREATE TABLE checkout_cash (request_id TEXT PRIMARY KEY NOT NULL REFERENCES checkout_sales(request_id), paid INTEGER NOT NULL CHECK(paid>0), tendered INTEGER NOT NULL CHECK(tendered>=paid), change_due INTEGER NOT NULL CHECK(change_due=tendered-paid), CHECK(typeof(paid)='integer' AND typeof(tendered)='integer' AND typeof(change_due)='integer'))");
        db.execSQL("CREATE TABLE checkout_stock_links (request_id TEXT NOT NULL, material_id TEXT NOT NULL, movement_id TEXT UNIQUE NOT NULL REFERENCES stock_movements(request_id), PRIMARY KEY(request_id,material_id), FOREIGN KEY(request_id,material_id) REFERENCES checkout_materials(request_id,material_id), FOREIGN KEY(request_id) REFERENCES checkout_sales(request_id))");
        db.execSQL("CREATE INDEX checkout_pending ON checkout_requests(state,created_ms,request_id)");
        db.execSQL("CREATE TRIGGER checkout_request_initial BEFORE INSERT ON checkout_requests WHEN NEW.state<>'PENDING' OR EXISTS(SELECT 1 FROM checkout_requests WHERE request_id=NEW.request_id) BEGIN SELECT RAISE(ABORT,'Checkout request identity must be new and pending'); END");
        db.execSQL("CREATE TRIGGER checkout_request_no_delete BEFORE DELETE ON checkout_requests BEGIN SELECT RAISE(ABORT,'Checkout request audit cannot be deleted'); END");
        db.execSQL("CREATE TRIGGER checkout_request_resolution BEFORE UPDATE ON checkout_requests WHEN OLD.state<>'PENDING' OR NEW.state NOT IN ('APPLIED','CANCELLED') OR NEW.request_id IS NOT OLD.request_id OR NEW.fingerprint_version IS NOT OLD.fingerprint_version OR NEW.fingerprint IS NOT OLD.fingerprint OR NEW.service_id IS NOT OLD.service_id OR NEW.service_name IS NOT OLD.service_name OR NEW.service_unit IS NOT OLD.service_unit OR NEW.service_revision IS NOT OLD.service_revision OR NEW.pricing_revision IS NOT OLD.pricing_revision OR NEW.recipe_revision IS NOT OLD.recipe_revision OR NEW.method IS NOT OLD.method OR NEW.basis IS NOT OLD.basis OR NEW.input_count IS NOT OLD.input_count OR NEW.pages IS NOT OLD.pages OR NEW.copies IS NOT OLD.copies OR NEW.width IS NOT OLD.width OR NEW.height IS NOT OLD.height OR NEW.dimension_unit IS NOT OLD.dimension_unit OR NEW.billable_hundredths IS NOT OLD.billable_hundredths OR NEW.produced_items IS NOT OLD.produced_items OR NEW.base_rate IS NOT OLD.base_rate OR NEW.rate IS NOT OLD.rate OR NEW.total IS NOT OLD.total OR NEW.material_cost IS NOT OLD.material_cost OR NEW.received IS NOT OLD.received OR NEW.change_due IS NOT OLD.change_due OR NEW.material_count IS NOT OLD.material_count OR NEW.created_ms IS NOT OLD.created_ms OR (NEW.state='APPLIED' AND (NOT EXISTS(SELECT 1 FROM checkout_sales s JOIN checkout_cash c ON s.request_id=c.request_id WHERE s.request_id=OLD.request_id AND c.paid=OLD.total AND c.tendered=OLD.received AND c.change_due=OLD.change_due) OR (SELECT COUNT(*) FROM checkout_materials WHERE request_id=OLD.request_id)<>OLD.material_count OR (SELECT COUNT(*) FROM checkout_stock_links WHERE request_id=OLD.request_id)<>OLD.material_count)) OR (NEW.state='CANCELLED' AND EXISTS(SELECT 1 FROM checkout_sales WHERE request_id=OLD.request_id)) BEGIN SELECT RAISE(ABORT,'Invalid checkout resolution'); END");
        db.execSQL("CREATE TRIGGER checkout_material_insert BEFORE INSERT ON checkout_materials WHEN NOT EXISTS(SELECT 1 FROM checkout_requests r WHERE r.request_id=NEW.request_id AND r.state='PENDING' AND (SELECT COUNT(*) FROM checkout_materials WHERE request_id=r.request_id)<r.material_count AND NOT EXISTS(SELECT 1 FROM checkout_materials WHERE request_id=NEW.request_id AND material_id=NEW.material_id) AND NEW.quantity_milli*100=NEW.per_unit_milli*CASE WHEN r.basis='PRODUCED_ITEM' THEN r.produced_items*100 ELSE r.billable_hundredths END) BEGIN SELECT RAISE(ABORT,'Invalid checkout material snapshot'); END");
        db.execSQL("CREATE TRIGGER checkout_sale_insert BEFORE INSERT ON checkout_sales WHEN NOT EXISTS(SELECT 1 FROM checkout_requests r WHERE r.request_id=NEW.request_id AND r.state='PENDING' AND (SELECT COUNT(*) FROM checkout_materials WHERE request_id=r.request_id)=r.material_count AND NOT EXISTS(SELECT 1 FROM checkout_sales WHERE request_id=NEW.request_id)) BEGIN SELECT RAISE(ABORT,'Checkout sale requires complete pending request'); END");
        db.execSQL("CREATE TRIGGER checkout_cash_insert BEFORE INSERT ON checkout_cash WHEN NOT EXISTS(SELECT 1 FROM checkout_requests r WHERE r.request_id=NEW.request_id AND r.state='PENDING' AND NEW.paid=r.total AND NEW.tendered=r.received AND NEW.change_due=r.change_due AND NOT EXISTS(SELECT 1 FROM checkout_cash WHERE request_id=NEW.request_id)) BEGIN SELECT RAISE(ABORT,'Invalid checkout cash'); END");
        db.execSQL("CREATE TRIGGER checkout_link_insert BEFORE INSERT ON checkout_stock_links WHEN NOT EXISTS(SELECT 1 FROM checkout_requests r JOIN checkout_materials m ON r.request_id=m.request_id JOIN stock_movements s ON s.request_id=NEW.movement_id WHERE r.request_id=NEW.request_id AND r.state='PENDING' AND m.material_id=NEW.material_id AND s.material_id=m.material_id AND s.catalog_revision=m.catalog_revision AND s.stock_revision=m.stock_revision+1 AND s.delta=-m.quantity_milli AND s.before_quantity=m.before_milli AND s.after_quantity=m.after_milli AND s.unit=m.unit AND s.reason='Preview checkout '||r.request_id AND NOT EXISTS(SELECT 1 FROM checkout_stock_links WHERE (request_id=NEW.request_id AND material_id=NEW.material_id) OR movement_id=NEW.movement_id)) BEGIN SELECT RAISE(ABORT,'Invalid checkout stock link'); END");
        db.execSQL("CREATE TRIGGER checkout_materials_no_update BEFORE UPDATE ON checkout_materials BEGIN SELECT RAISE(ABORT,'Checkout audit rows are immutable'); END");
        db.execSQL("CREATE TRIGGER checkout_materials_no_delete BEFORE DELETE ON checkout_materials BEGIN SELECT RAISE(ABORT,'Checkout audit rows are immutable'); END");
        db.execSQL("CREATE TRIGGER checkout_sales_no_update BEFORE UPDATE ON checkout_sales BEGIN SELECT RAISE(ABORT,'Checkout audit rows are immutable'); END");
        db.execSQL("CREATE TRIGGER checkout_sales_no_delete BEFORE DELETE ON checkout_sales BEGIN SELECT RAISE(ABORT,'Checkout audit rows are immutable'); END");
        db.execSQL("CREATE TRIGGER checkout_cash_no_update BEFORE UPDATE ON checkout_cash BEGIN SELECT RAISE(ABORT,'Checkout audit rows are immutable'); END");
        db.execSQL("CREATE TRIGGER checkout_cash_no_delete BEFORE DELETE ON checkout_cash BEGIN SELECT RAISE(ABORT,'Checkout audit rows are immutable'); END");
        db.execSQL("CREATE TRIGGER checkout_stock_links_no_update BEFORE UPDATE ON checkout_stock_links BEGIN SELECT RAISE(ABORT,'Checkout audit rows are immutable'); END");
        db.execSQL("CREATE TRIGGER checkout_stock_links_no_delete BEFORE DELETE ON checkout_stock_links BEGIN SELECT RAISE(ABORT,'Checkout audit rows are immutable'); END");
        db.execSQL("CREATE TRIGGER checkout_linked_stock_no_update BEFORE UPDATE ON stock_movements WHEN EXISTS(SELECT 1 FROM checkout_stock_links WHERE movement_id=OLD.request_id) BEGIN SELECT RAISE(ABORT,'Linked stock movement is immutable'); END");
    }
    static final class Record {
        final String id,fingerprint,serviceId,serviceName,serviceUnit,state,method,cancelReason;
        final long serviceRevision,pricingRevision,recipeRevision,billableHundredths,baseRate,rate,total,materialCost,received,change,createdMs;
        final int producedItems,materialCount;
        final Long resolvedMs;
        final CheckoutPlan.Inputs inputs;
        private Record(Cursor c) {
            id=str(c,"request_id");fingerprint=str(c,"fingerprint");serviceId=str(c,"service_id");
            if(num(c,"fingerprint_version")!=1)throw new IllegalStateException("Unsupported checkout fingerprint version");
            serviceName=str(c,"service_name");serviceUnit=str(c,"service_unit");state=str(c,"state");method=str(c,"method");
            serviceRevision=num(c,"service_revision");pricingRevision=num(c,"pricing_revision");recipeRevision=num(c,"recipe_revision");
            billableHundredths=num(c,"billable_hundredths");baseRate=num(c,"base_rate");rate=num(c,"rate");total=num(c,"total");
            materialCost=num(c,"material_cost");received=num(c,"received");change=num(c,"change_due");createdMs=num(c,"created_ms");
            producedItems=(int)num(c,"produced_items");materialCount=(int)num(c,"material_count");
            int ix=c.getColumnIndexOrThrow("resolved_ms");resolvedMs=c.isNull(ix)?null:c.getLong(ix);cancelReason=str(c,"cancel_reason");
            String dim=str(c,"dimension_unit");
            inputs=new CheckoutPlan.Inputs((int)num(c,"input_count"),(int)num(c,"pages"),(int)num(c,"copies"),
                str(c,"width"),str(c,"height"),dim==null?null:PricingMath.DimensionUnit.valueOf(dim),CheckoutPlan.UsageBasis.valueOf(str(c,"basis")),received);
        }
        void verifyFingerprint(String expected){
            if(!fingerprint.equals(expected))throw new IllegalStateException("Checkout identity already used for a different confirmation");
        }
        String previewReceipt(){
            if(!"APPLIED".equals(state))throw new IllegalStateException("No recorded preview checkout");
            return "PRINTSHOP POS\nSYNTHETIC CHECKOUT RECORD — NOT A TAX DOCUMENT\nNOT PROOF OF REAL PAYMENT\n"+serviceName+
                "\nBillable: "+PricingMath.displayQuantity(billableHundredths)+"\nRate: "+QuoteMath.display(rate)+
                "\nTotal: "+QuoteMath.display(total)+"\nFixture cash: "+QuoteMath.display(received)+"\nChange: "+QuoteMath.display(change)+"\nRequest: "+id;
        }
    }
    private static String str(Cursor c,String key){return c.getString(c.getColumnIndexOrThrow(key));}
    private static long num(Cursor c,String key){return c.getLong(c.getColumnIndexOrThrow(key));}
    static Record read(SQLiteDatabase db,String id){
        try(Cursor c=db.query("checkout_requests",null,"request_id=?",new String[]{id},null,null,null)){
            return c.moveToFirst()?new Record(c):null;
        }
    }
    static List<Record> pending(SQLiteDatabase db,int limit){
        if(limit<1||limit>50)throw new IllegalArgumentException("Invalid checkout history limit");
        List<Record> result=new ArrayList<>();
        try(Cursor c=db.query("checkout_requests",null,"state='PENDING'",null,null,null,"created_ms,request_id",Integer.toString(limit))){
            while(c.moveToNext())result.add(new Record(c));
        }
        return result;
    }
    static void stage(SQLiteDatabase db,CheckoutPlan.Proposal p){
        ContentValues v=new ContentValues();
        v.put("request_id",p.requestId);v.put("fingerprint_version",1);v.put("fingerprint",p.fingerprint());
        v.put("service_id",p.serviceId);v.put("service_name",p.serviceName);v.put("service_unit",p.serviceUnit);
        v.put("service_revision",p.serviceRevision);v.put("pricing_revision",p.pricingRevision);v.put("recipe_revision",p.recipeRevision);
        v.put("method",p.method.name());v.put("basis",p.basis.name());v.put("input_count",p.inputs.count);
        v.put("pages",p.inputs.pages);v.put("copies",p.inputs.copies);v.put("width",p.inputs.width);v.put("height",p.inputs.height);
        v.put("dimension_unit",p.inputs.dimensionUnit==null?null:p.inputs.dimensionUnit.name());
        v.put("billable_hundredths",p.billableHundredths);v.put("produced_items",p.producedItems);
        v.put("base_rate",p.baseRateCentavos);v.put("rate",p.rateCentavos);v.put("total",p.totalCentavos);
        v.put("material_cost",p.materialCostCentavos);v.put("received",p.receivedCentavos);v.put("change_due",p.changeCentavos);
        v.put("material_count",p.materials.size());v.put("created_ms",System.currentTimeMillis());v.put("state","PENDING");
        db.insertOrThrow("checkout_requests",null,v);
        for(CheckoutPlan.Consumption m:p.materials){
            ContentValues x=new ContentValues();
            x.put("request_id",p.requestId);x.put("material_id",m.materialId);x.put("name",m.name);x.put("unit",m.unit);
            x.put("catalog_revision",m.catalogRevision);x.put("stock_revision",m.stockRevision);x.put("per_unit_milli",m.perUnitMilli);
            x.put("quantity_milli",m.quantityMilli);x.put("before_milli",m.beforeMilli);x.put("after_milli",m.afterMilli);x.put("unit_cost",m.unitCostCentavos);
            db.insertOrThrow("checkout_materials",null,x);
        }
    }
    static void saleAndCash(SQLiteDatabase db,Record r){
        ContentValues s=new ContentValues();s.put("request_id",r.id);s.put("recorded_ms",System.currentTimeMillis());
        db.insertOrThrow("checkout_sales",null,s);
        ContentValues c=new ContentValues();c.put("request_id",r.id);c.put("paid",r.total);c.put("tendered",r.received);c.put("change_due",r.change);
        db.insertOrThrow("checkout_cash",null,c);
    }
    static void link(SQLiteDatabase db,String id,String material,String movement){
        ContentValues v=new ContentValues();v.put("request_id",id);v.put("material_id",material);v.put("movement_id",movement);
        db.insertOrThrow("checkout_stock_links",null,v);
    }
    static void resolve(SQLiteDatabase db,String id,String state,String reason){
        ContentValues v=new ContentValues();v.put("state",state);v.put("resolved_ms",System.currentTimeMillis());v.put("cancel_reason",reason);
        if(db.update("checkout_requests",v,"request_id=? AND state='PENDING'",new String[]{id})!=1)
            throw new IllegalStateException("Checkout request changed; review before retrying");
    }
}
