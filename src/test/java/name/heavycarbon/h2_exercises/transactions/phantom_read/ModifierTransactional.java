package name.heavycarbon.h2_exercises.transactions.phantom_read;

import name.heavycarbon.h2_exercises.transactions.agent.AgentContainerBase.Op;
import name.heavycarbon.h2_exercises.transactions.common.ModifierTransactionalInterface;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.EnsembleId;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import name.heavycarbon.h2_exercises.transactions.session.Isol;
import name.heavycarbon.h2_exercises.transactions.session.SessionManip;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// ---
// Class holding the methods annotated "@Transactional"
// Spring will weave some code around those methods.
// - This class must be a Component, otherwise @Transactional does nothing.
// - @Transactional annotations must be on the "public" methods otherwise @Transactional does nothing.
// ---

// This class being "autowired", pass any variable values through method calls
// rather than the constructor.

// The transaction level is left unspecified in the annotation and set explicitly
// inside the transaction.
// @Transactional
// instead of
// @Transactional(isolation = Isolation.READ_UNCOMMITTED)

@Slf4j
@Component
public class ModifierTransactional implements ModifierTransactionalInterface {

    @Autowired
    private Db db;

    @Autowired
    private SessionManip sm;

    @Transactional
    public void runInsideTransaction(@NotNull Op op, @NotNull Isol isol, @NotNull StuffId stuffIdOfRowToModify) {
        sm.setMySessionIsolationLevel(isol);
        switch (op) {
            case Insert -> {
                // add a record matching the reader's predicate (must be "ensemble 2")
                db.insert(EnsembleId.Two, "YYY");
            }
            case Update -> {
                // Update a record matching the reader's predicate
                // so that it *no longer matches* (move from "ensemble 2" to "ensemble 1")
                int count = db.updateById(stuffIdOfRowToModify, EnsembleId.One);
                assert count == 1;
            }
            case Delete -> {
                // Delete a record matching the reader's predicate
                // (must have been "ensemble 2")
                int count = db.deleteById(stuffIdOfRowToModify);
                assert count == 1;
            }
        }
    }

}
