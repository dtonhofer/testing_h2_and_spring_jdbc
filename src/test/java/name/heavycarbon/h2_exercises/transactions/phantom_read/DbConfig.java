package name.heavycarbon.h2_exercises.transactions.phantom_read;

import name.heavycarbon.h2_exercises.transactions.db.EnsembleId;
import name.heavycarbon.h2_exercises.transactions.db.Stuff;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class DbConfig {

    // Selection "by ensemble": Those with value EnsembleId.One
    // Selection "by payload": Those which end in "AA" (we use a LIKE match)

    // ---
    // We are looking for records with that suffix and in that ensemble
    // ---

    public final String desiredSuffix = "AAA";
    public final EnsembleId desiredEnsemble = EnsembleId.One;

    // ---
    // The following record will all be inserted initially
    // ---

    public final Stuff alfa = new Stuff(100, EnsembleId.One, "ALFA_AAA");
    public final Stuff bravo = new Stuff(101, EnsembleId.One, "BRAVO_AAA");
    public final Stuff charlie = new Stuff(102, EnsembleId.One, "CHARLIE_XXX");
    public final Stuff delta = new Stuff(103, EnsembleId.Two, "DELTA_AAA");

    // ---
    // This record will be inserted initially (it is in 'initialStuff'),
    // then later deleted and matches "selection by ensemble" and "by payload"
    // ---

    public final Stuff deleteMe = new Stuff(104, EnsembleId.One, "DELETE_ME_AAA");

    // ---
    // This will be inserted initially (it is in 'initialStuff').
    // Does not initially appear in any result set.
    // It will be later updated once on field "ensemble" and once on field "payload" to appear in
    // one or the other result set.
    // ---

    public final Stuff updateForMovingIn = new Stuff(105, EnsembleId.Two, "UPDATE_IN_ORIGINAL_HHH");
    public final Stuff updateForMovingInChanged = new Stuff(105, EnsembleId.One, "UPDATE_IN_CHANGED_AAA");

    // ---
    // This will be inserted initially (it is in 'initialStuff').
    // Initially appears in both result sets.
    // It will be later updated once on field "ensemble" and once on field "payload" to disappear from
    // one or the other result set.
    // ---

    public final Stuff updateForMovingOut = new Stuff(106, EnsembleId.One, "UPDATE_OUT_ORIGINAL_AAA");
    public final Stuff updateForMovingOutChanged = new Stuff(106, EnsembleId.Two, "UPDATE_OUT_CHANGED_HHH");

    // ---
    // This one is not in 'initialStuff'.
    // It will be inserted later
    // It appears in both result sets.
    // ---

    public final Stuff insertMe = new Stuff(107, EnsembleId.One, "INSERT_ME_AAA");

    // ---
    // What is initially in the database
    // ---

    public final List<Stuff> initialStuff =
            Collections.unmodifiableList(
                    Stuff.sortById(
                            List.of(alfa, bravo, charlie, delta, deleteMe, updateForMovingIn, updateForMovingOut)));

    // ---
    // Returns a modifiable list obtained by removing "record" from "list".
    // Keeps the order of the passed-in "list".
    // ---

    private List<Stuff> removeRecord(@NotNull List<Stuff> list, @NotNull Stuff record) {
        final Stream<Stuff> stream = list.stream();
        final List<Stuff> res = stream.filter(stuff -> !stuff.getId().equals(record.getId())).toList();
        // res is unmodifiable, make sure it becomes modifiable
        return new ArrayList<>(res);
    }

    // ---
    // Returns a modifiable list obtained by adding "record" to "list".
    // Keeps the order of the passed-in "list".
    // ---

    private List<Stuff> addRecord(@NotNull List<Stuff> list, @NotNull Stuff record) {
        final List<Stuff> res = new ArrayList<>(list);
        res.add(record);
        return Stuff.sortById(res);
    }

    // ---
    // Returns a modifiable list obtained by selecting the records from the "initial stuff" records
    // whose "ensemble" field matches the "desired ensemble".
    // The returned list is ordered by id!
    // ---

    public List<Stuff> getInitialRecordsWithDesiredEnsemble() {
        final Stream<Stuff> stream = initialStuff.stream();
        final List<Stuff> res = stream.filter(stuff -> stuff.isOfEnsemble(desiredEnsemble)).toList();
        // res is unmodifiable, make sure it becomes modifiable
        return new ArrayList<>(res);
    }

    // ---
    // Returns a modifiable list obtained by selecting the records from the "initial stuff" records
    // whose "payload" field has the "desired suffix".
    // The returned list is ordered by id!
    // ---

    public List<Stuff> getInitialRecordsWithDesiredSuffix() {
        final Stream<Stuff> stream = initialStuff.stream();
        final List<Stuff> res = stream.filter(stuff -> stuff.payloadEndsWith(desiredSuffix)).toList();
        // res is unmodifiable, make sure it becomes modifiable
        return new ArrayList<>(res);
    }

    // ---
    // Returns a modifiable list obtained by selecting the records from the "initial stuff" records
    // whose "payload" field has the "desired suffix" and whose "ensemble" field matches the "desired ensemble"
    // The returned list is ordered by id!
    // ---

    public List<Stuff> getInitialRecordsWithDesiredEnsembleAndSuffix() {
        final Stream<Stuff> stream = initialStuff.stream();
        final List<Stuff> res = stream.filter(stuff -> stuff.isOfEnsemble(desiredEnsemble) && stuff.payloadEndsWith(desiredSuffix)).toList();
        // res is unmodifiable, make sure it becomes modifiable
        return new ArrayList<>(res);
    }

    // ---
    // Returns a modifiable list obtained by selecting the records from the "initial stuff" records
    // whose "ensemble" field matches the "desired ensemble", with the passed "record" added.
    // The returned list is ordered by id!
    // ---

    public List<Stuff> getInitialRecordsWithDesiredEnsembleAndAdd(@NotNull Stuff record) {
        return addRecord(getInitialRecordsWithDesiredEnsemble(), record);
    }

    // ---
    // Returns a modifiable list obtained by selecting the records from the "initial stuff" records
    // whose "payload" field has the "desired suffix", with the passed "record" added.
    // The returned list is ordered by id!
    // ---

    public List<Stuff> getInitialRecordsWithDesiredSuffixAndAdd(@NotNull Stuff record) {
        return addRecord(getInitialRecordsWithDesiredSuffix(), record);
    }

    // ---
    // Returns a modifiable list obtained by selecting the records from the "initial stuff" records
    // whose "payload" field has the "desired suffix" and whose "ensemble" field matches the "desired ensemble",
    // with the passed "record" added.
    // The returned list is ordered by id!
    // ---

    public List<Stuff> getInitialRecordsWithDesiredEnsembleAndSuffixAndAdd(@NotNull Stuff record) {
        return addRecord(getInitialRecordsWithDesiredEnsembleAndSuffix(), record);
    }

    // ---
    // Returns a modifiable list obtained by taking all the records that are initially found in
    // the database, with the passed "record" added.
    // The returned list is ordered by id!
    // ---

    public List<Stuff> getInitialRecordsAndAdd(@NotNull Stuff record) {
        return addRecord(initialStuff, record);
    }

    // ---
    // Returns a modifiable list obtained by taking all the records that are initially found in
    // the database, with the passed "record" removed (if it is in the list).
    // The returned list is ordered by id!
    // ---

    public List<Stuff> getInitialRecordsAndRemove(@NotNull Stuff record) {
        return removeRecord(initialStuff, record);
    }

    // ---
    // Returns a modifiable list obtained by taking all the records that are initially found in
    // the database, with the passed "existing" removed (if it is in the list) and the
    // "replacement" added.
    // The returned list is ordered by id!
    // ---

    public List<Stuff> getInitialRecordsAndReplace(@NotNull Stuff existing, @NotNull Stuff replacement) {
        return addRecord(removeRecord(initialStuff, existing), replacement);
    }

    // ---
    // Returns a modifiable list obtained by taking all the records that are initially found in
    // the database and that match the "desired ensemble" on the ensemble field, with the passed
    // "record" removed (if it is in the list).
    // The returned list is ordered by id!
    // ---

    public List<Stuff> getInitialRecordsWithDesiredEnsembleAndRemove(@NotNull Stuff record) {
        return removeRecord(getInitialRecordsWithDesiredEnsemble(), record);
    }

    // ---
    // Returns a modifiable list obtained by taking all the records that are initially found in
    // the database and that match the "desired suffix" on the payload field, with the passed
    // "record" removed (if it is in the list).
    // The returned list is ordered by id!
    // ---

    public List<Stuff> getInitialRecordsWithDesiredSuffixAndRemove(@NotNull Stuff record) {
        return removeRecord(getInitialRecordsWithDesiredSuffix(), record);
    }

    // ---
    // Returns a modifiable list obtained by taking all the records that are initially found in
    // the database and that match the "desired ensemble" on the ensemble field and that match
    // the "desired suffix" on the payload field, with the passed "record" removed (if it is in the list).
    // The returned list is ordered by id!
    // ---

    public List<Stuff> getInitialRecordsWithDesiredEnsembleAndSuffixAndRemove(@NotNull Stuff record) {
        return removeRecord(getInitialRecordsWithDesiredEnsembleAndSuffix(), record);
    }

}
