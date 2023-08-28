package name.heavycarbon.h2_exercises.transactions.db;

import org.jetbrains.annotations.NotNull;

// ---
// The "ANSI isolation level", a confusing and ill-defined concept, and also badly named.
// (see "A critique of ANSI SQL Isolation Levels", https://arxiv.org/abs/cs/0701157, June 1995)
//
// This is basically the same as the H2 class "org.h2.engine.IsolationLevel", but simpler.
// http://h2database.com/javadoc/org/h2/engine/IsolationLevel.html?highlight=isolationLevel&search=isolationlevel
//
// N.B. Not "comparator" because the levels do not form a total order
// ---

public enum Isol {

    READ_UNCOMMITTED,
    READ_COMMITTED,
    REPEATABLE_READ,
    SERIALIZABLE,
    SNAPSHOT;

    public static Isol fromString(@NotNull String x) {
        String xx = x.toUpperCase();
        switch (xx) {
            case "READ UNCOMMITTED" -> {
                // "A transaction sees (can read) uncommitted data from another transaction"
                // In other words, a transaction is of no use.
                // Allows a "dirty read"
                return READ_UNCOMMITTED;
            }
            case "READ COMMITTED" -> {
                // "A transaction sees changes to data already accessed performed by
                // another transaction once it committed"
                // In other words, the "consensus state" of the database sees in too early.
                // Allows a "non-repeatable (fuzzy) read"
                return READ_COMMITTED;
            }

            case "REPEATABLE READ" -> {

                // "A transaction can data from another transaction only if it has been committed"

                return REPEATABLE_READ;
            }
            case "SERIALIZABLE" -> {
                return SERIALIZABLE;
            }
            case "SNAPSHOT" -> {
                return SNAPSHOT;
            }
            default -> throw new IllegalArgumentException("Cannot map string " + x + " to a valid " + Isol.class.getName());
        }
    }

    public static String toSql (@NotNull Isol isol) {
        return switch (isol) {
            case READ_UNCOMMITTED -> "READ UNCOMMITTED";
            case READ_COMMITTED -> "READ COMMITTED";
            case REPEATABLE_READ -> "REPEATABLE READ";
            case SERIALIZABLE -> "SERIALIZABLE";
            case SNAPSHOT -> "SNAPSHOT";
        };
    }
}
