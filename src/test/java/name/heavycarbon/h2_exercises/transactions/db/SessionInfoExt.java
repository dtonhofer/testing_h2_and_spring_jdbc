package name.heavycarbon.h2_exercises.transactions.db;

import org.jetbrains.annotations.NotNull;

public record SessionInfoExt(@NotNull SessionId sessionId, @NotNull Isol isol,
                             boolean isMySession) {
}
