# Helidon Data JDBC chaos integration tests

This Maven reactor tests how Helidon Data JDBC handles failures and recovers
from them. It runs the same scenarios through the public `JdbcClient` API and
through a generated `@Data.Repository`.

The tests deliberately cause SQL, constraint, conversion, transaction,
physical-session, JDBC lifecycle, lock, and pool-acquisition failures. They
verify that the provider remains usable after each failure. They also verify
that the provider releases connections back to the pool, rolls back failed
transaction work, and does not reveal test canaries in errors that an
application can observe.

## Current test matrix

The default smoke profile runs six tests from
`AbstractJdbcChaosSmokeContract` for each database and application style. The
bounded real-chaos profile adds lifecycle, physical-session disruption, lock,
and pool-contention contracts. The complete profile has 76 test invocations
when Docker is available and all container-backed classes run.

| Database | Imperative module | Declarative module | Test runtime |
| --- | --- | --- | --- |
| H2 | `imperative/h2` | `declarative/h2` | The tests use a dedicated H2 database in memory. |
| MySQL | `imperative/mysql` | `declarative/mysql` | Testcontainers starts MySQL. |
| PostgreSQL | `imperative/pgsql` | `declarative/pgsql` | Testcontainers builds the local image and starts PostgreSQL. |
| Oracle Database | `imperative/oracle` | `declarative/oracle` | Testcontainers starts Oracle Database Free. |

The H2 tests do not require a container runtime. Every container-backed
concrete class uses `@Testcontainers(disabledWithoutDocker = true)`. JUnit
skips these classes when Docker is unavailable instead of reporting failures.

| Contract | H2 | MySQL | PostgreSQL | Oracle Database | Tests per style and database |
| --- | --- | --- | --- | --- | --- |
| Portable database-failure smoke | Yes | Yes | Yes | Yes | 6 |
| Injected JDBC lifecycle failures | Yes | No | No | No | 8 |
| Native physical-session termination | Yes | Yes | Yes | Yes | 1 |
| Lock and pool-acquisition timeouts | Yes | No | No | No | 2 |

The lifecycle contract uses real H2 JDBC connections behind a deterministic,
one-shot test datasource. It does not use a mocked connection. The native
disruption contract acquires the database's session identifier from the
application transaction, waits until the transaction is observably blocked on
a database lock, and terminates that exact session from an independent control
connection. MySQL uses `KILL CONNECTION`, PostgreSQL uses
`pg_terminate_backend`, Oracle Database uses `ALTER SYSTEM KILL SESSION`, and
H2 uses `ABORT_SESSION`.

No scenario relies on random delays. Every asynchronous wait has a bounded
deadline and observes database or pool state before proceeding. Each disruption
or concurrency scenario uses an isolated one-connection application pool so a
leaked or poisoned lease cannot be hidden by another connection.

## Portable smoke contract

Before every test, the fixture restores two contacts. Contact `1` is named
`alpha`, and contact `2` is named `beta`. The fixture also clears the table
used to test generated keys. After every test, it stops the Helidon service
registry and clears the test configuration.

### Malformed SQL failure and recovery

The `malformedSqlFailureIsSanitizedAndAllowsRecoveryQuery` test selects a
column that does not exist. The SQL comment contains a canary that must remain
private. The test expects a `DataException` and examines every visible error in
the exception graph. It examines messages, localized messages, stack traces,
causes, suppressed exceptions, and chained `SQLException` instances. None of
these values may contain the SQL canary, the database URL canary, or a driver
diagnostic canary. The test then confirms that the original two rows remain
unchanged and that the same application adapter can execute a valid count.

### Constraint failure and recovery

The `constraintFailureIsSanitizedAndAllowsRecoveryQuery` test tries to insert
contact ID `1` a second time. It uses a secret bind value as the contact name.
The primary key violation must appear as a sanitized `DataException`. The
error must not reveal the bind value or any configured canary. The test then
confirms that the original data remains intact and that a valid count succeeds.

### Scalar conversion failure and recovery

The `conversionFailureIsSanitizedAndAllowsRecoveryQuery` test selects a canary
string and asks the provider to map it to `Long`. The conversion must fail with
a sanitized `DataException`. The error must not reveal the selected value or
any configuration canary. The test also confirms that the failed result was
closed, the committed data did not change, and a later query succeeds.

