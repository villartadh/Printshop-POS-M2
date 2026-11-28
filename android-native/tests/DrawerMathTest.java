package ph.printshop.pos;

/** Exact fixture drawer review arithmetic; no Android dependencies. */
public final class DrawerMathTest {
    private static int checks;
    private static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;}
    private static void reject(Runnable test){try{test.run();}catch(IllegalArgumentException ex){checks++;return;}throw new AssertionError("Invalid drawer review accepted");}
    public static void main(String[] args){
        ShiftMath.DrawerKind expense=ShiftMath.DrawerKind.EXPENSE,in=ShiftMath.DrawerKind.CASH_IN,out=ShiftMath.DrawerKind.CASH_OUT;
        long max=ShiftMath.MAX_DRAWER;
        check(ShiftMath.drawerAfter(1000,expense,150)==850,"expense");
        check(ShiftMath.drawerAfter(1000,out,150)==850,"cash out");
        check(ShiftMath.drawerAfter(1000,in,150)==1150,"cash in");
        check(ShiftMath.drawerAfter(1,expense,1)==0,"exact depletion");
        check(ShiftMath.drawerAfter(max,out,max)==0,"maximum exact debit");
        check(ShiftMath.drawerAfter(0,in,max)==max,"maximum exact credit");
        check(ShiftMath.drawerAfter(max-1,in,1)==max,"last centavo");
        for(ShiftMath.DrawerKind kind:ShiftMath.DrawerKind.values()){
            reject(()->ShiftMath.drawerAfter(0,kind,0));
            reject(()->ShiftMath.drawerAfter(100,kind,-1));
            reject(()->ShiftMath.drawerAfter(-1,kind,1));
            reject(()->ShiftMath.drawerAfter(max+1,kind,1));
            reject(()->ShiftMath.drawerAfter(0,kind,max+1));
        }
        reject(()->ShiftMath.drawerAfter(0,expense,1));
        reject(()->ShiftMath.drawerAfter(0,out,1));
        reject(()->ShiftMath.drawerAfter(max,in,1));
        reject(()->ShiftMath.drawerAfter(100,null,1));
        check(ShiftMath.category(expense,"  Synthetic paper  ").equals("Synthetic paper"),"trim category");
        check(ShiftMath.category(expense,"x".repeat(80)).length()==80,"max category");
        for(String invalid:new String[]{null,""," ","x".repeat(81),"bad\ntext","bad"+(char)0+"text","bad"+(char)127+"text"})reject(()->ShiftMath.category(expense,invalid));
        check(ShiftMath.category(in,null)==null,"no adjustment category");
        check(ShiftMath.category(out,null)==null,"no adjustment category");
        reject(()->ShiftMath.category(in,"supplies"));
        reject(()->ShiftMath.category(out,""));
        reject(()->ShiftMath.category(null,"supplies"));
        long balance=1000;
        balance=ShiftMath.addPayment(balance,500);
        balance=ShiftMath.drawerAfter(balance,expense,250);
        balance=ShiftMath.drawerAfter(balance,in,1000);
        balance=ShiftMath.addPayment(balance,500);
        balance=ShiftMath.drawerAfter(balance,out,200);
        check(balance==2550,"mixed drawer balance");
        check(ShiftMath.variance(balance,2500)==-50,"variance");
        boolean exact=true;
        for(int i=0;i<1000;i++){
            long credited=ShiftMath.drawerAfter(0,in,max-i);
            exact&=ShiftMath.drawerAfter(credited,out,max-i)==0;
        }
        check(exact,"1000 bounded running-chain cycles");
        System.out.println("DrawerMath: "+checks+" checks passed");
    }
}
