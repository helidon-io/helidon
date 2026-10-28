# Mixed-provider runtime integration test

This module is a self-contained Helidon application and test suite proving that
Jakarta Persistence and JDBC repositories can coexist when each repository
selects its provider with `@Data.Provider`.

The application is under `src/main`, including its Hikari-backed MySQL
configuration. The Jakarta Persistence unit loads the schema and sample data
from `src/main/resources/init.sql`. The test starts a MySQL container and supplies
its connection settings before resolving the application services. It closes the
HTTP client, server, and service registry before Testcontainers stops the database.
The HTTP-level validation and generated-source assertions are under `src/test`.

The application exposes three read paths:

- `GET /mixed/jpa/{id}` uses the generated Jakarta Persistence repository.
- `GET /mixed/jdbc/declarative/{id}` uses the generated JDBC repository.
- `GET /mixed/jdbc/imperative/{id}` uses the public `JdbcClient` API directly.

The test also verifies that only `JpaBookRepository__Jpa` and
`JdbcInventoryRepository__Jdbc` are generated and that their generated
constructor dependencies contain the expected persistence-unit and JDBC-client
qualifiers.

Select the module from the Data JDBC integration reactor, building its MySQL
fixture dependency from `common/fixtures/mysql` with `-am`:

```bash
mvn -f tests/integration/data-jdbc/pom.xml -Plong-tests -pl mixed-provider -am clean package
```

To run directly from this module directory, first install the test parent POMs
and support modules (the parent POMs may already be installed by a repository
test build):

```bash
mvn -N -f ../../../pom.xml install
mvn -N -f ../../pom.xml install
mvn -f ../pom.xml -P-long-tests install
mvn clean verify
```

The `long-tests` profile controls inclusion from the Data JDBC reactor; directly
targeting this module runs its tests regardless of that gate.

These tests require Docker and use the same approved MySQL image as the JDBC
matrix. They are skipped when Docker is unavailable.
