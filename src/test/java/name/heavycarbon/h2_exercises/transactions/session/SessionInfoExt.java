package name.heavycarbon.h2_exercises.transactions.session;

import org.jetbrains.annotations.NotNull;

public record SessionInfoExt(@NotNull SessionId sessionId, @NotNull Isol isol,
                             boolean isMySession) {
}
