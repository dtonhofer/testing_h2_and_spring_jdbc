package name.heavycarbon.h2_exercises.transactions.agent;

import name.heavycarbon.h2_exercises.transactions.db.Stuff;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record TransactionResult1(@NotNull List<Stuff> readResult) {
}