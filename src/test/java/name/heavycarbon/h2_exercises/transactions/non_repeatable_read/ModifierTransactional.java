package name.heavycarbon.h2_exercises.transactions.non_repeatable_read;

import name.heavycarbon.h2_exercises.transactions.agent.AgentContainerBase.Op;
import name.heavycarbon.h2_exercises.transactions.common.ModifierTransactionalInterface;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.EnsembleId;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import name.heavycarbon.h2_exercises.transactions.session.Isol;
import name.heavycarbon.h2_exercises.transactions.session.SessionManip;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// ---
// Class holding the methods annotated "@Transactional"
// Spring will weave some code around those methods.
// - This class must be a Component, otherwise @Transactional does nothing.
// - @Transactional annotations must be on the "public" methods otherwise @Transactional does nothing.
// ---

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
                db.insert(EnsembleId.Two, "BBB");
            }
            case Update -> {
                db.updateById(stuffIdOfRowToModify, "XXX");
            }
            case Delete -> {
                int count = db.deleteById(stuffIdOfRowToModify);
                Assertions.assertEquals(count, 1);
            }
        }
    }

}