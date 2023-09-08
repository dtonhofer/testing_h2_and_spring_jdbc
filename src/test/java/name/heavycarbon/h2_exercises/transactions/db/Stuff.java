package name.heavycarbon.h2_exercises.transactions.db;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// ---
// Data Transfer Object: A row from the table we use to test transactions.
// ---

@Value
public class Stuff {

    @NotNull StuffId id;
    @NotNull EnsembleId ensembleId;
    @NotNull String payload;

    public Stuff(@NotNull StuffId id, @NotNull EnsembleId ensembleId, @NotNull String payload) {
        this.id = id;
        this.ensembleId = ensembleId;
        this.payload = payload;
    }

    public Stuff(int id, @NotNull EnsembleId ensembleId, @NotNull String payload) {
        this(new StuffId(id), ensembleId, payload);
    }

    public String toString() {
        return "(" + id + "," + ensembleId + "," + payload + ")";
    }

    public boolean isEqualExceptForId(@NotNull EnsembleId ensembleId, @NotNull String payload) {
        return this.ensembleId.equals(ensembleId) && this.payload.equals(payload);
    }

    public boolean isSameId(@NotNull Stuff stuff) {
        return this.id.equals(stuff.id);
    }

    public Stuff with(@NotNull String newPayload) {
        return new Stuff(this.id, this.ensembleId, newPayload);
    }

    public boolean payloadEndsWith(@NotNull String suffix) {
        return payload.endsWith(suffix);
    }

    public boolean isOfEnsemble(@NotNull EnsembleId ensembleId) {
        return this.ensembleId.equals(ensembleId);
    }

    // ---
    // Sort the passed list by "id", returning a NEW list (the list is NOT sorted in-place)
    // ---

    public static List<Stuff> sortById(@NotNull List<Stuff> list) {
        List<Stuff> res = new ArrayList<>(list);
        res.sort(Comparator.comparing(Stuff::getId));
        return res;
    }

}