### Generated key success and recovery

The `generatedKeyInsertCommitsRowAndAllowsRecoveryQuery` test inserts a row
into the generated key table and reads the key assigned by the database. The
key must be positive. The fixture independently confirms that exactly one row
with the expected name was committed. The test then executes a normal count to
confirm that the application adapter remains usable. This test provides a
successful control case for the generated key lifecycle.

### Transaction rollback and recovery

The `transactionFailureRollsBackPriorJdbcWorkAndAllowsRecoveryQuery` test opens
a local `Tx.Type.REQUIRED` transaction. It inserts a row that contains a
rollback canary and then executes malformed SQL. The transaction boundary must
report a `TxException`, and its exception graph must contain a `DataException`.
The error must not reveal any canary. An independent fixture query confirms
that the first insert was rolled back. The test also confirms that the two
original rows remain and that a later operation outside the transaction
succeeds.

### Connection pool lease recovery

The `malformedSqlFailureReturnsOneConnectionPoolLease` test restarts the
application with a HikariCP data source. The pool contains one connection and
uses a connection timeout of one second. After malformed SQL fails, the Hikari
management bean must report that no connections are active. The test executes a
valid count and checks the pool again. A leaked lease cannot be hidden by a
second connection because the pool contains only one connection.

## Imperative and declarative execution

The shared contract calls the `ChaosContactOperations` interface so that both
application styles run identical scenarios.

`ImperativeChaosContactOperations` creates statements with `JdbcClient`. It
binds positional parameters, requests generated keys, and maps scalar rows.

`DeclarativeChaosContactOperations` delegates to `ChaosContactRepository`.
The annotation processor generates the repository implementation from
`@Jdbc.Statement`, `@Jdbc.Execution`, and `@Jdbc.GeneratedKeys` declarations.

The fixture reads the database through its own `JdbcClient` calls outside the
application adapter. These reads confirm provider recovery without repeating
the application operation that is under test.

## Lifecycle, disruption, and concurrency contracts

`AbstractJdbcLifecycleChaosContract` injects one-shot failures from
`getAutoCommit`, `setAutoCommit(false)`, `commit`, `rollback`,
`setAutoCommit(true)`, `abort`, `Connection.close`, and
`PreparedStatement.close`. It verifies primary and suppressed failure
semantics, invalidation, transaction outcomes where knowable, committed state,
and recovery through a newly created live-driver connection.

`AbstractJdbcConnectionLossChaosContract` terminates a physical database
session while its transaction is blocked in statement execution. It verifies
the transaction failure, sanitization, lease return, rollback of the gate
update, creation of a different session, and a subsequent application query.
The recovery assertions are intentional: the test must fail if the provider or
pool returns a dead physical connection after termination.

`AbstractJdbcConcurrencyChaosContract` creates a real H2 row-lock timeout and
an exhausted one-connection HikariCP pool. It verifies bounded failure,
rollback, lease accounting, committed state, and recovery after the lock or
held lease is released.

### Known product finding

`DATA-JDBC-CHAOS-001` is currently exposed by both H2 connection-loss tests.
After the database terminates an in-flight physical session, the next operation
receives that dead H2 connection and fails from
`JdbcConnectionLease.Owned.acquire` with SQLSTATE `90121`. The imperative and
declarative H2 connection-loss tests are disabled while this finding remains
unresolved. The equivalent MySQL, PostgreSQL, and Oracle Database tests remain
enabled and continue to require immediate recovery with a replacement session.
Do not add a test retry or weaken the recovery assertion when re-enabling the
H2 tests.

## Directory layout

