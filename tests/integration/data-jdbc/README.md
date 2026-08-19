# Helidon Data JDBC integration tests

This module verifies the Helidon Data JDBC provider against the supported JDBC
databases and through both supported application styles:

- imperative use of the public `JdbcClient` API; and
- generated declarative repositories using `@Jdbc.Statement` and related JDBC
  annotations.

The tests are intentionally integration-level. They validate provider behavior
against real JDBC drivers, database dialect differences, transaction behavior,
generated keys, bootstrap diagnostics, resource ownership, SQL/bind handling,
and chaos/fault-injection scenarios.

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

## Directory layout

The main reactor is `tests/integration/data-jdbc/pom.xml`.

```text
tests/integration/data-jdbc/
  common/
    src/main/java/        Shared contracts, application adapters, repositories, and support code.
    src/<db>/java/        Database-specific fixtures and configuration helpers.
    src/<db>/resources/   Database-specific schema/init scripts.
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
  chaos/                  Explicitly activated chaos and fault-injection tests.
```

The imperative and declarative database leaves add the appropriate
`common/src/<db>` source and resource directories as test roots. Keep
database-specific setup in the database leaf or `common/src/<db>`; keep portable
test contracts in `common/src/main/java`.

## Regular integration tests

Run the normal JDBC integration suite:

```bash
mvn -f tests/integration/data-jdbc/pom.xml verify
```

Run only H2 style tests:

```bash
mvn -f tests/integration/data-jdbc/pom.xml -pl imperative/h2,declarative/h2 -am verify
```

Run one external database across both styles:

```bash
mvn -f tests/integration/data-jdbc/pom.xml -pl imperative/mysql,declarative/mysql -am verify
mvn -f tests/integration/data-jdbc/pom.xml -pl imperative/pgsql,declarative/pgsql -am verify
mvn -f tests/integration/data-jdbc/pom.xml -pl imperative/oracle,declarative/oracle -am verify
```

Run one style across all configured databases:

```bash
mvn -f tests/integration/data-jdbc/pom.xml -pl imperative -am verify
mvn -f tests/integration/data-jdbc/pom.xml -pl declarative -am verify
```

Run provider-level H2 tests:

```bash
mvn -f tests/integration/data-jdbc/pom.xml -pl h2 -am verify
```

## Chaos and fault-injection tests

Chaos tests are under `tests/integration/data-jdbc/chaos` and are not part of
the normal integration run. They are enabled only when the parent reactor is run
with:

```bash
-Ddata.jdbc.chaos=true
```

Chaos test goals:

- catch resource leaks after failures;
- verify failed operations do not poison the persistence unit;
- verify transaction rollback behavior after JDBC failures;
- verify generated-key and conversion failure recovery;
- verify diagnostics are sanitized and do not expose SQL or bind-value canaries;
- provide deterministic H2 fault injection for provider lifecycle boundaries
  that are hard to reproduce reliably with real database outages.

Chaos test categories are selected with `data.jdbc.chaos.mode`:

| Mode | Meaning |
| --- | --- |
| `smoke` | Supported-database representative smoke tests. This is the default. |
| `full` | Smoke tests plus the H2 deterministic fault-injection matrix. |
| `disruption` | Vendor-specific disruption tests only. Reserved for deferred tests; not PR-default. |
| `all` | All chaos categories. Currently smoke plus H2 deterministic tests until vendor disruption tests are added. |

Invalid `data.jdbc.chaos.mode` values fail fast during Maven validation.

Run the default chaos smoke suite:

```bash
mvn -f tests/integration/data-jdbc/pom.xml -Ddata.jdbc.chaos=true verify
```

Run only H2 chaos smoke tests:

```bash
mvn -f tests/integration/data-jdbc/pom.xml -Ddata.jdbc.chaos=true -pl chaos/imperative/h2,chaos/declarative/h2 -am verify
```

Run H2 deterministic fault injection plus H2 smoke tests:

```bash
mvn -f tests/integration/data-jdbc/pom.xml -Ddata.jdbc.chaos=true -Ddata.jdbc.chaos.mode=full -pl chaos/h2,chaos/imperative/h2,chaos/declarative/h2 -am verify
```

Run the full chaos mode from the JDBC integration parent:

```bash
mvn -f tests/integration/data-jdbc/pom.xml -Ddata.jdbc.chaos=true -Ddata.jdbc.chaos.mode=full verify
```

Run all chaos categories from the chaos project directly:

```bash
mvn -f tests/integration/data-jdbc/chaos/pom.xml -Ddata.jdbc.chaos.mode=all verify
```

Run one chaos database leaf:

```bash
mvn -f tests/integration/data-jdbc/pom.xml -Ddata.jdbc.chaos=true -pl chaos/imperative/mysql -am verify
mvn -f tests/integration/data-jdbc/pom.xml -Ddata.jdbc.chaos=true -pl chaos/declarative/pgsql -am verify
mvn -f tests/integration/data-jdbc/pom.xml -Ddata.jdbc.chaos=true -pl chaos/imperative/oracle -am verify
```

Vendor disruption tests are documented as a later phase in
`docs/codex/chaos-fault-injection-test-plan.md`. They should remain isolated
from normal PR CI unless they become deterministic and bounded in the target CI
environment.

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
- Keep chaos tests deterministic. Prefer H2 fault-injection wrappers for
  provider lifecycle edges and reserve real network/session/container
  disruptions for explicitly selected vendor disruption tests.
- Avoid committing failing chaos tests to the main branch. If a chaos test
  exposes a product issue, keep it enabled on the feature branch and fix the
  product behavior or update the documented contract before merge.
