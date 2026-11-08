<!--@frontmatter
description: "Use Helidon Data JDBC with the imperative programming model"
-->
# Imperative Programming Model

## Overview

The Data JDBC imperative programming model builds each database operation
through `JdbcClient`. An operation combines SQL, positional parameters, and a
result mapping, then executes when the application invokes a terminal method.
Helidon materializes the complete result before that method returns.

The imperative and [declarative programming models](declarative.md) can coexist
in the same application. The declarative model is useful when Helidon can
validate a predefined set of operations during compilation, while `JdbcClient`
suits operations whose SQL is selected at runtime.

The imperative model needs the runtime dependency and JDBC driver described in
[Maven Coordinates](data-jdbc.md#maven-coordinates), but it does not need the
Data JDBC code generator.

> [!NOTE]
> Helidon Data JDBC is an incubating feature. It is not production ready. Its
> APIs and behavior may change incompatibly or be removed in any release.

## JDBC Client Configuration

Applications that use the Service Registry can inject a configured client. A
managed client can participate in a local JDBC transaction.

A client created with `JdbcClient.builder()` is not managed by the Service
Registry. It obtains and closes a connection for every operation. The client
can still ask the Service Registry to resolve a named data source, but the
client itself remains outside transactions applied to managed services.

### Injected Clients

Clients configured under `data.clients.jdbc` can be injected by provider type
and configured name. The service acknowledges the incubating status of
`JdbcClient` with the focused suppression described in
[Annotation Processing](declarative.md#annotation-processing):

```java
import io.helidon.common.Api;
import io.helidon.data.jdbc.JdbcClient;

@SuppressWarnings(Api.SUPPRESS_INCUBATING)
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

The `contacts` qualifier selects the client with the matching configured name.
[Configuration](data-jdbc.md#configuration) shows how those names and
connection sources are declared. Because the client is managed by the Service
Registry, it can use the connection associated with a local JDBC transaction.

### Standalone Clients

A standalone client has exactly one connection source. When the application
already owns a `DataSource`, the builder can use it directly:

```java
JdbcClient jdbcClient = JdbcClient.builder()
        .dataSource(contactsDataSource)
        .build();
```

The application continues to own the `DataSource`, while Helidon closes every
connection that it obtains from that source.

The builder can instead refer to an SQL data source registered with the Service
Registry:

```java
JdbcClient jdbcClient = JdbcClient.builder()
        .dataSourceName("contacts-datasource")
        .build();
```

Direct JDBC settings provide the third option:

```java
JdbcClient jdbcClient = JdbcClient.builder()
        .connection(connection -> connection
                .url("jdbc:postgresql://database.example/contacts")
                .jdbcDriverClassName("org.postgresql.Driver"))
        .build();
```

When an application needs to retain or share an immutable client configuration,
`buildPrototype()` produces a `JdbcClientConfig` that can later be supplied to
`JdbcClient.create`:

```java
JdbcClientConfig config = JdbcClient.builder()
        .dataSource(contactsDataSource)
        .buildPrototype();

JdbcClient jdbcClient = JdbcClient.create(config);
```

A standalone client obtains and closes a connection for every operation.
Referring to a named data source does not turn it into a client managed by the
Service Registry, so it remains outside annotated transactions.

### Programmatic Configuration

Client configurations can also be supplied in Java. They need to be registered
before the first lookup of `JdbcClientConfig` or `JdbcClient`. The Service
Registry rejects a later replacement with `ServiceRegistryException`:

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

A single `Services.set` call receives the complete configuration set. This set
replaces any client configurations loaded from `application.yaml` rather than
merging with them.

## Statement Execution

`JdbcClient.create` begins an operation without accessing the database. The
application can then add positional bindings and, for a query, choose how rows
are mapped. Database access begins only when a terminal method executes the
statement.

This query maps zero or one row to a name:

```java
Optional<String> name = jdbcClient.create(
        "SELECT NAME FROM CONTACT WHERE ID = ?")
        .bind(1, id)
        .map(String.class)
        .optional();
```

This update returns the number of affected rows as a `long`:

```java
long updated = jdbcClient.create(
        "UPDATE CONTACT SET STATUS = ? WHERE ID = ?")
        .bind(1, status)
        .bind(2, id)
        .execute();
```

`execute()` returns the update count as a `long`. With drivers that expose only
an integer update count, the returned value cannot represent a count outside the
integer range.

Each operation receives one SQL statement.
[SQL Guidelines](data-jdbc.md#sql-guidelines) describes safe parameter handling
and portable marker syntax.

## Parameter Binding

Imperative statements use positional `?` markers. Positions begin at one and
follow the order of the markers in the SQL. Execution can begin after every
position has been bound exactly once:

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

Each bound value must be a nonnull
[supported scalar type](declarative.md#supported-scalar-types). Because the
imperative API does not support typed null bindings, an operation that needs SQL
`NULL` includes it in the SQL and uses a separate statement for that case.

Parameter markers represent values rather than SQL structure. Identifiers,
operators, and sort directions remain values selected and controlled by the
application.

## Result Mapping

The `map` method accepts the class of a supported scalar to read from the first
selected column:

```java
long count = jdbcClient.create("SELECT COUNT(*) FROM CONTACT")
        .map(Long.class)
        .one();
```

For scalar mapping, `one()` and `list()` fail when the selected column contains
SQL `NULL`.
`optional()` returns `Optional.empty()` when no row exists or when the first
column of the single row is SQL `NULL`. A custom mapper that returns
`Optional<T>` can distinguish those cases because the outer optional then
represents row presence.

### Custom Row Mapping

A `JdbcClient.RowMapper<T>` handles a row that contains several columns or
needs a conversion specific to the application:

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

Within the mapper callback, `Row.get` reads a required value and `Row.optional`
reads a nullable value. Both methods accept a column label or an index whose
numbering begins at one. The row remains valid only on the callback thread and
only until the mapper returns, so the returned value must contain everything
the application needs and must not retain the `Row`.

Helidon invokes the mapper once for each physical row and does not combine rows
into an object graph. The mapper returns a value that is independent of the
JDBC row and its resources.

Outside a local transaction, a runtime exception raised by the mapper passes
through the `JdbcClient` terminal method unchanged. Inside a managed local
transaction, the transaction provider can report a `TxException` with the
mapper exception as its cause.

### Result Cardinality

The terminal method expresses how many rows a query or operation that returns
generated keys expects:

- `one()` requires exactly one row. It throws `NoResultException` for no rows
  and `NonUniqueResultException` for more than one row.
- `optional()` accepts zero or one row. It throws `NonUniqueResultException`
  for more than one row.
- `list()` returns all rows as an immutable list in JDBC encounter order.

Data JDBC does not impose a row limit on `list()`, so SQL should constrain
results that could become large.

## Return Generated Keys

After an insert or update, `generatedKeys()` requests the generated keys, `map`
converts each key row, and `one`, `optional`, or `list` validates and returns the
expected number of rows. Without `addColumn`, Helidon asks the driver to return
its default generated keys. Where the driver supports named key columns,
`addColumn` identifies the requested columns:

```java
long id = jdbcClient.create("INSERT INTO CONTACT (NAME) VALUES (?)")
        .bind(1, name)
        .generatedKeys()
        .addColumn("ID")
        .map(row -> row.get(1, Long.class))
        .one();
```

Support for generated keys and handling of column names vary among drivers.
Verify that each supported database and driver returns the requested generated
key columns with the expected column names.

When a client owns its connection, the driver can commit the update before
Helidon finishes reading and validating the keys. An injected client running in
a local transaction keeps the update and key processing atomic. If key
processing or cleanup fails, allow the exception to leave the method that starts
the transaction so Helidon can roll back. Catching the exception still marks
the transaction for rollback, but continuing hides the original failure and
cannot result in a commit. Such a failure does not establish that a retry is
safe.

## Resource Management

Helidon materializes each result before returning it and closes the result set
and prepared statement. Outside a local transaction, Helidon also closes the
connection used by the operation. A local transaction retains its connection
until the transaction completes. The API does not return streams, cursors, or
iterators that expose JDBC resources.

`JdbcClient` is safe to share. Each operation begins with a new call to
`JdbcClient.create`. The statement and result objects belong to one operation
and cannot be reused or accessed concurrently.

Resources are also released when database access, mapping, or result validation
fails. [Errors and JDBC Warnings](data-jdbc.md#errors-and-jdbc-warnings)
describes the resulting diagnostic information.

## Transaction Participation

A client obtained from the Service Registry can participate in a local JDBC
transaction. Transaction annotations take effect on intercepted methods of
service instances obtained from the Service Registry. They do not take effect
on directly constructed instances.

Within a registry-managed service, a call to another intercepted method on the
same instance also applies the called method's transaction annotation. For
example, an internal call to an intercepted `@Tx.New` method starts a new
transaction, while an internal call to an intercepted `@Tx.Unsupported` method
suspends the current transaction.

Private methods are not intercepted. A call to a private method stays in the
caller's transaction context and does not apply the private method's transaction
annotations. Annotation processing rejects a transaction annotation declared on
a private method when no interception annotation applies to the service type.
When the service type carries an interception annotation, private methods are
excluded from interception and their transaction annotations are ignored.

In the following example, both statements share the connection associated with
the transaction. If an exception leaves the method, Helidon rolls back their
work:

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

A transaction can also begin programmatically:

```java
ContactStatus contact = Tx.transaction(() -> changeStatus(id, status));
```

Local JDBC transactions are synchronous and remain associated with the thread
that starts them. Every operation in a transaction uses the same data source,
while standalone clients remain outside the transaction.

[Local Transactions](data-jdbc.md#local-transactions) describes propagation
modes and provider restrictions. Because DDL can cause implicit commits, schema
changes run separately as explained in
[Schema Management](data-jdbc.md#schema-management).
