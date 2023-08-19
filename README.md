# Testing working with the H2 database with Spring JDBC

Code to exercise myself with H2 and Spring JDBC, including testing behaviour of transactions.

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

The JUnit5 test class to run is `TestAgentsExchangingMsgs`.

## Storing java.time.Instant

Package `name.heavycarbon.h2_exercises.storing_instants`.

A perennial problem is to make sure a `java.util.Date` or better a `[java.time.Instant](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/Instant.html)`
is correctly stored in and retrieved from a database, with no mysterious shifts due local time zones configured in the database server, the JDBC driver, or otherwise,
getting in the way, possible only on one side of the back-and-forth of the data. Here we are testing that.

The Junit5 test class to run is `TestStoringInstants`.

## Trying and testing transactions

*Still under extension!*

Package `name.heavycarbon.h2_exercises.transactions`.

Transactions are one of the core problems that databases are supposed to handle (except from getting stuff
on and off the disk with some efficiency), so this is the biggest package. This package also tests 
how to use transactions under Spring JDBC.

The Junit5 test classes to run are:

- `TestElicitingDirtyReads`: Try to elicit a "dirty read" whereby transaction T1
  reads data written but not yet committed by transaction T2. This crass unsoundness is
  supposed to go away at transaction level `READ COMMITTED` and above, and it does.
- `TestElicitingNonRepeatableReads`:  Try to elicit a "non-repeateable read" whereby transaction T1
  reads data set A (defined by some predicate), another transaction T2 changes that set and
  commits, and then transaction T1 re-reads that data set and finds it has changed.
  This unsoundness is supposed to go away at transaction level `REPEATABLE READ` and above, and it does.
- `TestElicitingPhantomReads`
- `TestVariousSequences`
