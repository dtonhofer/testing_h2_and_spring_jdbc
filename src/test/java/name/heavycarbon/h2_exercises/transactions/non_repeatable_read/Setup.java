package name.heavycarbon.h2_exercises.transactions.non_repeatable_read;

import lombok.Value;
import name.heavycarbon.h2_exercises.transactions.agent.AgentContainer;
import name.heavycarbon.h2_exercises.transactions.db.Stuff;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import org.jetbrains.annotations.NotNull;

@Value
public class Setup {

    @NotNull Stuff initRow;
    @NotNull Stuff updateRow;
    @NotNull Stuff insertRow;
    @NotNull Stuff deleteRow;

    public @NotNull StuffId getIdThatShallBeReadForGivenOp(@NotNull AgentContainer.Op op) {
        return switch (op) {
            case Update -> updateRow.getId();
            case Insert -> insertRow.getId();
            case Delete -> deleteRow.getId();
            default -> throw new IllegalArgumentException("Unhandled op " + op);
        };
    }
}
