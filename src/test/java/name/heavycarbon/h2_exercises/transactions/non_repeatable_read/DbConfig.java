package name.heavycarbon.h2_exercises.transactions.non_repeatable_read;

import name.heavycarbon.h2_exercises.transactions.db.EnsembleId;
import name.heavycarbon.h2_exercises.transactions.db.Stuff;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;

// ---
// Information on how to set up the database and what to modify
// ---

public class DbConfig {

    private final StuffId initRowId = new StuffId(100);

    // initial row in database
    public final Stuff initRow = new Stuff(initRowId, EnsembleId.Two, "INITIAL");

    // the initRow will be updated to this
    public final Stuff updateRow = new Stuff(initRowId, EnsembleId.Two, "UPDATED");

    // this row will be additionally inserted
    public final Stuff insertRow = new Stuff(new StuffId(200), EnsembleId.Two, "INSERTED");

    // this row is initially in the database and will be deleted
    public final Stuff deleteRow = new Stuff(new StuffId(300), EnsembleId.Two, "DELETED");

}