| Path | Purpose |
| --- | --- |
| `pom.xml` | This file defines the reactor, the default smoke tag, and the opt-in real-chaos profile. |
| `common/pom.xml` | This module builds the shared contract and application adapters. |
| `common/src/main/java` | This directory contains the shared test code. |
| `common/src/h2` | This directory contains the H2 configuration, pool support, and schema. |
| `common/src/mysql` | This directory contains the MySQL container configuration, pool support, and schema. |
| `common/src/pgsql` | This directory contains the PostgreSQL image, configuration, pool support, and schema. |
| `common/src/oracle` | This directory contains the Oracle container configuration, pool support, and schema. |
| `imperative/pom.xml` | This module configures Failsafe for the imperative tests. |
| `imperative/h2` | This module runs the imperative contract against H2. |
| `imperative/mysql` | This module runs the imperative contract against MySQL. |
| `imperative/pgsql` | This module runs the imperative contract against PostgreSQL. |
| `imperative/oracle` | This module runs the imperative contract against Oracle Database. |
| `declarative/pom.xml` | This module configures Failsafe for the declarative tests. |
| `declarative/h2` | This module runs the declarative contract against H2. |
| `declarative/mysql` | This module runs the declarative contract against MySQL. |
| `declarative/pgsql` | This module runs the declarative contract against PostgreSQL. |
| `declarative/oracle` | This module runs the declarative contract against Oracle Database. |

The `common/src/main/java` directory is packaged as the shared test support
artifact. Each database module adds the matching
`common/src/<database>/java` directory as a test source. It also adds the
matching resources directory as a test resource. Each concrete test class
chooses the imperative or declarative adapter. Its database superclass
provides configuration, manages the container, and checks the HikariCP pool.

## Maven activation and test selection

The parent `tests/integration/data-jdbc/pom.xml` does not include the chaos
reactor in its normal module list. The `-Ddata.jdbc.chaos=true` property
activates the smoke-only `data-jdbc-chaos` Maven profile and adds this reactor.
The `data-jdbc-chaos-real` Maven profile also adds the reactor and selects all
four contracts. Neither activation is required when Maven starts directly from
`chaos/pom.xml`.

Every contract has the `data-jdbc-chaos` JUnit tag. Individual contracts also
have one of these selection tags:

| Tag | Contract |
| --- | --- |
| `data-jdbc-chaos-smoke` | Portable database-failure smoke coverage |
| `data-jdbc-chaos-lifecycle` | Deterministic live-driver JDBC lifecycle failure injection |
| `data-jdbc-chaos-disruption` | Native physical-session termination during execution |
| `data-jdbc-chaos-concurrency` | Real lock and one-connection-pool timeout coverage |

The default `data.jdbc.chaos.groups` value selects only the smoke contract.
The `data-jdbc-chaos-real` Maven profile selects all four tags. A developer can
override `data.jdbc.chaos.groups` to run one contract while diagnosing a
failure.

Surefire excludes classes whose names end in `Test`. Failsafe explicitly
includes the chaos test packages and runs its `integration-test` and `verify`
goals. Run the Maven `verify` phase instead of the `test` phase to execute
these tests.

## Running the tests

The following commands assume that the repository root is the current working
directory.

Use this command to run the default smoke suite from the JDBC integration
reactor.

```bash
mvn -f tests/integration/data-jdbc/pom.xml -Ddata.jdbc.chaos=true verify
```

Use this command to run the same suite directly from the chaos reactor.

```bash
mvn -f tests/integration/data-jdbc/chaos/pom.xml verify
```

Use this command to run every chaos contract, in both application styles,
against every applicable database. `-Plong-tests` explicitly includes MySQL,
PostgreSQL, and Oracle Database. `-Pdata-jdbc-chaos-real` selects the lifecycle,
disruption, and concurrency contracts in addition to smoke coverage.

```bash
mvn -f tests/integration/data-jdbc/pom.xml \
    -Plong-tests,data-jdbc-chaos-real verify
```

The equivalent command starting directly from the chaos reactor is:

```bash
mvn -f tests/integration/data-jdbc/chaos/pom.xml \
    -Plong-tests,data-jdbc-chaos-real verify
```

If Docker is absent, both commands skip the MySQL, PostgreSQL, and Oracle
Database classes. Missing Docker does not fail the build. Review the Failsafe
summary to distinguish executed tests from skipped tests.

Use this command to run all H2 chaos contracts without Docker.

```bash
mvn -f tests/integration/data-jdbc/chaos/pom.xml \
    -P-long-tests,data-jdbc-chaos-real \
    -pl imperative/h2,declarative/h2 -am verify
```

