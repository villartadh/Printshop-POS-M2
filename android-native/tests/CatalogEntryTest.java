package ph.printshop.pos;
public final class CatalogEntryTest {
    private static int checks;
    private static void check(boolean ok) { if (!ok) throw new AssertionError("check " + (checks+1)); checks++; }
    private static void reject(Runnable run) { try { run.run(); } catch (IllegalArgumentException | IllegalStateException expected) { checks++; return; } throw new AssertionError("expected rejection"); }
    public static void main(String[] args) {
        CatalogEntry a = CatalogEntry.create(CatalogEntry.Kind.SERVICE, "  Test print  ", "page", "1.99");
        check(a.name.equals("Test print"));
        check(a.centavos == 199 && a.revision == 1 && !a.archived);
        CatalogEntry b = a.edit("Test color", "page", "2.01");
        check(b.id.equals(a.id) && b.kind == a.kind && b.revision == 2 && b.centavos == 201);
        CatalogEntry c = b.archive(true);
        check(c.archived && c.revision == 3 && c.centavos == 201);
        reject(() -> c.edit("X", "page", "1"));
        reject(() -> c.archive(true));
        check(c.archive(false).revision == 4 && !c.archive(false).archived);
        reject(() -> CatalogEntry.create(CatalogEntry.Kind.SERVICE, "", "page", "1"));
        reject(() -> CatalogEntry.create(CatalogEntry.Kind.MATERIAL, "Test paper", "", "1"));
        reject(() -> CatalogEntry.create(CatalogEntry.Kind.MATERIAL, "Test paper", "sheet", "1.001"));
        reject(() -> CatalogEntry.create(null, "Test paper", "sheet", "1"));
        reject(() -> CatalogEntry.create(CatalogEntry.Kind.MATERIAL, "A\nB", "sheet", "1"));
        System.out.println("CatalogEntry: " + checks + " checks passed");
    }
}
