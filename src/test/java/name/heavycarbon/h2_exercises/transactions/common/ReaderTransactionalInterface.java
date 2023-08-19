package name.heavycarbon.h2_exercises.transactions.common;

import name.heavycarbon.h2_exercises.transactions.agent.AgentRunnable;
import name.heavycarbon.h2_exercises.transactions.agent.TransactionResult2;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public interface ReaderTransactionalInterface {

    @NotNull Optional<TransactionResult2> runInsideTransaction(@NotNull AgentRunnable ar);

}
