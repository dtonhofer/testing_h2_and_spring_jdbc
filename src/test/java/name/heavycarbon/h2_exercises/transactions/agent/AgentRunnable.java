package name.heavycarbon.h2_exercises.transactions.agent;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.transactions.db.Db;
import name.heavycarbon.h2_exercises.transactions.db.Isol;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

// ---
// NB: This is NOT an autowired Component or a superclass of one.
// So that we can recreate it easily in tests.
// ---

@Slf4j
public abstract class AgentRunnable implements Runnable {

    // The class fronting database queries

    @Getter
    @NotNull private final Db db;

    // The unique holder of the integer state of the common finite state machine
    // through which agents synchronize. Agents will always try to hold the
    // monitor of this object, so there is always exactly one that is running.
    // This can be regarded as a "permission to run" token being passed between agents.

    @Getter
    @NotNull private final AppState appState;

    // The id of the agent that is being implemented by this AgentRunnableAbstract instance.

    @Getter
    @NotNull private final AgentId agentId;

    // What ANSI isolation level to apply

    @Getter
    @NotNull private final Isol isol;

    // A boolean that signals that the agent should break out of its inner loop and exit cleanly.
    // This boolean is set from the main thread.

    private final AtomicBoolean stop = new AtomicBoolean(false);

    // Agent state, not to be confused with the common app state.
    // A value that indicates:
    // 0: Agent's thread not yet started (Thread.isAlive() probably returns false)
    // 1: Agent's thread has been started (Thread.isAlive() returns true)
    // 1: Agent's thread terminated unexpectedly (Thread.isAlive() returns false, again but the value is still 1)
    // 2: Agent's thread terminated nicely/normally (Thread.isAlive() probably returns false)

    private final AtomicInteger agentState = new AtomicInteger(0);

    // A callback to a method that can query the state of all the agents
    // (not accessible directly from here) whether they have "terminated badly".
    // May be left unset.

    @Setter
    private volatile Supplier<Boolean> anyAgentTerminatedBadly = null;

    // ---

    public AgentRunnable(@NotNull Db db, @NotNull AppState appState, @NotNull AgentId agentId, @NotNull Isol isol) {
        this.db = db;
        this.appState = appState;
        this.agentId = agentId;
        this.isol = isol;
    }

    // ---
    // Called from within the agent's Runnable.run() implementation or maybe from the main thread
    // to exhort the run() loop to to break so that the agent terminates smoothly.
    // ---

    public void setStop() {
        stop.set(true);
    }

    // ---
    // Called from within the agent's Runnable.run() to check whether one of the
    // other agents "terminated badly", upon which the run() implementation's
    // loop is supposed to break so that the agent terminates smoothly.
    // ---

    protected boolean isAnyAgentTerminatedBadly() {
        Supplier<Boolean> supplier = anyAgentTerminatedBadly;
        if (supplier == null) {
            // can't say
            return false;
        } else {
            // ask the supplier
            return supplier.get();
        }
    }

    // ---
    // Get the numeric state of the agent
    // TODO: This shoudl return a proper enum.
    // ---

    public int getAgentState() {
        return agentState.get();
    }

    // ---
    // Called from run(). Before terminating, the agent calls this to signal
    // that everything went well, and it didn't terminate due to an exception.
    // ---

    protected void setAgentTerminatedNicely() {
        agentState.set(2);
    }

    // ---
    // Called from run() as the first instruction.
    // ---

    protected void setAgentStarted() {
        agentState.set(1);
    }

    // ---
    // Called regularly from run() to decide whether to continue with the loop.
    // Checks that "stop" hasn't been set and no other thread terminated badly.
    // ---

    protected boolean isContinue() {
        return !stop.get() && !isAnyAgentTerminatedBadly();
    }

    // ---
    // Move to the (strictly) next common application state (that's all we ever need for now).
    // This is only called if the calling agent holds the monitor to appState.
    // ---

    protected void incAppState() {
        // no need to synchronize again, but let's make clear what we want
        synchronized (appState) {
            appState.incrementAndGet();
            appState.notify(); // must hold monitor for this
        }
    }

    // ---
    // Wait for a "notify" signal on the "appState" monitor. Called from run().
    // This is only called if the caller holds the monitor to appState.
    // Waiting forever should work but will lead to lockup if the
    // other thread crashes! Wait a few ms instead.
    // ---

    private final static long wait_ms = 100;

    protected void waitOnAppState() throws InterruptedException {
        // no need to synchronize again, but let's make clear what we want
        synchronized (appState) {
            if (isContinue()) {
                appState.wait(wait_ms);
            }
        }
    }

    // ---
    // Called from within the agent's thread as last message
    // ---

    protected String finalMessage(boolean interrupted) {
        return "'" + agentId + "' terminating. stop = " + stop + ", interrupted = " + interrupted + ", bad threads = " + isAnyAgentTerminatedBadly();
    }

    // ---
    // Called from within the agent's thread as last message if a Throwable or Exception was caught
    // ---

    protected void exceptionMessage(@NotNull Logger log, @NotNull Throwable th, @NotNull PrintException print) {
        log.error("'{}' terminating due to '{}' with message '{}'", agentId, th.getClass().getName(), th.getMessage());
        if (print == PrintException.Yes) {
            log.error("The throwable", th);
        }
    }
}
