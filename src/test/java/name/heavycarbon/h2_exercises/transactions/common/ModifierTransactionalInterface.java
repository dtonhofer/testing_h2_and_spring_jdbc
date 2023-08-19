package name.heavycarbon.h2_exercises.transactions.common;

import name.heavycarbon.h2_exercises.transactions.agent.AgentContainerBase;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import name.heavycarbon.h2_exercises.transactions.session.Isol;
import org.jetbrains.annotations.NotNull;

public interface ModifierTransactionalInterface {

    void runInsideTransaction(@NotNull AgentContainerBase.Op op, @NotNull Isol isol, @NotNull StuffId stuffIdOfRowToModify);
}
