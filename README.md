# Exercises with the H2 database and Spring JDBC

Code written to exercise myself with H2 and Spring JDBC, including exercising the
behaviour of transactions. 

You know the drill! 

<img src="https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/trying_stuff_until_it_works.png" alt="Trying stuff until it works" />

Some links:

- [Spring Data JDBC](https://spring.io/projects/spring-data-jdbc)
- [The H2 database](http://h2database.com/html/main.html)

A must-read is this technical report:

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

The Junit5 test classes to run are:

- [`TestElicitingDirtyReads`](https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/src/test/java/name/heavycarbon/h2_exercises/transactions/TestElicitingDirtyReads.java):
  Try to elicit a "dirty read" whereby transaction T1
  reads data written but not yet committed by transaction T2. This crass unsoundness is
  supposed to go away at transaction level `READ COMMITTED` and above, and it does.
- [`TestElicitingNonRepeatableReads`](https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/src/test/java/name/heavycarbon/h2_exercises/transactions/TestElicitingNonRepeatableReads.java):
  Try to elicit a "non-repeateable read" whereby transaction T1
  reads data set A (defined by some predicate), another transaction T2 changes that set and
  commits, and then transaction T1 re-reads that data set and finds it has changed.
  This unsoundness is supposed to go away at transaction level `REPEATABLE READ` and above, and it does.
- [`TestElicitingPhantomReads`](https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/src/test/java/name/heavycarbon/h2_exercises/transactions/TestElicitingPhantomReads.java):
  Try to elicit a "phantom read".
- [`TestVariousSequences`](https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/src/test/java/name/heavycarbon/h2_exercises/transactions/TestVariousSequences.java):
  Not properly working for now
- [`TestWritingToSameRowAndCannotGetLock`](https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/src/test/java/name/heavycarbon/h2_exercises/transactions/TestWritingToSameRowAndCannotGetLock):
  Agent 1 writes to row A, Agent 2 writes to row B, Agent A then writes to row X and doesn't commit. Agent 2 tries to write to the same row X, waits for 2 seconds (by default), then throws an Exception.
  A number of interesting facts pop up:
  - Instead of receiving a proper `org.springframework.dao.QueryTimeoutException`, Spring
    gives us an `org.springframework.transaction.TransactionSystemException` because it cannot translate the 
    original `org.h2.jdbc.JdbcSQLTimeoutException`. This is probably fixable, but how?
  - Empirically the lock timeout is 2 seconds even though [the documentation for 'default lcok timeout'](https://www.h2database.com/html/commands.html#set_default_lock_timeout)
    says it is 1 second. No matter, [`SET DEFAULT_LOCK_TIMEOUT`](https://www.h2database.com/html/commands.html#set_lock_timeout) and
    `SELECT * FROM INFORMATION_SCHEMA.SETTINGS  WHERE SETTING_NAME = 'DEFAULT_LOCK_TIMEOUT'` can be used to set/get the lock timeout.
    You can also set it per session with [`SET LOCK TIMEOUT`](https://www.h2database.com/html/commands.html#set_lock_timeout).
- [`TestWritingToSameRowAndDetectDeadlock`](https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/src/test/java/name/heavycarbon/h2_exercises/transactions/TestWritingToSameRowAndDetectDeadlock.java):
  Agent 1 writes to row A, Agent 2 writes to row B, Agent 1 then writes to row X *and commits*. Agent 2 tries to write to the same row X. Then:
  - In isolation levels `REPEATABLE_READ`, `SERIALIZABLE`, `SNAPSHOT`, a `org.springframework.dao.CannotAcquireLockException` with the text
    `Deadlock detected. The current transaction was rolled back` is raised for Agent 2 immediately and the transaction is rolled back,
    although one might argue that there is no real problem in this situatoin.
  - In isolation levels `READ_UNCOMMITTED`, `READ_COMMITTED`, both transactions succeeds and Agent 2, the last one to write, "wins".  


### Isolation Levels matrix

<img src="https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/isolation_levels_matrix.png" width="400" alt="isolation levels matrix" />

### Dirty Read sequence

<img src="https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/dirty_read_sequence.png" width="400" alt="dirty read sequence" />

### Non-Repeatable Read sequence

<img src="https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/non_repeatable_read_sequence.png" width="400" alt="non-repeatable read sequence" />

### Phantom Read sequence

<img src="https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/phantom_read_sequence.png" width="400" alt="phantom read sequence" />




