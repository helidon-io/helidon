<!--@frontmatter
description: "Use Helidon Data JDBC with the declarative programming model"
-->
# Declarative Programming Model

## Overview

In the Data JDBC declarative approach, application code defines database
operations as methods on an annotated Java interface. Each method specifies its
SQL, parameters, return type, and mapping requirements. Helidon validates the
contract and generates the repository implementation during compilation.

Generated repositories use the public `JdbcClient` API to execute their
operations, so an application can combine the declarative and imperative
programming models. A repository works well when a fixed set of SQL operations
forms an application API and validation during compilation is valuable. Use
[`JdbcClient`](imperative.md) when the application selects SQL at runtime.

Before defining a repository, add the runtime dependency and JDBC driver
described in [Maven Coordinates](README.md#maven-coordinates). Configure a JDBC
client and prepare the database schema.

> [!NOTE]
> Helidon Data JDBC is an incubating feature. It is not production ready. Its
> APIs and behavior may change incompatibly or be removed in any release.

## Annotation Processing

Add the Helidon annotation processor bundle and the Data JDBC code generator to
the Maven compiler configuration:

```xml [pom.xml]
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-compiler-plugin</artifactId>
  <configuration>
    <compilerArgs combine.children="append">
      <arg>-Ahelidon.api.incubating=warn</arg>
    </compilerArgs>
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

The Helidon API stability processor rejects Incubating API usage by default.
The `warn` setting allows Data JDBC usage and reports a compiler warning. To
retain the default policy elsewhere, apply
`@SuppressWarnings(Api.SUPPRESS_INCUBATING)` only to the package, type, or
method that uses Data JDBC. The constant is declared by
`io.helidon.common.Api`.

The imperative programming model does not require the Data JDBC code generator.

## Repository Definition

Annotate an interface with `@Data.Repository` and `@Data.Provider("jdbc")`.
Then add one `@Jdbc.Statement` to each abstract repository method:

```java
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

After compilation, obtain the generated implementation from the Service
Registry:

```java
ContactRepository repository = Services.get(ContactRepository.class);
Optional<Contact> contact = repository.findById(100);
```

You can omit `@Data.Provider("jdbc")` when JDBC is the only Data code generator
on the annotation processor path. Keep the annotation when the application
uses more than one Data provider.

### JDBC Client Selection

A repository uses the client named `@default` unless the repository interface
selects another configured client with `@Jdbc.Client`. The preceding
`ContactRepository` selects `contacts`; this repository selects `audit`:

```java
@Data.Repository
@Data.Provider("jdbc")
@Jdbc.Client("audit")
interface AuditRepository {
    // Repository methods
}
```

The selected client must exist. A missing named client does not fall back to
`@default`. Use `@Jdbc.Client`, rather than `@Data.PersistenceUnit`, for a JDBC
repository.

### Repository Interface Inheritance

A JDBC repository does not need a parent interface. You can extend
`Data.GenericRepository<E, ID>` when its entity and identifier types provide
useful application metadata:

```java
@Data.Repository
@Data.Provider("jdbc")
interface ContactRepository extends Data.GenericRepository<Contact, Long> {

    @Jdbc.Statement("SELECT ID AS id, NAME AS name FROM CONTACT")
    List<Contact> findAll();
}
```

`Data.GenericRepository` does not add operations or change SQL or mapping.
Other Helidon Data repository interfaces declare entity operations that the
JDBC provider does not generate.

Application-defined parent interfaces can contribute annotated methods,
including generic methods whose types the child resolves. Every inherited
abstract method needs `@Jdbc.Statement`, and every parameter and mapped result
must resolve to a concrete supported type.

## Repository Methods

Use these annotations to describe a JDBC repository and its methods:

| Annotation | Purpose |
|------------|---------|
| `@Jdbc.Client` | Selects the registry-managed JDBC client for the repository. |
| `@Jdbc.Statement` | Declares the SQL for a method. |
| `@Jdbc.Execution` | Selects `AUTO`, `QUERY`, or `UPDATE` execution. |
| `@Jdbc.GeneratedKeys` | Requests generated keys from an update. |
| `@Jdbc.RowMapper` | Selects an application-provided row mapper service. |

Each method executes one SQL statement. Follow the shared
[SQL guidelines](README.md#sql-guidelines) for safe binding and
portable marker parsing.

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

Use every method parameter in the SQL, and do not mix named and positional
markers. With positional markers, the number of method parameters must match
the number of markers. Data JDBC does not expand a collection into an SQL `IN`
list; declare each marker explicitly.

Parameters use the [supported scalar types](#supported-scalar-types). A
reference argument can be `null`; generated code binds an explicitly typed SQL
`NULL`. Primitive arguments cannot be null, and repository parameters cannot
use `Optional`.

Account for SQL null semantics in the statement. For example, the following
method omits its filter when `email` is null:

```java
@Jdbc.Statement("""
        SELECT ID AS id, NAME AS name
        FROM CONTACT
        WHERE (:email IS NULL OR EMAIL = :email)
        """)
List<Contact> findByOptionalEmail(String email);
```

> [!NOTE]
> Named markers in an inherited method require the original Java parameter
> names. Compile a separately published parent interface with `-parameters`, or
> use positional markers in that interface.

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
| Primitive `int` or `long` without stronger evidence | Ambiguous; declare `QUERY` or `UPDATE`. |

Primitive `int` and `long` can represent either a scalar query result or an
update count. Select the execution type for these methods:

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

Queries and generated-key operations support the following result types. `T`
is a supported scalar, record, or row-mapper result.

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
row or SQL `NULL` in the first column. Return a record with an optional
component when the application must distinguish row presence from column
nullability.

## Result Mapping

Data JDBC maps query results in one of three ways: it reads a scalar from the
first selected column, constructs a flat record from column labels, or calls a
row mapper supplied by the application.

Data JDBC maps each physical row independently. For a joined or hierarchical
result, return a flat detached value and aggregate the materialized values in
application code, or use separate repository operations.

### Scalars and Records

A required scalar fails when the first column contains SQL `NULL`. Use
`Optional<S>` for a nullable record component, where `S` is a supported scalar.

Helidon matches column labels to record component names without regard to case
or column order. Use SQL aliases to make the mapping explicit:

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

Record component names must be unique without regard to case. Each component
must be a supported scalar or `Optional` of a supported scalar. Use a row
mapper for nested records, mutable classes, and application-specific
conversions.

### Supported Scalar Types

| Family | Java types |
|--------|------------|
| Boolean and numeric | `boolean`/`Boolean`, `byte`/`Byte`, `short`/`Short`, `int`/`Integer`, `long`/`Long`, `float`/`Float`, `double`/`Double`, `BigDecimal` |
| Text and binary | `String`, `byte[]` |
| Java time | `LocalDate`, `LocalTime`, `LocalDateTime` |
| JDBC time | `java.sql.Date`, `java.sql.Time`, `java.sql.Timestamp` |

These types work as repository parameters, automatic results, generated keys,
record components, imperative bind values, and typed row reads. For another
Java type, store a portable representation and convert it in application code
or a row mapper.

Data JDBC does not map `OffsetTime` or `OffsetDateTime` implicitly because
databases do not preserve those values consistently. Store the local value and
offset separately, or store an ISO 8601 value in a character column and
reconstruct the value in a row mapper.

### Custom Row Mapping

Implement `JdbcClient.RowMapper<T>` when automatic mapping does not fit the
result. Register the mapper as a Service Registry service, then select it with
`@Jdbc.RowMapper`:

```java
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

The class-valued annotation selects that exact service type. The marker form
`@Jdbc.RowMapper` selects a service by the exact `JdbcClient.RowMapper<T>`
contract and follows normal Service Registry preference. Repository activation
fails if the requested mapper is unavailable.

Use `Row.get` for a required column and `Row.optional` for a nullable column.
Both methods accept a column label or a one-based column index. Read all values
before `map` returns. The row is valid only during the mapper call and only on
the callback thread. Return a non-null value that does not depend on the row or
its JDBC resources.

A mapper used by a singleton repository must be stateless or safe for
concurrent use. Helidon propagates application mapper exceptions unchanged.

## Return Generated Keys

Add `@Jdbc.GeneratedKeys` to an insert or update that returns values generated
by the database. With no column names, Helidon requests the driver's default
keys. To request specific columns, list them in the annotation:

```java
@Jdbc.Statement("""
        INSERT INTO CONTACT (NAME, STATUS)
        VALUES (:name, :status)
        """)
@Jdbc.GeneratedKeys("ID")
long addContact(String name, String status);
```

Generated keys support the same return types and mapping choices as queries.
Driver support and column-name handling vary, so test generated-key behavior
with your database and driver.

Outside a local transaction, the driver can commit an update before Helidon
finishes reading and validating its generated keys. Use a local transaction
when the update and key processing must succeed or fail together. Let failures
cross the transaction boundary so Helidon can roll back, and do not
automatically retry after key processing or cleanup fails.

## Result Handling and Resource Management

Helidon materializes the complete result before a repository method returns and
closes the result set and statement. Outside a local transaction, Helidon also
closes the operation's connection. A local transaction retains its connection
until the transaction completes. Repository methods do not return JDBC-backed
streams, cursors, or iterators.

Data JDBC does not impose a row limit on `List<T>`. Limit potentially large
results in SQL.

## Transactions

Repository methods can participate in
[local JDBC transactions](README.md#local-transactions). Annotate a repository
method when one operation needs a specific propagation mode. Annotate an
application service method when one transaction must contain several repository
or client operations.

```java
@Tx.Required
@Jdbc.Statement("UPDATE CONTACT SET STATUS = :status WHERE ID = :id")
@Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
long updateStatus(long id, String status);
```

Transaction annotations on repository methods are copied to the generated
service method. Because transaction annotations are retained only in source,
Helidon cannot recover an annotation declared only on a separately compiled
parent interface. Redeclare the method and its transaction annotation in the
child, or place the annotation on an application service method.

Keep DDL outside local transactions. See
[Schema Management](README.md#schema-management).
