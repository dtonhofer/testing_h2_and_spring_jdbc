package name.heavycarbon.h2_exercises.transactions.dirty_read;

import name.heavycarbon.h2_exercises.transactions.agent.Agent;
import name.heavycarbon.h2_exercises.transactions.agent.AgentContainerBase;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.common.ReaderRunnable;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import name.heavycarbon.h2_exercises.transactions.session.Isol;
import org.jetbrains.annotations.NotNull;

public class AgentContainer_DirtyRead extends AgentContainerBase {

    public final AgentId modifierId = new AgentId("modifier");
    public final AgentId readerId = new AgentId("reader");
    public final AppState appState = new AppState();

    public AgentContainer_DirtyRead(
            @NotNull Db db,
            @NotNull Transactional_DirtyRead_Modifier modifierTx,
            @NotNull Transactional_DirtyRead_Reader readerTx,
            @NotNull Isol isol,
            @NotNull Op op) {
        final var modifierRunnable = new ModifierRunnable(db, appState, modifierId, isol, modifierTx, op);
        final var readerRunnable = new ReaderRunnable(db, appState, readerId, isol, readerTx);
        setUnmodifiableAgentMap(
                new Agent(modifierId, new Thread(modifierRunnable), modifierRunnable),
                new Agent(readerId, new Thread(readerRunnable), readerRunnable)
        );
    }

    public @NotNull ReaderRunnable getReaderRunnable() {
        return (ReaderRunnable) (get(readerId).runnable());
    }

    public @NotNull ModifierRunnable getModifierRunnable() {
        return (ModifierRunnable) (get(modifierId).runnable());
    }

    // Called later than construction time from main
    // once "stuffId" is known; this actually happens before the modifier
    // thread is started.

    public void setStuffIdOfRowToModify(@NotNull StuffId stuffId) {
        getModifierRunnable().setRowToModifyId(stuffId);
    }

}
