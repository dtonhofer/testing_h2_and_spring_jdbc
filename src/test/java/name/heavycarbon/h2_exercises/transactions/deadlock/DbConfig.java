package name.heavycarbon.h2_exercises.transactions.deadlock;

import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.EnsembleId;
import name.heavycarbon.h2_exercises.transactions.db.Stuff;
import org.jetbrains.annotations.NotNull;

public class DbConfig {

    // X: the record on which the two transaction collide
    // Z: An unrelated record in common table that is manipulated by "bravo" instead of X
    // K: An unrelated record in an unrelated table that is manipulated by "bravo" instead of X

    public final String updateString_bravo_did_x_early = "BRAVO UPDATED X EARLY";
    public final String updateString_bravo_did_z_early = "BRAVO UPDATED Z EARLY";
    public final String updateString_bravo_did_k_early = "BRAVO UPDATED K EARLY";

    public final String updateString_bravo_did_x = "BRAVO UPDATED X";
    public final String updateString_alfa_did_x = "ALFA UPDATED X";

    public final Stuff stuff_x = new Stuff(200, EnsembleId.Two, "INITIAL X");
    public final Stuff stuff_z = new Stuff(300, EnsembleId.Two, "INITIAL Z");
    public final Stuff stuff_k = new Stuff(400, EnsembleId.Two, "INITIAL K");

    public void updateX(@NotNull Db db, String string) {
        final var id_x = stuff_x.getId();
        db.updatePayloadById(id_x, string);
    }

    public String readX(@NotNull Db db) {
        final var id_x = stuff_x.getId();
        final Stuff x = db.readById(id_x).orElseThrow();
        return x.getPayload();
    }

}
