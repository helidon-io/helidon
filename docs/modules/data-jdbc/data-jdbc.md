<!--@frontmatter
description: "Use the Helidon Data JDBC provider"
-->
# Helidon Data JDBC Provider

## Overview

Helidon Data JDBC executes SQL that your application defines through Java
Database Connectivity (JDBC) APIs. It binds parameters, maps results, and
manages the JDBC resources used by each operation.

This document uses the shorter name Data JDBC for the provider.

Data JDBC does not provide entity management or derive queries from repository
method names. It supports two programming models:

- The [declarative programming model](declarative.md) defines operations on an
  annotated repository interface. Helidon validates the interface and generates
  the repository implementation during compilation.
- The [imperative programming model](imperative.md) uses `JdbcClient` to build
  and execute operations directly in application code.

You can combine declarative repository methods with imperative `JdbcClient`
operations in the same application. Both models provide consistent result
mapping, transaction participation, and resource management.

Each Data JDBC operation executes one SQL statement. Database schema creation
remains separate and should be complete before data operations begin.
[Schema Management](#schema-management) explains how schema changes fit into an
application using Data JDBC.

> [!NOTE]
> Helidon Data JDBC is an incubating feature. It is not production ready. Its
> APIs and behavior may change incompatibly or be removed in any release.

## Maven Coordinates

The following dependencies provide Data JDBC, YAML configuration support, and
the MySQL JDBC driver. Projects that use the Helidon application parent or
import `helidon-dependencies` receive the corresponding dependency versions from
Helidon. For other dependency management options, see
[Managing Dependencies](../../guides/maven.md#dependency-management).

<!--@mdc ::code-callout -->
```xml [pom.xml]
<dependencies>
  <dependency>
    <groupId>io.helidon.data.jdbc</groupId>
    <artifactId>helidon-data-jdbc</artifactId> <!-- Data JDBC API and runtime -->
  </dependency>
  <dependency>
    <groupId>io.helidon.config</groupId>
    <artifactId>helidon-config-yaml</artifactId> <!-- support for application.yaml -->
  </dependency>
  <dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId> <!-- MySQL JDBC driver -->
    <scope>runtime</scope>
  </dependency>
</dependencies>
```
<!--@mdc :: -->

## Configuration

Helidon creates a `JdbcClient` service for each entry under
`data.clients.jdbc`. A client can use direct JDBC connection properties or
refer to an SQL data source registered with the Service Registry. An imperative
application can also create a standalone client from an existing `DataSource`,
in which case the application continues to own the data source.
[Standalone Clients](imperative.md#standalone-clients) describes that option.

Client names distinguish configurations when an application connects to more
than one data source. The direct connection example creates `contacts` and
`audit` clients. A configuration without a `name` becomes the `@default` client.
Repositories use this default unless `@Jdbc.Client` selects another client,
while injected clients use their configured names as Service Registry
qualifiers.

### Direct JDBC Connections

The following configuration creates clients named `contacts` and `audit`:

```yaml [application.yaml]
data:
  clients:
    jdbc:
      - name: "contacts"
        connection:
          username: "user"
          password: "password"
          url: "jdbc:mysql://localhost:3306/contacts"
          jdbc-driver-class-name: "com.mysql.cj.jdbc.Driver"
      - name: "audit"
        connection:
          username: "user"
          password: "password"
          url: "jdbc:mysql://localhost:3306/audit"
          jdbc-driver-class-name: "com.mysql.cj.jdbc.Driver"
```

Every direct connection requires a JDBC URL, while the driver class name can be
omitted when the driver registers itself and recognizes the configured URL.

Although `data.clients.jdbc` is a list, it can contain a single client. In a
properties file, the client indexes begin at zero:

```properties [application.properties]
data.clients.jdbc.0.name=contacts
data.clients.jdbc.0.connection.username=user
data.clients.jdbc.0.connection.password=password
data.clients.jdbc.0.connection.url=jdbc:mysql://localhost:3306/contacts
data.clients.jdbc.0.connection.jdbc-driver-class-name=com.mysql.cj.jdbc.Driver
```

### Registered Data Sources

When several components need to share a data source or connection pool, the
JDBC client can refer to an SQL data source registered with the Service
Registry. The following example uses the HikariCP provider. Its dependency
version is supplied by the Helidon application parent or
`helidon-dependencies`, just like the dependencies shown in
[Maven Coordinates](#maven-coordinates).

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.data.sql.datasource</groupId>
  <artifactId>helidon-data-sql-datasource-hikari</artifactId>
</dependency>
```

Once the data source is registered, its name connects the JDBC client to it:

```yaml [application.yaml]
data:
  url: "jdbc:mysql://localhost:3306/pokemons"
  sources:
    sql:
      - name: "pokemon-datasource"
        provider.hikari:
          username: "user"
          password: "changeit"
          url: "${data.url}"
  clients:
    jdbc:
      - data-source: "pokemon-datasource"
```

The `data-source` value identifies an SQL data source in the Service Registry
and must match its registered name. This example omits the client `name`, so
Helidon makes the client available as `@default`.

Imperative applications can also configure clients managed by the Service
Registry in code or create standalone clients. These options are described in
[JDBC Client Configuration](imperative.md#jdbc-client-configuration).

## SQL Guidelines

Each repository method or `JdbcClient` terminal method executes one prepared
SQL statement. A query must produce a result set, even when that result set is
empty. An ordinary update must produce an update count, and an update configured
for generated keys must make those keys available through the JDBC driver.

A workflow that spans several statements is represented by several Data JDBC
operations. When those statements must succeed or fail together, the operations
can run within a [local transaction](#local-transactions).

Values supplied by the application belong in bound parameters, while the
application controls the SQL structure. Parameter markers can represent values,
but they cannot represent table names, column names, operators, or sort
directions. When these structural elements vary, select them from a closed set
of values defined by the application. Do not construct them directly from
application input.

Declarative statements support either named markers or positional `?` markers
in a given statement. Imperative statements use positional markers.
[Statement Parameters](declarative.md#statement-parameters) and
[Parameter Binding](imperative.md#parameter-binding) explain the two forms.

Data JDBC does not treat text inside standard string literals, quoted
identifiers, comments, PostgreSQL escape strings, PostgreSQL strings that use
dollar quoting, or valid Oracle strings that use alternative quoting as
parameters. It also preserves doubled `??` characters for drivers that use
them as escape syntax.

Portable parsing relies on a few SQL conventions. An ordinary string literal
represents an apostrophe with two apostrophes instead of a MySQL backslash
escape. A line comment begins when `--` is followed by whitespace, an ISO
control character, or the end of the SQL text. Block comments are not nested.

Data JDBC recognizes an Oracle `q` or `nq` literal only when its prefix does not
immediately follow a letter, digit, underscore, or dollar sign. SQL Server
bracketed identifiers require particular care because Data JDBC treats square
brackets as ordinary punctuation. A colon or question mark inside a bracketed
identifier can therefore be interpreted as a parameter marker and cause
parameter validation or binding to fail.

## Local Transactions

Generated repositories and `JdbcClient` services managed by the Service
Registry can participate in Helidon local JDBC transactions. A transaction can
begin when a managed service method carries an annotation such as
`@Tx.Required`, or when the application calls `Tx.transaction`. A standalone
client created with `JdbcClient.builder()` always uses its own connection and
does not join the local transaction.

Local JDBC transactions are synchronous and remain associated with the thread
that starts them. All Data JDBC operations in a local transaction must use the
same data source. An operation that selects another data source fails before
accessing it.

When a local transaction is active, `REQUIRED`, `MANDATORY`, and `SUPPORTED`
operations join it. A failure in any joined operation marks the transaction for
rollback, even when the caller catches the exception.

A `NEW` operation temporarily suspends the current transaction and starts an
independent transaction with another connection. It does not create a
savepoint. An `UNSUPPORTED` operation also suspends the transaction, but
executes with a connection owned by that operation and with automatic commits
enabled. Helidon resumes the suspended transaction after either operation
completes.

[Transaction Types and Annotations](../data.md#transaction-types-and-annotations)
describes the available propagation modes.

Data JDBC participates only in transactions owned by the local JDBC transaction
provider. It does not enlist a connection in JTA, XA, or Jakarta Persistence
transactions that manage their own local resources. If another transaction
provider is active, the JDBC operation fails before acquiring a connection.

If commit, rollback, or connection cleanup fails and Helidon cannot determine
the transaction outcome, verify the resulting database state before retrying
the operation. Guidance for each programming model is available in
[Transactions](declarative.md#transactions) and
[Transaction Participation](imperative.md#transaction-participation).

## Schema Management

Database migration or deployment tools are the preferred way to create and
update the schema. Keeping schema changes separate from transactions that
modify application data also avoids differences in how databases commit DDL.

Data JDBC can execute one DDL statement for isolated setup work, such as
creating a test fixture. It is not a replacement for a database migration or
deployment tool. Such a statement should run outside a local JDBC transaction.
`@Tx.Never` rejects the operation when a transaction is active, while
`@Tx.Unsupported` suspends the active transaction until the statement finishes.

A database can commit DDL or another command implicitly, so a later rollback
might not reverse the resulting changes. The statement API is not suitable for
SQL that controls transactions or changes connection or session state.

## Database Portability

Data JDBC uses standard JDBC APIs, but SQL syntax, type conversions, generated
keys, and transaction behavior can vary by database and driver. Applications
should verify each database and driver combination that they support.

Connection pooling, timeouts, and properties defined by a database vendor are
configured through the data source, JDBC driver, connection URL, or database.
Applications can limit large results in SQL and provide deterministic ordering
for paging.

## Errors and JDBC Warnings

Data JDBC converts an `SQLException` to a Helidon Data `DataException`. The
exception can retain the SQLSTATE and vendor error code reported by the driver.
It excludes SQL text, bound values, credentials, and driver messages that could
reveal application data.

Outside a local transaction, a runtime exception raised by an application
`JdbcClient.RowMapper` passes through unchanged. Inside a managed local
transaction, the transaction provider can report a `TxException` and retain the
mapper exception as its cause.

Data JDBC does not promote JDBC warnings to exceptions. Applications that need
to monitor warnings can use facilities provided by the database or driver.
These facilities can also report data truncation that occurs while results are
read.

## Parameter Count Cache

`JdbcClient` keeps a bounded cache of parameter counts for SQL that it executes
repeatedly. The default cache holds entries for 256 statements and can be
configured under `properties.jdbc.parameter-count-cache`:

```yaml [application.yaml]
data:
  clients:
    jdbc:
      - name: "contacts"
        data-source: "contacts-datasource"
        properties:
          jdbc:
            parameter-count-cache:
              capacity: 512
              max-sql-length: 8192
```

The cache applies only to SQL supplied through imperative `JdbcClient`
operations. It stores parameter counts rather than prepared statements. SQL
longer than `max-sql-length` remains valid even though its count is not retained.

| Setting | Default | Accepted values |
|---------|---------|-----------------|
| `capacity` | `256` | An integer from `0` through `4096`. A value of `0` disables the cache. |
| `max-sql-length` | `4096` | A positive integer for which `capacity` multiplied by `max-sql-length` does not exceed `16,777,216`. |

Helidon validates this combined limit for each client.

## Next Steps

From here, continue with the programming model that best fits the application:

- [Declarative Programming Model](declarative.md) explains repository
  definition, result mapping, generated keys, and transactions.
- [Imperative Programming Model](imperative.md) covers direct statement
  execution and custom row mapping.
