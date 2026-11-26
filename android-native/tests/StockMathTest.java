package ph.printshop.pos;
public final class StockMathTest {
    static int checks;
    static void check(boolean ok) { if (!ok) throw new AssertionError("check " + (checks+1)); checks++; }
    static void reject(Runnable action) { try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; } throw new AssertionError("expected rejection"); }
    public static void main(String[] args) {
        check(StockMath.parse("0.001") == 1);
        check(StockMath.parse("12.345") == 12345);
        check(StockMath.apply(1000, 501) == 1501);
        check(StockMath.apply(1501, -1501) == 0);
        check(StockMath.display(1501).equals("1.501"));
        check(StockMath.reason("  Count correction  ").equals("Count correction"));
        reject(() -> StockMath.parse("1.0001"));
        reject(() -> StockMath.parse("-1"));
        reject(() -> StockMath.parse("1e2"));
        reject(() -> StockMath.apply(1, -2));
        reject(() -> StockMath.apply(StockMath.MAX, 1));
        reject(() -> StockMath.apply(0, 0));
        reject(() -> StockMath.reason(" "));
        reject(() -> StockMath.validateRequestId("not-a-uuid"));
        StockMath.validateRequestId(java.util.UUID.randomUUID().toString()); checks++;
        System.out.println("StockMath: " + checks + " checks passed");
    }
}
