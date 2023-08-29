package name.heavycarbon.h2_exercises.transactions.common;

import lombok.Value;
import name.heavycarbon.h2_exercises.transactions.agent.AgentContainerAbstract;
import name.heavycarbon.h2_exercises.transactions.db.Stuff;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import org.jetbrains.annotations.NotNull;

@Value
public class Setup_DirtyAndNonRepeatableRead {

    @NotNull Stuff initRow;
    @NotNull Stuff updateRow;
    @NotNull Stuff insertRow;
    @NotNull Stuff deleteRow;

    public @NotNull StuffId getIdThatShallBeReadForGivenOp(@NotNull AgentContainerAbstract.Op op) {
        return switch (op) {
            case Update -> updateRow.getId();
            case Insert -> insertRow.getId();
            case Delete -> deleteRow.getId();
            default -> throw new IllegalArgumentException("Unhandled op " + op);
        };
    }
}
