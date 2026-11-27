package ph.printshop.pos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable non-fiscal receipt content and explicit physical page geometry.
 * No printer invocation or financial mutation occurs in this model.
 */
final class ReceiptDocument {
    static final String NOTICE="NON-TAX DOCUMENT - NOT A TAX INVOICE";
    static final String TRAINING="SYNTHETIC TRAINING ONLY";
    enum Kind { CASH, CREDIT }
    enum Paper { THERMAL_58, THERMAL_80, A4, LETTER, CUSTOM }
    static final class Geometry {
        final double widthMm,heightMm,marginMm;
        Geometry(double width,double height,double margin){
            if(!Double.isFinite(width)||!Double.isFinite(height)||width<=2*margin||height<=2*margin)
                throw new IllegalArgumentException("Invalid receipt page");
            widthMm=width;heightMm=height;marginMm=margin;
        }
        double contentWidthPoints(){return (widthMm-2*marginMm)*72/25.4;}
        double contentHeightPoints(){return (heightMm-2*marginMm)*72/25.4;}
    }
    static Geometry geometry(Paper paper,double customWidth,double thermalHeight){
        if(paper==null)throw new IllegalArgumentException("Choose receipt paper");
        if(paper==Paper.A4)return new Geometry(210,297,10);
        if(paper==Paper.LETTER)return new Geometry(215.9,279.4,10);
        double width=paper==Paper.THERMAL_58?58:paper==Paper.THERMAL_80?80:customWidth;
        if(!Double.isFinite(width)||width<30||width>500||!Double.isFinite(thermalHeight)||thermalHeight<30||thermalHeight>1000)
            throw new IllegalArgumentException("Unsupported receipt paper dimensions");
        return new Geometry(width,thermalHeight,4);
    }
    final Kind kind;
    final String reference,service,customer;
    final long total,paid,tender,change,originalDebt;
    private ReceiptDocument(Kind kind,String reference,String service,String customer,long total,long paid,long tender,long change){
        if(kind==null||total<=0||total>9999999999900000L||paid<0||paid>total||tender<paid||tender>9999999999900000L||change!=tender-paid||
            (kind==Kind.CASH&&paid!=total)||(kind==Kind.CREDIT&&(paid>=total||tender!=paid||change!=0)))
            throw new IllegalArgumentException("Invalid receipt amounts");
        this.kind=kind;this.reference=text(reference,80,true);this.service=text(service,80,true);
        this.customer=text(customer,80,kind==Kind.CREDIT);
        this.total=total;this.paid=paid;this.tender=tender;this.change=change;this.originalDebt=total-paid;
    }
    private static String text(String raw,int max,boolean required){
        if(raw==null)raw="";
        String value=raw.trim();
        if(value.length()>max||(required&&value.isEmpty())||value.codePoints().anyMatch(c->Character.isISOControl(c)||Character.getType(c)==Character.FORMAT))
            throw new IllegalArgumentException("Invalid receipt text");
        return value;
    }
    static ReceiptDocument cash(String reference,String service,String customer,long total,long tender,long change){
        return new ReceiptDocument(Kind.CASH,reference,service,customer,total,total,tender,change);
    }
    static ReceiptDocument credit(String reference,String service,String customer,long total,long paid){
        return new ReceiptDocument(Kind.CREDIT,reference,service,customer,total,paid,paid,0);
    }
    /** Reprints show the original payment snapshot, not later collections or refunds. */
    List<String> lines(boolean copy){
        List<String> rows=new ArrayList<>();
        rows.add("PRINTSHOP POS");rows.add(TRAINING);rows.add(NOTICE);
        rows.add(copy?"REPRINT - ORIGINAL RECORD":"RECORDED RECEIPT");
        rows.add("Reference: "+reference);rows.add("Service: "+service);
        rows.add("Customer: "+(customer.isEmpty()?"Walk-in / unassigned":customer));
        rows.add("Total: "+QuoteMath.display(total));rows.add("Original cash paid: "+QuoteMath.display(paid));
        if(kind==Kind.CASH){rows.add("Tendered: "+QuoteMath.display(tender));rows.add("Change: "+QuoteMath.display(change));}
        else rows.add("Original outstanding: "+QuoteMath.display(originalDebt));
        rows.add("Later collections or compensation not included.");
        rows.add("Not proof of real payment.");
        return Collections.unmodifiableList(rows);
    }
    /** Deterministic pagination using caller-selected measured line count; renderer must measure and wrap text. */
    static List<List<String>> paginate(List<String> measuredLines,int lineCapacity){
        if(measuredLines==null||measuredLines.isEmpty()||lineCapacity<1||lineCapacity>10000)throw new IllegalArgumentException("Invalid receipt layout");
        List<List<String>> pages=new ArrayList<>();
        for(int i=0;i<measuredLines.size();i+=lineCapacity){
            List<String> page=new ArrayList<>(measuredLines.subList(i,Math.min(measuredLines.size(),i+lineCapacity)));
            if(page.stream().anyMatch(x->x==null))throw new IllegalArgumentException("Invalid receipt line");
            pages.add(Collections.unmodifiableList(page));
        }
        return Collections.unmodifiableList(pages);
    }
}
