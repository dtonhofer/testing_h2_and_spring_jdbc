package name.heavycarbon.h2_exercises.transactions.agent;

import name.heavycarbon.h2_exercises.transactions.db.Stuff;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

// The second result may be null

public record TransactionResult2(@NotNull List<Stuff> readResult1, List<Stuff> readResult2) {

    public static @NotNull Optional<TransactionResult2> makeOptional(@NotNull List<Stuff> readResult1, @NotNull List<Stuff> readResult2) {
        if (readResult1 != null && readResult2 != null) {
            return Optional.of(new TransactionResult2(readResult1, readResult2));
        } else {
            return Optional.empty();
        }
    }

    public static @NotNull Optional<TransactionResult2> makeOptional(@NotNull List<Stuff> readResult) {
        if (readResult != null) {
            return Optional.of(new TransactionResult2(readResult, null));
        } else {
            return Optional.empty();
        }
    }
}
