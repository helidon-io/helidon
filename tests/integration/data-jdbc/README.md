# Helidon Data JDBC integration tests

This module verifies the Helidon Data JDBC provider against the supported JDBC
databases and through both supported application styles:

- imperative use of the public `JdbcClient` API; and
- generated declarative repositories using `@Jdbc.Statement` and related JDBC
  annotations.

The tests are intentionally integration-level. They validate provider behavior
against real JDBC drivers, database dialect differences, transaction behavior,
generated keys, resource ownership, and SQL and bind handling.

## Supported databases and JDBC drivers

Driver versions are inherited from Helidon Maven dependency management. Do not
hard-code driver versions in tests unless the project dependency management is
also changed.

| Database | Driver dependency | Configured JDBC driver class | Runtime used by tests |
| --- | --- | --- | --- |
| MySQL | `com.mysql:mysql-connector-j` | `com.mysql.cj.jdbc.Driver` | Testcontainers MySQL using `container-registry.oracle.com/mysql/community-server:9.7.3` |
| PostgreSQL | `org.postgresql:postgresql` | `org.postgresql.Driver` | Testcontainers image built from `common/fixtures/pgsql/src/main/resources/db/pgsql/docker` |
| Oracle Database | `com.oracle.database.jdbc:ojdbc17-production` | `oracle.jdbc.OracleDriver` | Testcontainers Oracle Database Free using `container-registry.oracle.com/database/free:23.26.3.0-lite` |

External database tests require a working Docker/Testcontainers environment.

The shared Oracle fixture raises `PROCESSES` to 400 to accommodate bursts of
short-lived, unpooled connections between listener load updates. Its startup
script restarts the prebuilt database to apply this static parameter. The
fixture waits for the script's success marker before creating test schemas;
the image's own readiness message appears before startup scripts run. The
tests retain direct JDBC connections and their existing concurrency.

## Test database credentials

> **Note:** These tests intentionally use hard-coded database credentials.
> They are demonstration values for temporary databases that Testcontainers
> creates for this suite only. Do not reuse them for shared databases,
> production systems, or any other environment.

The MySQL username is `test`, and its password is `mysql123`. To change these
values, update the `withUsername` and `withPassword` calls in
`common/fixtures/mysql/src/main/java/io/helidon/tests/integration/data/jdbc/database/mysql/MySqlDatabase.java`.

The PostgreSQL username is `test`, and its password is `pgsql123`. To change
these values, update the password passed to `withPassword` and the default
username in
`common/fixtures/pgsql/src/main/java/io/helidon/tests/integration/data/jdbc/database/pgsql/PostgreSqlDatabase.java`.
Also update the fallback username and password in
`common/fixtures/pgsql/src/main/resources/db/pgsql/docker/entrypoint.sh`.

The Oracle Database password is `oracle123`. The tests use it for the built-in
`system` account and for each generated integration test schema. To change this
password, update the `PASSWORD` constant in
`common/fixtures/oracle/src/main/java/io/helidon/tests/integration/data/jdbc/database/oracle/OracleDatabase.java`.
That constant supplies the container password, the JDBC password, and the
generated schema password.

Choose strong and unique passwords before running the container tests on a
machine where other users or systems can reach the mapped database ports. Keep
each password consistent across the files listed above. Use values that satisfy
the password rules of the selected database image. Do not replace these test
values with production credentials, and do not commit private credentials to
the repository.

## Directory layout

The main reactor is `tests/integration/data-jdbc/pom.xml`.

```text
tests/integration/data-jdbc/
  common/                  Shared-code aggregator.
    application/
      src/main/java/       Shared models, repositories, adapters, contracts, and helpers.
      src/main/resources/  Portable SQL resources.
      src/test/java/       Tests of shared helpers.
    fixtures/
      mysql/
      oracle/
      pgsql/               Database fixture JARs with src/main/java and src/main/resources.
  imperative/
    mysql/                 Includes public client and resource-ownership regressions.
    oracle/
    pgsql/                 Imperative JdbcClient tests by database.
  declarative/
    mysql/                 Includes generated repository/client-configuration regressions.
    oracle/
    pgsql/                 Generated repository tests by database.
  mixed-provider/          MySQL/Hikari application combining Jakarta Persistence and JDBC repositories.
```

Integration applications and test support use the base package
`io.helidon.tests.integration.data.jdbc`. Database fixtures use
`.database.mysql`, `.database.oracle`, and `.database.pgsql` beneath this base.
Public client regressions use `.imperative.mysql`; generated repository
configuration regressions use `.declarative.mysql`.

All integration sources use `io.helidon.tests.integration.data.jdbc.*`.
Package-private JDBC implementation tests live with the provider tests under
`data/jdbc/jdbc/src/test/java/io/helidon/data/jdbc`. The former top-level MySQL
module has been removed; its public client and transaction coverage runs in
`imperative/mysql`, and its repository configuration coverage runs in
`declarative/mysql`.

Database leaves depend on their fixture JAR with test scope. Fixture resources
are loaded from the classpath, including PostgreSQL's Dockerfile and entrypoint,
so running a leaf does not depend on the shell's working directory. Database
fixtures do not depend on the shared test application.

The imperative and declarative modules continue to compile
`common/application/src/main/java` as test sources. Their annotation processors differ:
imperative leaves generate services, while declarative leaves also generate
JDBC repositories. Keep this application compilation local to each consumer;
do not also add the application JAR to their dependencies. This preserves generated
service discovery and tests that inspect locally generated repository sources.

