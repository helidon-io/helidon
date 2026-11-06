<!--@frontmatter
description: "Execute JDBC queries, updates, and generated-key operations"
-->
# Queries and Updates

## Statement Execution

Each repository method or `JdbcClient` terminal method executes one prepared SQL
statement. A query must produce a result set, even when that result set is empty. An
ordinary update must produce an update count, and an update configured for generated
keys must make those keys available through the JDBC driver.

A workflow that spans several statements is represented by several Data JDBC operations.
When those statements must succeed or fail together, the operations can run within a
[local transaction](transactions.md#local-transactions).



## Supported Operations

| Operation | Declarative | Imperative |
| --- | --- | --- |
| Query | Scalar, record, custom mapper, `Optional<T>`, or `List<T>` | `map(...).one()`, `optional()`, or `list()` |
| Update | `void`, `int`, or `long` | `execute()` returning `long` |
| Update returning generated keys | `@Jdbc.GeneratedKeys` with query result shapes | `generatedKeys().map(...)` with query terminals |

## Execution Selection

`@Jdbc.Execution` defaults to `AUTO`. Helidon infers query or update execution from the
Java method contract and JDBC annotations, not from SQL keywords.

| Method contract | Inferred execution |
|-----------------|--------------------|
| `@Jdbc.GeneratedKeys` | Update that maps generated keys |
| `void` | Update |
| `Optional<T>` or `List<T>` | Query |
| Supported record or scalar other than primitive `int` or `long` | Query |
| `@Jdbc.RowMapper` without generated keys | Query |
| Primitive `int` or `long` when no other annotation determines execution | Ambiguous. The method must declare `QUERY` or `UPDATE`. |

Because primitive `int` and `long` can represent either a scalar query result or an
update count, methods returning these types need an explicit execution type:

```java
@Jdbc.Statement("SELECT COUNT(*) FROM CONTACT")
@Jdbc.Execution(Jdbc.ExecutionType.QUERY)
long count();

@Jdbc.Statement("DELETE FROM CONTACT WHERE ID = :id")
@Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
long deleteById(long id);
```

Helidon reports ambiguous or incompatible contracts during compilation.

## Imperative Execution

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

## Result Cardinality

### Declarative Cardinality

Queries and operations that return generated keys support the following result types.
`T` is a supported scalar, record, or result produced by a row mapper.

| Return type | Behavior |
|-------------|----------|
| `T` | Requires exactly one row. |
| `Optional<T>` | Accepts zero or one row. |
| `List<T>` | Returns all rows as an immutable list in JDBC encounter order. |

`List<T>` is the only supported return type for multiple rows. The declared return type
must be exactly `List<T>`. A repository method cannot return a `java.util.Collection`,
`java.util.Map`, or `java.util.stream.BaseStream` implementation or subtype instead.
These types also cannot serve as the mapped type `T` within `Optional<T>` or `List<T>`.
Supplying a row mapper does not make any of these return types valid.

A method returning `T` throws `NoResultException` when no row exists. Both `T` and
`Optional<T>` throw `NonUniqueResultException` when more than one row exists.

An ordinary update can return `void`, primitive `int`, or primitive `long`. `void`
discards the update count. A `long` preserves the count reported by the driver. An `int`
method fails when the count exceeds the range of `int`.

For an automatically mapped scalar, `Optional.empty()` represents either no row or SQL
`NULL` in the first column. A record with an optional component can distinguish row
presence from column nullability.

### Imperative Cardinality

The terminal method expresses how many rows a query or operation that returns generated
keys expects:

- `one()` requires exactly one row. It throws `NoResultException` for no rows
  and `NonUniqueResultException` for more than one row.
- `optional()` accepts zero or one row. It throws `NonUniqueResultException`
  for more than one row.
- `list()` returns all rows as an immutable list in JDBC encounter order.

Data JDBC does not impose a row limit on `list()`, so SQL should constrain results that
could become large.

## Generated Keys

### Declarative Generated Keys

An insert or update can return values generated by the database when its method declares
`@Jdbc.GeneratedKeys`. Without column names in the annotation, Helidon asks the driver
to return its default generated keys. Column names in the annotation identify specific
keys:

```java
@Jdbc.Statement("""
        INSERT INTO CONTACT (NAME, STATUS)
        VALUES (:name, :status)
        """)
@Jdbc.GeneratedKeys("ID")
long addContact(String name, String status);
```

Generated keys support the same return types and mapping choices as query results.
Requested column names are passed to the driver in declaration order. Blank names and
exact duplicates cause compilation to fail, while names that differ only by case are
preserved for the driver to resolve. Verify that each supported database and driver
returns the requested generated key columns and preserves the expected handling of
column names.

Outside a local transaction, the driver can commit an update before Helidon finishes
reading and validating its generated keys. A local transaction keeps the update and key
processing atomic. If key processing or cleanup fails, allow the exception to leave the
method that starts the transaction so Helidon can roll back. Catching the exception
still marks the transaction for rollback, but continuing hides the original failure and
cannot result in a commit. Such a failure does not establish that a retry is safe.

### Imperative Generated Keys

After an insert or update, `generatedKeys()` requests the generated keys, `map` converts
each key row, and `one`, `optional`, or `list` validates and returns the expected number
of rows. Without `addColumn`, Helidon asks the driver to return its default generated
keys. Where the driver supports named key columns, `addColumn` identifies the requested
columns:

```java
long id = jdbcClient.create("INSERT INTO CONTACT (NAME) VALUES (?)")
        .bind(1, name)
        .generatedKeys()
        .addColumn("ID")
        .map(row -> row.get(1, Long.class))
        .one();
```

Support for generated keys and handling of column names vary among drivers. Verify that
each supported database and driver returns the requested generated key columns with the
expected column names.

When a client owns its connection, the driver can commit the update before Helidon
finishes reading and validating the keys. An injected client running in a local
transaction keeps the update and key processing atomic. If key processing or cleanup
fails, allow the exception to leave the method that starts the transaction so Helidon
can roll back. Catching the exception still marks the transaction for rollback, but
continuing hides the original failure and cannot result in a commit. Such a failure does
not establish that a retry is safe.
