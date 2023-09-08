package name.heavycarbon.h2_exercises.transactions.common;

// ---
// This is called if we want to have a random small delay at the
// startup of threads. To see whether anything changes.
// ---

public abstract class Randomizer {

    public static void randomizeStartup() {
        try {
            // wait between 0 and 20ms, randomly
            Thread.sleep(Math.round(Math.random()) * 20);
        } catch (InterruptedException ex) {
            // NOP
        }
    }
}
