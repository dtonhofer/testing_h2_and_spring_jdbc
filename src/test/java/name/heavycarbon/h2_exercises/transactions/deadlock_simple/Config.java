package name.heavycarbon.h2_exercises.transactions.deadlock_simple;

import lombok.Value;
import name.heavycarbon.h2_exercises.transactions.agent.PrintException;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import org.jetbrains.annotations.NotNull;

// ---
// How to configure the "deadlock elicitation" test
// ---

@Value
public class Config {

    // The update of X can be performed by Alfa in state 1 (early, before Bravo
    // does its operation) or state 3 (late, after Bravo does its operation).
    // This should not change the outcome of the test!

    public enum AlfaUpdateTiming {Early, Late}

    // Bravo's operation can be varied, we want to test them all!
    // ReadX, UpdateX: Read or Update the record on which the two transaction collide
    // ReadZ, UpdateZ, InsertZ, DeleteZ: Perform an operation on an unrelated record in the common table
    // UpdateK, InsertK, DeleteK: Perform an operation on an unrelated record in an unrelated table

    public enum BravoFirstOp {

        ReadX("x", true), UpdateX("x", true),
        ReadZ("z", true), UpdateZ("z", true), InsertZ("z", false), DeleteZ("x", true),
        ReadK("k", true), UpdateK("k", true), InsertK("k", false), DeleteK("k", true);

        public final String rowName;
        public final boolean rowInitiallyExists;

        BravoFirstOp(@NotNull String rowName, boolean rowInitiallyExists) {
            this.rowName = rowName;
            this.rowInitiallyExists = rowInitiallyExists;
        }

    }

    // On startup, we may also try to introduce a random delay of a few milliseconds to see
    // whether that changes anything

    public enum RandomStartupDelay {Yes, No}

    Isol isol;
    AlfaUpdateTiming alfaUpdateTiming; // only for ALFA but also given to BRAVO via this class
    BravoFirstOp bravoFirstOp; // only for BRAVO but also given to ALFA via this class
    RandomStartupDelay randomStartupDelay;
    PrintException pex;

    // various convenience functions

    public boolean isRandomStartupDelay() {
        return randomStartupDelay == RandomStartupDelay.Yes;
    }

    public boolean isAlfaUpdateEarly() {
        return alfaUpdateTiming == AlfaUpdateTiming.Early;
    }

    public boolean isAlfaUpdateLate() {
        return alfaUpdateTiming == AlfaUpdateTiming.Late;
    }

}
