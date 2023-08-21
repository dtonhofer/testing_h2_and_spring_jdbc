package name.heavycarbon.h2_exercises.transactions.phantom_read;

import name.heavycarbon.h2_exercises.transactions.agent.*;
import name.heavycarbon.h2_exercises.transactions.common.ReaderRunnable;
import name.heavycarbon.h2_exercises.transactions.common.ModifierRunnable;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import name.heavycarbon.h2_exercises.transactions.session.Isol;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class AgentContainer_PhantomRead extends AgentContainerBase {

    public final AgentId modifierId = new AgentId("modifier");
    public final AgentId readerId = new AgentId("reader");
    public final AppState appState = new AppState();

    public AgentContainer_PhantomRead(
            @NotNull Db db,
            @NotNull Transactional_PhantomRead_Modifier modifierTx,
            @NotNull Transactional_PhantomRead_Reader readerTx,
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

    // This is called by main at the very end to get the result of reading

    public @NotNull Optional<TransactionResult2> getResult() {
        assert isAllThreadsTerminated();
        return getReaderRunnable().getResult();
    }

    // Called later than construction time from main
    // once "stuffId" is known; this actually happens before the modifier
    // thread is started.

    public void setStuffIdOfRowToModify(@NotNull StuffId stuffId) {
        getModifierRunnable().setRowToModifyId(stuffId);
    }

}
