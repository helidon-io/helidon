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
| H2 | `com.h2database:h2` | `org.h2.Driver` | In-memory H2 database |
| MySQL | `com.mysql:mysql-connector-j` | `com.mysql.cj.jdbc.Driver` | Testcontainers MySQL using `container-registry.oracle.com/mysql/community-server:9.7.1` |
| PostgreSQL | `org.postgresql:postgresql` | `org.postgresql.Driver` | Testcontainers image built from `common/src/pgsql/docker` |
| Oracle Database | `com.oracle.database.jdbc:ojdbc17-production` | `oracle.jdbc.OracleDriver` | Testcontainers Oracle Database Free using `container-registry.oracle.com/database/free:latest-lite` |

External database tests require a working Docker/Testcontainers environment.
H2 tests do not require Docker.

## Test database credentials

> **Note:** These tests intentionally use hard-coded database credentials.
> They are demonstration values for temporary databases that Testcontainers
> creates for this suite only. Do not reuse them for shared databases,
> production systems, or any other environment.

The MySQL username is `test`, and its password is `mysql123`. To change these
values, update the `withUsername` and `withPassword` calls in
`common/src/mysql/java/io/helidon/data/jdbc/tests/database/MySqlDatabase.java`.

The PostgreSQL username is `test`, and its password is `pgsql123`. To change
these values, update the password passed to `withPassword` and the default
username in
`common/src/pgsql/java/io/helidon/data/jdbc/tests/database/PostgreSqlDatabase.java`.
Also update the fallback username and password in
`common/src/pgsql/docker/entrypoint.sh`.

The Oracle Database password is `oracle123`. The tests use it for the built-in
`system` account and for each generated integration test schema. To change this
password, update the `PASSWORD` constant in
`common/src/oracle/java/io/helidon/data/jdbc/tests/database/OracleDatabase.java`.
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
  common/
    src/main/java/        Shared contracts, application adapters, repositories, and support code.
    src/<db>/java/        Database-specific fixtures and configuration helpers.
    src/<db>/resources/   Database-specific Testcontainers initialization resources.
  h2/                     Provider-level H2 integration tests.
  imperative/
    h2/
    mysql/
    oracle/
    pgsql/                Imperative JdbcClient tests by database.
  declarative/
    h2/
    mysql/
    oracle/
    pgsql/                Generated repository tests by database.
```

The imperative and declarative database leaves add the appropriate
`common/src/<db>` source and resource directories as test roots. Keep
database-specific setup in the database leaf or `common/src/<db>`; keep portable
test contracts in `common/src/main/java`.

## Maven profiles

The MySQL, PostgreSQL, and Oracle Database modules are in the `long-tests`
Maven profile. This profile is enabled by default so the complete integration
suite continues to run without additional command-line options.

The Maven options have different meanings:

| Option | Meaning in this reactor |
| --- | --- |
| `-Plong-tests` | Explicitly activates the `long-tests` Maven profile and includes the MySQL, PostgreSQL, and Oracle Database modules. |
| `-P-long-tests` | Explicitly deactivates the `long-tests` Maven profile and leaves only the H2 modules enabled. |
| `-Dlong-tests` | Defines a Maven property. Because the profile is automatically activated only when this property is absent (`!long-tests`), defining it also excludes the external database modules. It does not enable them. |

The commands below use explicit profile activation or deactivation to avoid
depending on the `!long-tests` automatic-activation rule.

## Regular integration tests

### Run every test against every supported database

Start Docker, make sure the configured container registries are reachable, and
run:

```bash
mvn -f tests/integration/data-jdbc/pom.xml -Plong-tests verify
```

This command runs the provider-level H2 tests and the imperative and declarative
test suites for H2, MySQL, PostgreSQL, and Oracle Database. The external database
tests use Testcontainers. They are skipped when Docker is unavailable, so a
successful Maven build does not by itself prove that those database tests ran.

### Run only H2 tests

Explicitly deactivate the profile to run the provider-level, imperative, and
declarative H2 tests without Docker:

```bash
mvn -f tests/integration/data-jdbc/pom.xml -P-long-tests verify
```

### Run one external database across both styles

```bash
mvn -f tests/integration/data-jdbc/pom.xml -Plong-tests -pl imperative/mysql,declarative/mysql -am verify
mvn -f tests/integration/data-jdbc/pom.xml -Plong-tests -pl imperative/pgsql,declarative/pgsql -am verify
mvn -f tests/integration/data-jdbc/pom.xml -Plong-tests -pl imperative/oracle,declarative/oracle -am verify
```

### Run one style across all configured databases

```bash
mvn -f tests/integration/data-jdbc/pom.xml -Plong-tests -pl imperative/h2,imperative/mysql,imperative/pgsql,imperative/oracle -am verify
mvn -f tests/integration/data-jdbc/pom.xml -Plong-tests -pl declarative/h2,declarative/mysql,declarative/pgsql,declarative/oracle -am verify
```

### Run only the provider-level H2 tests

```bash
mvn -f tests/integration/data-jdbc/pom.xml -pl h2 -am verify
```

## Test design notes

- Prefer portable contracts in `common/src/main/java` and small database leaves
  that only provide configuration and fixture differences.
- Keep database-specific SQL and generated-key behavior explicit. Some drivers
  legitimately differ; for example, MySQL Connector/J exposes generated keys
  differently from H2, PostgreSQL, and Oracle.
- Use HikariCP one-connection pools when a test must prove that a failed
  operation returned its connection lease.
- Do not let application-visible failures expose configured SQL, secret bind
  values, driver diagnostic canaries, or raw next-exception details.
