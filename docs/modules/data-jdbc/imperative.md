<!--@frontmatter
description: "Use Helidon Data JDBC with the imperative programming model"
-->
# Imperative Programming Model

## Overview

The Data JDBC imperative programming model builds each database operation through
`JdbcClient`. An operation combines SQL, bound parameters, and a result mapping,
then executes when the application invokes a terminal method. Helidon materializes the
complete result before that method returns.

The imperative and [declarative programming models](declarative.md) can coexist in the
same application. The declarative model is useful when Helidon can validate a predefined
set of operations during compilation, while `JdbcClient` suits operations whose SQL is
selected at runtime.

The imperative model needs the runtime dependency and JDBC driver described in [Maven
Coordinates](getting-started.md#dependencies), but it does not need the Data JDBC code
generator.

> [!NOTE]
> Helidon Data JDBC is an incubating feature. It is not production ready. Its
> APIs and behavior may change incompatibly or be removed in any release.

## JDBC Client Configuration

Managed clients can join local JDBC transactions. A standalone client owns each
operation connection. See [Clients and Data Sources](configuration.md).

### Injected Clients

Inject a client by provider type and configured name. See [Injected
Clients](configuration.md#injected-clients).

### Standalone Clients

The application owns its data source; Helidon closes acquired connections. Standalone
clients do not join local transactions. See [Standalone
Clients](configuration.md#standalone-clients).

### Programmatic Configuration

Register configurations before the first client lookup. See [Programmatic
Configuration](configuration.md#programmatic-configuration).

## Statement Execution

`JdbcClient.create` begins an operation without accessing the database. The application
can then add positional bindings and, for a query, choose how rows are mapped. Database
access begins only when a terminal method executes the statement.

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

`execute()` returns the update count as a `long`. With drivers that expose only an
integer update count, the returned value cannot represent a count outside the integer
range.

Each operation receives one SQL statement. [SQL
Guidelines](parameters.md#sql-safety-and-markers) describes safe parameter handling and
portable marker syntax.

## SQL from Configuration

Use ordinary `Config` lookup to obtain SQL, then retain a reusable statement supplier:

```java
String sql = config.get("application.contacts.find-by-id").asString().get();
Supplier<JdbcClient.Statement> findById = jdbcClient.prepare(sql);

Optional<String> name = findById.get()
        .bind("id", id)
        .map(row -> row.get("name", String.class))
        .optional();
```

The example uses `java.util.function.Supplier`. Each supplier invocation creates a
fresh stage. Preparation supports named or positional markers and does not acquire a
connection. JDBC does not resolve `${...}` expressions. See [SQL from
Configuration](configured-statements.md#imperative-applications) for defaults and the
ordered-input overload.

## Parameter Binding

`create(sql)` uses positional bindings. Reusable definitions from `prepare(...)` also
support named bindings. Bind every required position exactly once using nonnull scalar
values. See [Imperative Binding](parameters.md#imperative-binding).

## Result Mapping

Map scalars, records, or application values with [Result Mapping](mapping.md).

### Custom Row Mapping

A mapper must return values independent of the row and its resources. See [Imperative
Row Mapping](mapping.md#imperative-row-mapping).

### Result Cardinality

Choose `one()`, `optional()`, or `list()`. See [Imperative
Cardinality](queries.md#imperative-cardinality).

## Return Generated Keys

```java
long id = jdbcClient.create("INSERT INTO CONTACT (NAME) VALUES (?)")
        .bind(1, name)
        .generatedKeys()
        .addColumn("ID")
        .map(row -> row.get(1, Long.class))
        .one();
```

See [Generated Keys](queries.md#generated-keys) for complete examples and transaction
consequences.

## Resource Management

Helidon closes statements, result sets, and operation-owned connections, including on
failure. A local transaction retains its connection. See [Resource
Ownership](transactions.md#resource-ownership).

## Transaction Participation

Only managed clients join local transactions. See [Imperative
Transactions](transactions.md#imperative-transactions).