`common/fixtures/mysql` also supplies `MySqlTestDatabase`, a suite-owned JUnit
extension that provisions isolated schemas for regression tests. It uses a
separate root-enabled container from the portable MySQL contract fixture.
Mockito-based connection instrumentation stays in the tests that need it.
The declarative client-configuration regression explicitly loads
`jdbc-client-config.yaml`, keeping its configuration isolated from the other
MySQL contracts in the same module.

The `mixed-provider` module is an independent application and test suite. It
uses both Jakarta Persistence and JDBC annotation processors, verifies the
generated repository implementations and their provider-specific qualifiers,
and invokes application endpoints backed by a Jakarta Persistence repository,
a declarative JDBC repository, and the imperative `JdbcClient` API. Its Hikari
pool uses a Testcontainers MySQL database initialized from `mixed-provider/src/main/resources/init.sql`.

## Maven profiles

The `long-tests` profile is defined once in the Data JDBC reactor. It includes
`imperative`, `declarative`, and `mixed-provider`, covering every
container-backed suite. The style aggregators include their database leaves
without further profile gates. The `common` aggregator includes its `application` and `fixtures` modules
regardless of this profile; these modules do not start databases during their
builds.

As in the DbClient integration reactor, `long-tests` activates automatically
when the Maven property of the same name is absent. Container-backed tests skip
when Docker is unavailable.

| Option | Meaning in this reactor |
| --- | --- |
| No option | Includes all suites because the `long-tests` property is absent. |
| `-Plong-tests` | Explicitly activates the profile and includes all suites. |
| `-P-long-tests` | Explicitly deactivates the profile; only support modules remain. |
| `-Dlong-tests` or `-Dlong-tests=false` | Defines the property, preventing automatic activation; only support modules remain unless the profile is explicitly activated. |

The profile controls reactor membership, not execution of an explicitly targeted
leaf. A direct leaf build still runs that leaf's tests.

The commands below use explicit profile activation or deactivation to avoid
depending on the `!long-tests` automatic-activation rule.

## Regular integration tests

### Run every test against every supported database

Start Docker, make sure the configured container registries are reachable, and
run:

```bash
mvn -f tests/integration/data-jdbc/pom.xml -Plong-tests verify
```

This command runs the mixed-provider test and the
imperative and declarative test suites for MySQL, PostgreSQL, and Oracle
Database. The external database tests use Testcontainers. They are skipped when
Docker is unavailable, so a successful Maven build does not by itself prove
that those database tests ran.

### Run one external database across both styles

```bash
mvn -f tests/integration/data-jdbc/pom.xml -Plong-tests -pl imperative/mysql,declarative/mysql -am verify
mvn -f tests/integration/data-jdbc/pom.xml -Plong-tests -pl imperative/pgsql,declarative/pgsql -am verify
mvn -f tests/integration/data-jdbc/pom.xml -Plong-tests -pl imperative/oracle,declarative/oracle -am verify
```

### Run one style across all configured databases

```bash
mvn -f tests/integration/data-jdbc/pom.xml -Plong-tests -pl imperative/mysql,imperative/pgsql,imperative/oracle -am verify
mvn -f tests/integration/data-jdbc/pom.xml -Plong-tests -pl declarative/mysql,declarative/pgsql,declarative/oracle -am verify
```

### Run only the mixed-provider test

```bash
mvn -f tests/integration/data-jdbc/pom.xml -Plong-tests -pl mixed-provider -am clean package
```

### Run a single leaf locally

Selecting a leaf from the reactor is recommended because `-am` builds its
required fixture modules:

```bash
mvn -f tests/integration/data-jdbc/pom.xml -Plong-tests -pl imperative/mysql -am clean verify
```

To invoke a leaf directly, first install the test parent POMs and support
artifacts at the current Helidon version. The parent POM steps are needed if
they have not already been installed by a repository test build; installing
only the Data JDBC reactor does not install its ancestors:

```bash
mvn -N -f tests/pom.xml install
mvn -N -f tests/integration/pom.xml install
mvn -f tests/integration/data-jdbc/pom.xml -P-long-tests install
mvn -f tests/integration/data-jdbc/imperative/mysql/pom.xml clean verify
```

The final command can also be run as `mvn clean verify` from the leaf directory.
Maven does not build sibling fixture modules automatically during a direct leaf
build. Both approaches require the usual Helidon dependencies to be available
from a prior repository build or the configured artifact repositories.

### Build support without running database suites

```bash
mvn -f tests/integration/data-jdbc/pom.xml -P-long-tests verify
```

## Test design notes

- Prefer portable contracts in `common/application/src/main/java` and small database leaves
  that only provide configuration and fixture differences.
- Keep reusable database setup in `common/fixtures/<db>/src/main` and consume it through
  a test-scoped dependency instead of adding sibling database source roots.
- Keep database-specific SQL and generated-key behavior explicit. Some drivers
  legitimately differ; for example, MySQL Connector/J exposes generated keys
  differently from PostgreSQL and Oracle.
- Use HikariCP one-connection pools when a test must prove that a failed
  operation returned its connection lease.
- Do not let application-visible failures expose configured SQL, secret bind
  values, driver diagnostic canaries, or raw next-exception details.
