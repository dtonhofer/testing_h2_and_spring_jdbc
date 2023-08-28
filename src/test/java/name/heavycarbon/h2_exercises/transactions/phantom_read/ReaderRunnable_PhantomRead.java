package name.heavycarbon.h2_exercises.transactions.phantom_read;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.AgentContainerAbstract.Op;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AgentRunnableAbstract;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.common.DualResult;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import name.heavycarbon.h2_exercises.transactions.db.Stuff;
import name.heavycarbon.h2_exercises.transactions.phantom_read.AgentContainer_PhantomRead.PhantomicPredicate;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

@Slf4j
public class ReaderRunnable_PhantomRead extends AgentRunnableAbstract {

    private final @NotNull TransactionalGateway txGw;

    // Store the result of reading here so that the main thread can pick it up

    private volatile List<Stuff> result1 = null;
    private volatile List<Stuff> result2 = null;

    private final @NotNull PhantomicPredicate phantomicPredicate;

    private final @NotNull SetupForPhantomReads setup;

    // ---

    public ReaderRunnable_PhantomRead(@NotNull Db db,
                                      @NotNull AppState appState,
                                      @NotNull AgentId agentId,
                                      @NotNull Isol isol,
                                      @NotNull Op op,
                                      @NotNull PhantomicPredicate phantomicPredicate,
                                      @NotNull SetupForPhantomReads setup,
                                      @NotNull TransactionalGateway txGw) {
        super(db, appState, agentId, isol, op);
        this.txGw = txGw;
        this.phantomicPredicate = phantomicPredicate;
        this.setup = setup;
    }

    public @NotNull Optional<DualResult> getResult() {
        if (result1 != null && result2 != null) {
            return Optional.of(new DualResult(result1, result2));
        } else {
            return Optional.empty();
        }
    }

    @Override
    public void run() {
        log.info("{} starting.", getAgentId());
        try {
            // >>> call a method marked @Transactional on an instance injected by Spring
            txGw.wrapIntoTransaction(this::syncOnAppState, getAgentId(), getIsol());
            // <<<
        } catch (Exception ex) {
            // Only Exceptions, Errors are let through
            exceptionMessage(log, ex);
        }
    }

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
            case 0 -> {
                result1 = readByPhantomicPredicate();
                incState();
            }
            case 2 -> {
                result2 = readByPhantomicPredicate();
                incState();
                setTerminatedNicely();
                setStop();
            }
            default -> waitOnAppState();
        }
    }

    private @NotNull List<Stuff> readByPhantomicPredicate() {
        return switch (phantomicPredicate) {
            case ByEnsemble -> getDb().readByEnsemble(setup.desiredEnsemble);
            case ByPayload -> getDb().readByPayloadSuffix(setup.desiredSuffix);
            case ByEnsembleAndPayload ->
                    getDb().readByEnsembleAndPayloadSuffix(setup.desiredEnsemble, setup.desiredSuffix);
            default -> throw new IllegalArgumentException("Unhandled phantomic predicate " + phantomicPredicate);
        };
    }
}
