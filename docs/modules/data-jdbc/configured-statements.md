<!--@frontmatter
description: "Load SQL from application configuration for repositories and JDBC clients"
-->
# SQL from Configuration

Define SQL at any level of the configuration tree. Declarative repositories reference
the key in `@Jdbc.Statement`. Imperative applications read the SQL with `Config` and
prepare a reusable statement supplier.

Configured SQL supports materialized queries, updates, and generated keys. The existing
[execution contracts](queries.md), [result mapping](mapping.md), and
[transaction rules](transactions.md) apply. Configured statements do not support
streaming, stored procedure calls, or batch processing.

## Application Configuration

SQL can appear at the root or any nested path, including inside a client entry under
`data.clients.jdbc`. The `application` prefix in these examples is a naming choice,
not a required namespace:

```yaml [application.yaml]
application:
  contacts:
    find-by-id: |-
      SELECT ID AS id, NAME AS name
      FROM CONTACT
      WHERE ID = :id
    rename: |-
      UPDATE CONTACT SET NAME = :name WHERE ID = :id
    insert: |-
      INSERT INTO CONTACT (NAME) VALUES (:name)
```

The YAML `|-` form preserves internal line breaks and strips trailing line breaks.
Each SQL value must be a nonblank string.

For example, SQL stored under `sql.find-by-id` in the first `data.clients.jdbc` entry
has the full key `data.clients.jdbc.0.sql.find-by-id`. Reference it with
`@Jdbc.Statement("${data.clients.jdbc.0.sql.find-by-id}")` or read it imperatively with
`config.get("data.clients.jdbc.0.sql.find-by-id").asString().get()` from the root Config.
The `0` is the entry's list index, not its client name. Here, `sql` is an application
key. Its location does not select a client; repositories still use `@Jdbc.Client`, and
imperative code uses its chosen `JdbcClient`.

Connection settings under `data.clients.jdbc` follow the structure described in
[Clients and Data Sources](configuration.md).

## Declarative Repositories

Use one complete configuration reference as the `@Jdbc.Statement` value:

```java
@SuppressWarnings(Api.SUPPRESS_INCUBATING)
@Data.Repository
@Data.Provider("jdbc")
@Jdbc.Client("contacts")
interface ContactRepository {

    @Jdbc.Statement("${application.contacts.find-by-id}")
    Optional<Contact> findById(long id);

    @Jdbc.Statement("${application.contacts.rename}")
    @Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
    long rename(String name, long id);

    @Jdbc.Statement("${application.contacts.insert}")
    @Jdbc.GeneratedKeys("ID")
    long insert(String name);
}

record Contact(long id, String name) {
}
```

The repository uses the `contacts` client and reads SQL from the root `Config` in the
same Service Registry. Helidon resolves and validates SQL once, when the repository is
activated. This may happen after application startup. Each method keeps an immutable
SQL definition and creates a new statement with independent bindings for each call.

Named markers match a parameter's `@Data.Param` alias, or its Java name when no alias is
present. Repeated markers receive the same argument at every occurrence. Methods
without aliases can instead use positional SQL, with one marker per parameter in
declaration order. For example, replace the `rename` value without recompiling:

```yaml [application.yaml]
application:
  contacts:
    rename: |-
      UPDATE CONTACT SET NAME = ? WHERE ID = ?
```

The first marker receives `name` and the second receives `id`. An explicit or inherited
`@Data.Param` alias requires named SQL for the whole method.

