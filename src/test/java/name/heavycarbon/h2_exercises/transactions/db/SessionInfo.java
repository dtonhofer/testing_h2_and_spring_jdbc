package name.heavycarbon.h2_exercises.transactions.db;

import org.jetbrains.annotations.NotNull;

public record SessionInfo(@NotNull SessionId sessionId, @NotNull Isol isol) {
}
