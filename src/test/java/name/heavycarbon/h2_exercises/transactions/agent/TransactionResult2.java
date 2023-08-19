package name.heavycarbon.h2_exercises.transactions.agent;

import name.heavycarbon.h2_exercises.transactions.db.Stuff;
import org.jetbrains.annotations.NotNull;

import java.util.List;

// The second result may be null

public record TransactionResult2(@NotNull List<Stuff> readResult1, List<Stuff> readResult2) {
}
