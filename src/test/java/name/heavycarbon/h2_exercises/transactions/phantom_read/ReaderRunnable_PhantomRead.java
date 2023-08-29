package name.heavycarbon.h2_exercises.transactions.phantom_read;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.AgentContainerAbstract.Op;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.common.AgentRunnableTransactionalAbstract;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import name.heavycarbon.h2_exercises.transactions.db.Stuff;
import name.heavycarbon.h2_exercises.transactions.common.DualListOfStuff;
import name.heavycarbon.h2_exercises.transactions.phantom_read.AgentContainer_PhantomRead.PhantomicPredicate;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

@Slf4j
public class ReaderRunnable_PhantomRead extends AgentRunnableTransactionalAbstract {

    // Store the result of reading here so that the main thread can pick it up

    private final DualListOfStuff result = new DualListOfStuff();

    // Instructions what to modify inside the modifying transaction

    private final @NotNull Setup_PhantomRead setup;

    // Additional indication on what to do during reading

    private final @NotNull PhantomicPredicate phantomicPredicate;

    // ---

    public ReaderRunnable_PhantomRead(@NotNull Db db,
                                      @NotNull AppState appState,
                                      @NotNull AgentId agentId,
                                      @NotNull Isol isol,
                                      @NotNull Op op,
                                      @NotNull PhantomicPredicate phantomicPredicate,
                                      @NotNull Setup_PhantomRead setup,
                                      @NotNull TransactionalGateway txGw) {
        super(db, appState, agentId, isol, op, txGw);
        this.phantomicPredicate = phantomicPredicate;
        this.setup = setup;
    }

    // Returns an empty optional if any of the List<Stuff> is still
    // unset, a filled Optional otherwise.

    public Optional<DualListOfStuff> getResult() {
        return result.getResult();
    }

    // This is eventually called from the state machine loop inside a transaction

    protected void switchByAppState() throws InterruptedException {
        switch (getAppState().get()) {
            case 0 -> {
                result.setResult1(readByPhantomicPredicate());
                incState();
            }
            case 2 -> {
                result.setResult2(readByPhantomicPredicate());
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
