<!--@frontmatter
description: "Use Helidon Data with JDBC"
navigation:
  icon: i-lucide-database
-->
# Helidon Data with JDBC

## Overview

Use the JDBC provider when your application owns its SQL and you want Helidon
to handle the repetitive work around JDBC. You can declare database operations
on repository interfaces or execute them with `JdbcClient`. Both approaches
use the same execution and result mapping support.

The provider works with SQL statements defined by the application using the
JDBC API. It opens connections, prepares statements, maps returned rows, and
closes every JDBC resource that it acquires. It does not manage entities or
create SQL from repository method names.

The provider supports queries, updates, and generated keys returned by an
update. Each operation accepts one SQL statement. This applies to both
`@Jdbc.Statement` and SQL passed to `JdbcClient.create`. Batch execution,
stored procedures, callable statements, and SQL scripts are not supported.

Helidon checks declarative repositories during compilation, so an invalid
method contract fails the build.

## Maven Coordinates

Add the JDBC provider and a driver for your database to `pom.xml`. This example
uses H2:

<!--@mdc ::code-callout -->
```xml [pom.xml]
<dependencies>
    <dependency>
        <groupId>io.helidon.data.jdbc</groupId>
        <artifactId>helidon-data-jdbc</artifactId> <!-- (1) -->
    </dependency>
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId> <!-- (2) -->
        <scope>runtime</scope>
    </dependency>
</dependencies>
```
1. Adds the Data JDBC API and its runtime support.
2. Adds the JDBC driver used by the application.
<!--@mdc :: -->

## Annotation Processor

If you use repository interfaces, add the Helidon annotation processor bundle
and the JDBC code generator to the compiler configuration:

```xml [pom.xml]
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>io.helidon.bundles</groupId>
                <artifactId>helidon-bundles-apt</artifactId>
                <version>${helidon.version}</version>
            </path>
            <path>
                <groupId>io.helidon.data.jdbc</groupId>
                <artifactId>helidon-data-jdbc-codegen</artifactId>
                <version>${helidon.version}</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

Applications that use only `JdbcClient` do not need the JDBC code generator.

## Choose an API

Use [Declarative JDBC Repositories](declarative.md) when you want Helidon to
generate an implementation from an annotated repository interface. The
repository page explains statements, parameters, return types, result mapping,
and generated keys.

Use the [JDBC Client](imperative.md) when a database operation fits more
naturally in application code. The client page explains how to create or
inject a client, execute statements, and map results.

An application can use both APIs. Generated repositories call the same public
`JdbcClient` API that is available to application code, so statement execution
and mapping behave consistently.

## Helidon Config

The Service Registry creates JDBC clients from the entries under
`data.clients.jdbc`. A client can contain its own connection settings or refer
to an SQL data source that is already registered. Choose one of these sources
for each client.

The value of `data.clients.jdbc` is always a list. This is also true when you
configure only one client, so remember the `-` before each YAML entry. Helidon
rejects a mapping placed directly under `jdbc` because it is not a list.

### Direct JDBC Connection

For a simple application, place the JDBC connection settings in the client
configuration:

```yaml [application.yaml]
data:
  clients:
    jdbc:
      - name: "@default"
        connection:
          url: "jdbc:h2:mem:contacts"
          jdbc-driver-class-name: "org.h2.Driver"
```

The URL is required. You can leave out `jdbc-driver-class-name` if the driver
registers itself with `DriverManager` and can be selected from the URL.

If you use a properties file instead of YAML, identify each list entry with an
index that starts at zero:

```properties [application.properties]
data.clients.jdbc.0.name=@default
data.clients.jdbc.0.connection.url=jdbc:h2:mem:contacts
data.clients.jdbc.0.connection.jdbc-driver-class-name=org.h2.Driver
```

### Registered Data Source

You can also keep connection pool settings in a shared SQL data source and
refer to it by name. For example, add the HikariCP provider when the application
uses a HikariCP data source:

```xml [pom.xml]
<dependency>
    <groupId>io.helidon.data.sql.datasource</groupId>
    <artifactId>helidon-data-sql-datasource-hikari</artifactId>
</dependency>
```

The following configuration creates `contacts-datasource` and uses it for the
default JDBC client:

```yaml [application.yaml]
data:
  sources:
    sql:
      - name: "contacts-datasource"
        provider.hikari:
          username: "sa"
          password: ""
          url: "jdbc:h2:mem:contacts"
  clients:
    jdbc:
      - name: "@default"
        data-source: "contacts-datasource"
```

The name in `data-source` must match an SQL data source available from the
Service Registry.

### Multiple Clients

Give clients unique names when the application connects to more than one data
source:

```yaml [application.yaml]
data:
  clients:
    jdbc:
      - name: "@default"
        data-source: "contacts-datasource"
      - name: "audit"
        data-source: "audit-datasource"
