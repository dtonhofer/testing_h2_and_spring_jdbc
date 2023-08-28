package name.heavycarbon.h2_exercises.transactions.common;

import lombok.Value;
import name.heavycarbon.h2_exercises.transactions.db.Stuff;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Value
public class DualResult {

    @NotNull List<Stuff> result1; // may be empty but not null
    @NotNull List<Stuff> result2; // may be empty but not null

    public DualResult(@NotNull List<Stuff> result1, @NotNull List<Stuff> result2) {
        this.result1 = result1;
        this.result2 = result2;
    }

}