Use these commands to select one bounded contract for diagnosis.

```bash
mvn -f tests/integration/data-jdbc/chaos/pom.xml \
    -P-long-tests \
    -Ddata.jdbc.chaos.groups=data-jdbc-chaos-lifecycle \
    -pl imperative/h2,declarative/h2 -am verify
mvn -f tests/integration/data-jdbc/chaos/pom.xml \
    -Plong-tests \
    -Ddata.jdbc.chaos.groups=data-jdbc-chaos-disruption verify
mvn -f tests/integration/data-jdbc/chaos/pom.xml \
    -P-long-tests \
    -Ddata.jdbc.chaos.groups=data-jdbc-chaos-concurrency \
    -pl imperative/h2,declarative/h2 -am verify
```

Use this command to run only the H2 smoke contract in both application styles.

```bash
mvn -f tests/integration/data-jdbc/chaos/pom.xml \
    -pl imperative/h2,declarative/h2 -am verify
```

Use these commands to run only the smoke contract for one database through
both application styles.

```bash
mvn -f tests/integration/data-jdbc/chaos/pom.xml \
    -pl imperative/mysql,declarative/mysql -am verify
mvn -f tests/integration/data-jdbc/chaos/pom.xml \
    -pl imperative/pgsql,declarative/pgsql -am verify
mvn -f tests/integration/data-jdbc/chaos/pom.xml \
    -pl imperative/oracle,declarative/oracle -am verify
```

Use these commands to run one application style or one database module.

```bash
mvn -f tests/integration/data-jdbc/chaos/pom.xml -pl imperative -am verify
mvn -f tests/integration/data-jdbc/chaos/pom.xml -pl declarative -am verify
mvn -f tests/integration/data-jdbc/chaos/pom.xml -pl imperative/mysql -am verify
```

## Environment and database information

A supported JDK and Maven are required to build Helidon. The H2 tests need no
additional runtime.

The MySQL, PostgreSQL, and Oracle Database tests require a working Docker and
Testcontainers environment. Docker must be able to obtain the required images.
Helidon dependency management supplies the JDBC driver versions.

### Test database credentials

The container tests use fixed credentials from the test source code. These
credentials are intended only for temporary databases that Testcontainers
creates for this suite. Do not use them for a shared database or for any other
environment.

The H2 tests do not configure a username or password. No H2 credential file
needs to be updated.

The MySQL username is `test`, and its password is `mysql123`. To change these
values, update the `withUsername` and `withPassword` calls in
`common/src/mysql/java/io/helidon/data/jdbc/tests/chaos/support/ChaosMySqlDatabase.java`.

The PostgreSQL username is `test`, and its password is `pgsql123`. To change
these values, update the password passed to `withPassword` and the default
username in
`common/src/pgsql/java/io/helidon/data/jdbc/tests/chaos/support/ChaosPostgreSqlDatabase.java`.
Also update the fallback username and password in
`common/src/pgsql/docker/entrypoint.sh`.

The Oracle Database password is `oracle123`. The test uses it for the built-in
`system` account and for each generated test schema. To change this password,
update the `PASSWORD` constant in
`common/src/oracle/java/io/helidon/data/jdbc/tests/chaos/support/ChaosOracleDatabase.java`.
That constant supplies the container password, the JDBC password, and the
generated schema password.

Choose strong and unique passwords before running the container tests on a
machine where other users or systems can reach the mapped database ports. Keep
each password consistent across the files listed above. Use values that satisfy
the password rules of the selected database image. Do not replace these test
values with production credentials, and do not commit private credentials to
the repository.

The MySQL tests use the
`container-registry.oracle.com/mysql/community-server:9.7.1` image.

The Oracle Database tests use the
`container-registry.oracle.com/database/free:latest-lite` image. The test
support creates a dedicated user and schema after the database starts.

The PostgreSQL tests build an image named `data-jdbc-chaos-pgsql` from
`common/src/pgsql/docker`. The Dockerfile defines its base image.

Every database uses its own schema script. Each schema creates the same contact,
generated-key, and gate tables, inserts the same baseline rows, and exposes a
database-specific session identifier through the same logical view.
