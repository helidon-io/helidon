<!--@frontmatter
description: "Use Helidon Data JDBC with the imperative programming model"
-->
# Imperative Programming Model

## Overview

With the Data JDBC imperative programming model, you create each database
operation through `JdbcClient`. You supply the SQL, bind parameters, select a
result mapping, and invoke a terminal method. Helidon reads the complete result
before that method returns.

Generated repositories use the same public `JdbcClient` API, so you can combine
both programming models. Use the
[declarative programming model](declarative.md) for a fixed set of operations
that Helidon can validate at compile time.

Add the runtime dependency and JDBC driver described in
[Maven Coordinates](data-jdbc.md#maven-coordinates). The imperative programming
model does not require the Data JDBC code generator.

> [!NOTE]
> Helidon Data JDBC is an incubating feature. It is not production ready. Its
> APIs and behavior may change incompatibly or be removed in any release.

## JDBC Client Configuration

If your application uses the Service Registry, inject a configured client. A
registry-managed client is also required to participate in a local JDBC
transaction. A standalone client acquires and closes its own connection for
each operation.

### Injected Clients

Configure clients under `data.clients.jdbc`, then inject the required client by
provider type and configured name:

```java
import io.helidon.data.jdbc.JdbcClient;

@Service.Singleton
final class ContactStore {
    private final JdbcClient jdbcClient;

    @Service.Inject
    ContactStore(@Data.ProviderType("jdbc")
                 @Service.Named("contacts")
                 JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }
}
```

The `contacts` qualifier matches the configured client name. To inject a
different client, use its configured name. See
[Configuration](data-jdbc.md#configuration) for file-based configuration.

Only a client managed by the Service Registry can use the connection associated
with a local JDBC transaction.

### Standalone Clients

A standalone client requires exactly one connection source. If your application
owns a `DataSource`, pass it to the builder:

```java
JdbcClient jdbcClient = JdbcClient.builder()
        .dataSource(contactsDataSource)
        .build();
```

Your application continues to own the `DataSource`. Helidon closes each
connection it obtains from the data source.

You can instead reference an SQL data source available from the Service
Registry:

```java
JdbcClient jdbcClient = JdbcClient.builder()
        .dataSourceName("contacts-datasource")
        .build();
```

For a direct connection, provide the JDBC settings:

```java
JdbcClient jdbcClient = JdbcClient.builder()
        .connection(connection -> connection
                .url("jdbc:postgresql://database.example/contacts")
                .jdbcDriverClassName("org.postgresql.Driver"))
        .build();
```

To retain the immutable configuration, build a prototype and pass it to
`JdbcClient.create`:

```java
JdbcClientConfig config = JdbcClient.builder()
        .dataSource(contactsDataSource)
        .buildPrototype();

JdbcClient jdbcClient = JdbcClient.create(config);
```

A standalone client acquires and closes a connection for every operation.
Resolving a named data source does not make the client registry-managed. The
standalone client therefore cannot join an annotated transaction.

### Programmatic Configuration

Install the client configurations before the first lookup of `JdbcClientConfig`
or `JdbcClient`:

```java
JdbcClientConfig contacts = JdbcClient.builder()
        .name("contacts")
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

Pass the complete list of configurations in one `Services.set` call. The call
replaces the configurations from `application.yaml` rather than merging with
them.

## Statement Execution

`JdbcClient.create` starts an operation without accessing the database. Add
positional bindings and select a row mapping for a query. A terminal method
executes the statement.

The following query returns zero or one name:

```java
Optional<String> name = jdbcClient.create(
        "SELECT NAME FROM CONTACT WHERE ID = ?")
        .bind(1, id)
        .map(String.class)
        .optional();
```

The following update returns its affected-row count as a `long`:

```java
long updated = jdbcClient.create(
        "UPDATE CONTACT SET STATUS = ? WHERE ID = ?")
        .bind(1, status)
        .bind(2, id)
        .execute();
```

`execute()` uses the driver's large update count when the driver supports it.
Otherwise, Data JDBC returns the legacy integer count as a `long`. The fallback
cannot represent a count outside the integer range.

Pass one SQL statement to `JdbcClient.create`. See
[SQL Guidelines](data-jdbc.md#sql-guidelines) for
safe parameter handling and portable marker syntax.

## Parameter Binding

Imperative statements use positional `?` markers. Positions start at one and
follow marker order. Bind every position exactly once before executing the
statement:

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

Each bind value must be non-null and use a
[supported scalar type](declarative.md#supported-scalar-types). The imperative
API does not support typed null bindings. When an operation needs SQL `NULL`,
include `NULL` in the SQL and use a separate statement for that case.

Bind markers represent values, not SQL structure. Select identifiers,
operators, and sort directions from values controlled by the application.

## Result Mapping

To map a supported scalar from the first selected column, pass its class to
`map`:

```java
long count = jdbcClient.create("SELECT COUNT(*) FROM CONTACT")
        .map(Long.class)
        .one();
```

For scalar mapping, `one()` and `list()` require non-null column values.
`optional()` returns `Optional.empty()` when no row exists or when the first
column of the single row is SQL `NULL`. To distinguish those cases, use a
custom mapper that returns `Optional<T>`. The outer optional then represents row
presence.

### Custom Row Mapping

Pass a `JdbcClient.RowMapper<T>` when a row contains several columns or needs
an application-specific conversion:

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
methods accept a column label or a one-based column index. Read all values on
the callback thread before the mapper returns. Do not retain the `Row` or pass
it to another thread.

The mapper must return a non-null value that does not depend on the JDBC row or
its resources. Helidon invokes the mapper once for each physical row and does
not combine rows into an object graph. Exceptions from application mappers are
propagated unchanged.

### Result Cardinality

Complete a query or generated-key operation with the terminal method that
matches the expected number of rows:

- `one()` requires exactly one row. It throws `NoResultException` for no rows
  and `NonUniqueResultException` for more than one row.
- `optional()` accepts zero or one row. It throws `NonUniqueResultException`
  for more than one row.
- `list()` returns all rows as an immutable list in JDBC encounter order.

Data JDBC does not impose a row limit on `list()`. Limit potentially large
results in SQL.

## Return Generated Keys

After an insert or update, call `generatedKeys()`, select a mapper, and invoke
the terminal method that matches the expected number of generated key rows. If
you do not call `addColumn`, Helidon requests the driver's default keys. Use
`addColumn` to request keys by column name when the driver supports it:

```java
long id = jdbcClient.create("INSERT INTO CONTACT (NAME) VALUES (?)")
        .bind(1, name)
        .generatedKeys()
        .addColumn("ID")
        .map(row -> row.get(1, Long.class))
        .one();
```

Driver support and column-name handling vary, so test generated-key behavior
with your database and driver.

When a client owns its connection, the driver can commit the update before
Helidon finishes reading and validating generated keys. Use an injected client
inside a local transaction when the update and key processing must succeed or
fail together. Let failures cross the transaction boundary, and do not
automatically retry after key processing or cleanup fails.

## Resource Management

Helidon materializes each result before returning it and closes the result set
and prepared statement. Outside a local transaction, Helidon also closes the
operation's connection. A local transaction retains its connection until the
transaction completes. The API does not return JDBC-backed streams, cursors,
or iterators.

`JdbcClient` is safe to share. The statement and result stages created for an
operation are single-use and are not safe for concurrent use. Start each
operation with a new call to `JdbcClient.create`.

Helidon releases its resources when database access, mapping, or result
validation fails. See
[Errors and JDBC Warnings](data-jdbc.md#errors-and-jdbc-warnings) for diagnostic
behavior.

## Transaction Participation

A registry-managed client can participate in a local JDBC transaction when the
Service Registry invokes the managed service method. Place the transaction
annotation on a managed service method. Direct construction and self-invocation
do not trigger interception.

In the following method, both statements use the connection associated with
the transaction. If an exception crosses the method boundary, Helidon rolls
back the transaction:

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

You can also start a transaction programmatically:

```java
ContactStatus contact = Tx.transaction(() -> changeStatus(id, status));
```

The transaction is synchronous and remains associated with the thread that
started it. All operations in the transaction must use the same data source.
A standalone client remains outside the transaction.

See [Local Transactions](data-jdbc.md#local-transactions) for propagation and
provider restrictions. Keep DDL outside local transactions as described in
[Schema Management](data-jdbc.md#schema-management).
