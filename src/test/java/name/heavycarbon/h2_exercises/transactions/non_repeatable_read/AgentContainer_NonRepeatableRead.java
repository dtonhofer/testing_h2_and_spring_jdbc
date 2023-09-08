package name.heavycarbon.h2_exercises.transactions.non_repeatable_read;

import name.heavycarbon.h2_exercises.transactions.agent.Agent;
import name.heavycarbon.h2_exercises.transactions.agent.AgentContainer;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AgentContainer_NonRepeatableRead extends AgentContainer {

    private final AgentId modifierId = new AgentId("modifier");
    private final AgentId readerId = new AgentId("reader");
    private final AppState appState = new AppState();

    public AgentContainer_NonRepeatableRead(
            @NotNull Db db,
            @NotNull TransactionalGateway txGw,
            @NotNull Config config,
            @NotNull DbConfig dbConfig) {
        final var mr = new AgentRunnable_NonRepeatableRead_Modifier(db, appState, modifierId, txGw, config, dbConfig);
        final var rr = new AgentRunnable_NonRepeatableRead_Reader(db, appState, readerId, txGw, config, dbConfig);
        setUnmodifiableAgentMap(List.of(new Agent(mr), new Agent(rr)));
    }

    public @NotNull AgentRunnable_NonRepeatableRead_Reader getReaderRunnable() {
        // Too much effort to make the get typesafe, just cast!
        return (AgentRunnable_NonRepeatableRead_Reader) (get(readerId).getRunnable());
    }

}
