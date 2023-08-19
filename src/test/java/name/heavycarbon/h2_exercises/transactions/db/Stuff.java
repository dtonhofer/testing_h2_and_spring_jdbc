package name.heavycarbon.h2_exercises.transactions.db;

import org.jetbrains.annotations.NotNull;

// ---
// A row from the database
// ---

public record Stuff(@NotNull StuffId id, @NotNull EnsembleId ensembleId, @NotNull String payload) {

    public String toString() {
        return "(" + id + "," + ensembleId + "," + payload + ")";
    }

    public boolean isEqualExceptForId(@NotNull EnsembleId ensembleId, @NotNull String payload) {
        return this.ensembleId.equals(ensembleId) && this.payload.equals(payload);
    }

    public Stuff with(@NotNull String newPayload) {
        return new Stuff(this.id, this.ensembleId, newPayload);
    }
}
