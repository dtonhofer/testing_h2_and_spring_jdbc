# Exercises with the H2 database and Spring JDBC

Code written to exercise myself with H2 and Spring JDBC, including exercising the
behaviour of transactions. 

You know the drill! 

<img src="https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/trying_stuff_until_it_works.png" alt="Trying stuff until it works" />

Some links:

- [Spring Data JDBC](https://spring.io/projects/spring-data-jdbc)
- [The H2 database](http://h2database.com/html/main.html)

A must-read is this technical report, even though it could use a review:

[A Critique of ANSI SQL Isolation Levels](https://arxiv.org/abs/cs/0701157)<br>
*Hal Berenson, Phil Bernstein, Jim Gray, Jim Melton, Elizabeth O'Neil, Patrick O'Neil*<br>
*Proc. ACM SIGMOD 95, pp. 1-10, San Jose CA, June 1995*<br>
*Microsoft Research Technical Report MSR-TR-95-51*<br>
What we have:

## Agents and Messages

Package `name.heavycarbon.h2_exercises.agents_and_msgs`.

Some code to start & run a set of independent threads ("agents") that send messages to each 
other through an H2 table, poll for new messages, and acknowledge the messages.

The executable part is packaged as a JUnit5 test but it doesn't check anything, it just runs
a fixed number of agents for a fixed number of seconds.

There are two type of messages:

- "true messages" carrying some (arbitrary) text from some agent A to some agent B
- "ack messages" sent from agent B to agent A after a "true message" has been
  received at B. "ack messages" are not acknowledged themselves.

Messages have a "state". A message just sent is in state "fresh". After reception,
the receiving agent updates the message's state to "seen" so that the
messages are not picked up again during the next poll.

The JUnit5 test class to run is
[`TestAgentsExchangingMsgs`](https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/src/test/java/name/heavycarbon/h2_exercises/agents_and_msgs/TestAgentsExchangingMsgs.java).

## Storing java.time.Instant

Package `name.heavycarbon.h2_exercises.storing_instants`.

A perennial problem is to make sure a `java.util.Date` or better a 
`[java.time.Instant](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/Instant.html)`
is correctly stored in and retrieved from a database, with no mysterious shifts due
local time zones configured in the database server, the JDBC driver, or otherwise,
getting in the way, possible only on one side of the back-and-forth of the data. 
Here we are testing that.

The JUnit5 test class to run is
[`TestStoringInstants`](https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/src/test/java/name/heavycarbon/h2_exercises/storing_instants/TestStoringInstants.java).

## Trying and testing transactions (still under construction)

Transactions are one of the core problems that databases are supposed to handle (except from getting stuff
on and off the disk with some efficiency), so this is the biggest package. This package also tests 
how to use transactions under Spring JDBC.

### Transaction isolation levels

Reading *A Critique of ANSI SQL Isolation Levels*, we find out that ANSI 92 defintions are quite lousy (maybe they are btter in later issues of the SQL standard)
and that the hierarchy of "isolation level", i.e. the imbricated set of "allowed histories" is rather complex. This graph from the mentioned technical report:

<img src="https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/history_hierarchy.png" width="400" alt="isolation levels matrix" />

### Isolation Levels matrix

<img src="https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/isolation_levels_matrix.png" width="400" alt="isolation levels matrix" />

### Accompanying graphs

The key to the graphs:

<img src="https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/def_key.png" width="400" alt="dirty read explained" />

### Dirty Write (?)

What happens when we two transactions write to the same row?

We can get a timeout, or a deadlock. Interesting.

#### Getting Timeout

[`TestWritingToSameRowAndCannotGetLock`](https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/src/test/java/name/heavycarbon/h2_exercises/transactions/TestWritingToSameRowAndCannotGetLock)

Agent 1 writes to row A, Agent 2 writes to row B, Agent A then writes to row X and doesn't commit. Agent 2 tries to write to the same row X, waits for 2 seconds (by default), then throws an Exception.

A number of interesting facts pop up:

- Instead of receiving a proper `org.springframework.dao.QueryTimeoutException`, Spring
  gives us an `org.springframework.transaction.TransactionSystemException` because it cannot translate the 
  original `org.h2.jdbc.JdbcSQLTimeoutException`. This is probably fixable, but how?
- Empirically the lock timeout is 2 seconds even though [the documentation for 'default lcok timeout'](https://www.h2database.com/html/commands.html#set_default_lock_timeout)
  says it is 1 second. No matter, [`SET DEFAULT_LOCK_TIMEOUT`](https://www.h2database.com/html/commands.html#set_lock_timeout) and
  `SELECT * FROM INFORMATION_SCHEMA.SETTINGS  WHERE SETTING_NAME = 'DEFAULT_LOCK_TIMEOUT'` can be used to set/get the lock timeout.
  You can also set it per session with [`SET LOCK TIMEOUT`](https://www.h2database.com/html/commands.html#set_lock_timeout).

**Getting Deadlock**

[`TestWritingToSameRowAndDetectDeadlock`](https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/src/test/java/name/heavycarbon/h2_exercises/transactions/TestWritingToSameRowAndDetectDeadlock.java)

Agent 1 writes to row A, Agent 2 writes to row B, Agent 1 then writes to row X *and commits*. Agent 2 tries to write to the same row X. Then:

- In isolation levels `REPEATABLE_READ`, `SERIALIZABLE`, `SNAPSHOT`, a `org.springframework.dao.CannotAcquireLockException` with the text
  `Deadlock detected. The current transaction was rolled back` is raised for Agent 2 immediately and the transaction is rolled back,
  although one might argue that there is no real problem in this situation.
- In isolation levels `READ_UNCOMMITTED`, `READ_COMMITTED`, both transactions succeeds and Agent 2, the last one to write, "wins".

I'm not sure why there should be a deadlock in this case.

### "Dirty Read" (P1)

JUnit5 class: [`TestElicitingDirtyReads`](https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/src/test/java/name/heavycarbon/h2_exercises/transactions/TestElicitingDirtyReads.java).

A "dirty read" happens when transaction *T2* can read data written by, but not yet committed by,
transaction *T1*. This unsoundness is supposed to disappear at transaction level `READ COMMITTED` 
and stronger, and it does.

Below are three cases, using a stronger definition of a "dirty read" than the one used by ANSI
in the SQL 92 standard as the latter is imprecise, see *A Critique of ANSI SQL Isolation Levels*.

<img src="https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/def_dirty_read.png" width="400" alt="dirty read explained" />

The code is based on two independent agents (thread + runnable) alternatingly applying their 
operations. In the diagram below, we use a "transaction as snapshot" perspective.

<img src="https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/dirty_read_sequence.png" width="400" alt="dirty read sequence" />

### "Non-Repeatable Read" aka. "Fuzzy Read" (P2)

JUnit5 class: [`TestElicitingNonRepeatableReads`](https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/src/test/java/name/heavycarbon/h2_exercises/transactions/TestElicitingNonRepeatableReads.java).

A "non-repeateable read" happens when transaction *T1* reads data item *D*, another transaction *T2* changes 
that that data item and commits, and then transaction *T1* re-reads the data item and finds it has changed. 
This unsoundness is supposed to disappear at transaction level `REPEATABLE READ` and stronger, and it does.

Below are three cases, using a stronger definition of a "non-repeatable read" than the one used by 
ANSI in the SQL 92 standard as the latter is imprecise, see *A Critique of ANSI SQL Isolation Levels*.

However, the cases INSERT and DELETE are actually "phantom read" scenarios, so will not be coded.

<img src="https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/def_fuzzy_read.png" width="400" alt="fuzzy read explained" />

The code is based on two independent agents (thread + runnable) alternatingly applying their
operations. In the diagram below, we use a "transaction as snapshot" perspective.

<img src="https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/non_repeatable_read_sequence.png" width="400" alt="non-repeatable read sequence" />

### "Phantom Read" (P3)

The JUnit5: [`TestElicitingPhantomReads`](https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/src/test/java/name/heavycarbon/h2_exercises/transactions/TestElicitingPhantomReads.java)

A "phantom read" happens when transaction *T1* reads a subset of data items *Ds* out a set of data items *Ks*
using a predicate *P* as selection criterium, then another transation *T2* changes *Ks* so that the set 
defined by *P* grows or shrinks (maybe to the empty set) and commits, and then transaction *T1* re-reads
the subset defined by *P* and finds it has changed relative to *Ks*.

Contrary to the "fuzzy read", the "phantom read" is about the extent of a selection predicate, which 
should not change for the duration of a transaction.

This unsoundness is supposed to disappear at transaction level `SERIALIZABLE`, and it does.

The code is based on two independent agents (thread + runnable) alternatingly applying their
operations. In the diagram below, we use a "transaction as snapshot" perspective.

<img src="https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/phantom_read_sequence.png" width="400" alt="phantom read sequence" />


### Test Various Sequences

Not properly working for now
    
The Junit5 class is: [`TestVariousSequences`](https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/src/test/java/name/heavycarbon/h2_exercises/transactions/TestVariousSequences.java):










