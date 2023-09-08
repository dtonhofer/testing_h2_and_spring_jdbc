package name.heavycarbon.h2_exercises.transactions.dirty_read;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.common.AgentRunnableWithAllActionsInsideTransaction;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.Stuff;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

@Slf4j
public class AgentRunnable_Reader extends AgentRunnableWithAllActionsInsideTransaction {

    private final @NotNull Config config;
    private final @NotNull DbConfig dbConfig;

    // Store the result of reading here so that the main thread can pick it up.

    private List<Stuff> result;

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

    // Returns an empty optional if the List<Stuff> is still
    // unset, a filled Optional otherwise.

    public Optional<List<Stuff>> getResult() {
        if (result != null) {
            return Optional.of(result);
        } else {
            return Optional.empty();
        }
    }

    private @NotNull StuffId getIdToReadForCurrentOp() {
        return switch (config.op()) {
            case Update -> dbConfig.updateRow.getId();
            case Insert -> dbConfig.insertRow.getId();
            case Delete -> dbConfig.deleteRow.getId();
            default -> throw new IllegalArgumentException("Unhandled op " + config.op());
        };
    }

    // This is eventually called from the state machine loop inside a transaction

    protected void switchByAppState() throws InterruptedException {
        switch (getAppState().get()) {
            case 1 -> {
                final StuffId id = getIdToReadForCurrentOp();
                final Optional<Stuff> optStuff = getDb().readById(id);
                // The optional result is mapped to a list which may or may not be empty
                // But "result" won't be null.
                result = optStuff.map(List::of).orElseGet(List::of);
                incAppState();
                setAgentTerminatedNicely();
                setStop();
            }
            default -> waitOnAppState();
        }
    }

}
