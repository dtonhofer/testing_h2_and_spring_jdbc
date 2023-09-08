package name.heavycarbon.h2_exercises.transactions.non_repeatable_read;

import name.heavycarbon.h2_exercises.transactions.agent.PrintException;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import org.jetbrains.annotations.NotNull;

public record Config(@NotNull Isol isol, @NotNull Op op, @NotNull PrintException pex) {

    public enum Op {Insert, Update, Delete}

}
