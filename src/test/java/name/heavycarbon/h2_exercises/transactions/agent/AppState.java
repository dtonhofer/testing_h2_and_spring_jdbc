package name.heavycarbon.h2_exercises.transactions.agent;

import java.util.concurrent.atomic.AtomicInteger;

public class AppState extends AtomicInteger {

    public AppState() {
        super(0);
    }

}
