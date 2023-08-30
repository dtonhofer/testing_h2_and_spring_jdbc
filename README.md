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

### Test 1: Eliciting "Dirty Reads"

[TestElicitingDirtyReads.java](https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/src/test/java/name/heavycarbon/h2_exercises/transactions/TestElicitingDirtyReads.java)

A "dirty read" (phenomenon "P1" in *A Critique of ANSI SQL Isolation Levels*) happens when transaction T2 (the "reader" transaction)
can read data written by, but not yet committed by, transaction T1 (the "modifier" transaction). 

This unsoundness is supposed to go away at isolation level ANSI "READ COMMITTED" and stronger.

Taking a "data item" to be a record identified by a fixed identifier, we test the following scenarios:

- UPDATE: T1 updates an existing data item D with x in action 0. After that, T2 can read the value x from D even though T1 is still active. This
  is undesirable irrespective of whether T1 eventually rolls back (then T2 has read something that never existed) or commits (then T2 has read something
  "from the future" which can lead to arbitrary problems. T1 may also update D a second time with z for example).
- INSERT: T1 inserts new data item D in action 0. After that, T2 sees D even though T1 is still active.
- DELETE: T1 deletes an existing data item D (considered as writing ε to D) in action 0. After that, T2 can no longer access D (a read yields ε) even though T1 is still active.

<img src="https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/swimlanes/swml_dirty_read.png" alt="Dirty Read swimlanes" width="600" />

GraphML file: [swml_dirty_read.graphml](https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/swimlanes/swml_dirty_read.graphml)

**Result for H2**: Everything is as expected. All three scenarios show up in isolation level ANSI "READ UNCOMMITTED" only.

### Test 2: Eliciting "Non-Repeatable Reads" (aka "Fuzzy Reads")

A "non-repeatable read" (phenomenon "P2" in *A Critique of ANSI SQL Isolation Levels*) happens when transaction T2 (the "reader" transaction)
reads data item D, obtaining value item x. Transaction T1 then updates D to y and commits. T2 then re-reads D and no longer finds the value x 
seen earlier but the value y written by T1, i.e. data already collected for T2 may unexpectedly change during T2.

This unsoundness is supposed to go away at isolation level ANSI "REPEATABLE READ" and stronger.

Taking a "data item" to be a record identified by a fixed identifier, we test the following scenarios:

- UPDATE: T2 reads D in action 0, finding x. In action 1, T1 then updates D to y and commits. T2 then re-reads D and find it has unexpectedly changed, i.e. it obtains y instead of x.
- INSERT: T2 reads D in action 0, and finds it does not exist i.e. it obtains ε. In action 1, T1 then creates D with value y and commits. T2 then re-reads D and find it is unexpectedly present with value y.
- DELETE: T2 reads D in action 0, finding x. In action 1, T1 then deletes D (considered as writing ε to D) and commits. T2 then re-reads D and find it is unexpectedly gone, i.e. it obtains ε.

<img src="https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/swimlanes/swml_non_repeatable_read.png" alt="Non-Repeatable Read swimlanes" width="600" />

GraphML file: [swml_non_repeatable_read.graphml](https://github.com/dtonhofer/testing_h2_and_spring_jdbc/blob/master/doc/swimlanes/swml_non_repeatable_read.graphml)

**Result for H2**: Everything is as expected. All three scenarios show up in isolation level ANSI "READ UNCOMMITTED" and ANSI "READ COMMITTED" only.

### Test 3: Eliciting "Phantom Reads"
