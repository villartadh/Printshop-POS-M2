package ph.printshop.pos;

import java.util.ArrayList;
import java.util.List;
import static ph.printshop.pos.ReceiptDocument.Paper.*;

public final class ReceiptDocumentTest {
    static int checks;
    static void check(boolean ok){checks++;if(!ok)throw new AssertionError("receipt check "+checks);}
    static void reject(Runnable f){checks++;try{f.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("Invalid receipt accepted");}
    public static void main(String[] args){
        ReceiptDocument r=ReceiptDocument.cash("sale-1","Print pages",null,12500,15000,2500);
        check(r.lines(false).contains("Total: PHP 125.00"));
        check(r.lines(false).contains("Change: PHP 25.00"));
        check(r.lines(false).contains(ReceiptDocument.NOTICE));
        check(r.lines(true).contains("REPRINT - ORIGINAL RECORD"));
        check(r.lines(true).contains("Reference: sale-1"));
        check(r.lines(false).contains("Customer: Walk-in / unassigned"));
        ReceiptDocument credit=ReceiptDocument.credit("sale-2","ID lace","Synthetic customer",12500,2500);
        check(credit.lines(false).contains("Original outstanding: PHP 100.00"));
        check(ReceiptDocument.credit("sale-3","Binding","Customer",1,0).originalDebt==1);
        reject(()->ReceiptDocument.cash("x","Print",null,500,400,0));
        reject(()->ReceiptDocument.cash("x","Print",null,500,600,99));
        reject(()->ReceiptDocument.credit("x","Print",null,500,100));
        reject(()->ReceiptDocument.credit("x","Print","Customer",500,500));
        reject(()->ReceiptDocument.credit("x","Print","Customer",500,-1));
        reject(()->ReceiptDocument.cash("x\nforged","Print",null,500,500,0));
        reject(()->ReceiptDocument.cash("x","Print\u202e",null,500,500,0));
        check(Math.abs(ReceiptDocument.geometry(THERMAL_58,0,150).contentWidthPoints()-50*72/25.4)<0.001);
        check(Math.abs(ReceiptDocument.geometry(THERMAL_80,0,150).contentWidthPoints()-72*72/25.4)<0.001);
        check(Math.abs(ReceiptDocument.geometry(A4,0,0).contentWidthPoints()-190*72/25.4)<0.001);
        check(Math.abs(ReceiptDocument.geometry(LETTER,0,0).contentWidthPoints()-195.9*72/25.4)<0.001);
        check(ReceiptDocument.geometry(CUSTOM,30,30).contentWidthPoints()>0);
        reject(()->ReceiptDocument.geometry(CUSTOM,29,100));
        reject(()->ReceiptDocument.geometry(CUSTOM,501,100));
        reject(()->ReceiptDocument.geometry(CUSTOM,Double.NaN,100));
        reject(()->ReceiptDocument.geometry(THERMAL_58,0,0));
        reject(()->ReceiptDocument.geometry(THERMAL_80,0,1001));
        List<String> original=r.lines(true);
        List<List<String>> pages=ReceiptDocument.paginate(original,3);
        List<String> flattened=new ArrayList<>();for(List<String> page:pages){check(page.size()<=3);flattened.addAll(page);}
        check(flattened.equals(original));check(ReceiptDocument.paginate(original,100).size()==1);
        reject(()->ReceiptDocument.paginate(original,0));
        System.out.println("ReceiptDocument: "+checks+" checks PASS");
    }
}
