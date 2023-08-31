# Exercises with the H2 database and Spring JDBC

Code written to exercise myself with H2 and Spring JDBC, including exercising the
behaviour of transactions. 

You know the drill! 

<img src="https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/extras/trying_stuff_until_it_works.png" alt="Trying stuff until it works" width="300" />

## References

Some links:

- [Spring Data JDBC](https://spring.io/projects/spring-data-jdbc)
- [The H2 database](http://h2database.com/html/main.html)

A must-read is this technical report, even though it could use a review:

[A Critique of ANSI SQL Isolation Levels](https://arxiv.org/abs/cs/0701157)<br>
*Hal Berenson, Phil Bernstein, Jim Gray, Jim Melton, Elizabeth O'Neil, Patrick O'Neil*<br>
*Proc. ACM SIGMOD 95, pp. 1-10, San Jose CA, June 1995*<br>
*Microsoft Research Technical Report MSR-TR-95-51*<br>

## Exercise 1: Agents and Messages

Package 
[`name.heavycarbon.h2_exercises.agents_and_msgs`](https://github.com/dtonhofer/testing_h2_and_spring_jdbc/tree/master/src/test/java/name/heavycarbon/h2_exercises/agents_and_msgs).

This is some code to start & run a set of agents (i.e. threads running their `Runnables`) 
that send messages to each other through an H2 table, poll for new messages, and acknowledge
the messages received.

There are two type of messages:

- "true messages" carrying some (arbitrary) text from an agent A to an agent B;
- "ack messages" sent from agent B to agent A after a "true message" from agent A
  has been received at agent B. "ack messages" are not acknowledged themselves.

Messages have a "state". A message that has just been set is in state "fresh". After 
it was found in the database (via polling) by the agent to whom the message was addressed,
the receiving agent updates the message's state to "seen" so that polling does not pick
it up again during the next poll.

The executable part is packaged as a JUnit5 test but it doesn't check anything, it just runs
a fixed number of agents for a fixed number of seconds.

The JUnit5 test class to run is
[`TestAgentsExchangingMsgs`](https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/src/test/java/name/heavycarbon/h2_exercises/agents_and_msgs/TestAgentsExchangingMsgs.java).

## Exercise 2: Storing `java.time.Instant`

Package
[`name.heavycarbon.h2_exercises.storing_instants`](https://github.com/dtonhofer/testing_h2_and_spring_jdbc/tree/master/src/test/java/name/heavycarbon/h2_exercises/storing_instants).

A perennial problem is to make sure a 
[`java.util.Date`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Date.html) 
or (better and in concordance with more modern Java) a 
[`java.time.Instant`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/Instant.html)
is correctly stored in and retrieved from a database. One wants to see no mysterious shifts due
local time zones configured in the database server, the JDBC driver, the system, or otherwise, possibly
being applied only on one side of the back-and-forth of the data. Here we are testing that we can
properly store an `Instant`.

The JUnit5 test class to run is
[`TestStoringInstants`](https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/src/test/java/name/heavycarbon/h2_exercises/storing_instants/TestStoringInstants.java).

## Exercise 3: Testing transactions

Apart from getting data on and off the disk with some efficiency and providing a high-level interface to data definition and manipulation,
transactions are one of the core problems that databases are supposed to handle. In this exercise, we try to find a good way to use transactions
with Spring JDBC and then run a few tests.

### Transaction isolation levels

Reading [*A Critique of ANSI SQL Isolation Levels*](https://arxiv.org/abs/cs/0701157), we find that ANSI 92 defintions of "isolation levels"
are rather unfortunate because they are based not on a theoretical understanding of transactions but on the appearance of a number of "phenomena",
which are supposed to be observable or disallowed at a given isolation level. These phenomena are found to be defined with some lack of clarity 
and do not form an exhaustive set of possibilities. (Maybe there are better definitions in later issues of the SQL standard?)

A matrix of the ANSI SQL isolation levels and the phenomena which are allowed or disallowed, depending on the same:

<img src="https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/general/isolation_levels_matrix.png" alt="ANSI SQL isolation level vs phenomena matrix"  />

Also available as [OpenDocument](https://en.wikipedia.org/wiki/OpenDocument) spreadsheet: [isolation_levels_matrix.ods](https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/general/isolation_levels_matrix.ods).

If one equates an "isolation level" to the set of possible "histories" that it allows, where a history is the timeline of interleaved actions
performed by a set of transactions on a set of data items, on obtains a graph expressing the "stronger than" relation, which is equivalent to
the subset relation among histories.

This image was adapted from [*A Critique of ANSI SQL Isolation Levels*](https://arxiv.org/abs/cs/0701157):

<img src="https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/general/isolation_levels_hierarchy.png" alt="Isolation level hierarchy" width="1200"  />

Also available as [GraphML](https://en.wikipedia.org/wiki/GraphML) file: [isolation_levels_hierarchy.graphml](https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/general/isolation_levels_hierarchy.graphml).

To illustrate what is happening in a particular test case, we will use swimlane diagrams with the following key:

<img src="https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/swimlanes/swml_key.png" alt="swimlane key" />

Also available as [GraphML](https://en.wikipedia.org/wiki/GraphML) file: [swml_key.graphml](https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/swimlanes/swml_key.graphml).

Note that in all case, the transactions are running on the same isolation level. In any case, H2 does not allow sessions with different isolation levels.

For notation purposes, ε (epsilon) is considered to be "the empty datum". In that view, if you read a nonexistent data item D, you obtain ε. 
If you write ε to a data item D, you erase it.

### Action sequencing

Implementationwise, we run separate thread to animate separate transactions.
The reason that we have several threads is that a Spring Data Transaction is a "per thread" concept, being equatable to a 
stack frame that is pushed on "transaction start" and popped on "transaction end". We cannot just keep 
connections in a data structure that is managed by a single thread.

As the threads run, they traverse a series of numbered "actions" in strict sequence, with an action implying operations
like reading, updating, inserting or deleting - generally just 1 operation. The strict action sequence is maintained by a
common "state" integer variable (an `AtomicInteger` inside class `AppState`) which is incremented by 1 after an action has
been executed by the proper thread.

At any time, only a single thread is able to run. This is done by having all threads synchronize on the single `AppState`
instance (i.e. acquire the monitor) early on ("high in the call stack") and never release the monitor except by calling
[`AppState.notify()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html#notify())
and then 
[`AppState.wait()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html#wait()) when they
notice that the current value of the state integer means it's not their turn. Calling `wait()` 
releases the monitor which is then acquired by the other, previously notified thread. This works like a handy 
"permission to act" token that needs little code. The result is a state machine animated by two threads.

Some diagrams showing how the state machine works (but from an early code iteration so may no longer fully reflect the code)

#### State machine for eliciting "Dirty Reads"

<img src="https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/sequences/dirty_read_sequence.png" alt="Dirty Read sequence" width="600" />

GraphML file: [dirty_read_sequence.graphml](https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/sequences/dirty_read_sequence.graphml)

#### State machine for eliciting "Non-Repeatable Reads"

<img src="https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/sequences/non_repeatable_read_sequence.png" alt="Non-Repeatable Read sequence" width="600" />

GraphML file: [non_repeatable_read_sequence.graphml](https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/sequences/non_repeatable_read_sequence.graphml)

#### State machine for eliciting "Phantom Reads"

<img src="https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/sequences/phantom_read_sequence.png" alt="Phantom Read sequence" width="600" />

GraphML file: [phantom_read_sequence.graphml](https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/sequences/phantom_read_sequence.graphml)

### Test 1: Eliciting "Dirty Reads"

[TestElicitingDirtyReads.java](https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/src/test/java/name/heavycarbon/h2_exercises/transactions/TestElicitingDirtyReads.java)

A "dirty read" (phenomenon "P1" in *A Critique of ANSI SQL Isolation Levels*) happens when transaction T2 (the "reader" transaction)
can read data written by, but not yet committed by, transaction T1 (the "modifier" transaction). 

This unsoundness is supposed to go away at isolation level ANSI "READ COMMITTED" and stronger.

Taking a "data item" to be a record identified by a fixed identifier, we test the following scenarios:

- **UPDATE**: T1 updates an existing data item D with x in action 0.
  After that, T2 can read the value x from D even though T1 is still active.
  This is undesirable irrespective of whether T1 eventually rolls back (then T2 has
  read something that never existed) or commits (then T2 has read something "from the future" which can lead to arbitrary problems.
  T1 may also update D a second time with z for example).
- **INSERT**: T1 inserts new data item D in action 0.
  After that, T2 sees D even though T1 is still active.
- **DELETE**: T1 deletes an existing data item D (considered as writing ε to D) in action 0.
  After that, T2 can no longer access D (a read yields ε) even though T1 is still active.

<img src="https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/swimlanes/swml_dirty_read.png" alt="Dirty Read swimlanes" width="600" />

GraphML file: [swml_dirty_read.graphml](https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/swimlanes/swml_dirty_read.graphml)

**Result for H2**

Everything is as expected. All three scenarios show up in isolation level ANSI "READ UNCOMMITTED" only.

### Test 2: Eliciting "Non-Repeatable Reads" (aka "Fuzzy Reads")

A "non-repeatable read" (phenomenon "P2" in *A Critique of ANSI SQL Isolation Levels*) happens when transaction T2 (the "reader" transaction)
reads data item D, obtaining value item x. Transaction T1 (the "modifier" transaction) then updates D to y and commits. T2 then re-reads D and no longer finds the value x 
seen earlier but the value y written by T1, i.e. data entrained via reads into T2 may unexpectedly change during T2.

This unsoundness is supposed to go away at isolation level ANSI "REPEATABLE READ" and stronger.

Taking a "data item" to be a record identified by a fixed identifier, we test the following scenarios:

- **UPDATE**: T2 reads D in action 0, finding x.
  Then, in action 1, T1 updates D to y and commits.
  T2 then re-reads D and find it has unexpectedly changed, i.e. it obtains y instead of x.i
- **INSERT**: T2 reads D in action 0, and finds it does not exist i.e. it obtains ε.
  Then, in action 1, T1 creates D with value y and commits.
  T2 then re-reads D and find it is unexpectedly present with value y.
- **DELETE**: T2 reads D in action 0, finding x.
  Then, in action 1, T1 then deletes D (considered as writing ε to D) and commits.
  T2 then re-reads D and find it is unexpectedly gone, i.e. it obtains ε.

<img src="https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/swimlanes/swml_non_repeatable_read.png" alt="Non-Repeatable Read swimlanes" width="600" />

GraphML file: [swml_non_repeatable_read.graphml](https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/swimlanes/swml_non_repeatable_read.graphml)

**Result for H2**

All three scenarios show up in isolation level ANSI "READ UNCOMMITTED" and ANSI "READ COMMITTED" only.
However, in level ANSI "READ COMMITTED", apparently randomly, in about ~0.17% of the cases, the anomaly is *not* observed. 
So something is going on.

### Test 3: Eliciting "Phantom Reads"

[TestElicitingPhantomReads.java](https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/src/test/java/name/heavycarbon/h2_exercises/transactions/TestElicitingPhantomReads.java)

A "phantom read" is a more hairy phenomenon as it involves result sets defined by predicates (hence the concept of a "predicate lock"). 

A "phantom read" (phenomenon "P3" in *A Critique of ANSI SQL Isolation Levels*) happens when transaction T2 (the "reader" transaction)
selects a set of data item using a predicate, obtaining the result Ds:P with value set Xs. Transaction T1 (the "modifier" transaction) then
updates the database so that Ds:P is extended to some Xs ∪ Ys (or reduced to some Xs - Ys) and commits. T2 then re-reads Ds:P and no longer
finds the value set Xs seen earlier but the value set Xs ∪ Ys (or Xs - Ys) written by T1, i.e. data entrained via predicate-based reads into T2
may unexpectedly grow or shrink during T2.

This are actually quite similar to "non-repeatable reads" and it is not immediately evident what the
essential difference is. After all, if you select "by id" when trying to elicit a "non-repeatable read", you are really using a selection "predicate" 
already.

This unsoundness is supposed to go away at isolation levels "SERIALIZABLE" and "SNAPSHOT".

Taking a "data item" to be a record identified by a fixed identifier, we test the following scenarios:

- **UPDATE-INTO-PREDICATE** and **INSERT-INTO-PREDICATE**: Ds:P grows due to an update of a record D previously outside of Ds:P resulting in it being in Ds:P.
  T2 reads Ds:P in action 0, finding Xs.
  In action 1, T1 then updates Ds:P to Xs ∪ Ys via an update or an insert, and commits.
  T2 then re-reads Ds:P and find it has unexpectedly grown.
- **UPDATE-OUT-OF-PREDICATE** and **DELETE-FROM-PREDICATE**: Ds:P shrinks due to an update of a record D previously inside of Ds:P resulting in it being outside of Ds:P.
  T2 reads Ds:P in action 0, finding Xs.
  In action 1, T1 then updates Ds:P to Xs - Ys via an update or a delete, and commits.
  T2 then re-reads Ds:P and find it has unexpectedly shrunk.

<img src="https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/swimlanes/swml_phantom_read.png" alt="Non-Repeatable Read swimlanes" width="600" />

GraphML file: [swml_non_repeatable_read.graphml](https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/swimlanes/swml_phantom_read.graphml)

**Result for H2**

I have been unable to produce a phantom read in isolation level ANSI "REPEATABLE READ". They only occur in 
levels ANSI "READ UNCOMMITTED" and ANSI "READ COMMITTED"! Maybe I'm doing something wrong or the H2 implementation fixes
the "phantom read" problem at lower levels already.

Moreover, in level ANSI "READ COMMITTED", apparently randomly, in about ~0.13% of the cases, the anomaly is *not* observed,
similar to the "Non-Repeatabale Reads". 

### Test 4: Eliciting "SQL Timeout"

[TestElicitingSqlTimeout.java](https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/src/test/java/name/heavycarbon/h2_exercises/transactions/TestElicitingSqlTimeout.java)

If a write lock on some data item D is held by transaction T1 and then a transaction T2 tries to write that some item D,
T2 will wait a few hundred milliseconds for that lock to be released before an exception signaling a timeout is raised.

Here is the scenario.

1. In actions 0 and 1, both transactions update non-conflicting data items with a marker string so that we can check that
rollbeck properly happened. Actions 0 and 1 can also be left out.
2. In action 2, transaction T1 writes to data item X but but does not yet commit. In action 3, transaction T2 tries to 
write to X too but cannot acquire the lock. After a wait time, an exception is raised on T2 is rolled back. 
3. Once the thread animating T2 has terminated, an internal lock is liberated and T1 can continue to action 4, after
which it commits. Data item X will have been updated with the text of T1.

<img src="https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/swimlanes/swml_sql_timeout.png" alt="Dirty Read swimlanes" width="600" />

GraphML file: [swml_sql_timeout.graphml](https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/swimlanes/swml_sql_timeout.graphml)

**Result for H2**

- At the JDBC level, the exception raised is an [`org.h2.jdbc.JdbcSQLTimeoutException`](https://h2database.com/javadoc/org/h2/jdbc/JdbcSQLTimeoutException.html)
  with the text `Timeout trying to lock table "STUFF"; SQL statement: ...`
- At the Spring Data JDBC level, the exception raised is an [`org.springframework.dao.QueryTimeoutException`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/dao/QueryTimeoutException.html), with the H2 exception as cause and with the text `PreparedStatementCallback; SQL [...]; Timeout trying to lock table "STUFF"; SQL statement: ...`
- At the Spring Transaction level, a problem appears. Apparently Spring tries to "translate" the original exception somehow, but fails.
  It then throws a [`org.springframework.transaction.TransactionSystemException`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/TransactionSystemException.html) with the message `JDBC rollback failed`, which is confusing. Can one fix that?

Empirically lock timeout is a few ms more than 2 seconds, although the manual for [`SET DEFAULT LOCK TIMEOUT`](https://www.h2database.com/html/commands.html#set_default_lock_timeout)
says it is actually 1 seconds. One can also set the lock timeout on a per-session basis: [`SET LOCK TIMEOUT`](https://www.h2database.com/html/commands.html#set_lock_timeout).

Run `SELECT * FROM INFORMATION_SCHEMA.SETTINGS WHERE SETTING_NAME = 'DEFAULT_LOCK_TIMEOUT'` to check the current value. 

Try `SET DEFAULT_LOCK_TIMEOUT 500` to accelerate the tests.

### Test 5: Eliciting "Read Skew" and "Write Skew"

We will pass on this for now, but here is a swimline to explain them (if I understoof them correctly):

<img src="https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/swimlanes/swml_read_and_write_skew.png" alt="Read Skew and Write Skew swimlanes" width="600" />

GraphML file: [swml_read_and_write_skew.graphml](https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/swimlanes/swml_read_and_write_skew.graphml)

### Test 6: Eliciting "Deadlock"

Here is a scenario for a "deadlock", which occurs when the database engine finds that the transactions have pretzelized themselves. 
In any isolation level above "READ COMMMITTED":

- Transaction T2 reads an existing data item X. If X is not read by T2, there won't be a deadlock!
- Once T2 has read X, T1 updates it and then commits.
- If T2 now tries to update X, an exception is raised to roll back T2, saying that a deadlock was detected. (T2 may or may not read X again before updating it, it doesn't matter)

 Alternatively, a "shifted" version:

- Transaction T1 updates an existing data item X.
- After that transaction T2 reads data item X (if X is not read by T2, there is no deadlock!)
- T1 then commits.
- If T2 now tries to update X, an exception is raised to roll back T2, saying that a deadlock was detected. (T2 may or may not read X again before updating it, it doesn't matter)

The scenario is not a problem for isolation levesl "READ UNCOMMITED" and "READ COMMITTED".

In the illustration belowm, "action 0" only exists due to implementation issues. 
The code for the thread running T1 has a structure that demands it must first encounter an action before entering a transaction. So be it!

<img src="https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/swimlanes/swml_deadlock_simple.png" alt="Simple deadlock swimlanes" width="600" />

GraphML file: [swml_sql_timeout.graphml](https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/swimlanes/swml_deadlock_simple.graphml)

[TestElicitingDeadlockSimple.java](https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/src/test/java/name/heavycarbon/h2_exercises/transactions/TestElicitingDeadlockSimple.java)

