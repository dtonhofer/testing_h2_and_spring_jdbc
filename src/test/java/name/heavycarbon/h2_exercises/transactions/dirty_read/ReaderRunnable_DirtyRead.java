package name.heavycarbon.h2_exercises.transactions.dirty_read;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.AgentContainerAbstract.Op;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AgentRunnableAbstract;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.common.SetupForDirtyAndNonRepeatableRead;
import name.heavycarbon.h2_exercises.transactions.common.SingleResult;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.Stuff;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;


@Slf4j
public class ReaderRunnable_DirtyRead extends AgentRunnableAbstract {

    private final @NotNull TransactionalGateway txGw;

    // Store the result of reading here so that the main thread can pick it up.

    private List<Stuff> result;

    // Instructions what to modify inside the modifying transaction

    private final @NotNull SetupForDirtyAndNonRepeatableRead mods;

    // ---

    public ReaderRunnable_DirtyRead(@NotNull Db db,
                                    @NotNull AppState appState,
                                    @NotNull AgentId agentId,
                                    @NotNull Isol isol,
                                    @NotNull Op op,
                                    @NotNull SetupForDirtyAndNonRepeatableRead mods,
                                    @NotNull TransactionalGateway txGw) {
        super(db, appState, agentId, isol, op);
        this.txGw = txGw;
        this.mods = mods;
    }

    public Optional<SingleResult> getResult() {
        if (result != null) {
            return Optional.of(new SingleResult(result));
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

    // Having opened a transaction via the "gateway" member, we are called back here...

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
                final StuffId id = mods.getIdThatShallBeReadForGivenOp(getOp());
                final Optional<Stuff> optStuff = getDb().readById(id);
                // the optional result is mapped to a list which may or may not be empty
                result = optStuff.map(List::of).orElseGet(List::of);
                incState();
                setTerminatedNicely();
                setStop();
            }
            default -> waitOnAppState();
        }
    }

}
