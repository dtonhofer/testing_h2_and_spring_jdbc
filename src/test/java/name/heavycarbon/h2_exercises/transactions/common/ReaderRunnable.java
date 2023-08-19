package name.heavycarbon.h2_exercises.transactions.common;

import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AgentRunnable;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.agent.TransactionResult2;
import name.heavycarbon.h2_exercises.transactions.common.ReaderTransactionalInterface;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.session.Isol;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

@Slf4j
public class ReaderRunnable extends AgentRunnable {

    private final ReaderTransactionalInterface readerTx;

    @Getter
    private Optional<TransactionResult2> result = Optional.empty();

    public ReaderRunnable(@NotNull Db db,
                          @NotNull AppState appState,
                          @NotNull AgentId agentId,
                          @NotNull Isol isol,
                          @NotNull ReaderTransactionalInterface readerTx) {
        super(db, appState, agentId, isol);
        this.readerTx = readerTx;
    }

    @Override
    public void run() {
        try {
            result = readerTx.runInsideTransaction(this);
        } catch (Throwable th) {
            AgentRunnable.throwableMessage(log, agentId, th);
        }
    }
}
