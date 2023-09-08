package name.heavycarbon.h2_exercises.transactions.sql_timeout;

import name.heavycarbon.h2_exercises.transactions.agent.PrintException;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import org.jetbrains.annotations.NotNull;

public record Config(@NotNull Isol isol, @NotNull PrintException pex) {

}
