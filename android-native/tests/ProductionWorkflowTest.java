package ph.printshop.pos;

import static ph.printshop.pos.ProductionWorkflow.Status.*;
import static ph.printshop.pos.ProductionWorkflow.Payment.*;
import java.util.List;

public final class ProductionWorkflowTest {
    private static int checks;
    private static void check(boolean ok){checks++;if(!ok)throw new AssertionError("production check "+checks);}
    private static void rejects(Runnable action){checks++;try{action.run();}catch(IllegalArgumentException|IllegalStateException expected){return;}throw new AssertionError("accepted invalid state "+checks);}
    public static void main(String[] args){
        ProductionWorkflow.FinancialState quote=new ProductionWorkflow.FinancialState(2500,2500,false,UNPAID);
        ProductionWorkflow.FinancialState partial=new ProductionWorkflow.FinancialState(2500,1500,true,PARTIAL);
        ProductionWorkflow.FinancialState paid=new ProductionWorkflow.FinancialState(2500,0,true,PAID);
        ProductionWorkflow.FinancialState reversed=new ProductionWorkflow.FinancialState(2500,0,true,REFUNDED);
        ProductionWorkflow.FinancialState voided=new ProductionWorkflow.FinancialState(2500,0,true,VOIDED);
        ProductionWorkflow.FinancialState free=new ProductionWorkflow.FinancialState(0,0,false,PAID);
        check(ProductionWorkflow.allowed(QUOTATION,quote).equals(List.of(AWAITING_APPROVAL,CANCELLED)));
        check(ProductionWorkflow.allowed(AWAITING_APPROVAL,quote).equals(List.of(QUOTATION,CANCELLED)));
        check(ProductionWorkflow.allowed(AWAITING_APPROVAL,partial).equals(List.of(QUOTATION,PENDING)));
        check(ProductionWorkflow.allowed(PENDING,paid).equals(List.of(IN_PRODUCTION)));
        check(ProductionWorkflow.allowed(IN_PRODUCTION,partial).equals(List.of(QUALITY_CHECKING)));
        check(ProductionWorkflow.allowed(QUALITY_CHECKING,partial).equals(List.of(IN_PRODUCTION,READY_FOR_PICKUP)));
        check(ProductionWorkflow.allowed(READY_FOR_PICKUP,partial).equals(List.of(QUALITY_CHECKING)));
        check(ProductionWorkflow.allowed(READY_FOR_PICKUP,paid).equals(List.of(QUALITY_CHECKING,COMPLETED)));
        check(ProductionWorkflow.allowed(READY_FOR_PICKUP,free).contains(COMPLETED));
        check(ProductionWorkflow.allowed(CANCELLED,paid).isEmpty());
        check(ProductionWorkflow.allowed(COMPLETED,paid).isEmpty());
        for(ProductionWorkflow.Status status:ProductionWorkflow.Status.values()){
            check(ProductionWorkflow.allowed(status,reversed).isEmpty());
            check(ProductionWorkflow.allowed(status,voided).isEmpty());
            rejects(()->ProductionWorkflow.requireTransition(status,status,paid));
            check(ProductionWorkflow.afterReversal(status)==(status==QUOTATION||status==AWAITING_APPROVAL||status==PENDING?CANCELLED:status));
        }
        rejects(()->ProductionWorkflow.requireTransition(PENDING,COMPLETED,paid));
        rejects(()->ProductionWorkflow.requireTransition(READY_FOR_PICKUP,COMPLETED,partial));
        rejects(()->ProductionWorkflow.requireTransition(PENDING,CANCELLED,paid));
        rejects(()->ProductionWorkflow.requireTransition(AWAITING_APPROVAL,PENDING,quote));
        rejects(()->new ProductionWorkflow.FinancialState(2500,1,true,PAID));
        rejects(()->new ProductionWorkflow.FinancialState(2500,0,false,PAID));
        rejects(()->new ProductionWorkflow.FinancialState(2500,0,true,PARTIAL));
        rejects(()->new ProductionWorkflow.FinancialState(2500,1000,false,PARTIAL));
        rejects(()->new ProductionWorkflow.FinancialState(2500,-1,true,PAID));
        rejects(()->new ProductionWorkflow.FinancialState(2500,2501,true,UNPAID));
        rejects(()->new ProductionWorkflow.FinancialState(2500,1,true,VOIDED));
        rejects(()->new ProductionWorkflow.FinancialState(2500,0,false,REFUNDED));
        System.out.println("ProductionWorkflow: "+checks+" checks PASS");
    }
}
