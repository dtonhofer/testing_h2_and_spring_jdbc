package name.heavycarbon.h2_exercises.transactions.sql_timeout;

import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.EnsembleId;
import name.heavycarbon.h2_exercises.transactions.db.Stuff;
import org.jetbrains.annotations.NotNull;

public class DbConfig {

    // stuff_a and stuff_b are written immediately by the agents so we can check
    // whether there was proper rollback.
    // The agents then collide on writing to stuff_x

    public final Stuff stuff_a = new Stuff(100, EnsembleId.Two, "---");
    public final Stuff stuff_b = new Stuff(101, EnsembleId.Two, "---");
    public final Stuff stuff_x = new Stuff(200, EnsembleId.Two, "---");

    public final String updateString_bravo_did_x = "BRAVO UPDATED X";
    public final String updateString_alfa_did_x = "ALFA UPDATED X";
    public final String updateString_alfa_marker = "ALFA WAS HERE";

    public void updateX(@NotNull Db db, String string) {
        final var id_x = stuff_x.getId();
        db.updatePayloadById(id_x, string);
    }

    public Stuff readX(@NotNull Db db) {
        final var id_x = stuff_x.getId();
        return db.readById(id_x).orElseThrow();
    }
}
