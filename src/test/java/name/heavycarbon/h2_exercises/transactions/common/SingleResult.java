package name.heavycarbon.h2_exercises.transactions.common;

import lombok.Value;
import name.heavycarbon.h2_exercises.transactions.db.Stuff;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Value
public class SingleResult {

    @NotNull List<Stuff> result; // may be empty but not null

    public SingleResult(@NotNull List<Stuff> result) {
        this.result = result;
    }

}
