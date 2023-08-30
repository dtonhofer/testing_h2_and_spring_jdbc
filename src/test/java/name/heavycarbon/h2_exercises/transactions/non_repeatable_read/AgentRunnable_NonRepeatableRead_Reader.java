package name.heavycarbon.h2_exercises.transactions.non_repeatable_read;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.AgentContainer.Op;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.agent.PrintException;
import name.heavycarbon.h2_exercises.transactions.common.AgentRunnableWithAllActionsInsideTransaction;
import name.heavycarbon.h2_exercises.transactions.common.DualListOfStuff;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import name.heavycarbon.h2_exercises.transactions.db.Stuff;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
public class AgentRunnable_NonRepeatableRead_Reader extends AgentRunnableWithAllActionsInsideTransaction {

    // Store the result of reading here so that the main thread can pick it up

    private final DualListOfStuff result = new DualListOfStuff();

    // Instructions what to modify inside the modifying transaction

    private final @NotNull Setup setup;

    // ---

    public AgentRunnable_NonRepeatableRead_Reader(@NotNull Db db,
                                                  @NotNull AppState appState,
                                                  @NotNull AgentId agentId,
                                                  @NotNull Isol isol,
                                                  @NotNull Op op,
                                                  @NotNull Setup setup,
                                                  @NotNull PrintException pex,
                                                  @NotNull TransactionalGateway txGw) {
        super(db, appState, agentId, isol, op, pex, txGw);
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
                result.setResult1(read());
                incState();
            }
            case 2 -> {
                result.setResult2(read());
                incState();
                setThreadTerminatedNicely();
                setStop();
            }
            default -> waitOnAppState();
        }
    }

    private List<Stuff> read() {
        Optional<Stuff> asRead = getDb().readById(setup.getIdThatShallBeReadForGivenOp(getOp()));
        return asRead.map(List::of).orElse(Collections.emptyList());
    }

}
