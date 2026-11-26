package ph.printshop.pos;
import java.util.List;
public final class MaterialCostTest {
    static int checks;
    static void check(boolean ok){if(!ok)throw new AssertionError("check "+(checks+1));checks++;}
    static void reject(Runnable r){try{r.run();}catch(IllegalArgumentException expected){checks++;return;}throw new AssertionError("expected rejection");}
    public static void main(String[] args){
        check(MaterialCost.estimate(List.of(new MaterialCost.Component(199,500,5000)),3).costCentavos==299);
        check(MaterialCost.estimate(List.of(new MaterialCost.Component(1,400,5000),new MaterialCost.Component(1,400,5000)),1).costCentavos==1);
        check(MaterialCost.estimate(List.of(new MaterialCost.Component(1,500,500)),1).costCentavos==1);
        check(MaterialCost.estimate(List.of(new MaterialCost.Component(1,499,500)),1).costCentavos==0);
        check(!MaterialCost.estimate(List.of(new MaterialCost.Component(199,1000,999)),1).stockSufficient);
        check(MaterialCost.estimate(List.of(new MaterialCost.Component(199,1000,1000)),1).stockSufficient);
        check(MaterialCost.estimate(List.of(),1).costCentavos==0);
        reject(()->new MaterialCost.Component(-1,1000,1000));
        reject(()->new MaterialCost.Component(100,0,1000));
        reject(()->new MaterialCost.Component(100,1000,-1));
        reject(()->MaterialCost.estimate(List.of(),0));
        reject(()->MaterialCost.estimate(List.of(new MaterialCost.Component(1,StockMath.MAX,0)),2));
        System.out.println("MaterialCost: "+checks+" checks passed");
    }
}
