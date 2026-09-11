<!--@frontmatter
description: "Execute JDBC statements with the Helidon JDBC client"
-->
# JDBC Client

## Overview

> [!NOTE]
> Helidon Data JDBC is an incubating feature. It is not production ready. Its
> APIs and behavior may change incompatibly or be removed in any release.

`JdbcClient` provides the imperative API for Helidon Data JDBC. It executes SQL
supplied by the application and reads the complete result before returning.
Use an imperative client when database operations fit naturally into
application control flow or when the application chooses SQL at runtime.

Generated repositories use the same public `JdbcClient` API. An application
can combine both styles, using a
[declarative JDBC repository](declarative.md) for a fixed set of operations
that Helidon can validate during compilation.

Add the runtime dependency and JDBC driver described in
[Helidon Data JDBC Provider](README.md#maven-coordinates). Imperative clients do
not need the JDBC code generator.

## JDBC Client Configuration

Inject a configured client when the application uses the Service Registry.
Create a standalone client when each operation should acquire and close its own
connection.

### Configure Clients in an Imperative Application

An imperative application can supply client configurations. Install the
complete list before the first lookup of `JdbcClientConfig` or `JdbcClient`:

```java
JdbcClientConfig contacts = JdbcClient.builder()
        .name("@default")
        .dataSourceName("contacts-datasource")
        .buildPrototype();

JdbcClientConfig audit = JdbcClient.builder()
        .name("audit")
        .connection(connection -> connection
                .url("jdbc:postgresql://database.example/audit")
                .jdbcDriverClassName("org.postgresql.Driver"))
        .buildPrototype();

Services.set(JdbcClientConfig.class, contacts, audit);
```

One `Services.set` call supplies the complete list. It replaces configurations
from `application.yaml` rather than merging with them.

### Injected Clients

Configure clients under `data.clients.jdbc`, then inject the required client by
its provider type and configured name:

```java
import io.helidon.data.jdbc.JdbcClient;

@Service.Singleton
final class ContactStore {
    private final JdbcClient jdbcClient;

    @Service.Inject
    ContactStore(@Data.ProviderType("jdbc")
                 @Service.Named("@default")
                 JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }
}
```

The `@default` qualifier matches the default client name. Use another
configured name when the service needs a different database. See
[JDBC Client Configuration](README.md#jdbc-client-configuration) for
configuration through a file or directly from an imperative application.

Use injection when client operations must run within a Helidon local JDBC
transaction. Only clients managed by the Service Registry can use the
connection associated with the current transaction.

### Standalone Clients

A standalone client must have exactly one connection source. If the
application already owns a `DataSource`, pass the instance to the builder:

```java
JdbcClient jdbcClient = JdbcClient.builder()
        .dataSource(contactsDataSource)
        .build();
```

The application continues to manage the lifecycle of the `DataSource`. Helidon
closes every connection that it obtains from the data source.

To use an SQL data source that is already available from the Service Registry,
provide its configured name:

```java
JdbcClient jdbcClient = JdbcClient.builder()
        .dataSourceName("contacts-datasource")
        .build();
```

For a client that does not share a data source, supply direct JDBC connection
settings:

```java
JdbcClient jdbcClient = JdbcClient.builder()
        .connection(connection -> connection
                .url("jdbc:postgresql://database.example/contacts")
                .jdbcDriverClassName("org.postgresql.Driver"))
        .build();
```

A directly created client acquires and closes a connection each time it executes
an operation. Resolving a named data source through the builder does not make
the client a Service Registry service. Therefore, none of these standalone
clients can join a transaction created by an annotation.

## Statement Execution

To execute an operation, create a statement, bind each positional parameter,
select a mapping for the returned rows, and call a method that runs the
statement. Creating and configuring the operation does not access the database.

The following query returns zero or one name:

```java
Optional<String> name = jdbcClient.create(
        "SELECT NAME FROM CONTACT WHERE ID = ?")
        .bind(1, id)
        .map(String.class)
        .optional();
```

The following update returns the number of affected rows as a `long`:

```java
long updated = jdbcClient.create(
        "UPDATE CONTACT SET STATUS = ? WHERE ID = ?")
        .bind(1, status)
        .bind(2, id)
        .execute();
```

Pass one SQL statement to `JdbcClient.create`. For safe parameter handling,
portable marker syntax, and DDL guidance, see
[SQL Guidelines](README.md#sql-guidelines).

## Parameter Binding

Imperative statements use positional `?` markers. Positions start at one and
follow the order of markers in the SQL. Bind every position exactly once
before executing the statement:

```java
List<String> names = jdbcClient.create("""
        SELECT NAME
        FROM CONTACT
        WHERE OWNER_ID = ? OR DELEGATE_ID = ?
        ORDER BY NAME
        """)
        .bind(1, ownerId)
        .bind(2, ownerId)
        .map(String.class)
        .list();
```

Each value must not be null and must use a
[supported scalar type](declarative.md#supported-scalar-types). The imperative
API does not provide a method for binding a typed null value. To write SQL
`NULL`, include `NULL` in the statement. A separate operation for the null case
can keep the intended SQL behavior explicit.

Markers represent values, not SQL structure. Never concatenate untrusted input
into the statement. Select identifiers, operators, and sort directions from
values controlled by the application.

## Result Mapping

Map a single supported scalar from the first selected column by passing its
class to `map`:

```java
long count = jdbcClient.create("SELECT COUNT(*) FROM CONTACT")
        .map(Long.class)
        .one();
```

For scalar mapping, `one()` and `list()` require column values that are not null.
`optional()` returns `Optional.empty()` when the query returns no row or when
its single row contains SQL `NULL` in the first column. When the application
must distinguish those cases, use a custom mapper that returns `Optional<T>`.
The `Optional` returned by the execution method then represents whether a row
exists.

### Custom Row Mapping

Pass a `JdbcClient.RowMapper<T>` when a row contains several columns or needs
conversion specific to the application:

```java
List<ContactSummary> contacts = jdbcClient.create("""
        SELECT ID AS id, NAME AS name, EMAIL AS email
        FROM CONTACT
        ORDER BY ID
        """)
        .map(row -> new ContactSummary(row.get("id", Long.class),
                                       row.get("name", String.class),
                                       row.optional("email", String.class)))
        .list();
```

Use `Row.get` for a required value and `Row.optional` for a nullable value. Both
methods accept a column label or a column index starting at 1. Read all values
on the thread that calls the mapper before returning from it. The `Row` is valid
only while the mapper runs and must not be retained or passed to another thread.

The mapper must return a value that is not null and independent of the JDBC row
and its resources. The value can contain a collection or map managed by the
application. The choice of `one()`, `optional()`, or `list()` applies to the
number of physical rows returned by the database:
`one()` maps exactly one row, `optional()` maps zero or one, and `list()` maps
every row.

Row mappers operate independently on physical result-set rows; they do not
reduce multiple rows into an object graph.

If a mapper provided by the application throws an exception, Helidon returns
that exception to the caller without changing it.

## Return Generated Keys

After an insert or update, call `generatedKeys()` before selecting a mapper and
calling `one()`, `optional()`, or `list()`. If you do not call `addColumn`,
Helidon requests the driver's default generated keys. Add each required column
when the driver supports selecting keys by column name:

```java
long id = jdbcClient.create("INSERT INTO CONTACT (NAME) VALUES (?)")
        .bind(1, name)
        .generatedKeys()
        .addColumn("ID")
        .map(row -> row.get(1, Long.class))
        .one();
```

This operation requests the `ID` column and requires exactly one row containing
generated keys. Support for generated keys and the way column names are
interpreted depend on the JDBC driver.

When a client acquires its own connection, the JDBC driver can commit the update
automatically before Helidon finishes reading, mapping, and validating generated
keys. When all these steps must succeed or fail together, use an injected client
inside a local transaction. Let any failure propagate beyond the transaction
boundary so Helidon can roll back the transaction. Do not automatically retry
an update after generated key processing or cleanup fails, because the driver
might already have committed the update.

## Result Cardinality

Complete each mapped query or generated key operation with the method that
matches the expected number of rows:

- `one()` requires exactly one row. It throws `NoResultException` for no rows
  and `NonUniqueResultException` for more than one row.
- `optional()` accepts zero or one row. It throws `NonUniqueResultException`
  for more than one row.
- `list()` returns all rows as an immutable list in JDBC encounter order.

Data JDBC does not impose a row limit on `list()`. When a query can return many
rows, limit the result in SQL. Use deterministic ordering when paging through
results.

## Resource Management

Helidon reads the complete result before returning it to the application. It
always closes the result set and prepared statement. It also closes any
connection that it acquired for the operation. The API does not return a
stream, cursor, or iterator that remains connected to JDBC resources.

`JdbcClient` is safe to share. The statement and mapping objects created for an
operation accept one execution and are not safe for concurrent use. Start each
operation with a new call to `JdbcClient.create`.

A JDBC driver failure becomes a `DataException`. Helidon also releases the
resources it owns if database access, mapping, or result count validation fails.
See [Errors and JDBC Warnings](README.md#errors-and-jdbc-warnings) for the JDBC
diagnostic policy.

## Transaction Participation

An injected client managed by the Service Registry can run within a local
JDBC transaction when the service method is invoked through the registry. Place
the transaction annotation on a method of a managed service. Calling a service
that was constructed directly, or calling the annotated method from another
method on the same instance, does not start the transaction.

For example, when the following method belongs to a managed service, both
statements use the connection associated with the transaction. An exception
that propagates from the method causes Helidon to roll back the transaction:

```java
@Tx.Required
public ContactStatus changeStatus(long id, String status) {
    jdbcClient.create("UPDATE CONTACT SET STATUS = ? WHERE ID = ?")
            .bind(1, status)
            .bind(2, id)
            .execute();

    return jdbcClient.create("""
            SELECT ID AS id, NAME AS name, STATUS AS status
            FROM CONTACT
            WHERE ID = ?
            """)
            .bind(1, id)
            .map(row -> new ContactStatus(row.get("id", Long.class),
                                          row.get("name", String.class),
                                          row.get("status", String.class)))
            .one();
}
```

You can also start the transaction by calling `Tx.transaction`. This approach
does not require the Service Registry to intercept a method call:

```java
ContactStatus contact = Tx.transaction(() -> changeStatus(id, status));
```

The transaction runs synchronously and remains associated with the thread that
started it. All operations in the transaction must use the same data source. A
`JdbcClient` constructed directly with its builder remains outside the local
transaction, even when called from an annotated method.

See [Local Transactions](README.md#local-transactions) for propagation,
restrictions involving other providers, and failure handling. Keep DDL outside
local transactions as described in [Schema Management](README.md#schema-management).
