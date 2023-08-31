package name.heavycarbon.h2_exercises.transactions;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.PrintException;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.*;
import name.heavycarbon.h2_exercises.transactions.deadlock_simple.AgentContainer_Deadlock;
import name.heavycarbon.h2_exercises.transactions.deadlock_simple.AgentRunnable_Alfa;
import name.heavycarbon.h2_exercises.transactions.deadlock_simple.AgentRunnable_Alfa.Shifted;
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
public class TestElicitingDeadlockSimple {

    // Spring does its autowiring magic here.

    @Autowired
    private Db db;

    @Autowired
    private TransactionalGateway txGw;

    // Information on how to set up the database and what to modify

    private static final Stuff stuff_x = new Stuff(200, EnsembleId.Two, "---");

    private void setupDb() {
        db.setupStuffTable(Db.AutoIncrementing.Yes, Db.CleanupFirst.Yes);
        db.insert(stuff_x);
    }

    private static Stream<Arguments> provideTestArgStream() {
        List<Arguments> res = new ArrayList<>();
        final int rounds = 1; // more for heavier tests
        for (int i=0;i<rounds;i++) {
            for (Shifted shifted : Shifted.values()) {
                for (Isol isol : List.of(Isol.READ_UNCOMMITTED, Isol.READ_COMMITTED, Isol.REPEATABLE_READ, Isol.SERIALIZABLE, Isol.SNAPSHOT)) {
                    res.add(Arguments.of(isol, shifted));
                }
            }
        }
        return res.stream();
    }

    @ParameterizedTest
    @MethodSource("provideTestArgStream")
    void testDeadlock(@NotNull Isol isol, @NotNull Shifted shifted) {
        final PrintException pex = PrintException.No;
        setupDb();
        final var ac = new AgentContainer_Deadlock(db, isol, stuff_x.getId(), pex, shifted, txGw);
        {
            ac.startAll();
            ac.joinAll();
        }
        if (isol == Isol.READ_UNCOMMITTED || isol == Isol.READ_COMMITTED) {
            // There is no problem! No exception!!
            Assertions.assertThat(ac.isAnyThreadTerminatedBadly()).isFalse();
            // Bravo won writing X
            Assertions.assertThat(db.readById(stuff_x.getId()).orElseThrow().getPayload()).isEqualTo("UPDATED BY BRAVO");
            if (shifted == Shifted.Yes && isol == Isol.READ_UNCOMMITTED) {
                // the uncommitted update is already visible!
                Assertions.assertThat(ac.getBravo().getReadInState2()).isEqualTo(stuff_x.with("UPDATED BY ALFA"));
            }
            else {
                // the uncommitted update is not yet visible!
                Assertions.assertThat(ac.getBravo().getReadInState2()).isEqualTo(stuff_x);
            }
            Assertions.assertThat(ac.getBravo().getReadInState5()).isEqualTo(stuff_x.with("UPDATED BY ALFA"));
        }
        else {
            // Bad things happened!
            Assertions.assertThat(ac.isAnyThreadTerminatedBadly()).isTrue();
            // Alfa won writing X
            Assertions.assertThat(db.readById(stuff_x.getId()).orElseThrow().getPayload()).isEqualTo("UPDATED BY ALFA");
            Assertions.assertThat(ac.getBravo().getReadInState2()).isEqualTo(stuff_x);
            Assertions.assertThat(ac.getBravo().getReadInState5()).isEqualTo(stuff_x);
            Assertions.assertThat(ac.getBravo().getExceptionSeen()).isInstanceOf(org.springframework.dao.CannotAcquireLockException.class);
        }
    }

}
