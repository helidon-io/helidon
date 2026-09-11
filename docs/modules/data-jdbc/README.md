<!--@frontmatter
description: "Use the Helidon Data JDBC provider"
navigation:
  icon: i-lucide-database
-->
# Helidon Data JDBC Provider

## Overview

> [!NOTE]
> Helidon Data JDBC is an incubating feature. It is not production ready. Its
> APIs and behavior may change incompatibly or be removed in any release.

Helidon Data JDBC is a Helidon Data provider implemented using Java Database
Connectivity (JDBC). The application supplies the SQL, and the provider
manages connections, prepared statements, parameter binding, result mapping,
and resource cleanup.

Choose Data JDBC when you want direct control over SQL and do not need entity
management or queries derived from repository method names. The provider
supports two programming models:

- [Declarative JDBC repositories](declarative.md) define operations on an
  annotated interface. Helidon validates the interface and generates the
  repository implementation during compilation. See the declarative guide for
  setup and usage details.
- [Imperative clients](imperative.md) use `JdbcClient` to build and execute
  operations directly in application code. See the imperative guide for API
  details and examples.

Generated repositories use the public `JdbcClient` API to execute their
operations. An application can therefore use declarative repositories and
imperative clients together. Both styles use the same statement execution,
result mapping, and resource cleanup. Clients managed by the Service Registry
can also participate in local JDBC transactions.

