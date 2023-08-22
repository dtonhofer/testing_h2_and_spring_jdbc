package name.heavycarbon.h2_exercises.transactions.dirty_read;

import name.heavycarbon.h2_exercises.transactions.agent.Agent;
import name.heavycarbon.h2_exercises.transactions.agent.AgentContainerBase;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.common.ReaderRunnable;
import name.heavycarbon.h2_exercises.transactions.common.WhatToRead;
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
        setUnmodifiableAgentMap(new Agent(modifierRunnable), new Agent(readerRunnable));
    }

    public @NotNull ReaderRunnable getReaderRunnable() {
        return (ReaderRunnable) (get(readerId).getRunnable());
    }

    public @NotNull ModifierRunnable getModifierRunnable() {
        return (ModifierRunnable) (get(modifierId).getRunnable());
    }

    // ---
    // Called later than construction time from "main" once "stuffId" is known.
    // This happens before the thread of this agent is started.
    // ---

    public void setWhatToModify(@NotNull StuffId stuffId) {
        getModifierRunnable().setWhatToModify(stuffId);
    }

    public void setWhatToRead(@NotNull WhatToRead whatToRead) { getReaderRunnable().setWhatToRead(whatToRead);}
}
