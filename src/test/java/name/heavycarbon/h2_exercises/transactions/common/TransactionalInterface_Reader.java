package name.heavycarbon.h2_exercises.transactions.common;

import name.heavycarbon.h2_exercises.transactions.agent.AgentRunnableBase;
import name.heavycarbon.h2_exercises.transactions.agent.TransactionResult2;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

// ---
// Implementations of this interface implement the runInsideTransaction()
// method and annotate it with "@Transactional"
// ---

public interface TransactionalInterface_Reader {

    @NotNull Optional<TransactionResult2> runStateMachineLoopInsideTransaction(@NotNull AgentRunnableBase ar);

}
