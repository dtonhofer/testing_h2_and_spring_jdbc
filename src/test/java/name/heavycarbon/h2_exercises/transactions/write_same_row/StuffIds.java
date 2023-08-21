package name.heavycarbon.h2_exercises.transactions.write_same_row;

import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import org.jetbrains.annotations.NotNull;

public record StuffIds(@NotNull StuffId writeFirstMarkId, @NotNull StuffId writeLastMarkId,
                       @NotNull StuffId collisionId) {
}
