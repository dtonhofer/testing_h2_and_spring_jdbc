package name.heavycarbon.h2_exercises.transactions;

import name.heavycarbon.h2_exercises.transactions.db.Stuff;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import org.jetbrains.annotations.NotNull;

import java.util.List;

record SetupData(@NotNull List<Stuff> initialRows, @NotNull StuffId modifyThisStuffId) {
}
