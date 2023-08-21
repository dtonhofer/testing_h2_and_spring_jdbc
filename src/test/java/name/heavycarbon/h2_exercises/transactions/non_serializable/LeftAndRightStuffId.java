package name.heavycarbon.h2_exercises.transactions.non_serializable;

import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import org.jetbrains.annotations.NotNull;

public record LeftAndRightStuffId(@NotNull StuffId leftId, @NotNull StuffId rightId, @NotNull StuffId role1Id,
                                  @NotNull StuffId role2Id) {
}
