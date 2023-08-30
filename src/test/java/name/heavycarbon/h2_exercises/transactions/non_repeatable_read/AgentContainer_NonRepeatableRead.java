package name.heavycarbon.h2_exercises.transactions.non_repeatable_read;

import name.heavycarbon.h2_exercises.transactions.agent.*;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AgentContainer_NonRepeatableRead extends AgentContainer {

    private final AgentId modifierId = new AgentId("modifier");
    private final AgentId readerId = new AgentId("reader");
    private final AppState appState = new AppState();

    public AgentContainer_NonRepeatableRead(
            @NotNull Db db,
            @NotNull Isol isol,
            @NotNull Op op,
            @NotNull Setup setup,
            @NotNull PrintException pex,
            @NotNull TransactionalGateway txGw) {
        final var mr = new AgentRunnable_NonRepeatableRead_Modifier(db, appState, modifierId, isol, op, setup, pex, txGw);
        final var rr = new AgentRunnable_NonRepeatableRead_Reader(db, appState, readerId, isol, op, setup, pex, txGw);
        setUnmodifiableAgentMap(List.of(new Agent(mr), new Agent(rr)));
    }

    public @NotNull AgentRunnable_NonRepeatableRead_Reader getReaderRunnable() {
        // Too much effort to make the get typesafe, just cast!
        return (AgentRunnable_NonRepeatableRead_Reader) (get(readerId).getRunnable());
    }

}
