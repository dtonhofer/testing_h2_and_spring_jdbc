package name.heavycarbon.h2_exercises.transactions.common;

import name.heavycarbon.h2_exercises.transactions.agent.AgentContainerBase;
import name.heavycarbon.h2_exercises.transactions.agent.AgentRunnableBase;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import org.jetbrains.annotations.NotNull;

// ---
// Implementations of this interface implement the runInsideTransaction()
// method and annotate it with "@Transactional"
// ---

public interface TransactionalInterface_OpSwitchOnly {

    void runOpSwitchInsideTransaction(@NotNull AgentRunnableBase ar, @NotNull AgentContainerBase.Op op, @NotNull StuffId rowToModifyId);

}
