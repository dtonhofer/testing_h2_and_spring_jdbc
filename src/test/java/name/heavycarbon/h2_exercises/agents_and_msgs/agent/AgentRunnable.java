package name.heavycarbon.h2_exercises.agents_and_msgs.agent;

import name.heavycarbon.h2_exercises.agents_and_msgs.crud.GiveMeRandomText;
import name.heavycarbon.h2_exercises.agents_and_msgs.db.Db;
import name.heavycarbon.h2_exercises.agents_and_msgs.msg.*;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class AgentRunnable implements Runnable {

    private final @NotNull AgentId me;
    private final @NotNull List<AgentId> others;

    private final @NotNull Db db;

    // To generate random text, use a "random",
    // The random is runnable-specific and thus thread-specific.
    // (as Random is thread-safe, one could also use a single instance
    // with more contention)

    private final Random rand = new Random();

    // This atomic boolean is used to tell the thread animating this runnable to stop.

    private final AtomicBoolean stop = new AtomicBoolean(false);

    // ----
    // Use the Spring JdbcTemplate of the "owner".
    // Javadoc says "an instance of JdbcTemplate is thread-safe once configured."
    // Empirically, Spring injects the same JdbcTemplate in multiple places, so it's ok.
    // ---

    public AgentRunnable(@NotNull Db db, @NotNull AgentId me, @NotNull List<AgentId> others) {
        this.me = me;
        this.others = Collections.unmodifiableList(others);
        this.db = db;
        if (others.isEmpty()) {
            throw new IllegalArgumentException("The passed list of AgentId is empty!");
            // "others" may contain "me" or the same AgentId several times (why not)
        }
    }

    // ---
    // This is called by the "control thread" (presumably the main thread) to
    // tell the thread animating this Runnable that it should stop.
    // Not to be confused with Thread.stop()  which would radically "stop the thread"
    // and is deprecated.
    // ---

    public void windDown() {
        stop.set(true);
    }

    // ---
    // Count the message addressed to "me" that are in state "fresh" (count both "ACKs" and True Messages)
    // ---

    private void countFreshMsgsAddressedToMeAndPrint() {
        final int count = db.countMsgs(me, MsgState.fresh);
        if (count > 0) {
            log.info("{}: {} messages to process", me, count);
        } else {
            log.trace("{}: no messages to process", me);
        }
    }

    private boolean withProbabilityOneThird() {
        return (rand.nextDouble() < 0.333);
    }

    private boolean withProbabilityTwoThirds() {
        return (rand.nextDouble() < 0.666);
    }

    // ---
    // Sleep a variable number of 100ms intervals (an exponentially decaying number of times
    // because we re-test a random value in a loop)
    // ---

    private boolean sleepRandomly() {
        final int sleep_ms = 100;
        boolean interrupted = false;
        while (!interrupted && !stop.get() && withProbabilityTwoThirds()) {
            try {
                Thread.sleep(sleep_ms);
            } catch (InterruptedException ex) {
                interrupted = true;
            }
        }
        return interrupted;
    }

    // ---
    // Obtain some receiver AgentId to send to
    // ---

    private AgentId getRandomReceiver() {
        final int receiverIndex = rand.nextInt(others.size());
        return others.get(receiverIndex);
    }

    // ---
    // Send a variable number of messages to other agents (an exponentially decaying number of messages
    // because we re-test a random value in a loop)
    // ---

    private boolean sendSeveralMsgsWithRandomText() {
        boolean interrupted = false;
        while (!interrupted && withProbabilityOneThird()) {
            final Instant createdWhen = Instant.now();
            final String text = GiveMeRandomText.getRandomText(rand);
            final AgentId receiver = getRandomReceiver();
            final AgentId sender = this.me;
            // choose a number in [0..3]
            switch (rand.nextInt(4)) {
                case 0 -> {
                    db.sendMsgWithSimpleJdbc(createdWhen, sender, receiver, text);
                }
                case 1 -> {
                    db.sendMsgWithJdbcTemplate(createdWhen, sender, receiver, text);
                }
                case 2 -> {
                    MsgId msgId = db.sendMsgWithSimpleJdbcReturningId(createdWhen, sender, receiver, text);
                    log.info("Inserted new message {}", msgId);
                }
                case 3 -> {
                    MsgId msgId = db.sendMsgWithJdbcTemplateReturningId(createdWhen, sender, receiver, text);
                    log.info("Inserted new message {}", msgId);
                }
            }
            interrupted = Thread.interrupted();
        }
        return interrupted;
    }

    // ---
    // Retrieve and process. Returns a boolean saying whether an interrupt occurred.
    // ---

    private boolean processMsg() {
        final List<MsgBase> msgs = db.retrieveMsgs(me, MsgState.fresh);
        boolean interrupted = Thread.interrupted();
        final Iterator<MsgBase> iter = msgs.iterator();
        while (iter.hasNext() && !interrupted && !stop.get()) {
            final MsgBase msg = iter.next();
            final Instant ackedWhen = Instant.now();
            if (msg instanceof TrueMsg) {
                final String receivedText = ((TrueMsg) msg).getText();
                log.info("{}: from {}: {} with text '{}'", me, msg.getSender(), msg.getId(), receivedText);
                // TODO The next two operations should be a single transaction!
                // TODO For that, one needs to define an autowired class marked @Transactional
                // TODO that performs those operations in a single method.
                final MsgId ackMsgId;
                {
                    db.markMsgAsSeen(msg.getId(), ackedWhen, me);
                    ackMsgId = db.sendAckMsgReturningId(me, (TrueMsg) msg, ackedWhen);
                }
                log.info("{}: acked {} by sending ack-msg {}", me, msg.getId(), ackMsgId);
            } else {
                assert msg instanceof AckMsg;
                log.info("{}: from {}: {}, which is ack-msg for {}", me, msg.getSender(), msg.getId(), ((AckMsg) msg).getTrueMsgId());
                db.markMsgAsSeen(msg.getId(), ackedWhen, me);
                log.info("{}: marked {} as 'seen'", me, msg.getId());
            }
            interrupted = Thread.interrupted();
        }
        return interrupted;
    }

    // ---
    // Runnable implementation
    // ---

    @Override
    public void run() {
        boolean interrupted = false;
        while (!stop.get() && !interrupted) {
            countFreshMsgsAddressedToMeAndPrint();
            interrupted = Thread.interrupted();
            if (!stop.get() && !interrupted) {
                interrupted = sleepRandomly();
            }
            if (!stop.get() && !interrupted) {
                interrupted = sendSeveralMsgsWithRandomText();
            }
            if (!stop.get() && !interrupted) {
                interrupted = processMsg();
            }
        }
    }

}
