<!--@frontmatter
description: "Use Helidon Data JDBC with the declarative programming model"
-->
# Declarative Programming Model

## Overview

The Data JDBC declarative programming model represents database operations as
methods on an annotated Java interface. Each method describes its SQL,
parameters, return type, and mapping requirements. Helidon validates this
contract and generates the repository implementation during compilation.

Repositories work well when an application exposes a predefined set of SQL
operations through a repository API. Declarative repositories and the
[imperative programming model](imperative.md) can coexist in the same
application. The imperative model is useful for operations whose SQL is
selected at runtime.

Repository development begins with the runtime dependency and JDBC driver
described in [Maven Coordinates](data-jdbc.md#maven-coordinates). The
application also needs a configured JDBC client and an available database
schema.

> [!NOTE]
> Helidon Data JDBC is an incubating feature. It is not production ready. Its
> APIs and behavior may change incompatibly or be removed in any release.

## Annotation Processing

Helidon generates repository implementations during compilation. The Maven
compiler configuration therefore includes the Helidon annotation processor
bundle and the Data JDBC code generator:

```xml [pom.xml]
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-compiler-plugin</artifactId>
  <version>${version.plugin.compiler}</version>
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

A project that manages the Maven compiler plugin through its parent can omit
the plugin `version`. A project that imports only `helidon-dependencies` still
needs to define `version.plugin.compiler` or replace the expression with a
compatible plugin version. The Helidon dependency BOM manages dependency
versions, but it does not manage Maven build plugins.

Because Data JDBC is incubating, the Helidon API stability processor rejects its
use by default. The focused suppression
`@SuppressWarnings(Api.SUPPRESS_INCUBATING)` permits intentional use on a
package, type, or method. The constant is declared by
`io.helidon.common.Api`, and the rest of the project retains the default
stability checks.

A project that uses incubating APIs throughout can instead configure the
compiler to report them as warnings:

```xml [pom.xml]
<compilerArgs combine.children="append">
  <arg>-Ahelidon.api.incubating=warn</arg>
</compilerArgs>
```

The `warn` value reports each use during compilation. A value of `ignore`
suppresses those reports.

## Repository Definition

A JDBC repository begins with an interface annotated with `@Data.Repository`
and `@Data.Provider("jdbc")`. Each abstract repository method declares its SQL
with one `@Jdbc.Statement`:

```java
@SuppressWarnings(Api.SUPPRESS_INCUBATING)
@Data.Repository
@Data.Provider("jdbc")
@Jdbc.Client("contacts")
public interface ContactRepository {

    @Jdbc.Statement("""
            SELECT ID AS id, NAME AS name
            FROM CONTACT
            WHERE ID = :id
            """)
    Optional<Contact> findById(long id);

    @Jdbc.Statement("""
            UPDATE CONTACT
            SET STATUS = :status
            WHERE ID = :id
            """)
    @Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
    long updateStatus(long id, String status);
}

record Contact(long id, String name) {
}
```

Compilation generates the implementation and registers it with the Service
Registry, where the application can obtain and invoke it:

```java
ContactRepository repository = Services.get(ContactRepository.class);
Optional<Contact> contact = repository.findById(100);
```

`@Data.Provider("jdbc")` can be omitted when JDBC is the only Data code
generator on the annotation processor path. When more than one Data code
generator is present, declare `@Data.Provider("jdbc")` so that only Data JDBC
generates the repository implementation. Helidon does not infer a provider from
repository methods or annotations.

### JDBC Client Selection

Without `@Jdbc.Client`, a repository resolves the client registered as
`@default`. The `ContactRepository` example selects the configured `contacts`
client, while the following repository selects `audit`:

```java
@Data.Repository
@Data.Provider("jdbc")
@Jdbc.Client("audit")
interface AuditRepository {
    // Repository methods
}
```

The value supplied to `@Jdbc.Client` must match the configured client name,
including its capitalization. Repository activation fails when that client is
not available from the Service Registry. A named client does not automatically
become the default, even when it is the only client configured. For example, a
repository that uses the sole client named `contacts` still declares
`@Jdbc.Client("contacts")`. JDBC repositories select clients with
`@Jdbc.Client`, not `@Data.PersistenceUnit`.

## Repository Methods

The following annotations describe a JDBC repository and its methods:

| Annotation | Purpose |
|------------|---------|
| `@Jdbc.Client` | Selects the JDBC client managed by the Service Registry. |
| `@Jdbc.Statement` | Declares the SQL for a method. |
| `@Jdbc.Execution` | Selects `AUTO`, `QUERY`, or `UPDATE` execution. |
| `@Jdbc.GeneratedKeys` | Requests generated keys from an update. |
| `@Jdbc.RowMapper` | Selects a row mapper service supplied by the application. |

Each method executes one SQL statement. The shared
[SQL guidelines](data-jdbc.md#sql-guidelines) explain safe binding and portable
marker parsing.

### Statement Parameters

Repository statements accept named or positional parameters. A named marker
matches a Java parameter name and can appear more than once:

```java
@Jdbc.Statement("""
        SELECT ID AS id, NAME AS name
        FROM CONTACT
        WHERE OWNER_ID = :ownerId OR DELEGATE_ID = :ownerId
        """)
List<Contact> findOwnedOrDelegated(long ownerId);
```

Positional `?` markers bind parameters in Java declaration order:

```java
@Jdbc.Statement("UPDATE CONTACT SET STATUS = ? WHERE ID = ?")
@Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
long updateStatus(String status, long id);
```

A statement uses either named markers or positional markers rather than mixing
the two forms. Every method parameter must be referenced by at least one named
marker or represented by one positional marker. A named parameter can appear
more than once. With positional markers, the number of Java parameters must
equal the number of markers.

Repository parameters cannot represent a variable length SQL `IN` list. Each
value requires a separate scalar parameter and marker. The imperative model can
construct SQL when the number of values is determined at runtime.

Repository parameters use the [supported scalar types](#supported-scalar-types).
Reference arguments can be `null`, in which case the generated implementation
binds SQL `NULL` with the corresponding JDBC type. Primitive parameters cannot
be null, and repository parameters do not use `Optional`.

SQL continues to determine what a null argument means. In the following
example, a null `email` removes the filter and allows every row to match:

```java
@Jdbc.Statement("""
        SELECT ID AS id, NAME AS name
        FROM CONTACT
        WHERE (:email IS NULL OR EMAIL = :email)
        """)
List<Contact> findByOptionalEmail(String email);
```

If a null argument should match only rows where `EMAIL` is SQL `NULL`, the
predicate can express that distinction explicitly:

```java
@Jdbc.Statement("""
        SELECT ID AS id, NAME AS name
        FROM CONTACT
        WHERE ((:email IS NULL AND EMAIL IS NULL) OR EMAIL = :email)
        """)
List<Contact> findByNullableEmail(String email);
```

Data JDBC supplies the typed SQL `NULL` without changing the predicate's
meaning.

Data JDBC passes wildcard characters in a value for an SQL `LIKE` predicate
without escaping them. When the input represents literal text, the application
escapes the configured escape character, `%`, and `_`, while the SQL declares
the same escape character:

```java
@Jdbc.Statement("""
        SELECT ID AS id, NAME AS name
        FROM CONTACT
        WHERE NAME LIKE :pattern ESCAPE '!'
        """)
List<Contact> findByNamePattern(String pattern);
```

In this example, `pattern` uses `!` to escape `!`, `%`, and `_`. The database
determines case sensitivity and collation.

> [!NOTE]
> Named markers in an inherited method require the original Java parameter
> names. A separately published parent interface must therefore be compiled
> with `-parameters` unless that interface uses positional markers.

### Execution Types

`@Jdbc.Execution` defaults to `AUTO`. Helidon infers query or update execution
from the Java method contract and JDBC annotations, not from SQL keywords.

| Method contract | Inferred execution |
|-----------------|--------------------|
| `@Jdbc.GeneratedKeys` | Update that maps generated keys |
| `void` | Update |
| `Optional<T>` or `List<T>` | Query |
| Supported record or scalar other than primitive `int` or `long` | Query |
| `@Jdbc.RowMapper` without generated keys | Query |
| Primitive `int` or `long` when no other annotation determines execution | Ambiguous. The method must declare `QUERY` or `UPDATE`. |

Because primitive `int` and `long` can represent either a scalar query result
or an update count, methods returning these types need an explicit execution
type:

```java
@Jdbc.Statement("SELECT COUNT(*) FROM CONTACT")
@Jdbc.Execution(Jdbc.ExecutionType.QUERY)
long count();

@Jdbc.Statement("DELETE FROM CONTACT WHERE ID = :id")
@Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
long deleteById(long id);
```

Helidon reports ambiguous or incompatible contracts during compilation.

### Return Types

Queries and operations that return generated keys support the following result
types. `T` is a supported scalar, record, or result produced by a row mapper.

| Return type | Behavior |
|-------------|----------|
| `T` | Requires exactly one row. |
| `Optional<T>` | Accepts zero or one row. |
| `List<T>` | Returns all rows as an immutable list in JDBC encounter order. |

A method returning `T` throws `NoResultException` when no row exists. Both `T`
and `Optional<T>` throw `NonUniqueResultException` when more than one row
exists.

An ordinary update can return `void`, primitive `int`, or primitive `long`.
`void` discards the update count. A `long` preserves the count reported by the
driver. An `int` method fails when the count exceeds the range of `int`.

For an automatically mapped scalar, `Optional.empty()` represents either no
row or SQL `NULL` in the first column. A record with an optional component can
distinguish row presence from column nullability.

## Result Mapping

Data JDBC can read a scalar from the first selected column, construct a flat
record from column labels, or delegate the row to a mapper supplied by the
application. Each physical row is mapped independently.

Data JDBC does not assemble joined rows into an object graph. The application
can aggregate the materialized values after the repository method returns or
obtain related data through separate repository operations.

### Scalars and Records

Mapping to a required scalar fails when the first column contains SQL `NULL`.
A nullable record component uses `Optional<S>`, where `S` is a supported scalar.

Helidon matches column labels to record component names without regard to case
or column order. SQL aliases make the mapping explicit:

```java
record ContactSummary(long id, String name, Optional<String> email) {
}

@Jdbc.Statement("""
        SELECT EMAIL AS email, ID AS id, NAME AS name
        FROM CONTACT
        ORDER BY NAME
        """)
List<ContactSummary> findAll();
```

Mapping fails if a record component has no matching column label or if multiple
column labels match the component without regard to case. When a driver returns
a blank label, Data JDBC uses the physical column name. Mapping also fails if
the driver provides neither a usable label nor a usable column name.

Record component names must be unique without regard to case. Each component
must be a supported scalar or `Optional` of a supported scalar. A row mapper
handles nested records, mutable classes, and conversions specific to the
application.

### Supported Scalar Types

| Family | Java types |
|--------|------------|
| Boolean and numeric | `boolean`/`Boolean`, `byte`/`Byte`, `short`/`Short`, `int`/`Integer`, `long`/`Long`, `float`/`Float`, `double`/`Double`, `BigDecimal` |
| Text and binary | `String`, `byte[]` |
| Java time | `LocalDate`, `LocalTime`, `LocalDateTime` |
| JDBC time | `java.sql.Date`, `java.sql.Time`, `java.sql.Timestamp` |

For a null reference argument, Data JDBC uses the following canonical JDBC
type:

| Java type | JDBC type |
|-----------|-----------|
| `Boolean` | `BOOLEAN` |
| `Byte` | `TINYINT` |
| `Short` | `SMALLINT` |
| `Integer` | `INTEGER` |
| `Long` | `BIGINT` |
| `Float` | `REAL` |
| `Double` | `DOUBLE` |
| `BigDecimal` | `DECIMAL` |
| `String` | `VARCHAR` |
| `byte[]` | `VARBINARY` |
| `LocalDate`, `java.sql.Date` | `DATE` |
| `LocalTime`, `java.sql.Time` | `TIME` |
| `LocalDateTime`, `java.sql.Timestamp` | `TIMESTAMP` |

This mapping applies only to reference parameters because primitive parameters
cannot be null.

These types are available for repository parameters, automatic results,
generated keys, record components, imperative bind values, and typed row reads.
For another Java type, store a supported representation such as text, bytes, or
a numeric value, then convert it in application code or a row mapper.

Data JDBC does not map `OffsetTime` or `OffsetDateTime` implicitly because
databases do not preserve those values consistently. An application can store
the local value and offset separately, or store an ISO 8601 value in a character
column and reconstruct the value in a row mapper.

### Custom Row Mapping

A `JdbcClient.RowMapper<T>` can handle a result that does not fit automatic
scalar or record mapping. The mapper becomes a Service Registry service, and
`@Jdbc.RowMapper` connects it to a repository method.

Because the mapper implements an incubating Data JDBC API, its class also
acknowledges that API status:

```java
@SuppressWarnings(Api.SUPPRESS_INCUBATING)
@Service.Singleton
final class ContactLabelMapper implements JdbcClient.RowMapper<ContactLabel> {

    @Override
    public ContactLabel map(JdbcClient.Row row) {
        return new ContactLabel(row.get("id", Long.class),
                                row.get("name", String.class));
    }
}

@Jdbc.Statement("SELECT ID AS id, NAME AS name FROM CONTACT WHERE ID = :id")
@Jdbc.RowMapper(ContactLabelMapper.class)
Optional<ContactLabel> findLabel(long id);
```

When the annotation names a mapper class, Helidon resolves that exact service
type. When the annotation does not name a class, Helidon obtains a
`JdbcClient.RowMapper<T>` service for the result type of the method. If several
mappers are available, the Service Registry selects the mapper with the highest
weight. The selection order is undefined when several mappers have the same
weight. Repository activation fails when no suitable mapper is available. See
[Injection Points](../injection/injection.md#injection-points) for more about
service selection.

During the callback, `Row.get` reads a required column and `Row.optional` reads
a nullable column. Both methods accept a column label or an index whose
numbering begins at one. Column label lookup ignores case and fails when the
requested label is missing or ambiguous. Access by index does not resolve
labels, so duplicate labels in other columns do not affect it. The row remains
valid only for the duration of the callback and only on the callback thread, so
the mapped result must contain all required values and must not depend on the
row or its JDBC resources.

A singleton repository can call its mapper concurrently, so the mapper needs
to be stateless or otherwise safe for concurrent calls. Outside a local
transaction, a runtime exception raised by the mapper passes through unchanged.
Inside a managed local transaction, the transaction provider can report a
`TxException` with the mapper exception as its cause.

## Return Generated Keys

An insert or update can return values generated by the database when its method
declares `@Jdbc.GeneratedKeys`. Without column names in the annotation, Helidon
asks the driver to return its default generated keys. Column names in the
annotation identify specific keys:

```java
@Jdbc.Statement("""
        INSERT INTO CONTACT (NAME, STATUS)
        VALUES (:name, :status)
        """)
@Jdbc.GeneratedKeys("ID")
long addContact(String name, String status);
```

Generated keys support the same return types and mapping choices as query
results. Requested column names are passed to the driver in declaration order.
Blank names and exact duplicates cause compilation to fail, while names that
differ only by case are preserved for the driver to resolve. Verify that each
supported database and driver returns the requested generated key columns and
preserves the expected handling of column names.

Outside a local transaction, the driver can commit an update before Helidon
finishes reading and validating its generated keys. A local transaction keeps
the update and key processing atomic. If key processing or cleanup fails, allow
the exception to leave the method that starts the transaction so Helidon can
roll back. Catching the exception still marks the transaction for rollback, but
continuing hides the original failure and cannot result in a commit. Such a
failure does not establish that a retry is safe.

## Result Handling and Resource Management

Helidon materializes the complete result before a repository method returns and
closes the result set and statement. Outside a local transaction, Helidon also
closes the connection used by the operation. A local transaction retains its
connection until the transaction completes. Repository methods do not return
streams, cursors, or iterators that expose JDBC resources.

Data JDBC does not impose a row limit on `List<T>`, so SQL should constrain
results that could become large.

## Repository Interface Inheritance

A JDBC repository does not need to extend another repository interface. It can
extend `Data.GenericRepository<E, ID>` to declare its entity and identifier
types. This inheritance does not add operations or change SQL and mapping
behavior:

```java
@SuppressWarnings(Api.SUPPRESS_INCUBATING)
@Data.Repository
@Data.Provider("jdbc")
interface ContactRepository extends Data.GenericRepository<Contact, Long> {

    @Jdbc.Statement("SELECT ID AS id, NAME AS name FROM CONTACT")
    List<Contact> findAll();
}
```

`Data.GenericRepository` is the only Helidon Data repository parent supported
by the JDBC provider. A JDBC repository that extends an interface such as
`Data.BasicRepository`, `Data.CrudRepository`, or `Data.PageableRepository`
fails during compilation. Those interfaces declare entity operations for which
the JDBC provider does not generate implementations.

Application interfaces can still contribute JDBC repository methods, including
generic methods whose types are resolved by the child repository. Every
inherited abstract method needs a `@Jdbc.Statement`, and its parameter and
result types must resolve to supported concrete types.

When several parent interfaces contribute the same method, Data JDBC uses the
most specific override. Compilation fails when unrelated parents declare
conflicting JDBC or transaction annotations for that method.

## Transactions

Repository methods can participate in
[local JDBC transactions](data-jdbc.md#local-transactions). A transaction
annotation on a repository method controls the propagation of that operation:

```java
@Tx.Required
@Jdbc.Statement("UPDATE CONTACT SET STATUS = :status WHERE ID = :id")
@Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
long updateStatus(long id, String status);
```

The Service Registry applies the transaction annotation when it invokes the
generated repository as a managed service. Method transaction annotations are
retained only in source. When a child repository is compiled separately from
its parent, the child must redeclare an inherited method and its transaction
annotation.

Data JDBC also recognizes a transaction annotation declared directly on the
repository type. Helidon does not inherit these annotations from parent
interfaces, resolve conflicting annotations, or define precedence between an
annotation on the repository type and one on a repository method.

An application service provides the clearest transaction boundary when several
repository or client operations must succeed or fail together. Placing the
transaction annotation on the service method also avoids the current
inheritance and precedence limitations.

Local transactions are intended for data operations rather than schema changes.
[Schema Management](data-jdbc.md#schema-management) explains how DDL fits into
an application that uses local transactions.
