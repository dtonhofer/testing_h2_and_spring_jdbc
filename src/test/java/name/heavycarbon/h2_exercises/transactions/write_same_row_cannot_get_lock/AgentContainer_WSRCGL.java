package name.heavycarbon.h2_exercises.transactions.write_same_row_cannot_get_lock;

import name.heavycarbon.h2_exercises.transactions.agent.Agent;
import name.heavycarbon.h2_exercises.transactions.agent.AgentContainerBase;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.session.Isol;
import name.heavycarbon.h2_exercises.transactions.write_same_row.AgentRunnable_WSR;
import name.heavycarbon.h2_exercises.transactions.write_same_row.StuffIds;
import org.jetbrains.annotations.NotNull;

public class AgentContainer_WSRCGL extends AgentContainerBase {

    public final AgentId writeFirstAgentId = new AgentId("write-first");
    public final AgentId writeLastAgentId = new AgentId("write-last");
    public final AppState appState = new AppState();
    private final @NotNull AgentRunnable_WSR writeFirstRunnable;
    private final @NotNull AgentRunnable_WSR writeLastRunnable;

    public AgentContainer_WSRCGL(
            @NotNull Db db,
            @NotNull Isol isol,
            @NotNull StuffIds ids,
            @NotNull Transactional_WSR_WriteFirst writeFirstTx,
            @NotNull Transactional_WSR_WriteLast writeLastTx) {
        writeFirstRunnable = new AgentRunnable_WSR(db, ids.writeFirstMarkId(), ids.collisionId(), appState, writeFirstAgentId, isol, writeFirstTx);
        writeLastRunnable = new AgentRunnable_WSR(db, ids.writeLastMarkId(), ids.collisionId(), appState, writeLastAgentId, isol, writeLastTx);
        setUnmodifiableAgentMap(
                new Agent(writeFirstAgentId, new Thread(writeFirstRunnable), writeFirstRunnable),
                new Agent(writeLastAgentId, new Thread(writeLastRunnable), writeLastRunnable)
        );
    }

    public @NotNull AgentRunnable_WSR getWriteFirstRunnable() {
        return writeFirstRunnable;
    }

    public @NotNull AgentRunnable_WSR getWriteLastRunnable() {
        return writeLastRunnable;
    }

}