For reference parameters, Java `null` binds typed SQL `NULL` using the declared scalar
type. See [Declarative Binding](parameters.md#declarative-binding) for aliases, inherited
contracts, and null predicates.

### Optional Defaults

Supply a default after the colon to use when the key is missing:

```java
@Jdbc.Statement("""
        ${application.contacts.find-by-id:
        SELECT ID AS id, NAME AS name
        FROM CONTACT
        WHERE ID = :id
        }
        """)
Optional<Contact> findById(long id);
```

The first colon separates the key from its default. The final closing brace ends the
reference. Defaults can span lines and contain colons and interior braces. Configured
values pass through normal Config filters. JDBC performs no further expression
resolution or unescaping.

A default must be nonblank and match the method's input contract. A missing key without
a default fails activation. A present blank, object, or list value fails activation even
when a default is supplied.

Keys must be nonblank and contain no braces. The annotation accepts one complete
reference, with no nested or multiple `${...}` references. If SQL needs a literal `${`
sequence, store it in configuration instead of the annotation default. Do not combine
literal SQL and configuration references in one annotation value.

## Imperative Applications

Read SQL with the ordinary Config API, then pass it to `prepare`. This example uses an
application root `Config config` and a `JdbcClient jdbcClient`:

```java
String sql = config.get("application.contacts.find-by-id").asString().get();
Supplier<JdbcClient.Statement> findById = jdbcClient.prepare(sql);

Optional<Contact> contact = findById.get()
        .bind("id", 100L)
        .map(row -> new Contact(row.get("id", Long.class),
                                row.get("name", String.class)))
        .optional();
```

Import `Supplier` from `java.util.function`, and `List` and `Optional` from `java.util`.
`prepare(sql)` accepts named, positional, or marker-free SQL. Bind named markers with
`bind(name, value)` and positional markers with `bind(index, value)`. JDBC indexes start
at one. JDBC receives resolved SQL. It does not interpret `${...}` references or retain
the `Config` object.

To declare the expected inputs, pass their names in order to `prepare(sql, inputNames)`:

```java
String sql = config.get("application.contacts.rename").asString()
        .orElse("UPDATE CONTACT SET NAME = ? WHERE ID = ?");
Supplier<JdbcClient.Statement> rename = jdbcClient.prepare(sql, List.of("name", "id"));

long updated = rename.get()
        .bind("id", 100L)
        .bind("name", "Updated name")
        .execute();
```

This overload checks the exact set of named inputs or the positional marker count.
The input list works with both the named SQL in YAML and the positional fallback. For
positional SQL, `name` identifies position one and `id` identifies position two.

Input names must be unique and follow Java identifier character rules. Helidon copies
the list. Indexed bindings always address physical JDBC positions, including individual
occurrences of repeated named markers.

Prepare once and retain the supplier. Each `prepare(...)` call scans SQL. Each supplier
call creates a new statement without another scan, configuration lookup, or JDBC work.
The supplier holds the definition, and the client retains no history of definitions.
These plans do not use the [parameter count cache](configuration.md#parameter-count-cache).

Imperative bindings require nonnull scalar values. The imperative API has no typed SQL
NULL binding, so express null handling in SQL, as described in
[Imperative Binding](parameters.md#imperative-binding). `JdbcClient.create(sql)` remains
available for positional statements without a reusable definition.

## Binding and Validation

A statement must not mix named and positional markers. Marker recognition follows the
[SQL safety rules](parameters.md#sql-safety-and-markers).
Markers inside supported strings, quoted identifiers, and comments do not create inputs.

| SQL style | Repository or ordered input contract |
| --- | --- |
| Named | Marker names must match the input names exactly. Markers can repeat. |
| Positional | One marker per input, in method declaration or input list order. Repository methods with aliases require named SQL. |
| No markers | No method parameters, or an empty input list. |

Without an input list, `prepare(sql)` derives its inputs from SQL. Assign each physical
JDBC position once, directly or through named binding. Unknown names, invalid positions,
and duplicate assignments fail during binding. A named binding validates every matching
position before assigning any. Missing bindings fail before the operation acquires a
connection.

| Phase | Checks |
| --- | --- |
| Repository compilation | Checks reference syntax, parameter types and reliable names, aliases, return and execution contracts, and markers in inline defaults. Reads no deployment configuration or database metadata. |
| Repository activation or imperative preparation | Checks for nonblank SQL, valid marker syntax and style, and matching input names or counts. Planning acquires no JDBC resources. |
| Binding and execution | Checks complete bindings before acquisition. The database validates SQL grammar and schema. Existing mapping, cardinality, and generated key checks apply. |

Repository activation may activate a client or data source before validating SQL. A
failure in that setup can therefore occur first.

## Lifetime and Configuration Changes

A prepared definition belongs to one client and keeps its resolved SQL. Each repository
instance keeps its own method definitions. Definitions do not share mutable binding
state across clients or Service Registries.

Suppliers are safe to share. Use each statement for one execution and do not mutate it
concurrently. Suppliers hold no JDBC resources or transaction context. The terminal
method uses the transaction active at execution and follows existing
[resource ownership rules](transactions.md#resource-ownership).

Existing definitions keep their original SQL. To adopt configuration changes, load the
new configuration and prepare a new supplier, or create a new repository and Service
Registry. Restarting the application also adopts the changes. The client can be reused
if its connection source and settings are unchanged.

Keep replacement SQL compatible with the method's inputs, scalar types, execution kind,
mapped columns, result cardinality, generated keys, and transaction rules. Named markers
can move or repeat. Positional replacements must preserve argument order and count.
Matching counts cannot detect a change in what a position means.

## Confidentiality

JDBC suppliers, statements, and infrastructure diagnostics omit SQL text, defaults, and
bound values. Existing rules for [errors and JDBC
warnings](data-jdbc.md#errors-and-jdbc-warnings) still apply.

SQL configuration values are not automatically marked confidential. Application logging
can still expose them. Inline defaults remain visible in source, classfiles, and
generated literals. Confidential diagnostics do not encrypt SQL or provide secret
storage.
