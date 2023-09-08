package name.heavycarbon.h2_exercises.transactions.phantom_read;

import name.heavycarbon.h2_exercises.transactions.agent.PrintException;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import org.jetbrains.annotations.NotNull;

public record Config(@NotNull Isol isol, @NotNull Op op,
                     @NotNull PhantomicPredicate phantomicPredicate,
                     @NotNull PrintException pex) {

    public enum Op {Insert, Delete, UpdateInto, UpdateOutOf}

    public enum PhantomicPredicate {ByEnsembleAndSuffix, ByEnsemble, BySuffix}

}

