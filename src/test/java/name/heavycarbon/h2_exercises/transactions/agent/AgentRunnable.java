package name.heavycarbon.h2_exercises.transactions.agent;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.agent.AgentContainer.Op;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import name.heavycarbon.h2_exercises.transactions.db.SessionInfoExt;
import name.heavycarbon.h2_exercises.transactions.db.SessionManip;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

// ---
// NB: This is NOT an autowired Component or a superclass of one.
// So that we can recreate it easily in tests.
// ---

@Slf4j
public abstract class AgentRunnable implements Runnable {

    // The class fronting database queries

    @Getter
    private final @NotNull Db db;

    // The unique holder of the integer state of the common finite state machine
    // through which agents synchronize. Agents will always try to hold the
    // monitor of this object, so there is always exactly one that is running.
    // This can be regarded as a "permission to run" token being passed between agents.

    @Getter
    private final @NotNull AppState appState;

    // The id of the agent that is being implemented by this AgentRunnableAbstract instance.

    @Getter
    private final @NotNull AgentId agentId;

    // What ANSI isolation level to apply

    @Getter
    private final @NotNull Isol isol;

    // The operation that shall be applied to the database by this "modifier" agent.
    // TODO: If this agent is a "reader" agent, having this may not make sense.

    @Getter
    private final @NotNull Op op;

    // A boolean that signals that run() should break out of its inner loop and exit cleanly.

    private final AtomicBoolean stop = new AtomicBoolean(false);

    // A callback to a method that can query all the Runnables (not accessible
    // directly from here) whether they have "terminated nicely" or not.
    // May be left unset.

    @Setter
    private Optional<Supplier<Boolean>> anyThreadTerminatedBadly = Optional.empty();

    // If a thread "terminates nicely", it sets this flag before exiting.
    // Made "atomic" to make sure reading & writing is consistent.
    // Could also be a "volatile boolean"

    @Getter
    private final AtomicBoolean terminatedNicely = new AtomicBoolean(false);

    // ---

    public AgentRunnable(@NotNull Db db, @NotNull AppState appState, @NotNull AgentId agentId, @NotNull Isol isol, @NotNull Op op) {
        this.db = db;
        this.appState = appState;
        this.agentId = agentId;
        this.op = op;
        this.isol = isol;
    }

    // Called from within the Runnable's run() implementation or maybe from the main thread
    // to exhort the run() loop to to break so that the agent terminates smoothly.

    public void setStop() {
        stop.set(true);
    }

    // Called from within the Runnable's run() implementation to check whether one of the
    // other agent threads has "terminated badly", upon which the run() implementation's
    // loop is supposed to break so that the agent terminates smoothly.

    protected boolean isAnyThreadTerminatedBadly() {
        return anyThreadTerminatedBadly.map(Supplier::get).orElse(false);
    }

    // Check whether this agent has set the "terminated nicely" boolean, which
    // is "faLse" by default. Calling this should only be done if the agent
    // terminated.

    protected boolean isTerminatedNicely() {
        return terminatedNicely.get();
    }

    // Called from run(). Before terminating, the agent calls this to signal
    // that everything went well, and it didn't terminate due to an exception.

    protected void setTerminatedNicely() {
        terminatedNicely.set(true);
    }

    // Called regularly from run() to decide whether to continue with the loop.
    // Checks that "stop" hasn't been set and no other thread terminated badly.

    protected boolean isContinue() {
        return !stop.get() && !isAnyThreadTerminatedBadly();
    }

    // Move to another state. Called from run().
    // This is only called if the caller holds the monitor to appState.

    protected void moveToState(int newState) {
        // no need to synchronize again, but let's make clear what we want
        synchronized (appState) {
            appState.set(newState);
            appState.notify(); // must hold monitor for this
        }
    }

    // Move to the next state.
    // This is only called if the calling agent holds the monitor to appState.

    protected void incState() {
        // no need to synchronize again, but let's make clear what we want
        synchronized (appState) {
            appState.incrementAndGet();
            appState.notify(); // must hold monitor for this
        }
    }

    // Wait for a "notify" signal on the "appState" monitor. Called from run().
    // This is only called if the caller holds the monitor to appState.
    // Waiting forever should work but will lead to lockup if the
    // other thread crashes! Wait a few ms instead.

    private final static long wait_ms = 100;

    protected void waitOnAppState() throws InterruptedException {
        // no need to synchronize again, but let's make clear what we want
        synchronized (appState) {
            if (isContinue()) {
                appState.wait(wait_ms);
            }
        }
    }

    protected static String printAllSessions(@NotNull SessionManip sm) {
        List<SessionInfoExt> list = sm.getAllSessionsInfo();
        StringBuilder buf = new StringBuilder("Sessions");
        for (SessionInfoExt iext : list) {
            buf.append("\n");
            buf.append("   ");
            buf.append(iext.sessionId());
            buf.append(", ");
            buf.append(iext.isol());
            if (iext.isMySession()) {
                buf.append(", mine");
            }
        }
        return buf.toString();
    }

    // Called from within the run() loop but from a Transactional_X class
    // outside the package, so public!

    protected String finalMessage(boolean interrupted) {
        return "'" + agentId + "' terminating. stop = " + stop + ", interrupted = " + interrupted + ", bad threads = " + isAnyThreadTerminatedBadly();
    }

    public enum PrintException {Yes, No}

    protected void exceptionMessage(@NotNull Logger log, @NotNull Exception ex, @NotNull PrintException print) {
        log.error("'{}' terminating due to '{}' with message '{}'", agentId, ex.getClass().getName(), ex.getMessage());
        if (print == PrintException.Yes) {
            log.error("The exception: {} ", ex);
        }
    }
}
