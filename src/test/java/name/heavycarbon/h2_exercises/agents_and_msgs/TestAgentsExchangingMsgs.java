package name.heavycarbon.h2_exercises.agents_and_msgs;

import lombok.extern.slf4j.Slf4j;
import name.heavycarbon.h2_exercises.agents_and_msgs.agent.Agent;
import name.heavycarbon.h2_exercises.agents_and_msgs.agent.AgentRunnable;
import name.heavycarbon.h2_exercises.agents_and_msgs.db.Db;
import name.heavycarbon.h2_exercises.agents_and_msgs.agent.AgentId;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureJdbc;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import java.util.*;
import java.util.stream.IntStream;

// ---
// This is not REALLY a JUnit test as we don't check anything.
// This is more like some code to start & run a set of independent threads
// ("agents") that send messages to each other through an H2 table, poll for new
// messages, and acknowledge the messages.
//
// There may be any number of agents, but configured by default are 2.
//
// There are two type of messages:
//
// - "true messages" carrying some (arbitrary) text from some agent A to some agent B
// - "ack messages" sent from agent B to agent A after a "true message" has been
//   received at B. "ack messages" are not acknowledged themselves.
//
// Messages have a "state". A message just sent is in state "fresh". After reception,
// the receiving agent updates the message's state to "seen" so that the
// messages are not picked up again during the next poll.
//
// For some info on threads, see:
//
// https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html
// ---

@Slf4j
@AutoConfigureJdbc
@SpringBootTest(classes = {TestAgentsExchangingMsgs.class, Db.class})
public class TestAgentsExchangingMsgs {

    // This class cannot be autowired by constructor (it would need some additional glue
    // code for this). We just have a "Db" field (with an instance that carries a
    // JdbcTemplate and all the database code we need) that is marked as "autowired".

    @Autowired
    private Db db;

    // ---

    private static Map<AgentId, Agent> buildAllAgents(@NotNull final Db db, final int agentCount) {
        if (agentCount < 2) {
            throw new IllegalArgumentException("Need at least 2 agents");
        }
        final List<AgentId> agentIds = new ArrayList<>(agentCount);
        IntStream.rangeClosed(1, agentCount).forEach(i -> agentIds.add(new AgentId(i)));
        Map<AgentId, Agent> res = new HashMap<>();
        agentIds.forEach(agentId -> {
            var others = new ArrayList<>(agentIds);
            others.remove(agentId);
            var runnable = new AgentRunnable(db, agentId, others);
            var thread = new Thread(runnable, agentId.toString());
            thread.setDaemon(true);
            res.put(agentId, new Agent(agentId, thread, runnable));
        });
        return res;
    }

    private static boolean isAllThreadsAlive(@NotNull Collection<Agent> agents) {
        return agents.stream().allMatch(agent -> agent.thread().isAlive());
    }

    private void runMsgExchangingAgents(final int agentCount, final TemporalAmount runTime) {
        Map<AgentId, Agent> agentMap = Collections.unmodifiableMap(buildAllAgents(db, agentCount));
        // starting!
        agentMap.values().forEach(agent -> agent.thread().start());
        {
            final Instant stopWhen = Instant.now().plus(runTime);
            boolean interrupted = false;
            // none of the threads is supposed to stop by itself so test isAllThreadsAlive()
            while (Instant.now().isBefore(stopWhen) && isAllThreadsAlive(agentMap.values()) && !interrupted) {
                try {
                    final int hundred_ms = 100;
                    Thread.sleep(hundred_ms);
                } catch (InterruptedException ex) {
                    // did the user interrupt us? anyway, get out
                    interrupted = true;
                }
            }
        }
        windDownAllThreads(agentMap);
        joinAllThreads(agentMap.values());
    }

    private static void windDownAllThreads(@NotNull Map<AgentId, Agent> agentMap) {
        agentMap.values().forEach(agent -> {
            // set the flag that causes the thread to exit its inner loop
            agent.runnable().windDown();
            // thread may be waiting or sleeping, interrupt it to make it get out to make sure
            agent.thread().interrupt();
        });
    }

    // join one thread after the other, but note that they stop concurrently/randomly

    private static void joinAllThreads(@NotNull Collection<Agent> agents) {
        // a loop so that we can break
        for (Agent agent : agents) {
            try {
                agent.thread().join();
            } catch (InterruptedException ex) {
                // Who the hell interrupted us? the user??
                // In any case, do *not* join any remaining threads!
                break;
            }
        }
    }

    // ---
    // Testing, or rather, running!
    // ---

    @Test
    void runAgents() {
        db.setupDatabase(true);
        {
            final Duration runTime = Duration.of(5, ChronoUnit.SECONDS);
            final int agentCount = 10;
            runMsgExchangingAgents(agentCount, runTime);
        }
    }

}

