package name.heavycarbon.h2_exercises.transactions.common;

import name.heavycarbon.h2_exercises.transactions.agent.MyRollbackException;

// Define a special "FunctionalInterface" that takes nothing
// and returns nothing but can throw MyRollbackException.

@FunctionalInterface
public interface ThrowingRunnable {

    void run() throws MyRollbackException;

}
