package name.heavycarbon.h2_exercises.transactions.deadlock;

import name.heavycarbon.h2_exercises.transactions.agent.AgentContainer;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.common.AgentRunnableSyncOnAppStateOutsideTransaction;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import org.jetbrains.annotations.NotNull;

public class Runnable_Alfa extends AgentRunnableSyncOnAppStateOutsideTransaction {

    public Runnable_Alfa(@NotNull Db db,
                          @NotNull AppState appState,
                          @NotNull AgentId agentId,
                          @NotNull Isol isol,
                          @NotNull AgentContainer.Op op,
                          @NotNull TransactionalGateway txGw) {
        super(db, appState, agentId, isol, op, txGw);
    }

    @Override
    protected void switchByAppStateOutsideTransaction() throws InterruptedException {

    }
}