package name.heavycarbon.h2_exercises.transactions.sql_timeout;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.AgentId;
import name.heavycarbon.h2_exercises.transactions.agent.AppState;
import name.heavycarbon.h2_exercises.transactions.common.AgentRunnableWithAllActionsInsideTransaction;
import name.heavycarbon.h2_exercises.transactions.common.TransactionalGateway;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class AgentRunnable_Alfa extends AgentRunnableWithAllActionsInsideTransaction {

    @NotNull
    private final DbConfig dbConfig;

    public AgentRunnable_Alfa(@NotNull Db db,
                              @NotNull AppState appState,
                              @NotNull AgentId agentId,
                              @NotNull TransactionalGateway txGw,
                              @NotNull Config config,
                              @NotNull DbConfig dbConfig) {
        super(db, appState, agentId, config.isol(), config.pex(), txGw);
        this.dbConfig = dbConfig;
    }

    // The only method that is being added

    protected void switchByAppState() throws InterruptedException {
        switch (getAppState().get()) {
            case 0 -> {
                getDb().updatePayloadById(dbConfig.stuff_a.getId(), dbConfig.updateString_alfa_marker);
                incAppState();
            }
            case 2 -> {
                getDb().updatePayloadById(dbConfig.stuff_x.getId(), dbConfig.updateString_alfa_did_x);
                incAppState();
            }
            case 4 -> {
                setAgentTerminatedNicely();
                incAppState();
                setStop();
            }
            default -> waitOnAppState();
        }
    }

}
