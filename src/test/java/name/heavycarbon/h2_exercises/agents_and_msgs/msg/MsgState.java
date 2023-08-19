package name.heavycarbon.h2_exercises.agents_and_msgs.msg;

import org.jetbrains.annotations.NotNull;

// ---
// Messages have a "state". A message just sent is in state "fresh". After reception,
// the receiving agent updates the message's state to "seen" so that the
// messages are not picked up again during the next poll.
// ---

public enum MsgState {

    fresh(0), seen(1);

    private final int code;

    MsgState(int code) {
        this.code = code;
    }

    public static @NotNull MsgState byCode(int code) {
        return switch (code) {
            case 0 -> fresh;
            case 1 -> seen;
            default -> throw new IllegalArgumentException("No 'state' exists for code " + code);
        };
    }

    public int getRaw() {
        return code;
    }
}
