package name.heavycarbon.h2_exercises.transactions.common;

import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AgentRunnableBase;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.agent.TransactionResult2;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.session.Isol;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

// ---
// Common code for an agent that "reads" the database in a single
// transaction. It references the class providing the methods marked
// as @Transactional through the "readerTx" field (actually an interface).
// ---

@Slf4j
public class ReaderRunnable extends AgentRunnableBase {

    private final TransactionalInterface_Reader readerTx;

    @Getter
    private Optional<TransactionResult2> result = Optional.empty();

    public ReaderRunnable(@NotNull Db db,
                          @NotNull AppState appState,
                          @NotNull AgentId agentId,
                          @NotNull Isol isol,
                          @NotNull TransactionalInterface_Reader readerTx) {
        super(db, appState, agentId, isol);
        this.readerTx = readerTx;
    }

    @Override
    public void run() {
        try {
            result = readerTx.runStateMachineLoopInsideTransaction(this);
        } catch (Throwable th) {
            AgentRunnableBase.throwableMessage(log, agentId, th);
        }
    }
}
