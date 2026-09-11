<!--@frontmatter
description: "Define Helidon Data repositories that execute JDBC statements"
-->
# Declarative JDBC Repositories

## Overview

> [!NOTE]
> Helidon Data JDBC is an incubating feature. It is not production ready. Its
> APIs and behavior may change incompatibly or be removed in any release.

A declarative JDBC repository defines database operations as methods on an
annotated Java interface. Each method specifies its SQL, parameters, return
type, and mapping requirements. Helidon validates the contract and generates
the repository implementation during compilation.

Generated repositories use the public `JdbcClient` API to execute their
operations, so an application can combine declarative repositories with
imperative clients. A repository works well when a fixed set of SQL operations
forms an application API and validation during compilation is valuable. Use
[`JdbcClient`](imperative.md) for operations that fit naturally into application
control flow or when the application selects SQL at runtime.

Before defining a repository, add the runtime dependency and JDBC driver
described in [Helidon Data JDBC Provider](README.md#maven-coordinates).
Configure at least one JDBC client and prepare the database schema.

## Annotation Processing

Declarative repositories require the JDBC code generator. Add the Helidon
annotation processor bundle and the JDBC code generator to the compiler
configuration:

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
Set the Incubating policy to `warn` to allow the application to use Data JDBC
while the compiler continues to display a warning. To keep the default policy
for the rest of the application, apply
`@SuppressWarnings(Api.SUPPRESS_INCUBATING)` only to the package, type, or
method that uses Data JDBC. The constant is declared by
`io.helidon.common.Api`. Use `-Ahelidon.api.incubating=ignore` only when the
build intentionally disables all Incubating API diagnostics.

The JDBC code generator is required only for declarative applications.

## Repository Definition

Annotate an interface with `@Data.Repository`. Add `@Data.Provider("jdbc")` to
select the JDBC code generator explicitly, and declare one `@Jdbc.Statement`
for each abstract repository method:

```java
@Data.Repository
@Data.Provider("jdbc")
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

After compilation, the Service Registry provides the generated implementation
as a Java service:

```java
ContactRepository repository = Services.get(ContactRepository.class);
Optional<Contact> contact = repository.findById(100);
```

If JDBC is the only Data code generator on the annotation processor path, you
can omit `@Data.Provider("jdbc")`. Keep the annotation when more than one
provider is available, because Helidon does not select a provider from
annotations on methods.

### JDBC Client Selection

A repository uses the client named `@default` unless the interface selects a
different configured client. For example, the following repository uses the
client named `audit`:

```java
@Data.Repository
@Data.Provider("jdbc")
@Jdbc.Client("audit")
interface AuditRepository {
    // Repository methods
}
```

The selected client must exist. Helidon does not fall back to `@default` when
a named client is unavailable. Use `@Jdbc.Client`, not
`@Data.PersistenceUnit`, to select a client for a JDBC repository.

### Repository Interface Inheritance

A JDBC repository does not need a parent interface. It can extend
`Data.GenericRepository<E, ID>` when the entity and identifier types provide
useful application metadata:

```java
@Data.Repository
@Data.Provider("jdbc")
interface ContactRepository extends Data.GenericRepository<Contact, Long> {

    @Jdbc.Statement("SELECT ID AS id, NAME AS name FROM CONTACT")
    List<Contact> findAll();
}
```

`Data.GenericRepository` does not add operations or change SQL and row
mapping. The JDBC provider does not generate entity operations, so do not
extend `Data.BasicRepository`, `Data.CrudRepository`, or
`Data.PageableRepository`.

Parent interfaces defined by the application can contribute annotated methods,
including generic contracts whose types the child repository resolves. Every
inherited abstract operation still needs `@Jdbc.Statement`.

## Repository Methods

The following annotations describe a JDBC repository and its operations:

| Annotation | Purpose |
|------------|---------|
| `@Jdbc.Client` | Selects the JDBC client managed by the Service Registry for the repository. |
| `@Jdbc.Statement` | Declares the SQL for a method. |
| `@Jdbc.Execution` | Selects `AUTO`, `QUERY`, or `UPDATE` execution. |
| `@Jdbc.GeneratedKeys` | Requests generated keys from an update. |
| `@Jdbc.RowMapper` | Selects a row mapper service that the application provides. |

Each method executes exactly one SQL statement. Use repository statements for
queries and data changes, and follow the [SQL Guidelines](README.md#sql-guidelines)
for portable marker parsing and safe parameter handling. For DDL, follow
[Schema Management](README.md#schema-management).

Before Helidon generates the repository, every parameter and mapped result must
resolve to a concrete type. A generic parent interface is valid when the child
repository resolves its type variables to supported types.

## Statement Parameters

Repository statements support either named or positional parameters. Named
markers use the Java parameter name and can occur more than once. The following
method binds `ownerId` to both JDBC positions:

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
markers in one statement. With positional markers, the number of method
parameters must equal the number of markers. Data JDBC does not expand a
collection into an SQL `IN` list. Declare the required markers explicitly or
provide methods for the supported list sizes.

Each parameter must use one of the [supported scalar types](#supported-scalar-types).
A reference argument can be `null`. Generated code binds it as an explicitly
typed SQL `NULL`. Primitive arguments cannot be null, and repository parameters
cannot use `Optional`.

An equality predicate does not match SQL `NULL`. Express the required behavior
in the statement. For example, the following method omits the filter when
`email` is null:

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
> names. Compile a parent interface published as a separate dependency with
> `-parameters`, or use positional markers in that interface.

## Execution Types

`@Jdbc.Execution` defaults to `AUTO`. Helidon infers the operation from the
Java method contract and JDBC annotations. It does not inspect SQL keywords.

| Method contract | Inferred execution |
|-----------------|--------------------|
| `@Jdbc.GeneratedKeys` | Update that maps generated keys |
| `void` | Update |
| `Optional<T>` or `List<T>` | Query |
| Supported record or scalar other than primitive `int` or `long` | Query |
| `@Jdbc.RowMapper` without generated keys | Query |
| Primitive `int` or `long` without stronger evidence | Ambiguous. Declare `QUERY` or `UPDATE`. |

Primitive `int` and `long` can represent either a scalar query result or an
update count. Select the execution type explicitly for those methods:

```java
@Jdbc.Statement("SELECT COUNT(*) FROM CONTACT")
@Jdbc.Execution(Jdbc.ExecutionType.QUERY)
long count();

@Jdbc.Statement("DELETE FROM CONTACT WHERE ID = :id")
@Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
long deleteById(long id);
```

An explicit execution type must agree with the annotations and return type.
Helidon reports incompatible or ambiguous contracts during compilation.

## Return Types

Query methods and methods that return generated keys support the following
result types. Here, `T` is a supported scalar, a supported record, or the
concrete type produced by a row mapper.

| Return type | Behavior |
|-------------|----------|
| `T` | Requires exactly one row. |
| `Optional<T>` | Accepts zero or one row. |
| `List<T>` | Returns all rows as an immutable list in JDBC encounter order. |

A method returning `T` throws `NoResultException` when the operation returns no
row. Both `T` and `Optional<T>` throw `NonUniqueResultException` when the
operation returns more than one row.

An ordinary update can return `void`, primitive `int`, or primitive `long`.
`void` discards the update count. A `long` preserves the count reported by the
driver. An `int` method fails if the count cannot be represented as an `int`.

`List<T>` is the only collection type that a repository can return directly. To
represent a map, set, or another collection produced from one row, return a
type defined by the application that contains the value and map that type
explicitly:

```java
record Attributes(Map<String, String> values) {
}

@Jdbc.Statement("SELECT ATTRIBUTES_JSON FROM ITEM WHERE ID = ?")
@Jdbc.RowMapper(AttributesMapper.class)
Attributes attributes(long id);
```

For an automatically mapped scalar, `Optional.empty()` represents either no
row or SQL `NULL` in the first column. Return a record with an optional
component when the application must distinguish row presence from column
nullability.

## Result Mapping

Choose the simplest mapping that represents the query result. Data JDBC maps a
supported scalar from the first selected column, constructs a flat record from
column labels, or delegates to a row mapper that the application provides.

Data JDBC maps every physical JDBC row independently. It does not use
identifiers to merge rows, deduplicate parent objects, construct entity
relationships, reduce joined rows into an object graph, or collect child rows
into nested collections. Consequently, `List<T>` contains one mapped value for
each physical row returned by the database.

For joined or hierarchical results, return a flat record or another explicitly
mapped detached value and aggregate the materialized results in application
code. Alternatively, issue separate repository operations. A
`JdbcClient.RowMapper` is invoked once for each physical row and cannot
aggregate across rows or retain the callback-scoped `Row`.

### Scalars and Records

A required scalar fails when the first column contains SQL `NULL`. A record can
use `Optional<S>` for a nullable component, where `S` is a supported scalar.

For records, Helidon matches column labels to record component names without
regard to case or column order. Use SQL aliases to make the mapping explicit
and independent of physical column names:

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
must be a supported scalar or `Optional` of a supported scalar. Automatic
mapping does not construct nested records or mutable classes. Use a row mapper
for those results.

### Supported Scalar Types

| Family | Java types |
|--------|------------|
| Boolean and numeric | `boolean`/`Boolean`, `byte`/`Byte`, `short`/`Short`, `int`/`Integer`, `long`/`Long`, `float`/`Float`, `double`/`Double`, `BigDecimal` |
| Text and binary | `String`, `byte[]` |
| Java time | `LocalDate`, `LocalTime`, `LocalDateTime` |
| JDBC time | `java.sql.Date`, `java.sql.Time`, `java.sql.Timestamp` |

These types work as repository parameters, automatic results, generated keys,
record components, imperative bind values, and typed row reads. For another
Java type, store a portable representation using supported scalar columns and
convert it in application code or a row mapper.

In particular, Data JDBC does not map `OffsetTime` or `OffsetDateTime`
implicitly because supported databases do not preserve those values
consistently. Store the local value and offset separately, or store an ISO 8601
value in a character column, and reconstruct the value in a row mapper.

### Row Mappers

Implement `JdbcClient.RowMapper<T>` when automatic scalar or record mapping
does not fit the result. Register each mapper used by a repository as a Service
Registry service.

Specify a mapper class in `@Jdbc.RowMapper` to select a particular mapper
service:

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

The mapper class must be concrete, implement `JdbcClient.RowMapper<T>` for the
exact mapped type, and be accessible to generated code. A nested mapper must be
static. If `@Jdbc.RowMapper` does not name a class, Helidon selects a mapper by
its exact `JdbcClient.RowMapper<T>` service contract.

Mapper services are resolved when the generated repository is activated. The
marker form `@Jdbc.RowMapper` uses normal Service Registry selection for the
exact `JdbcClient.RowMapper<T>` service contract. When several services match,
Service Registry preference, including `@Weight`, determines the selected
mapper. Normal deterministic Service Registry tie-breaking applies when
matching services have equal preference.

If no matching service exists, repository activation fails. If the preferred
service cannot be activated, Helidon reports that activation failure and does
not fall back to a lower-preference mapper.

The class-valued form `@Jdbc.RowMapper(MyMapper.class)` requests that exact
concrete service type, regardless of other mapper services for the same result
type. The requested mapper must be registered and activatable. Use the
class-valued form when repository behavior must not depend on Service Registry
preference.

Use `Row.get` for a required column and `Row.optional` for a nullable column.
Both methods accept a column label or a column index starting at 1. Read every
value before `map` returns. The returned value must not be null or depend on the
row. The row is valid only during the `map` call and only on the thread that
invoked the mapper. A mapper held by a singleton repository must be stateless
or safe for concurrent use.

If a mapper provided by the application throws an exception, Helidon returns
that exception to the caller without changing it. JDBC and provider mapping
failures use the Helidon Data exception model. See
[Errors and JDBC Warnings](README.md#errors-and-jdbc-warnings) for the JDBC
diagnostic policy.

## Return Generated Keys

Add `@Jdbc.GeneratedKeys` to an insert or update that returns values generated
by the database. If you do not specify column names, Helidon requests the
driver's default generated keys. To request specific columns, list their names
in the annotation:

```java
@Jdbc.Statement("""
        INSERT INTO CONTACT (NAME, STATUS)
        VALUES (:name, :status)
        """)
@Jdbc.GeneratedKeys("ID")
long addContact(String name, String status);
```

Generated keys support the same `T`, `Optional<T>`, and `List<T>` result types
and mapping choices as queries. Support for generated keys and the way column
names are interpreted vary by JDBC driver.

Outside a local transaction, the JDBC driver can commit the update automatically
before Helidon finishes reading, mapping, and validating the generated keys. If
all these steps must succeed or fail together, invoke the repository method
inside a local transaction. Let any failure propagate beyond the transaction
boundary so Helidon can roll back the transaction. Do not automatically retry
the update if generated key processing or cleanup later fails, because the
driver might already have committed it.

## Result Handling and Resource Management

Helidon reads the complete repository result before the method returns. It
always closes the result set and statement. It also closes any connection that
it acquired for the operation. Repository methods therefore do not return a
stream, cursor, or iterator that remains connected to JDBC resources.

Data JDBC does not impose a row limit on `List<T>`. When a query can return many
rows, limit the result in SQL. Use deterministic ordering when paging through
results.

## Transactions

Repository methods can run within the
[local JDBC transactions](README.md#local-transactions) managed by Helidon.
Annotate a repository method when that single operation needs a specific
propagation mode. Annotate an application service method when one transaction
must contain several repository or client operations.

```java
@Tx.Required
@Jdbc.Statement("UPDATE CONTACT SET STATUS = :status WHERE ID = :id")
@Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
long updateStatus(long id, String status);
```

A transaction annotation declared directly on the repository interface is
copied to the generated service class. The annotation can therefore affect
service creation as well as repository method calls. Prefer annotations on
methods or application services when operations need different policies. The
precedence of interface and method annotations is not currently defined.

Transaction annotations are retained only in source code. If a child repository
is compiled against a parent class file, Helidon cannot recover an annotation
declared only on the parent. Redeclare the complete method and its transaction
annotation in the child, or place the transaction annotation on an application
service method compiled with the repository.

Do not run DDL within a repository transaction. See
[Schema Management](README.md#schema-management) for details.
