package name.heavycarbon.h2_exercises.transactions;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.PrintException;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.*;
import name.heavycarbon.h2_exercises.transactions.sql_timeout.AgentContainer_SqlTimeout;
import name.heavycarbon.h2_exercises.transactions.sql_timeout.Setup;
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
public class TestElicitingSqlTimeout {

    // Spring does its autowiring magic here.

    @Autowired
    private Db db;

    @Autowired
    private TransactionalGateway txGw;

    // Information on how to set up the database and what to modify
    // stuff_a and stuff_b are written immediately by the agents so we can check
    // whether there was proper rollback.
    // The agents then contend on writing to stuff_x

    private static final Stuff stuff_a = new Stuff(100, EnsembleId.Two, "---");
    private static final Stuff stuff_b = new Stuff(101, EnsembleId.Two, "---");
    private static final Stuff stuff_x = new Stuff(200, EnsembleId.Two, "---");

    private void setupDb() {
        db.setupStuffTable(Db.AutoIncrementing.Yes, Db.CleanupFirst.Yes);
        db.insert(stuff_a);
        db.insert(stuff_b);
        db.insert(stuff_x);
    }

    // We get the same exception in all isolation levels. Good!

    @ParameterizedTest
    @MethodSource("provideTestArgStream")
    void testSqlTimeoutException(@NotNull Isol isol) {
        setupDb();
        final PrintException pex = PrintException.Yes;
        final var ac = new AgentContainer_SqlTimeout(db, isol, new Setup(stuff_a, stuff_b, stuff_x), pex, txGw);
        {
            ac.startAll();
            ac.joinAll();
        }
        if (isol == Isol.READ_UNCOMMITTED) {
            // Bravo read the X as updated by Alfa
            Assertions.assertThat(ac.getBravo().getReadInState3()).isEqualTo(stuff_x.with("UPDATED BY ALFA"));
        } else {
            // Bravo read the X that existed of the start of transaction
            Assertions.assertThat(ac.getBravo().getReadInState3()).isEqualTo(stuff_x);
        }
        Assertions.assertThat(ac.isAnyThreadTerminatedBadly()).isTrue();
        // Alfa wrote its marker A
        Assertions.assertThat(db.readById(stuff_a.getId()).orElseThrow().getPayload()).isEqualTo("ALFA WAS HERE");
        // Bravo was rolled back, so its marker B is untouched
        Assertions.assertThat(db.readById(stuff_b.getId()).orElseThrow().getPayload()).isEqualTo("---");
        // Alfa won writing X
        Assertions.assertThat(db.readById(stuff_x.getId()).orElseThrow().getPayload()).isEqualTo("UPDATED BY ALFA");
        // Found an "org.springframework.dao.QueryTimeoutException" at the Spring Data JDBC level
        // TODO : Which is however transformed into an "org.springframework.transaction.TransactionSystemException"
        // TODO: at the @Transaction level due to inability of Spring to "translate" "org.h2.jdbc.JdbcSQLTimeoutException"
        // TODO: Can one fix that?
        Assertions.assertThat(ac.getBravo().getExceptionSeen()).isInstanceOf(org.springframework.dao.QueryTimeoutException.class);
    }

    private static Stream<Arguments> provideTestArgStream() {
        List<Arguments> res = new ArrayList<>();
        for (Isol isol : List.of(Isol.READ_UNCOMMITTED, Isol.READ_COMMITTED, Isol.REPEATABLE_READ, Isol.SERIALIZABLE, Isol.SNAPSHOT)) {
            res.add(Arguments.of(isol));
        }
        return res.stream();
    }
}
