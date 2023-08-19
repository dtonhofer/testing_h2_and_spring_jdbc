package name.heavycarbon.h2_exercises.transactions.phantom_read;

import name.heavycarbon.h2_exercises.transactions.agent.*;
import name.heavycarbon.h2_exercises.transactions.common.ReaderRunnable;
import name.heavycarbon.h2_exercises.transactions.common.ModifierRunnable;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import name.heavycarbon.h2_exercises.transactions.session.Isol;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class AgentContainer_PhantomRead extends AgentContainerBase {

    public final AgentId modifierId = new AgentId("modifier");
    public final AgentId readerId = new AgentId("reader");
    public final AppState appState = new AppState();

    public AgentContainer_PhantomRead(
            @NotNull Db db,
            @NotNull ModifierTransactional modifierTx,
            @NotNull ReaderTransactional readerTx,
            @NotNull Isol isol,
            @NotNull Op op) {
        final Map<AgentId, Agent> agentMap = new HashMap<>();
        final var modifierRunnable = new ModifierRunnable(db, appState, modifierId, isol, modifierTx, op);
        final var readerRunnable = new ReaderRunnable(db, appState, readerId, isol, readerTx);
        final var modifierThread = new Thread(modifierRunnable);
        final var readerThread = new Thread(readerRunnable);
        agentMap.put(modifierId, new Agent(modifierId, modifierThread, modifierRunnable));
        agentMap.put(readerId, new Agent(readerId, readerThread, readerRunnable));
        agentMap.values().forEach(agent -> {
            agent.thread().setDaemon(true);
            agent.thread().setName(agent.agentId().toString());
            agent.runnable().setAnyThreadTerminatedBadly(this::isAnyThreadTerminatedBadly);
        });
        setUnmodifiableAgentMap(agentMap);
    }

    public @NotNull ReaderRunnable getReaderRunnable() {
        return (ReaderRunnable) (get(readerId).runnable());
    }

    public @NotNull ModifierRunnable getModifierRunnable() {
        return (ModifierRunnable) (get(modifierId).runnable());
    }

    // This is called by main at the very end to get the result of reading

    public @NotNull Optional<TransactionResult2> getResult() {
        assert isAllThreadsTerminated();
        return getReaderRunnable().getResult();
    }

    // Called later than construction time from main
    // once "stuffId" is known; this actually happens before the modifier
    // thread is started.

    public void setStuffIdOfRowToModify(@NotNull StuffId stuffId) {
        getModifierRunnable().setStuffIdOfRowToModify(stuffId);
    }

}
