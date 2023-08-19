package name.heavycarbon.h2_exercises.transactions.session;

import org.jetbrains.annotations.NotNull;

public record SessionInfo(@NotNull SessionId sessionId, @NotNull Isol isol) {
}
