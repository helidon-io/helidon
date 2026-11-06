<!--@frontmatter
description: "Use Helidon Data JDBC with the declarative programming model"
-->
# Declarative Programming Model

## Overview

The Data JDBC declarative programming model represents database operations as methods on
an annotated Java interface. Each method describes its SQL, parameters, return type, and
mapping requirements. Helidon validates this contract and generates the repository
implementation during compilation.

Repositories work well when an application exposes a predefined set of SQL operations
through a repository API. Declarative repositories and the [imperative programming
model](imperative.md) can coexist in the same application. The imperative model is
useful for operations whose SQL is selected at runtime.

Repository development begins with the runtime dependency and JDBC driver described in
[Maven Coordinates](getting-started.md#dependencies). The application also needs a
configured JDBC client and an available database schema.

> [!NOTE]
> Helidon Data JDBC is an incubating feature. It is not production ready. Its
> APIs and behavior may change incompatibly or be removed in any release.

## Annotation Processing

Helidon generates repository implementations during compilation. The Maven compiler
configuration therefore includes the Helidon annotation processor bundle and the Data
JDBC code generator:

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

A project that manages the Maven compiler plugin through its parent can omit the plugin
`version`. A project that imports only `helidon-dependencies` still needs to define
`version.plugin.compiler` or replace the expression with a compatible plugin version.
The Helidon dependency BOM manages dependency versions, but it does not manage Maven
build plugins.

Because Data JDBC is incubating, the Helidon API stability processor rejects its use by
default. The focused suppression `@SuppressWarnings(Api.SUPPRESS_INCUBATING)` permits
intentional use on a package, type, or method. The constant is declared by
`io.helidon.common.Api`, and the rest of the project retains the default stability
checks.

A project that uses incubating APIs throughout can instead configure the compiler to
report them as warnings:

```xml [pom.xml]
<compilerArgs combine.children="append">
  <arg>-Ahelidon.api.incubating=warn</arg>
</compilerArgs>
```

The `warn` value reports each use during compilation. A value of `ignore` suppresses
those reports.

## Repository Definition

A JDBC repository begins with an interface annotated with `@Data.Repository` and
`@Data.Provider("jdbc")`. Each abstract repository method declares its SQL with one
`@Jdbc.Statement`:

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

Compilation generates the implementation and registers it with the Service Registry,
where the application can obtain and invoke it:

```java
ContactRepository repository = Services.get(ContactRepository.class);
Optional<Contact> contact = repository.findById(100);
```

`@Data.Provider("jdbc")` can be omitted when JDBC is the only Data code generator on the
annotation processor path. When more than one Data code generator is present, declare
`@Data.Provider("jdbc")` so that only Data JDBC generates the repository implementation.
Helidon does not infer a provider from repository methods or annotations.

### JDBC Client Selection

Without `@Jdbc.Client`, a repository resolves the client registered as `@default`. The
`ContactRepository` example selects the configured `contacts` client, while the
following repository selects `audit`:

```java
@Data.Repository
@Data.Provider("jdbc")
@Jdbc.Client("audit")
interface AuditRepository {
    // Declare repository methods here.
}
```

The value supplied to `@Jdbc.Client` must match the configured client name, including
its capitalization. Repository activation fails when that client is not available from
the Service Registry. A named client does not automatically become the default, even
when it is the only client configured. For example, a repository that uses the sole
client named `contacts` still declares `@Jdbc.Client("contacts")`. JDBC repositories
select clients with `@Jdbc.Client`, not `@Data.PersistenceUnit`.

Enable a health check on that client through its configuration. An application service
can invoke the registered check without adding a repository health method. See
[Invoke Checks in a Declarative Application](health-checks.md#invoke-checks-in-a-declarative-application).

## Repository Methods

The following annotations describe a JDBC repository and its methods:

| Annotation | Purpose |
|------------|---------|
| `@Jdbc.Client` | Selects the JDBC client managed by the Service Registry. |
| `@Jdbc.Statement` | Declares the SQL for a method. |
| `@Jdbc.Execution` | Selects `AUTO`, `QUERY`, or `UPDATE` execution. |
| `@Jdbc.GeneratedKeys` | Requests generated keys from an update. |
| `@Jdbc.RowMapper` | Selects a row mapper service supplied by the application. |
| `@Data.Param` | Associates a repository parameter with a named SQL marker. It belongs to the Helidon Data API, but currently applies only to repository methods using `@Jdbc.Statement`. `Data.Query` and Jakarta Persistence repository generation ignore it. |

Each method executes one SQL statement. The shared [SQL
guidelines](parameters.md#sql-safety-and-markers) explain safe binding and portable
marker parsing.

### Statement Parameters

Repository statements accept named or positional parameters. A named marker
matches the `@Data.Param` alias assigned to a parameter, or the Java parameter
name when no alias is present. Named markers can appear more than once:

```java
@Jdbc.Statement("""
        SELECT ID AS id, NAME AS name
        FROM CONTACT
        WHERE OWNER_ID = :ownerId OR DELEGATE_ID = :ownerId
        """)
List<Contact> findOwnedOrDelegated(long ownerId);
```

`@Data.Param` supplies a SQL name independently of the Java parameter name:

```java
@Jdbc.Statement("SELECT NAME FROM CONTACT WHERE ID = :id")
Optional<String> findName(@Data.Param("id") long contactId);
```

Here, `:id` binds to `contactId`. Alias names are case sensitive and must follow
Java identifier character rules, without whitespace or a colon prefix. Effective
names must be unique across all parameters, including parameters without aliases.

A method with any explicit or inherited alias requires named SQL markers.

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
> `@Data.Param` aliases are retained across separate compilation. Aliased
> parameters in inherited methods do not require `-parameters`. For named
> markers whose parameters have no aliases, compile the parent interface with
> `-parameters` or redeclare the method with explicit parameter names. Literal
> positional statements need no parameter-name metadata.

### Execution Types

Helidon selects execution from the method contract. See [Execution
Selection](queries.md#execution-selection).

### Return Types

Queries return one value, `Optional<T>`, or `List<T>`. See [Result
Cardinality](queries.md#result-cardinality).

## Result Mapping

Map scalars, records, or application values with [Result Mapping](mapping.md).

### Scalars and Records

Use column aliases to match record components. See [Scalars and
Records](mapping.md#scalars-and-records).

### Supported Scalar Types

See [Supported Scalar Types](mapping.md#supported-scalar-types) for supported values and
canonical JDBC null types.

### Custom Row Mapping

A mapper must return values independent of the row and its resources. See [Custom Row
Mapping](mapping.md#custom-row-mapping).

## Return Generated Keys

```java
@Jdbc.Statement("""
        INSERT INTO CONTACT (NAME, STATUS)
        VALUES (:name, :status)
        """)
@Jdbc.GeneratedKeys("ID")
long addContact(String name, String status);
```

See [Generated Keys](queries.md#generated-keys) for complete examples and transaction
consequences.

## Result Handling and Resource Management

Results are materialized before return. Helidon closes statements and result sets, while
a local transaction retains its connection. See [Resource
Ownership](transactions.md#resource-ownership).

## Repository Interface Inheritance

A JDBC repository does not need to extend another repository interface. It can extend
`Data.GenericRepository<E, ID>` to declare its entity and identifier types. This
inheritance does not add operations or change SQL and mapping behavior:

```java
@SuppressWarnings(Api.SUPPRESS_INCUBATING)
@Data.Repository
@Data.Provider("jdbc")
interface ContactRepository extends Data.GenericRepository<Contact, Long> {

    @Jdbc.Statement("SELECT ID AS id, NAME AS name FROM CONTACT")
    List<Contact> findAll();
}
```

`Data.GenericRepository` is the only Helidon Data repository parent supported by the
JDBC provider. A JDBC repository that extends an interface such as
`Data.BasicRepository`, `Data.CrudRepository`, or `Data.PageableRepository` fails during
compilation. Those interfaces declare entity operations for which the JDBC provider does
not generate implementations.

Application interfaces can still contribute JDBC repository methods, including generic
methods whose types are resolved by the child repository. Every inherited abstract
method needs a `@Jdbc.Statement`, and its parameter and result types must resolve to
supported concrete types.

When several parent interfaces contribute the same method, Data JDBC uses the most
specific override. Compilation fails when unrelated parents declare conflicting JDBC or
transaction annotations for that method.

## Transactions

Use a transaction annotation on a managed repository or application service. See
[Declarative Transactions](transactions.md#declarative-transactions).
