<!--@frontmatter
description: "Use the Helidon Data JDBC provider"
navigation:
  icon: i-lucide-database
-->
# Helidon Data JDBC Provider

## Overview

Helidon Data JDBC is a data provider that executes application-defined
SQL through Java Database Connectivity (JDBC). It binds parameters, maps
results, and manages the JDBC resources used by each operation.

For brevity, this documentation refers to the Helidon Data JDBC provider as
Data JDBC.

Data JDBC does not provide entity management or derive queries from repository
method names. It supports two programming models:

- The [declarative programming model](declarative.md) defines operations on an
  annotated repository interface. Helidon validates the interface and generates
  the repository implementation during compilation.
- The [imperative programming model](imperative.md) uses `JdbcClient` to build
  and execute operations directly in application code.

An application can therefore combine declarative repository
methods with imperative `JdbcClient` operations. Both programming models use
the same statement execution, result mapping, and resource cleanup.

Each Data JDBC operation executes one SQL statement. The provider does not
initialize the database schema, so the schema must exist before data operations
begin. See [Schema Management](#schema-management) for guidance about schema
changes.

> [!NOTE]
> Helidon Data JDBC is an incubating feature. It is not production ready. Its
> APIs and behavior may change incompatibly or be removed in any release.

## Maven Coordinates

To enable Data JDBC with YAML configuration and H2, add the following
dependencies to your project’s `pom.xml` (see
[Managing Dependencies](../../dependency-management.md)):

<!--@mdc ::code-callout -->
```xml [pom.xml]
<dependencies>
  <dependency>
    <groupId>io.helidon.data.jdbc</groupId>
    <artifactId>helidon-data-jdbc</artifactId> <!-- (1) -->
  </dependency>
  <dependency>
    <groupId>io.helidon.config</groupId>
    <artifactId>helidon-config-yaml</artifactId> <!-- (2) -->
  </dependency>
  <dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId> <!-- (3) -->
    <scope>runtime</scope>
  </dependency>
</dependencies>
```
1. Adds the Data JDBC API and runtime.
2. Adds support for `application.yaml`. Omit this dependency if your
   application already supports YAML or uses another configuration format.
3. Adds the H2 JDBC driver.
<!--@mdc :: -->

## Configuration

The Service Registry creates `JdbcClient` services from the list under
`data.clients.jdbc`. Configure exactly one connection source for each client:
a direct JDBC connection or a registered SQL data source.

Give each client a unique name when your application uses more than one data
source. The examples on this page use `contacts` as the client name. If you
omit `name`, Helidon registers the client as `@default`. Repositories use the
default client unless they select another client with `@Jdbc.Client`. Injected
clients use the configured name as their Service Registry qualifier.

### Direct JDBC Connections

The following configuration creates clients named `contacts` and `audit`:

```yaml [application.yaml]
data:
  clients:
    jdbc:
      - name: "contacts"
        connection:
          url: "jdbc:h2:mem:contacts;DB_CLOSE_DELAY=-1"
          jdbc-driver-class-name: "org.h2.Driver"
      - name: "audit"
        connection:
          url: "jdbc:h2:mem:audit;DB_CLOSE_DELAY=-1"
          jdbc-driver-class-name: "org.h2.Driver"
```

The connection URL is required. You can omit `jdbc-driver-class-name` when the
driver registers itself and accepts the configured URL.

`data.clients.jdbc` is a list, even if the application defines only one client.
Use zero-based indexes when you configure the list in a properties file:

```properties [application.properties]
data.clients.jdbc.0.name=contacts
data.clients.jdbc.0.connection.url=jdbc:h2:mem:contacts;DB_CLOSE_DELAY=-1
data.clients.jdbc.0.connection.jdbc-driver-class-name=org.h2.Driver
```

### Registered Data Sources

Use a registered SQL data source when components share a data source or
connection pool. For example, add the HikariCP provider:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.data.sql.datasource</groupId>
  <artifactId>helidon-data-sql-datasource-hikari</artifactId>
</dependency>
```

Then define the data source and reference its name from the JDBC client:

```yaml [application.yaml]
data:
  sources:
    sql:
      - name: "contacts-datasource"
        provider.hikari:
          username: "sa"
          password: ""
          url: "jdbc:h2:mem:contacts;DB_CLOSE_DELAY=-1"
  clients:
    jdbc:
      - name: "contacts"
        data-source: "contacts-datasource"
```

The `data-source` value must match an SQL data source available from the
Service Registry.

Imperative applications can also configure registry-managed clients in code
or create standalone clients. See
[JDBC Client Configuration](imperative.md#jdbc-client-configuration).

## SQL Guidelines

Each repository method and each terminal `JdbcClient` operation executes one
prepared SQL statement. The statement must return the result expected by the
operation: rows for a query, an update count for an update, or generated keys
for an update that requests them.

Use separate operations for a multi-step workflow. Enclose the operations in a
[local transaction](#local-transactions) when they must be atomic.

Always bind application input. Bind markers represent values only. Select
table names, column names, operators, sort directions, and other SQL structure
from an allowlist controlled by the application.

Declarative statements accept named markers or positional `?` markers, but not
both in the same statement. The imperative approach accepts positional markers
only. See [Statement Parameters](declarative.md#statement-parameters) and
[Parameter Binding](imperative.md#parameter-binding) for details.

Data JDBC ignores text that resembles a bind marker in standard string
literals, quoted identifiers, comments, PostgreSQL escape and dollar-quoted
strings, and valid Oracle alternative-quoted strings. It preserves doubled
`??` as driver escape syntax. For portable SQL:

- Use doubled apostrophes in ordinary string literals instead of MySQL
  backslash escapes.
- Add whitespace after `--` when starting a line comment, and do not nest block
  comments.
- Start Oracle `q` and `nq` alternative-quoted literals at a token boundary.
- Avoid text that resembles a bind marker in SQL Server bracketed identifiers.
  Data JDBC treats square brackets as ordinary punctuation.

## Local Transactions

Generated repositories and registry-managed `JdbcClient` services can
participate in Helidon local JDBC transactions. Apply a transaction annotation,
such as `@Tx.Required`, to a managed service method, or call `Tx.transaction`.
A standalone client created with `JdbcClient.builder()` always uses its own
connection and does not join the local transaction.

Local JDBC transactions are synchronous and remain associated with the thread
that starts them. Operations in one transaction must use the same data source.
See [Transaction Types and Annotations](../data.md#transaction-types-and-annotations)
for the available propagation modes.

Data JDBC participates only in transactions owned by the local JDBC transaction
provider. It does not enlist a connection in JTA, XA, or Jakarta Persistence
resource-local transactions. If another transaction provider is active, the
JDBC operation fails before acquiring a connection.

If a transaction outcome is unknown, check the database before retrying the
operation. For model-specific guidance, see
[Transactions](declarative.md#transactions) and
[Transaction Participation](imperative.md#transaction-participation).

## Schema Management

Use your database's migration or deployment tools to create and update the
schema. Run schema changes separately from transactions that modify application
data.

Data JDBC can execute a single DDL statement for limited setup tasks, such as
creating a test fixture. Run the statement outside a local JDBC transaction.
Use `@Tx.Never` to reject an active transaction or `@Tx.Unsupported` to suspend
one while the statement executes.

Some databases implicitly commit DDL and other statements. Because Data JDBC
does not inspect or classify SQL, a later rollback might not undo those changes.
Do not use the statement API for transaction-control or connection- and
session-state commands.

## Database Portability

Data JDBC uses standard JDBC APIs, but SQL syntax, type conversions, generated
keys, and transaction behavior can vary by database and driver. Test each
database and driver combination that your application supports.

Configure pooling, timeouts, and vendor-specific properties through the data
source, JDBC driver, connection URL, or database. Limit large results in SQL,
and use deterministic ordering when paging.

## Errors and JDBC Warnings

Data JDBC translates an `SQLException` into a Helidon Data `DataException`.
The diagnostic can include the SQLSTATE and vendor error code supplied by the
driver, but omits SQL text, bind values, credentials, and driver messages that
might disclose application data. An exception from an application-provided
`JdbcClient.RowMapper` is propagated unchanged.

Data JDBC does not promote JDBC warnings to exceptions. Use database- or
driver-specific observability if your application must monitor warnings,
including read-side data truncation reported as a warning.

## Parameter Count Cache

`JdbcClient` caches bind-marker counts for SQL that it executes repeatedly. The
default capacity is 256 statements. Configure the cache for a client under
`properties.jdbc.parameter-count-cache`:

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

The cache stores marker counts, not prepared statements. A statement longer
than `max-sql-length` remains valid, but Helidon does not cache its marker
count.

| Setting | Default | Accepted values                                   |
|---------|---------|---------------------------------------------------|
| `capacity` | `256` | `0` through `4096`, where `0` disables retention. |
| `max-sql-length` | `4096` | A positive integer.                               |


## Next Steps

- Read [Declarative Programming Model](declarative.md) for repository
  definition, result mapping, generated keys, and transactions.
- Read [Imperative Programming Model](imperative.md) for direct statement
  execution and custom row mapping.
