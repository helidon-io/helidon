<!--@frontmatter
description: "Use Helidon Data JDBC with the declarative programming model"
-->
# Declarative Programming Model

## Overview

The Data JDBC declarative programming model represents database operations as
methods on an annotated Java interface. Each method describes its SQL,
parameters, return type, and mapping requirements. Helidon validates this
contract and generates the repository implementation during compilation.

Repositories work well when an application exposes a stable set of SQL
operations as an application API. Because generated repositories execute
through the public `JdbcClient` API, the same application can also use the
[imperative programming model](imperative.md) for SQL selected at runtime.

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
use unless the project acknowledges that status. The most focused option is
`@SuppressWarnings(Api.SUPPRESS_INCUBATING)`, which can be placed on each
package, type, or method that intentionally uses Data JDBC. The constant is
declared by `io.helidon.common.Api`, and the rest of the project retains the
default stability checks.

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
and `@Data.Provider("jdbc")`. Each abstract method then associates its Java
contract with one `@Jdbc.Statement`:

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
generator on the annotation processor path. Keeping it explicit makes the
provider choice clear and prevents conflicts when another generator is added
later. Helidon does not infer a provider from repository methods or annotations.

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

A value supplied to `@Jdbc.Client` must match an available client exactly. A
named client does not automatically become the default, even when it is the
only client configured. For example, a repository that uses the sole client
named `contacts` still declares `@Jdbc.Client("contacts")`. JDBC repositories
select clients with `@Jdbc.Client`, not `@Data.PersistenceUnit`.

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

Every method parameter corresponds to SQL in the statement, and a statement
uses either named markers or positional markers rather than mixing the two
forms. With positional markers, the number of Java parameters must equal the
number of markers. Collections are not expanded into an SQL `IN` list, so each
value needs its own declared marker.

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
| Primitive `int` or `long` without stronger evidence | Ambiguous. The method must declare `QUERY` or `UPDATE`. |

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

Joined or hierarchical results therefore begin as detached flat values. The
application can aggregate those values after the result has been materialized
or obtain related data through separate repository operations.

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
Other Java types can be stored in a portable representation and converted by
application code or a row mapper.

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
type. The marker form `@Jdbc.RowMapper` resolves the exact
`JdbcClient.RowMapper<T>` contract according to normal Service Registry
preference. Repository activation fails when no suitable mapper is available.

During the callback, `Row.get` reads a required column and `Row.optional` reads
a nullable column. Both methods accept a column label or an index whose
numbering begins at one. Column label lookup ignores case and fails when the
requested label is missing or ambiguous. Access by index does not resolve
labels, so duplicate labels in other columns do not affect it. The row remains
valid only for the duration of the callback and only on the callback thread, so
the mapped result must contain all required values and must not depend on the
row or its JDBC resources.

A singleton repository can call its mapper concurrently, so the mapper needs
to be stateless or otherwise safe for concurrent calls. A runtime exception
raised by the mapper reaches the repository operation boundary unchanged. If
the failure then crosses a managed local transaction boundary, the transaction
provider can report a `TxException` with the mapper exception as its cause.

## Return Generated Keys

An insert or update can return values generated by the database when its method
declares `@Jdbc.GeneratedKeys`. Without column names in the annotation, Helidon
requests the driver's default keys. Column names in the annotation identify
specific keys:

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
differ only by case are preserved for the driver to resolve. Since driver
support and handling of column names vary, this behavior should be verified
with every supported database and driver.

Outside a local transaction, the driver can commit an update before Helidon
finishes reading and validating its generated keys. A local transaction keeps
the update and key processing within the same success or failure boundary. An
exception from key processing or cleanup has to cross the transaction boundary
for Helidon to roll back. Such a failure does not establish that a retry is
safe.

## Result Handling and Resource Management

Helidon materializes the complete result before a repository method returns and
closes the result set and statement. Outside a local transaction, Helidon also
closes the operation's connection. A local transaction retains its connection
until the transaction completes. Repository methods do not return streams,
cursors, or iterators that expose JDBC resources.

Data JDBC does not impose a row limit on `List<T>`, so SQL should constrain
results that could become large.

## Repository Interface Inheritance

A JDBC repository does not need to extend another repository interface.
Extending `Data.GenericRepository<E, ID>` can provide useful entity and
identifier metadata, but it does not add operations or change SQL and mapping
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
repository type. This support is currently an interim bridge. Helidon copies
the annotation to the generated implementation, but it does not search parent
interfaces for type annotations or resolve competing annotations. It also does
not define precedence between a type annotation and a method annotation.
Because the annotation is placed on the generated implementation type, it can
also affect interception of generated constructors and lifecycle methods.

An application service provides the clearest transaction boundary when several
repository or client operations must succeed or fail together. Placing the
transaction annotation on the service method also avoids the current
inheritance and precedence limitations.

Local transactions are intended for data operations rather than schema changes.
[Schema Management](data-jdbc.md#schema-management) explains how DDL fits into
an application that uses local transactions.
