package name.heavycarbon.h2_exercises.transactions.deadlock_onlywriting;

import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import org.jetbrains.annotations.NotNull;

public record Setup(@NotNull StuffId alfaMarkerId, @NotNull StuffId bravoMarkerId, @NotNull StuffId collisionId) { }
