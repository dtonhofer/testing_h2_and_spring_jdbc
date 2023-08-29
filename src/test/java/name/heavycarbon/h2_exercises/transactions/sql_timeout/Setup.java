package name.heavycarbon.h2_exercises.transactions.sql_timeout;

import name.heavycarbon.h2_exercises.transactions.db.Stuff;
import org.jetbrains.annotations.NotNull;

public record Setup(@NotNull Stuff stuff_a, @NotNull Stuff stuff_b, @NotNull Stuff stuff_x) {
}
