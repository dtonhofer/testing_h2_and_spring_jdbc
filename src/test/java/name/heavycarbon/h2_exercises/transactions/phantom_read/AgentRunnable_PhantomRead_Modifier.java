package name.heavycarbon.h2_exercises.transactions.phantom_read;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.AgentContainer.Op;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.common.AgentRunnableWithAllActionsInsideTransaction;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.EnsembleId;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class AgentRunnable_PhantomRead_Modifier extends AgentRunnableWithAllActionsInsideTransaction {

    private final Setup setup;

    public AgentRunnable_PhantomRead_Modifier(@NotNull Db db,
                                              @NotNull AppState appState,
                                              @NotNull AgentId agentId,
                                              @NotNull Isol isol,
                                              @NotNull Op op,
                                              @NotNull Setup setup,
                                              @NotNull TransactionalGateway txGw) {
        super(db, appState, agentId, isol, op, PrintException.No, txGw);
        this.setup = setup;
    }

    // This is eventually called from the state machine loop inside a transaction

    protected void switchByAppState() throws InterruptedException {
        switch (getAppState().get()) {
            case 1 -> {
                switchByOpAndPhantomicPredicate();
                incState();
                setTerminatedNicely();
                setStop();
            }
            default -> waitOnAppState();
        }
    }

    private void switchByOpAndPhantomicPredicate() {
        switch (getOp()) {
            case Insert -> getDb().insert(setup.insertMe);
            case Delete -> getDb().deleteById(setup.deleteMe.getId());
            case UpdateIntoPredicateSet -> {
                final StuffId id = setup.updateForMovingIn.getId();
                final String newPayload = setup.updateForMovingInChanged.getPayload();
                final EnsembleId newEnsembleId = setup.updateForMovingInChanged.getEnsembleId();
                getDb().updateEnsembleById(id, newEnsembleId);
                getDb().updatePayloadById(id, newPayload);
            }
            case UpdateOutOfPredicateSet -> {
                final StuffId id = setup.updateForMovingOut.getId();
                final String newPayload = setup.updateForMovingOutChanged.getPayload();
                final EnsembleId newEnsembleId = setup.updateForMovingOutChanged.getEnsembleId();
                getDb().updateEnsembleById(id, newEnsembleId);
                getDb().updatePayloadById(id, newPayload);
            }
            default -> throw new IllegalArgumentException("Unhandled op " + getOp());
        }
    }

}
