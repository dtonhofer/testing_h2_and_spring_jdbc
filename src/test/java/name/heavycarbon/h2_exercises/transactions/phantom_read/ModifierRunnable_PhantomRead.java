package name.heavycarbon.h2_exercises.transactions.phantom_read;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.AgentContainerAbstract.Op;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AgentRunnableAbstract;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.EnsembleId;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import name.heavycarbon.h2_exercises.transactions.phantom_read.AgentContainer_PhantomRead.PhantomicPredicate;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class ModifierRunnable_PhantomRead extends AgentRunnableAbstract {

    private final TransactionalGateway txGw;

    // Instructions as to what modifications to apply to the database

    private final SetupForPhantomReads mods;
    private final PhantomicPredicate phantomicPredicate;

    // ---

    public ModifierRunnable_PhantomRead(@NotNull Db db,
                                        @NotNull AppState appState,
                                        @NotNull AgentId agentId,
                                        @NotNull Isol isol,
                                        @NotNull Op op,
                                        @NotNull PhantomicPredicate phantomicPredicate,
                                        @NotNull SetupForPhantomReads mods,
                                        @NotNull TransactionalGateway txGw) {
        super(db, appState, agentId, isol, op);
        this.txGw = txGw;
        this.mods = mods;
        this.phantomicPredicate = phantomicPredicate;
    }

    @Override
    public void run() {
        log.info("{} starting.", getAgentId());
        try {
            txGw.wrapIntoTransaction(this::syncOnAppState, getAgentId(), getIsol());
        } catch (Exception ex) {
            // Only Exceptions, Errors are let through
            exceptionMessage(log, ex);
        }
    }

    // Having opened a transaction, we are called back ... here!

    public void syncOnAppState() {
        synchronized (getAppState()) {
            log.info("{} in critical section.", getAgentId());
            catchInterruptedExceptionFromStateMachineLoop();
        }
    }

    private void catchInterruptedExceptionFromStateMachineLoop() {
        boolean interrupted = false; // set when the thread notices it has been interrupted
        try {
            runStateMachineLoop();
        } catch (InterruptedException ex) {
            interrupted = true;
        }
        log.info(finalMessage(interrupted));
    }

    private void runStateMachineLoop() throws InterruptedException {
        while (isContinue()) {
            switchByAppState();
        }
    }

    private void switchByAppState() throws InterruptedException {
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
            case Insert -> getDb().insert(mods.insertMe);
            case Delete -> getDb().deleteById(mods.deleteMe.getId());
            case UpdateIntoPredicateSet -> {
                final StuffId id = mods.updateForMovingIn.getId();
                final String newPayload = mods.updateForMovingInChanged.getPayload();
                final EnsembleId newEnsembleId = mods.updateForMovingInChanged.getEnsembleId();
                getDb().updateEnsembleById(id, newEnsembleId);
                getDb().updatePayloadById(id, newPayload);
            }
            case UpdateOutOfPredicateSet -> {
                final StuffId id = mods.updateForMovingOut.getId();
                final String newPayload = mods.updateForMovingOutChanged.getPayload();
                final EnsembleId newEnsembleId = mods.updateForMovingOutChanged.getEnsembleId();
                getDb().updateEnsembleById(id, newEnsembleId);
                getDb().updatePayloadById(id, newPayload);
            }
            default -> throw new IllegalArgumentException("Unhandled op " + getOp());
        }
    }

}
