<!--@frontmatter
description: "Define Helidon Data repositories that execute JDBC statements"
navigation:
  icon: i-lucide-files
-->
# Declarative JDBC Repositories

## Overview

A declarative repository lets you describe JDBC operations on a Java interface.
You provide the SQL and the Java method contract, and Helidon generates the
implementation during compilation. The generated code uses the JDBC API to
execute the statement, bind method arguments, and map the result.

Repository generation gives you an early check on the contract. If a statement,
parameter, return type, or mapping is not supported, the application fails to
compile instead of discovering the problem on its first database call.

Add the runtime and annotation processor dependencies described in
[Helidon Data with JDBC](README.md#maven-coordinates).

## Usage

Start with an interface annotated with `@Data.Repository` and select the JDBC
provider. Each abstract method describes one database operation and supplies
its SQL in `@Jdbc.Statement`:

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

After compilation, obtain the generated implementation from the Service
Registry and call it like any other Java service:

```java
ContactRepository repository = Services.get(ContactRepository.class);
Optional<Contact> contact = repository.findById(100);
```

If JDBC is the only Data code generator on the annotation processor path, you
can omit `@Data.Provider("jdbc")`. Keeping it on the interface makes the choice
clear and avoids ambiguity when the application later adds another generator.
Helidon does not choose a provider from the annotations on individual methods.

Repositories use the JDBC client named `@default` unless you select another
configured client. For example, add `@Jdbc.Client("audit")` to the repository
when its operations belong in the audit database.

### Repository Parent Interfaces

A JDBC repository does not need to extend another interface. If the application
benefits from shared entity and identifier types, it can extend
`Data.GenericRepository<E, ID>` directly or through a parent interface defined
by the application. `Data.GenericRepository` contributes only that type
information. It does not add database operations, create SQL, or change how
rows are mapped.

```java
@Data.Repository
@Data.Provider("jdbc")
interface ContactRepository extends Data.GenericRepository<Contact, Long> {

    @Jdbc.Statement("SELECT ID AS id, NAME AS name FROM CONTACT")
    List<Contact> findAll();
}
```

Every abstract operation still needs its own `@Jdbc.Statement`. Do not extend
`Data.BasicRepository`, `Data.CrudRepository`, or `Data.PageableRepository`.
Those interfaces expect generated entity operations, which the JDBC provider
does not create, so Helidon rejects them during compilation.

## Repository Methods

The annotations on a repository method answer a few practical questions: which
client should run the operation, what SQL should it execute, and how should it
handle the result?

| Annotation | Purpose |
|------------|---------|
| `@Jdbc.Client` | Selects a JDBC client managed by the Service Registry. |
| `@Jdbc.Statement` | Declares the SQL for a method. |
| `@Jdbc.Execution` | Selects `AUTO`, `QUERY`, or `UPDATE`. |
| `@Jdbc.GeneratedKeys` | Requests generated keys from an update. |
| `@Jdbc.RowMapper` | Selects a row mapper service provided by the application. |

Each method executes one SQL statement. Do not place several statements or an
SQL script in `@Jdbc.Statement`. Batch execution, stored procedures, and
callable statements are not supported.

In most cases, you can leave `@Jdbc.Execution` at its default value of `AUTO`.
Helidon decides whether to run a query or an update from the Java method
contract, not from the first word in the SQL:

| Method contract | Execution |
|-----------------|-----------|
| `@Jdbc.GeneratedKeys` | Update with generated key mapping |
| `void` | Update |
| `Optional<T>`, `List<T>`, a supported record, or a scalar other than primitive `int` or `long` | Query |
| `@Jdbc.RowMapper` without generated keys | Query |
| Primitive `int` or `long` without stronger evidence | Specify `QUERY` or `UPDATE` |

Primitive `int` and `long` can represent either a scalar query result or an
update count. Set the execution type explicitly when the rest of the method
does not make that choice clear. The declared type must agree with the method
annotations and return value, or Helidon reports a compilation error.

### Return Types

| Return type | Behavior |
|-------------|----------|
| `T` | Requires exactly one row. |
| `Optional<T>` | Allows zero or one row. |
| `List<T>` | Returns an immutable list in encounter order. |
| `void` | Executes an update and discards the count. |
| `int` or `long` | Returns the update count. |

Choose a return type that describes how many rows the application expects.
A method returning `T` throws `NoResultException` if the query finds no row.
Both `T` and `Optional<T>` throw `NonUniqueResultException` if the query finds
more than one row.

For an optional scalar, `Optional.empty()` can mean that the query found no row
or that the first column contained SQL `NULL`. If the application needs to tell
those cases apart, return a record with an optional component instead.

### Generated Keys

Add `@Jdbc.GeneratedKeys` when an insert or update needs to return values
generated by the database. With no column names, the annotation requests the
driver's default generated keys. You can also name the generated columns that
you need:

```java
@Jdbc.Statement("""
        INSERT INTO CONTACT (NAME, STATUS)
        VALUES (:name, :status)
        """)
@Jdbc.GeneratedKeys("ID")
long addContact(String name, String status);
```

The method can return generated keys as `T`, `Optional<T>`, or `List<T>`.
Whether a driver returns generated keys, and how it interprets column names,
depends on that driver.

## Statement Parameters

Repository statements support named and positional parameters. Named markers
refer to Java parameter names and can appear more than once in the SQL. In this
example, Helidon binds `ownerId` in both places:

```java
@Jdbc.Statement("""
        SELECT ID AS id, NAME AS name
        FROM CONTACT
        WHERE OWNER_ID = :ownerId OR DELEGATE_ID = :ownerId
        """)
List<Contact> findOwnedOrDelegated(long ownerId);
```

Positional `?` markers are useful when parameter names are not available. They
bind in the order of the Java method declaration:

```java
@Jdbc.Statement("UPDATE CONTACT SET STATUS = ? WHERE ID = ?")
@Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
long updateStatus(String status, long id);
```

Use every method parameter in the statement, and choose either named or
positional markers for a method. Helidon does not expand a collection into an
`IN` list. If the application supports a few known list sizes, provide the
required number of markers or separate methods for those cases. See
[Portable SQL](README.md#portable-sql) for syntax considerations across
databases.

A reference argument can be `null`. Helidon binds it as an explicitly typed SQL
`NULL`. Primitive arguments cannot be null, and repository parameters cannot
use `Optional`. Remember that binding SQL `NULL` does not change the behavior
of an SQL predicate. Write any optional filtering logic in the statement.

### Current Limitation: Parameter Names in Compiled Parent Interfaces

An inherited repository method can use named markers only if its compiled parent
interface retains the original Java parameter names. When publishing a parent
interface for other projects, compile it with `-parameters`:

```xml [pom.xml]
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <parameters>true</parameters>
    </configuration>
</plugin>
```

If you cannot change how the parent was compiled, use positional markers in its
repository methods. Other inheritance behavior continues to work across
compiled dependencies, including the resolution of generic types.

## Result Mapping

Choose the simplest mapping that represents the result. Data JDBC can read a
single scalar value, populate a flat Java record, or call a row mapper supplied
by the application.

### Scalars and Records

A scalar return value comes from the first selected column. If that return value
is required, the column cannot contain SQL `NULL`.

For a flat record, Helidon matches result column labels to record component
names without regard to case or column order. SQL aliases make that relationship
easy to see and keep it independent of the physical column names:

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

Each record component can be a supported scalar type or `Optional<S>`, where
`S` is a supported scalar. Automatic mapping does not cover nested records,
collections, or mutable classes. Use a row mapper when a result needs one of
those shapes.

### Supported Scalar Types

| Family | Java types |
|--------|------------|
| Boolean and numeric | `boolean`/`Boolean`, `byte`/`Byte`, `short`/`Short`, `int`/`Integer`, `long`/`Long`, `float`/`Float`, `double`/`Double`, `BigDecimal` |
| Text and binary | `String`, `byte[]` |
| Java time | `LocalDate`, `LocalTime`, `LocalDateTime` |
| JDBC time | `java.sql.Date`, `java.sql.Time`, `java.sql.Timestamp` |

The types in this table work as parameters, automatic results, generated keys,
record components, and typed row reads. For another Java type, convert the
database value to one of these types or provide a row mapper.

### Current Limitation: Offset Date and Time Types

Data JDBC does not support `OffsetTime` or `OffsetDateTime` as parameters,
automatic results, generated keys, record components, imperative bind values,
or typed row reads. JDBC drivers do not represent these values consistently
across the supported databases.

An application can store the local value and offset in separate columns. It can
also store the complete ISO 8601 value in a character column. Reconstruct the
offset value in application code or in a row mapper.

### Row Mappers

For a result that cannot be mapped automatically, implement
`JdbcClient.RowMapper<T>`. The mapper can read supported scalar values by column
label or by an index that starts at one. It must return a non-null application
value that does not depend on the JDBC row after the callback finishes:

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

Register a mapper used by a repository as a service. Call `Row.get` for a column
that must contain a value, or `Row.optional` when SQL `NULL` should become
`Optional.empty()`. Read everything the result needs before returning from
`map`. The `Row` is valid only during that call and must not be stored. If the
mapper throws an exception, Helidon passes that exception back to the caller.

## Result Materialization

When a repository method returns, its result is fully available and the result
set, statement, and connection opened for the operation are already closed.
For this reason, repository methods do not expose streams, iterators, or cursors
that remain connected to JDBC resources.

Helidon does not impose a row limit on `List<T>`. Add an appropriate limit to
the SQL when a query could return many rows, and use deterministic ordering when
paging through a result.

## Transactions

To include a repository operation in a local JDBC transaction, put the required
transaction annotation directly on that repository method. The general
transaction behavior and supported propagation modes are described in
[Local Transactions](README.md#local-transactions).

### Current Limitation: Transaction Annotations on Repository Types

Do not put a transaction annotation on the repository interface. Helidon
currently copies that annotation to the generated service class, where it can
affect construction of the repository as well as calls to its methods. For
example, `@Tx.Mandatory` can prevent the first repository lookup outside a
transaction. `@Tx.Required` or `@Tx.New` can start a transaction merely to
construct the service.

An annotation on the repository type does not act as a method default, and its
precedence relative to a method annotation is not defined. Annotate each method
that needs transaction propagation instead.

### Current Limitation: Transaction Annotations in Compiled Parent Interfaces

Transaction annotations are available only from source during repository
generation. If a child repository is compiled against a parent class file,
Helidon cannot recover an annotation declared only on the parent method or
interface.

Redeclare the complete method and its transaction annotation on the child
repository. Another option is to put the transaction boundary on an application
service method that Helidon compiles directly. The `-parameters` compiler option
preserves parameter names, but it does not preserve transaction annotations for
repository generation.
