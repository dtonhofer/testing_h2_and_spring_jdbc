package name.heavycarbon.h2_exercises.transactions;

import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.EnsembleId;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;
import name.heavycarbon.h2_exercises.transactions.non_serializable.AgentContainer_NonSerializable;
import name.heavycarbon.h2_exercises.transactions.non_serializable.ReadWriteTransactional;
import name.heavycarbon.h2_exercises.transactions.session.Isol;
import name.heavycarbon.h2_exercises.transactions.session.SessionManip;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureJdbc;
import org.springframework.boot.test.context.SpringBootTest;
import name.heavycarbon.h2_exercises.transactions.non_serializable.LeftAndRightStuffId;
import name.heavycarbon.h2_exercises.transactions.non_serializable.ReadWriteRunnable.Role2Behaviour;

// ---
// Trying out various sequences, some of which cannot be serialized or "snapshotted".

// ---
// WORK IN PROGRESS
// ---

// Assumption that the classes with the methods marked "Transactional" needs to be injected for a
// valid "transactional proxy" to be slapped around Agent1Transactional.

@Slf4j
@AutoConfigureJdbc
@SpringBootTest(classes = {Db.class, SessionManip.class, ReadWriteTransactional.class})
public class TestVariousSequences {

    @Autowired
    private Db db;

    @Autowired
    private ReadWriteTransactional tx;

    private @NotNull LeftAndRightStuffId setupDb() {
        db.setupDatabase(true);
        StuffId leftId = db.insert(EnsembleId.Two, "LEFT");
        StuffId rightId = db.insert(EnsembleId.Two, "RIGHT");
        StuffId role1Id = db.insert(EnsembleId.Two, "ROLE_1");
        StuffId role2Id = db.insert(EnsembleId.Two, "ROLE_2");
        return new LeftAndRightStuffId(leftId, rightId, role1Id, role2Id);
    }

    public void testNotSerializable(@NotNull Isol isol, @NotNull Role2Behaviour r2b) {
        final LeftAndRightStuffId ids = setupDb();
        // NB we use the same ReadWriteTransactional for both threads
        var ac = new AgentContainer_NonSerializable(db, isol, tx, ids, r2b);
        ac.startAll();
        ac.joinAll();
        // None of the threads should have terminated badly!
        Assertions.assertThat(ac.isAnyThreadTerminatedBadly()).isFalse();
    }

    @Test
    public void testVariousSequences_alfa_read_uncomitted() {
        testNotSerializable(Isol.READ_UNCOMMITTED,  Role2Behaviour.Alfa);
    }

    @Test
    public void testVariousSequences_alfa_read_comitted() {
        testNotSerializable(Isol.READ_COMMITTED,  Role2Behaviour.Alfa);
    }

    @Test
    public void testVariousSequences_alfa_repeatable_read() {
        testNotSerializable(Isol.REPEATABLE_READ,  Role2Behaviour.Alfa);
    }

    @Test
    public void testVariousSequences_alfa_serializable() {
        testNotSerializable(Isol.SERIALIZABLE,  Role2Behaviour.Alfa);
    }

    @Test
    public void testVariousSequences_alfa_snapshot() {
        testNotSerializable(Isol.SNAPSHOT,  Role2Behaviour.Alfa);
    }

    @Test
    public void testVariousSequences_bravo_read_uncomitted() {
        testNotSerializable(Isol.READ_UNCOMMITTED,  Role2Behaviour.Alfa);
    }

    @Test
    public void testVariousSequences_bravo_read_comitted() {
        testNotSerializable(Isol.READ_COMMITTED,  Role2Behaviour.Bravo);
    }

    @Test
    public void testVariousSequences_bravo_repeatable_read() {
        testNotSerializable(Isol.REPEATABLE_READ,  Role2Behaviour.Bravo);
    }

    @Test
    public void testVariousSequences_bravo_serializable() {
        testNotSerializable(Isol.SERIALIZABLE,  Role2Behaviour.Bravo);
    }

    @Test
    public void testVariousSequences_bravo_snapshot() {
        testNotSerializable(Isol.SNAPSHOT,  Role2Behaviour.Bravo);
    }

    @Test
    public void testVariousSequences_charlie_read_uncomitted() {
        testNotSerializable(Isol.READ_UNCOMMITTED,  Role2Behaviour.Charlie);
    }

    @Test
    public void testVariousSequences_charlie_read_comitted() {
        testNotSerializable(Isol.READ_COMMITTED,  Role2Behaviour.Charlie);
    }

    @Test
    public void testVariousSequences_charlie_repeatable_read() {
        testNotSerializable(Isol.REPEATABLE_READ,  Role2Behaviour.Charlie);
    }

    @Test
    public void testVariousSequences_charlie_serializable() {
        testNotSerializable(Isol.SERIALIZABLE,  Role2Behaviour.Charlie);
    }

    @Test
    public void testVariousSequences_charlie_snapshot() {
        testNotSerializable(Isol.SNAPSHOT,  Role2Behaviour.Charlie);
    }

    @Test
    public void testVariousSequences_delta_read_uncomitted() {
        testNotSerializable(Isol.READ_UNCOMMITTED,  Role2Behaviour.Delta);
    }

    @Test
    public void testVariousSequences_delta_read_comitted() {
        testNotSerializable(Isol.READ_COMMITTED,  Role2Behaviour.Delta);
    }

    @Test
    public void testVariousSequences_delta_repeatable_read() {
        testNotSerializable(Isol.REPEATABLE_READ,  Role2Behaviour.Delta);
    }

    @Test
    public void testVariousSequences_delta_serializable() {
        testNotSerializable(Isol.SERIALIZABLE,  Role2Behaviour.Delta);
    }

    @Test
    public void testVariousSequences_delta_snapshot() {
        testNotSerializable(Isol.SNAPSHOT,  Role2Behaviour.Delta);
    }
}