package ph.printshop.pos;
public final class QuoteMathTest {
    private static int checks;
    private static void check(boolean result) { if (!result) throw new AssertionError("check " + (checks + 1)); checks++; }
    private static void reject(Runnable operation) { try { operation.run(); } catch (IllegalArgumentException expected) { checks++; return; } throw new AssertionError("Expected rejection"); }
    public static void main(String[] args) {
        check(QuoteMath.parseCentavos("0.01") == 1);
        check(QuoteMath.parseCentavos("12.3") == 1230);
        check(QuoteMath.total(199, 3) == 597);
        check(QuoteMath.change(597, 1000) == 403);
        check(QuoteMath.display(403).equals("PHP 4.03"));
        check(QuoteMath.total(0, 1) == 0);
        reject(() -> QuoteMath.parseCentavos("1.001"));
        reject(() -> QuoteMath.parseCentavos("-1"));
        reject(() -> QuoteMath.parseCentavos("1e3"));
        reject(() -> QuoteMath.parseCentavos("1000000000"));
        reject(() -> QuoteMath.total(100, 0));
        reject(() -> QuoteMath.total(100, 100001));
        reject(() -> QuoteMath.change(100, 99));
        System.out.println("QuoteMath: " + checks + " checks passed");
    }
}