```

Client names let repositories and injected services select the correct
database. If you leave out `name`, Helidon registers the client as `@default`.
Repositories use this client unless they select another one with
`@Jdbc.Client`, such as `@Jdbc.Client("audit")`.

When you inject a client, use its configured name as the Service Registry
qualifier. If that client is unavailable, Helidon reports the missing service
instead of silently switching to `@default`.

### Programmatic Configuration

Configuration does not have to come from a file. To supply it from application
code, install every client configuration before the first lookup of
`JdbcClientConfig` or `JdbcClient`:

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

Pass the complete client list in one `Services.set` call. This list replaces
the YAML configuration rather than adding to it.

The same approach can publish a client backed by a `DataSource` that the
application already owns:

```java
JdbcClientConfig contacts = JdbcClient.builder()
        .dataSource(contactsDataSource)
        .buildPrototype();

Services.set(JdbcClientConfig.class, contacts);
```

Once the Service Registry manages this client, it can participate in the local
transactions described below. Helidon closes connections acquired from the
data source. The application remains responsible for the lifecycle of the data
source itself.

## Portable SQL

Treat the SQL in `@Jdbc.Statement` and `JdbcClient.create` as executable code.
Bind values as parameters instead of building SQL by concatenating input. If an
application choice affects an identifier or another part of the SQL structure,
map that choice to a fixed fragment that you control.

A declarative statement can use named markers or positional `?` markers, but
not both in the same statement. An imperative statement uses positional
markers. Helidon ignores text that looks like a marker inside string literals,
quoted identifiers, and comments. It also passes a doubled `??` to the driver
as escape syntax instead of reading it as two parameters.

SQL syntax varies between databases. Keep these details in mind when writing a
statement that needs to remain portable:

- PostgreSQL: avoid nested block comments. PostgreSQL casts (`::`), escape
  strings, and dollar quoted strings are recognized.
- Oracle: start `q` and `nq` alternative quoted literals at a token boundary.
  Bind dynamic values instead of constructing alternative quoted text.
- MySQL: use doubled apostrophes in ordinary string literals instead of
  backslash escapes. Backtick identifiers are recognized.
- SQL Server: avoid text that resembles a marker inside bracketed identifiers.
- All databases: add whitespace after `--` when starting a line comment.

## Local Transactions

Repositories and injected `JdbcClient` services can participate in a Helidon
local JDBC transaction. Place `@Tx.Required`, `@Tx.New`, or another transaction
annotation on the service or repository method that defines the transaction
boundary. The client must be managed by the Service Registry. A client created
directly with `JdbcClient.builder()` does not join a transaction established by
an annotation.

During a transaction, JDBC work stays on the calling thread. Operations against
the same data source reuse one connection. Helidon rejects an attempt to use a
second data source in that transaction. The supported propagation modes are
`REQUIRED`, `MANDATORY`, `SUPPORTED`, `NEW`, `NEVER`, and `UNSUPPORTED`. A
`NEW` transaction uses an independent connection rather than a savepoint.

Helidon can apply a transaction annotation only when it sees that annotation
during the current compilation. It cannot apply one inherited only from a
separately compiled service or repository interface. For the repository
workaround, see [Transaction Annotations in Compiled Parent
Interfaces](declarative.md#current-limitation-transaction-annotations-in-compiled-parent-interfaces).

For a repository, annotate the individual methods that need a transaction.
Putting a transaction annotation on the repository type can also affect how
Helidon creates the repository service. See [Transaction Annotations on Repository
Types](declarative.md#current-limitation-transaction-annotations-on-repository-types)
for the current limitation and its workaround.

A local JDBC connection cannot join a JTA or XA transaction, or a transaction
owned by another data provider. If another provider already owns the active
transaction, JDBC access fails. Use separate transaction boundaries for work
performed by different providers.

## Schema Management

Creating a `JdbcClient` does not prepare the database for the application. The
client will not create, migrate, seed, or drop database objects. Continue to use
the deployment process or migration tool chosen for your database.

You can create a small test fixture by executing each DDL statement as a
separate operation. Data JDBC does not run SQL scripts.

## JDBC Provider Configuration

Most applications do not need provider settings. When tuning is necessary, add
them under `properties.jdbc`. Helidon uses these values internally and does not
pass them to the data source or JDBC driver.

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

The parameter count cache remembers how many markers occur in frequently used
imperative SQL. It does not contain prepared statements, and generated
repositories do not use it.

| Setting | Default | Accepted values |
|---------|---------|-----------------|
| `parameter-count-cache.capacity` | `256` | `0` through `4096`. Zero disables retention. |
| `parameter-count-cache.max-sql-length` | `4096` | A positive integer |

The product of these values cannot exceed `16777216`. A statement longer than
`max-sql-length` remains valid, but Helidon does not retain its marker count.

Data JDBC does not add a statement timeout, network timeout, or result size
limit. Limit large results in SQL, and configure timeouts through the data
source, JDBC driver, or database.

## Additional Information

- [JDBC client configuration reference](../../config/io.helidon.data.jdbc.JdbcClient.md)
