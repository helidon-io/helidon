# Mixed-provider runtime integration test

This module is a self-contained Helidon application and test suite proving that
Jakarta Persistence and JDBC repositories can coexist when each repository
selects its provider with `@Data.Provider`.

The application is under `src/main`, including its Hikari-backed in-memory H2
configuration. The Jakarta Persistence unit loads the H2 schema and sample data
from `src/main/resources/init.sql`; the test does not create or seed the database.
The HTTP-level validation and generated-source assertions are under `src/test`.

The application exposes three read paths:

- `GET /mixed/jpa/{id}` uses the generated Jakarta Persistence repository.
- `GET /mixed/jdbc/declarative/{id}` uses the generated JDBC repository.
- `GET /mixed/jdbc/imperative/{id}` uses the public `JdbcClient` API directly.

The test also verifies that only `JpaBookRepository__Jpa` and
`JdbcInventoryRepository__Jdbc` are generated and that their generated
constructor dependencies contain the expected persistence-unit and JDBC-client
qualifiers.

Run the module directly:

```bash
mvn clean package
```

Or select it from the Data JDBC integration reactor:

```bash
mvn -f tests/integration/data-jdbc/pom.xml -pl mixed-provider -am clean package
```
