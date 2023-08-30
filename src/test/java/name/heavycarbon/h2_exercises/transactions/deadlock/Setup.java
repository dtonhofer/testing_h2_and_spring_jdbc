package name.heavycarbon.h2_exercises.transactions.deadlock;

import name.heavycarbon.h2_exercises.transactions.db.Stuff;
import org.jetbrains.annotations.NotNull;

public record Setup(@NotNull Stuff stuff_a,
                    @NotNull Stuff stuff_b,
                    @NotNull Stuff stuff_x,
                    boolean withMarkers,
                    boolean withReadingInState2,
                    boolean withReadingInState5) {
}
