package name.heavycarbon.h2_exercises.transactions.phantom_read;

import name.heavycarbon.h2_exercises.transactions.agent.*;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AgentContainer_PhantomRead extends AgentContainer {

    private final AgentId modifierId = new AgentId("modifier");
    private final AgentId readerId = new AgentId("reader");
    private final AppState appState = new AppState();

    public AgentContainer_PhantomRead(
            @NotNull Db db,
            @NotNull TransactionalGateway txGw,
            @NotNull Config config,
            @NotNull DbConfig dbConfig) {
        final var mr = new AgentRunnable_PhantomRead_Modifier(db, appState, modifierId, txGw, config, dbConfig);
        final var rr = new AgentRunnable_PhantomRead_Reader(db, appState, readerId, txGw, config, dbConfig);
        setUnmodifiableAgentMap(List.of(new Agent(mr), new Agent(rr)));
    }

    public @NotNull AgentRunnable_PhantomRead_Reader getReaderRunnable() {
        // Too much effort to make the get typesafe, just cast!
        return (AgentRunnable_PhantomRead_Reader) (get(readerId).getRunnable());
    }

}
