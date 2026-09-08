<!--@frontmatter
description: "Execute JDBC statements with the Helidon JDBC client"
-->
# JDBC Client

## Overview

`JdbcClient` is useful when a database operation belongs directly in
application code. It provides the same statement execution and row mapping
support used by generated repositories, but lets the application control when
each operation is created and called.

Add the runtime dependency described in
[Helidon Data with JDBC](README.md#maven-coordinates). The annotation processor
is not required for imperative use.

The examples on this page use the Data JDBC client:

```java
import io.helidon.data.jdbc.JdbcClient;
```

## Creating a Client

Before creating a client, decide where its connections will come from. A client
uses one source: a `DataSource` supplied by the application, a data source found
through the Service Registry, or connection settings given to the builder.

### Existing Data Source

If the application already creates and manages a `DataSource`, pass that
instance to the client:

```java
JdbcClient jdbcClient = JdbcClient.builder()
        .dataSource(contactsDataSource)
        .build();
```

The client uses the supplied instance directly and does not publish it to the
Service Registry. The application remains responsible for the lifecycle of the
data source, while Helidon closes each connection that it acquires from it.

### Named Data Source

When an SQL data source is already available from the Service Registry, refer
to its configured name:

```java
JdbcClient jdbcClient = JdbcClient.builder()
        .dataSourceName("contacts-datasource")
        .build();
```

This tells the client to use the data source registered as
`contacts-datasource`. The `dataSourceName()` method returns that configured
name. In contrast, `dataSource()` returns a `DataSource` instance supplied
directly by the application. The older `dataSource(String)` builder method
remains available for compatibility, but new code should use
`dataSourceName(String)`.

### Direct JDBC Connection

For a client that does not share an existing data source, provide the connection
settings on the builder:

```java
JdbcClient jdbcClient = JdbcClient.builder()
        .connection(connection -> connection
                .url("jdbc:postgresql://database.example/contacts")
                .jdbcDriverClassName("org.postgresql.Driver"))
        .build();
```

Each terminal operation on a directly constructed client acquires its own
connection. Such a client does not participate in a transaction established by
an annotation.

## Injecting a Client

Use injection when the client is configured under `data.clients.jdbc`. This is
also the appropriate choice when its operations need to participate in a local
transaction managed by Helidon:

```java
@Service.Singleton
final class ContactStore {
    private final JdbcClient jdbcClient;

    @Service.Inject
    ContactStore(@Data.ProviderType("jdbc")
                 @Service.Named("@default")
                 JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Tx.Required
    void changeStatus(long id, String status) {
        jdbcClient.create("UPDATE CONTACT SET STATUS = ? WHERE ID = ?")
                .bind(1, status)
                .bind(2, id)
                .execute();
    }
}
```

The `@default` qualifier matches the configured client name. See
[Helidon Config](README.md#helidon-config) for other client configurations and
[Configure Clients in Code](README.md#configure-clients-in-code) when the
application supplies client configuration without a configuration file. See
[Local Transactions](README.md#local-transactions) for transaction behavior.

## Executing Statements

A `JdbcClient` operation follows a short sequence. Create the statement, bind
each positional marker once, choose how to map returned rows, and finish with a
terminal method:

```java
Optional<String> name = jdbcClient.create("SELECT NAME FROM CONTACT WHERE ID = ?")
        .bind(1, id)
        .map(String.class)
        .optional();

long updated = jdbcClient.create("UPDATE CONTACT SET STATUS = ? WHERE ID = ?")
        .bind(1, status)
        .bind(2, id)
        .execute();
```

The query ends with `optional()` because it expects zero or one row. The update
ends with `execute()`, which returns its update count.

Imperative statements use positional `?` markers and follow the
[Portable SQL](README.md#portable-sql) guidance. Bind each marker to a non-null
value from the list of [supported scalar types](declarative.md#supported-scalar-types).
When an operation must write SQL `NULL`, put `NULL` in the SQL rather than
binding a null Java reference. It is usually clearest to make that a separate
operation.

### Custom Mapping

For a result that needs more than scalar mapping, convert each row into an
application value:

```java
List<Contact> contacts = jdbcClient.create("""
        SELECT ID AS id, NAME AS name
        FROM CONTACT
        ORDER BY ID
        """)
        .map(row -> new Contact(row.get("id", Long.class),
                                row.get("name", String.class)))
        .list();
```

Call `Row.get` when the column must contain a value. For a nullable column,
`Row.optional` represents SQL `NULL` as `Optional.empty()`. Read all required
values inside the mapping callback because the `Row` is not valid after the
callback returns.

### Generated Keys

After an insert or update, the generated key stage can ask the driver for values
created by the database:

```java
long id = jdbcClient.create("INSERT INTO CONTACT (NAME) VALUES (?)")
        .bind(1, name)
        .generatedKeys()
        .addColumn("ID")
        .map(row -> row.get(1, Long.class))
        .one();
```

In this example, the client requests the `ID` column and expects exactly one
key. Support for generated keys and the interpretation of column names depend
on the JDBC driver.

## Results and Resource Ownership

Finish each query with the method that matches the number of rows you expect:

- Use `one()` when the query must return exactly one row. No rows result in
  `NoResultException`, and more than one results in `NonUniqueResultException`.

- Use `optional()` when the query can return zero or one row. More than one row
  results in `NonUniqueResultException`.

- Use `list()` when you want every returned row. Helidon does not impose a row
  limit, so limit large results in SQL and use deterministic ordering when
  paging through them.

The value returned by any of these methods is fully available to the
application. Before returning it, Helidon closes the result set, statement, and
connection used for the operation. This is why the API does not return a
stream, cursor, or iterator that remains connected to JDBC resources.

Keep and share the `JdbcClient` itself as needed. Create a new statement chain
for each operation and finish it with one terminal method. Do not retain or
share the intermediate statement, generated key, or result stages between
threads.

A problem reported by the JDBC driver becomes a `DataException`. If an
application mapper throws an exception, Helidon passes it back to the caller
unchanged.
