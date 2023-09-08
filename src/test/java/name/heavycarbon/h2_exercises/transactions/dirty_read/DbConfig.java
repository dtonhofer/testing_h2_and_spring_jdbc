package name.heavycarbon.h2_exercises.transactions.dirty_read;

import name.heavycarbon.h2_exercises.transactions.db.EnsembleId;
import name.heavycarbon.h2_exercises.transactions.db.Stuff;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;

// ---
// Information on how to set up the database and what to modify
// ---

public class DbConfig {

    public final StuffId initRowId = new StuffId(100);

    // initial row in database
    public final Stuff initRow = new Stuff(initRowId, EnsembleId.Two, "INIT");

    // the initRow will be updated to this
    public final Stuff updateRow = new Stuff(initRowId, EnsembleId.Two, "XXX");

    // this row will be additionally inserted
    public final Stuff insertRow = new Stuff(200, EnsembleId.Two, "INSERT");

    // this row is initially in the database and will be deleted
    public final Stuff deleteRow = new Stuff(300, EnsembleId.Two, "DELETE");

}
