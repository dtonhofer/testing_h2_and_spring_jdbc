package name.heavycarbon.h2_exercises.transactions.dirty_read;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.AgentContainer.Op;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.agent.PrintException;
import name.heavycarbon.h2_exercises.transactions.common.AgentRunnableWithAllActionsInsideTransaction;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import name.heavycarbon.h2_exercises.transactions.db.Stuff;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

@Slf4j
public class AgentRunnable_DirtyRead_Reader extends AgentRunnableWithAllActionsInsideTransaction {

    // Store the result of reading here so that the main thread can pick it up.

    private List<Stuff> result;

    // Instructions what to modify inside the modifying transaction

    private final @NotNull Setup setup;

    // ---

    public AgentRunnable_DirtyRead_Reader(@NotNull Db db,
                                          @NotNull AppState appState,
                                          @NotNull AgentId agentId,
                                          @NotNull Isol isol,
                                          @NotNull Op op,
                                          @NotNull Setup setup,
                                          @NotNull TransactionalGateway txGw) {
        super(db, appState, agentId, isol, op, PrintException.No, txGw);
        this.setup = setup;
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

    // This is eventually called from the state machine loop inside a transaction

    protected void switchByAppState() throws InterruptedException {
        switch (getAppState().get()) {
            case 1 -> {
                final StuffId id = setup.getIdThatShallBeReadForGivenOp(getOp());
                final Optional<Stuff> optStuff = getDb().readById(id);
                // The optional result is mapped to a list which may or may not be empty
                // But "result" won't be null.
                result = optStuff.map(List::of).orElseGet(List::of);
                assert result != null;
                incState();
                setThreadTerminatedNicely();;
                setStop();
            }
            default -> waitOnAppState();
        }
    }

}
