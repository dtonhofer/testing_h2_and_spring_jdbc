package name.heavycarbon.h2_exercises.transactions.phantom_read;

import name.heavycarbon.h2_exercises.transactions.db.EnsembleId;
import name.heavycarbon.h2_exercises.transactions.db.Stuff;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class Setup {

    // Selection "by ensemble": Those with value EnsembleId.One
    // Selection "by payload": Those which end in "AA" (we use a LIKE match)

    // These will all be inserted initially

    public final Stuff alfa = new Stuff(100, EnsembleId.One, "ALFA_AAA");
    public final Stuff bravo = new Stuff(101, EnsembleId.One, "BRAVO_AAA");
    public final Stuff charlie = new Stuff(102, EnsembleId.One, "CHARLIE_XXX");
    public final Stuff delta = new Stuff(103, EnsembleId.Two, "DELTA_AAA");

    // This will be inserted (it is in 'initialStuff'),
    // then later deleted and matches selection by ensemble and by payload

    public final Stuff deleteMe = new Stuff(104, EnsembleId.One, "DELETE_ME_AAA");

    // This will be inserted (it is in 'initialStuff').
    // Does not initially appear in any result set.
    // It will be later updated once on "ensemble" and once on "payload" to appear in one
    // or the other result set.

    public final Stuff updateForMovingIn = new Stuff(105, EnsembleId.Two, "UPDATE_IN_ORIGINAL_HHH");
    public final Stuff updateForMovingInChanged = new Stuff(105, EnsembleId.One, "UPDATE_IN_CHANGED_AAA");

    // This will be inserted (it is in 'initialStuff'),
    // Initially appears in both result sets.
    // It will be later updated once on "ensemble" and once on "payload" to disappear from
    // one or the other result set.

    public final Stuff updateForMovingOut = new Stuff(106, EnsembleId.One, "UPDATE_OUT_ORIGINAL_AAA");
    public final Stuff updateForMovingOutChanged = new Stuff(106, EnsembleId.Two, "UPDATE_OUT_CHANGED_HHH");

    // This one is not in 'initialStuff'.
    // It will be inserted later
    // It appears in both result sets.

    public final Stuff insertMe = new Stuff(107, EnsembleId.One, "INSERT_ME_AAA");

    // What is initially in the database

    public final List<Stuff> initialStuff = List.of(alfa, bravo, charlie, delta, deleteMe, updateForMovingIn, updateForMovingOut);

    // We are looking for records with that suffix and in that ensemble

    public final String desiredSuffix = "AAA";
    public final EnsembleId desiredEnsemble = EnsembleId.One;

    private List<Stuff> renameRecord(@NotNull List<Stuff> list, @NotNull Stuff delete) {
        final Stream<Stuff> stream = list.stream();
        final List<Stuff> res = stream.filter(stuff -> !stuff.getId().equals(delete.getId())).toList();
        return new ArrayList<>(res);
    }

    public List<Stuff> getInitialRecordsInDesiredEnsemble() {
        final Stream<Stuff> stream = initialStuff.stream();
        final List<Stuff> res = stream.filter(stuff -> stuff.isOfEnsemble(desiredEnsemble)).toList();
        return new ArrayList<>(res);
    }

    public List<Stuff> getInitialRecordsWithMatchingSuffix() {
        final Stream<Stuff> stream = initialStuff.stream();
        final List<Stuff> res = stream.filter(stuff -> stuff.payloadEndsWith(desiredSuffix)).toList();
        return new ArrayList<>(res);
    }

    public List<Stuff> getInitialRecordsInDesiredEnsembleWithMatchingSuffix() {
        final Stream<Stuff> stream = initialStuff.stream();
        final List<Stuff> res = stream.filter(stuff -> stuff.isOfEnsemble(desiredEnsemble) && stuff.payloadEndsWith(desiredSuffix)).toList();
        return new ArrayList<>(res);
    }

    public List<Stuff> getInitialRecordsInDesiredEnsemble_AddRecord(@NotNull Stuff insert) {
        final List<Stuff> res = getInitialRecordsInDesiredEnsemble();
        res.add(insert);
        return new ArrayList<>(res);
    }

    public List<Stuff> getInitialRecordsWithMatchingSuffix_AddRecord(@NotNull Stuff insert) {
        final List<Stuff> res = getInitialRecordsWithMatchingSuffix();
        res.add(insert);
        return new ArrayList<>(res);
    }

    public List<Stuff> getInitialRecordsInDesiredEnsembleWithMatchingSuffix_AddRecord(@NotNull Stuff insert) {
        final List<Stuff> res = getInitialRecordsInDesiredEnsembleWithMatchingSuffix();
        res.add(insert);
        return new ArrayList<>(res);
    }

    public List<Stuff> getInitialRecords_AddRecord(@NotNull Stuff insert) {
        final List<Stuff> res = new ArrayList<>(initialStuff);
        res.add(insert);
        return res;
    }

    public List<Stuff> getInitialRecords_RemoveRecord(@NotNull Stuff delete) {
        return renameRecord(initialStuff, delete);
    }

    public List<Stuff> getInitialRecordsInDesiredEnsemble_RemoveRecord(@NotNull Stuff delete) {
        return renameRecord(getInitialRecordsInDesiredEnsemble(), delete);
    }

    public List<Stuff> getInitialRecordsWithMatchingSuffix_RemoveRecord(@NotNull Stuff delete) {
        return renameRecord(getInitialRecordsWithMatchingSuffix(), delete);
    }

    public List<Stuff> getInitialRecordsInDesiredEnsembleWithMatchingSuffix_RemoveRecord(@NotNull Stuff delete) {
        return renameRecord(getInitialRecordsInDesiredEnsembleWithMatchingSuffix(), delete);
    }

}
