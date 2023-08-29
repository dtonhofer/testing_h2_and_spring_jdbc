package name.heavycarbon.h2_exercises.transactions.common;

import name.heavycarbon.h2_exercises.transactions.db.Stuff;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class DualListOfStuff {

    // These are accessed from main and agents but at different times.
    // Make them volatile for good measure.

    private volatile List<Stuff> result1 = null;

    private volatile List<Stuff> result2 = null;

    public void setResult1(@NotNull List<Stuff> result1) {
        Objects.requireNonNull(result1);
        this.result1 = result1;
    }

    public void setResult2(@NotNull List<Stuff> result2) {
        Objects.requireNonNull(result2);
        this.result2 = result2;
    }

    public List<Stuff> getResult1() {
        return result1;
    }

    public List<Stuff> getResult2() {
        return result2;
    }

    // Returns an empty optional if any of the List<Stuff> is still
    // unset, a valid Optional of "this" otherwise.

    public @NotNull Optional<DualListOfStuff> getResult() {
        if (result1 != null && result2 != null) {
            // both results have been read
            return Optional.of(this);
        } else {
            // at least one result was not read
            return Optional.empty();
        }
    }
}
