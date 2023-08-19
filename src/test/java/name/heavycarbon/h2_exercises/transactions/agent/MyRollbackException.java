package name.heavycarbon.h2_exercises.transactions.agent;

import org.jetbrains.annotations.NotNull;

public class MyRollbackException extends Exception {

    public MyRollbackException(@NotNull String msg) {
        super(msg);
    }
}
