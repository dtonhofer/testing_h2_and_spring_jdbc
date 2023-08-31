package name.heavycarbon.h2_exercises.transactions.common;

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
