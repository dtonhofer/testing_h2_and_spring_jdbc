package name.heavycarbon.h2_exercises.transactions;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.PrintException;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.*;
import name.heavycarbon.h2_exercises.transactions.deadlock_onlywriting.AgentContainer_DeadlockOnlyWriting;
import name.heavycarbon.h2_exercises.transactions.deadlock_onlywriting.Setup;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureJdbc;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@AutoConfigureJdbc
@SpringBootTest(classes = {Db.class, SessionManip.class, TransactionalGateway.class})
public class TestElicitingDeadlockOnlyWriting {

    // Spring does its autowiring magic here.

    @Autowired
    private Db db;

    @Autowired
    private TransactionalGateway txGw;

    // Information on how to set up the database and what to modify
    // aMarkerStuff: Alfa writes a marker string to this record
    // bMarkerStuff: Bravo writes a marker string to this record
    // collStuff: Alfa and Bravo write to this record in turn

    private static final Stuff aMarkerStuff = new Stuff(200, EnsembleId.One, "---");
    private static final Stuff bMarkerStuff = new Stuff(201, EnsembleId.One, "---");
    private static final Stuff collStuff = new Stuff(202, EnsembleId.One, "---");

    private void setupDb() {
        db.setupStuffTable(Db.AutoIncrementing.No, Db.CleanupFirst.Yes);
        db.insert(aMarkerStuff);
        db.insert(bMarkerStuff);
        db.insert(collStuff);
    }

    private static Stream<Arguments> provideTestArgStream() {
        List<Arguments> res = new ArrayList<>();
        final int rounds = 1; // more for heavier tests
        for (int i = 0; i < rounds; i++) {
            for (Isol isol : List.of(Isol.READ_UNCOMMITTED, Isol.READ_COMMITTED, Isol.REPEATABLE_READ, Isol.SERIALIZABLE, Isol.SNAPSHOT)) {
                res.add(Arguments.of(isol));
            }
        }
        return res.stream();
    }

    @ParameterizedTest
    @MethodSource("provideTestArgStream")
    void testDeadlock(@NotNull Isol isol) {
        final PrintException pex = PrintException.No;
        setupDb();
        final var setup = new Setup(aMarkerStuff.getId(), bMarkerStuff.getId(), collStuff.getId());
        final var ac = new AgentContainer_DeadlockOnlyWriting(db, isol, setup, pex, txGw);
        {
            ac.startAll();
            ac.joinAll();
        }
        if (isol == Isol.READ_UNCOMMITTED || isol == Isol.READ_COMMITTED) {
            // There is no problem! No exception!!
            Assertions.assertThat(ac.isAnyThreadTerminatedBadly()).isFalse();
            // Alfa committed
            Assertions.assertThat(db.readById(aMarkerStuff.getId()).orElseThrow().getPayload()).isEqualTo("ALFA WAS HERE");
            // Bravo committed
            Assertions.assertThat(db.readById(bMarkerStuff.getId()).orElseThrow().getPayload()).isEqualTo("BRAVO WAS HERE");
            // Bravo won writing X
            Assertions.assertThat(db.readById(collStuff.getId()).orElseThrow().getPayload()).isEqualTo("UPDATED BY BRAVO");
        }
        else {
            // Bad things happened!
            Assertions.assertThat(ac.isAnyThreadTerminatedBadly()).isTrue();
            // Alfa committed
            Assertions.assertThat(db.readById(aMarkerStuff.getId()).orElseThrow().getPayload()).isEqualTo("ALFA WAS HERE");
            // Bravo rolled back
            Assertions.assertThat(db.readById(bMarkerStuff.getId()).orElseThrow().getPayload()).isEqualTo("---");
            // Alfa won writing the "collision record"
            Assertions.assertThat(db.readById(collStuff.getId()).orElseThrow().getPayload()).isEqualTo("UPDATED BY ALFA");
            Assertions.assertThat(ac.getBravo().getExceptionSeen()).isInstanceOf(org.springframework.dao.CannotAcquireLockException.class);
        }
    }


}