For each Data JDBC operation, the application supplies one SQL statement. The
provider does not generate SQL, create or manage entities, or initialize the
database schema. The schema must exist before data operations begin. See
[Schema Management](#schema-management) for guidance about schema changes.

## Maven Coordinates

Add the Data JDBC dependency, the configuration parser used by the
application, and the JDBC driver for the database to `pom.xml`. The following
example uses YAML configuration and H2:

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
1. Adds the Data JDBC API and runtime support.
2. Adds support for `application.yaml`. Omit this dependency when the
   application already provides YAML support or uses another configuration
   format.
3. Adds the JDBC driver used by the application.
<!--@mdc :: -->

See [Managing Dependencies](../../dependency-management.md) for information
about Helidon dependency management.

## JDBC Client Configuration

The Service Registry creates `JdbcClient` services from the list under
`data.clients.jdbc`. Each client must use exactly one connection source: direct
JDBC connection settings or the name of a registered SQL data source.

### Direct JDBC Connections

Configure a direct connection for each client that does not use a registered
data source. The following example defines a default client with a direct
connection and a separate client for audit data:

```yaml [application.yaml]
data:
  clients:
    jdbc:
      - name: "@default"
        connection:
          url: "jdbc:h2:mem:contacts;DB_CLOSE_DELAY=-1"
          jdbc-driver-class-name: "org.h2.Driver"
      - name: "audit"
        connection:
          url: "jdbc:h2:mem:audit;DB_CLOSE_DELAY=-1"
          jdbc-driver-class-name: "org.h2.Driver"
```

The connection URL is required. The driver class name is optional when the
driver registers itself with `DriverManager` and the URL identifies the
driver.

Assign a unique name to each client when the application connects to more than
one data source. If you omit `name`, Helidon registers the client as
`@default`. A repository uses the default client unless its interface selects
another client with `@Jdbc.Client`, such as `@Jdbc.Client("audit")`. Injected
clients use the configured name as their Service Registry qualifier. Helidon
reports a missing selected client instead of falling back to the default.

`data.clients.jdbc` is a list, even when the application defines only one
client. In a properties file, use an index starting at zero for each entry:

```properties [application.properties]
data.clients.jdbc.0.name=@default
data.clients.jdbc.0.connection.url=jdbc:h2:mem:contacts;DB_CLOSE_DELAY=-1
data.clients.jdbc.0.connection.jdbc-driver-class-name=org.h2.Driver
```

### Registered Data Sources

Use a registered SQL data source when several components share the same pool
or data source configuration. For example, add the HikariCP data source
provider when the application uses HikariCP:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.data.sql.datasource</groupId>
  <artifactId>helidon-data-sql-datasource-hikari</artifactId>
</dependency>
```

The following configuration creates `contacts-datasource` and assigns it to
the default JDBC client:

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
      - name: "@default"
        data-source: "contacts-datasource"
```

The `data-source` value must match the name of an SQL data source available
from the Service Registry.

### Configure Clients in an Imperative Application

An imperative application can configure clients before the first lookup of
`JdbcClientConfig` or `JdbcClient`. For example, it can supply an existing
`DataSource`:

```java
JdbcClientConfig contacts = JdbcClient.builder()
        .dataSource(contactsDataSource)
        .buildPrototype();

Services.set(JdbcClientConfig.class, contacts);
```

The application continues to manage the lifecycle of the `DataSource`. Helidon
closes every connection that it acquires from the data source. The Service
Registry manages the resulting client, so its operations can run within the
local transactions described later on this page.

One `Services.set` call supplies the complete list and replaces configurations
from `application.yaml` rather than merging with them. For an example with
multiple clients, see
[Configure Clients in an Imperative Application](imperative.md#configure-clients-in-an-imperative-application).
For details about injected and standalone clients, see
[JDBC Client Configuration](imperative.md#jdbc-client-configuration).

## SQL Guidelines

Each `@Jdbc.Statement` repository invocation and each terminal operation
created through `JdbcClient.create` executes exactly one prepared SQL statement.
The statement must produce the result expected by the selected operation: a
query result set, an update count, or an update count followed by keys obtained
through JDBC's generated-keys facility. An incompatible primary result, or any
subsequent result set or update count, causes a `DataException`.

Data JDBC does not support SQL scripts, JDBC batch execution,
`CallableStatement`, stored-procedure or stored-function calls and their
callable parameters, driver-specific compound or multiple-statement strings,
or operations that return multiple results.

Execute an ordered workflow as separate repository or client operations in
application code. When those operations must be atomic, execute them within a
supported local JDBC transaction. Use the statement API primarily for queries
and data changes. See [Schema Management](#schema-management) before executing
data definition language (DDL) statements.

Treat SQL as executable application code. Bind untrusted values instead of
concatenating them into SQL. Bind markers represent values only. Select table
names, column names, operators, sort directions, and other SQL structure from
an explicit allowlist defined by the application.

Declarative statements support named markers or positional `?` markers, but a
statement cannot mix the two styles. `JdbcClient` accepts positional markers
only. For details, see [Statement Parameters](declarative.md#statement-parameters)
and [Parameter Binding](imperative.md#parameter-binding).

Data JDBC uses a consistent set of rules to identify bind markers. It ignores
text that resembles a bind marker in standard string literals, quoted
identifiers, conventional comments, PostgreSQL escape strings and dollar quoted
strings, and valid Oracle alternative quoted strings. It preserves doubled
`??` as driver escape syntax. For portable SQL:

- Use doubled apostrophes in ordinary string literals instead of MySQL
  backslash escapes.
- Add whitespace after `--` when starting a line comment, and do not nest block
  comments.
- Start Oracle `q` and `nq` alternative quoted literals at a token boundary.
- Avoid text that resembles a bind marker in SQL Server bracketed identifiers.
  Data JDBC treats square brackets as ordinary punctuation.

## Database Compatibility

Data JDBC uses standard JDBC APIs. Helidon tests the provider with H2, MySQL,
PostgreSQL, and Oracle Database. Other databases may also work if they provide
a JDBC driver, but Helidon does not currently test them.

SQL syntax, type conversions, generated key handling, and transaction behavior
can vary by database and JDBC driver. Verify these behaviors with each database
and driver combination that your application supports.

## Errors and JDBC Warnings

An `SQLException` reported by the JDBC driver is translated to a Helidon Data
`DataException`. The diagnostic identifies the kind of operation and, when
supplied by the driver, includes a portable SQLSTATE description, the exact
SQLSTATE, and the vendor error code. Consult the JDBC-driver documentation when
interpreting the exact state or vendor code.

To avoid disclosing application data or credentials, translated diagnostics do
not include the SQL statement, bind values, the driver-provided message, JDBC
URL details, usernames, or passwords. Provider-observed cleanup failures are
sanitized and may be attached as suppressed exceptions. An exception thrown by
an application-provided `JdbcClient.RowMapper` is propagated unchanged.

Data JDBC does not inspect, clear, expose, or promote JDBC warnings from a
connection, statement, or result set. A warning therefore does not cause an
operation to fail or become a `DataException`. This includes read-side
`DataTruncation` reported by the driver as a warning. A truncation reported as
an `SQLException`, such as a write-side truncation, follows the normal
exception-translation rules. Use driver- or database-specific observability
when warning monitoring is required.

## Local Transactions

Generated repositories and `JdbcClient` services managed by the Service
Registry can run within Helidon local JDBC transactions. Apply a
transaction annotation such as `@Tx.Required`, or call `Tx.transaction`, to
define where a transaction begins and ends. A `JdbcClient` created directly
with `JdbcClient.builder()` acquires its own connection for each operation and
does not join a transaction created by an annotation.

Local JDBC transactions are synchronous and remain associated with the thread
that started them. Operations against the same data source share one connection
for the transaction. An attempt to use a second data source fails before
Helidon starts work on that source. For the general meaning of each propagation
mode and its corresponding annotation, see
[Transaction Types and Annotations](../data.md#transaction-types-and-annotations).
For local JDBC transactions, `NEW` suspends the current transaction and uses an
independent JDBC connection; it does not create a savepoint. `UNSUPPORTED`
suspends the current transaction and executes JDBC operations using
operation-owned, automatically committed connections.

Data JDBC participates only in a transaction owned by the local JDBC transaction
provider. If another transaction provider is active, the JDBC operation fails
before acquiring a JDBC connection. Data JDBC does not enlist its connection in
that transaction or automatically switch to a JDBC transaction. Consequently,
one transaction cannot atomically combine Data JDBC work with JTA, XA, or
Jakarta Persistence resource-local work in this release.

When the outcome of a transaction is unknown, check the database before deciding
whether to retry the operation.

See [Transactions](declarative.md#transactions) for repository annotation
placement and [Transaction Participation](imperative.md#transaction-participation)
for imperative usage.

## Schema Management

Creating a JDBC client does not create, alter, or drop schema objects. Use the
migration or deployment tools for your database to manage its schema. Run
schema changes separately from transactions that modify application data.

For a limited setup task, such as creating a test fixture, Data JDBC can execute
one DDL statement using a connection acquired for that operation, with automatic
commit enabled. Run this operation outside a local JDBC transaction. Use
`@Tx.Never` to reject an active transaction or `@Tx.Unsupported` to suspend one
while the DDL executes.

Some DDL and other SQL statements can commit or end a local transaction
implicitly. Do not execute these statements within a local transaction. Helidon
does not inspect the SQL and therefore cannot detect every implicit commit. A
later rollback might not undo changes that were already committed, so a
reported failure does not prove that the database is unchanged.

The statement API does not support SQL that controls transactions or commands
that change the state of a connection or session.

## Parameter Count Cache

Most applications can use the provider defaults. Imperative clients can cache
the number of parameters in SQL statements that they use repeatedly. Configure
this cache with `properties.jdbc.parameter-count-cache` on the client:

```yaml [application.yaml]
data:
  clients:
    jdbc:
      - name: "@default"
        data-source: "contacts-datasource"
        properties:
          jdbc:
            parameter-count-cache:
              capacity: 512
              max-sql-length: 8192
```

The cache applies only to imperative clients. It records the number of bind
markers in each SQL statement and does not store prepared statements.

| Setting | Default | Accepted values |
|---------|---------|-----------------|
| `parameter-count-cache.capacity` | `256` | `0` through `4096`. A value of `0` disables retention. |
| `parameter-count-cache.max-sql-length` | `4096` | A positive integer. |

The product of the two values cannot exceed `16777216`. A statement longer than
`max-sql-length` remains valid, but Helidon does not cache its bind marker count.

## JDBC Options and Limits

The parameter-count cache settings described above are the only Data JDBC
provider tuning controls in this release. Data JDBC does not expose
application-configurable prepared-statement options such as query timeout,
fetch size, maximum rows, result-set type, concurrency or holdability, escape
processing, or cancellation.

The JDBC client does not accept or forward arbitrary data-source or JDBC-driver
property maps. Its bind API also does not provide a custom JDBC-type override,
vendor-specific binding hook, or codec mechanism.

Configure connection pooling, timeouts, and supported vendor properties through
the selected data-source provider, JDBC connection URL, JDBC driver, or
database. Limit result sizes in SQL. A setting accepted by a particular data
source, driver, or database is not a portable Data JDBC feature.

## Next Steps

- Follow the [Data JDBC guide](../../guides/data-jdbc.md) to build a complete
  application with a generated repository and H2.
- Read [Declarative JDBC Repositories](declarative.md) for repository contracts,
  mapping, generated keys, and transactions.
- Read [JDBC Client](imperative.md) for direct statement execution and custom
  row mapping.
- Explore the [declarative examples][declarative-examples] and
  [imperative examples][imperative-examples] for complete examples that target
  specific databases.

[declarative-examples]: https://github.com/helidon-io/helidon-examples/tree/helidon-27.x/examples/declarative/data-jdbc
[imperative-examples]: https://github.com/helidon-io/helidon-examples/tree/helidon-27.x/examples/imperative/data-jdbc
