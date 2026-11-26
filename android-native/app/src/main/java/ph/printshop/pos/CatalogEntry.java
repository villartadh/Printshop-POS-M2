package ph.printshop.pos;

import java.util.UUID;

/** Preview-only catalog record. Service prices and material costs are distinct kinds. */
public final class CatalogEntry {
    public enum Kind { SERVICE, MATERIAL }
    public final String id, name, unit;
    public final Kind kind;
    public final long centavos, revision;
    public final boolean archived;

    public CatalogEntry(String id, Kind kind, String name, String unit, long centavos, long revision, boolean archived) {
        if (id == null || !UUID.fromString(id).toString().equals(id)) throw new IllegalArgumentException("Invalid catalog identity");
        if (kind == null || revision < 1) throw new IllegalArgumentException("Invalid catalog kind or revision");
        QuoteMath.total(centavos, 1);
        this.id = id;
        this.kind = kind;
        this.name = text(name, 80, "Name");
        this.unit = text(unit, 24, "Unit");
        this.centavos = centavos;
        this.revision = revision;
        this.archived = archived;
    }
    private static String text(String value, int max, String label) {
        if (value == null) throw new IllegalArgumentException(label + " is required");
        String result = value.trim();
        if (result.isEmpty() || result.length() > max || result.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException(label + " must contain 1 to " + max + " printable characters");
        return result;
    }
    public static CatalogEntry create(Kind kind, String name, String unit, String amount) {
        return new CatalogEntry(UUID.randomUUID().toString(), kind, name, unit, QuoteMath.parseCentavos(amount), 1, false);
    }
    public CatalogEntry edit(String name, String unit, String amount) {
        if (archived) throw new IllegalStateException("Restore archived entry before editing");
        return new CatalogEntry(id, kind, name, unit, QuoteMath.parseCentavos(amount), Math.addExact(revision, 1), false);
    }
    public CatalogEntry archive(boolean next) {
        if (archived == next) throw new IllegalStateException("Entry already has requested archive state");
        return new CatalogEntry(id, kind, name, unit, centavos, Math.addExact(revision, 1), next);
    }
}
