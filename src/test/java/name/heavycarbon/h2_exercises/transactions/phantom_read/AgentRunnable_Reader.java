package name.heavycarbon.h2_exercises.transactions.phantom_read;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.common.AgentRunnableWithAllActionsInsideTransaction;
import name.heavycarbon.h2_exercises.transactions.common.DualListOfStuff;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.Stuff;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

@Slf4j
public class AgentRunnable_Reader extends AgentRunnableWithAllActionsInsideTransaction {

    @NotNull
    private final Config config;
    @NotNull
    private final DbConfig dbConfig;

    // Store the result of reading here so that the main thread can pick it up

    private final DualListOfStuff result = new DualListOfStuff();

    public AgentRunnable_Reader(@NotNull Db db,
                                @NotNull AppState appState,
                                @NotNull AgentId agentId,
                                @NotNull TransactionalGateway txGw,
                                @NotNull Config config,
                                @NotNull DbConfig dbConfig) {
        super(db, appState, agentId, config.isol(), config.pex(), txGw);
        this.config = config;
        this.dbConfig = dbConfig;
    }

    // Returns an empty optional if any of the List<Stuff> is still
    // unset, a filled Optional otherwise.

    public Optional<DualListOfStuff> getResult() {
        return result.getResult();
    }

    // ---
    // This is eventually called from the state machine loop inside a transaction
    // ---

    protected void switchByAppState() throws InterruptedException {
        switch (getAppState().get()) {
            case 0 -> {
                result.setResult1(readByPhantomicPredicate());
                incAppState();
            }
            case 2 -> {
                result.setResult2(readByPhantomicPredicate());
                incAppState();
                setAgentTerminatedNicely();
                ;
                setStop();
            }
            default -> waitOnAppState();
        }
    }

    private @NotNull List<Stuff> readByPhantomicPredicate() {
        return switch (config.phantomicPredicate()) {
            case ByEnsemble -> getDb().readByEnsemble(dbConfig.desiredEnsemble);
            case BySuffix -> getDb().readByPayloadSuffix(dbConfig.desiredSuffix);
            case ByEnsembleAndSuffix ->
                    getDb().readByEnsembleAndPayloadSuffix(dbConfig.desiredEnsemble, dbConfig.desiredSuffix);
            default ->
                    throw new IllegalArgumentException("Unhandled phantomic predicate " + config.phantomicPredicate());
        };
    }
}
