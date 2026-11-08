<!--@frontmatter
description: "Load SQL from application configuration for repositories and JDBC clients"
-->
# SQL In Configuration

Helidon Data JDBC can load SQL from application configuration, allowing an application
to replace statements without recompiling its repository interfaces. The replacement
SQL must remain compatible with the declared inputs and expected results. Declarative
repositories identify the configuration key through `@Jdbc.Statement`, while imperative
applications read the SQL through `Config` and pass it to `JdbcClient.prepare(...)`.

Loading SQL from configuration changes how a statement is selected. The existing
[execution contracts](data-jdbc.md#sql-guidelines),
[result mapping](declarative.md#result-mapping), and
[transaction rules](data-jdbc.md#local-transactions) still govern its execution. This
support covers materialized queries, updates, and generated keys. Streaming, stored
procedure calls, and batch processing are outside its scope.

## Application Configuration

The application controls where SQL appears in the configuration tree. A statement can
be stored at the root, under an application section, or inside a client entry under
`data.clients.jdbc`. The following example groups three statements under
`application.contacts`, but neither that prefix nor that nesting depth is required:

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

Each value contains a nonblank SQL string. The YAML `|-` notation preserves internal
line breaks and removes trailing line breaks, allowing longer statements to remain
readable across several lines.

The same lookup rules apply when SQL is stored beside client settings. For example,
`sql.find-by-id` inside the first `data.clients.jdbc` entry has the full key
`data.clients.jdbc.0.sql.find-by-id`. A repository can reference that value through
`@Jdbc.Statement("${data.clients.jdbc.0.sql.find-by-id}")`, and imperative code can read
it from the root Config through
`config.get("data.clients.jdbc.0.sql.find-by-id").asString().get()`. The `0` identifies
the first entry in the client list, and `sql` is a key defined by the application.

The location of a SQL value does not determine which client executes it. Repositories
continue to select a client through `@Jdbc.Client`, and imperative applications use the
chosen `JdbcClient` instance. Connection settings follow the structure described in
[Configuration](data-jdbc.md#configuration), regardless of where the SQL is stored.

## Declarative Repositories

A repository method selects SQL through a complete configuration reference in
`@Jdbc.Statement`. The following repository uses the YAML keys for a query, an update,
and an insert that returns a generated key:

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

When this repository is activated, Helidon reads the keys from the root `Config` in
the same Service Registry and prepares the statements for the `contacts` client.
Activation can happen after application startup. The resolved SQL is then retained in
an immutable definition for each method, and every method call creates a new statement
with independent bindings.

The configured statements follow the same binding rules as literal SQL. Named markers
match the `@Data.Param` alias assigned to a parameter, or its Java name when no alias is
present. Repeating a marker supplies the same argument to each occurrence. For methods
without aliases, positional SQL provides an alternative with one marker per parameter
in declaration order. The `rename` statement can therefore be replaced with the
following value without recompiling the repository:

```yaml [application.yaml]
application:
  contacts:
    rename: |-
      UPDATE CONTACT SET NAME = ? WHERE ID = ?
```

With this replacement, `name` binds to the first marker and `id` to the second, matching
the method declaration. Reference arguments still support Java `null`, which generated
code binds as typed SQL `NULL` using the declared scalar type. The SQL predicate
determines its meaning, as explained in [Statement Parameters](declarative.md#statement-parameters).

An alias allows the Java parameter name to change independently of SQL. The same query
can therefore retain `:id` while using `contactId` in Java:

```java
@Jdbc.Statement("${application.contacts.find-by-id}")
Optional<Contact> findById(@Data.Param("id") long contactId);
```

Here, `id` becomes the effective input name, which must be unique among all parameters,
including those without aliases. Any explicit or inherited `@Data.Param` alias requires
named SQL in both defaults and deployment replacements. The positional replacement
shown earlier therefore applies only to methods without aliases.

For an inherited configured method, each parameter without an alias needs a reliable
Java name from the parent declaration. A parent compiled with `-parameters` preserves
that name. An application can instead supply `@Data.Param` or redeclare the method with
explicit parameter names. This requirement still applies when the current configured
SQL is positional, because a later replacement can use named markers. Inherited aliases
are retained without `-parameters`, while literal positional methods need no parameter
name metadata.

### Optional Defaults

A reference can provide default SQL when its key is absent. The default follows the
first colon and can span several lines to keep the fallback readable:

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

The first colon separates the key from its default, and the final closing brace ends
the reference. Interior colons and braces remain part of the SQL. Configured values
pass through normal Config filters, with no further expression resolution or unescaping
by JDBC.

The default must be nonblank and match the declared method inputs. A present blank
value, object, or list causes activation to fail even when a default exists, because
fallback applies only to absent keys. A missing key without a default also fails
activation.

The annotation accepts one complete reference with a nonblank key that contains no
braces. Nested or multiple `${...}` references are rejected, and literal SQL cannot be
combined with configuration references in the annotation value. SQL that needs a
literal `${` sequence can be stored in configuration instead of an annotation default,
subject to the normal Config filters.

## Imperative Applications

An imperative application resolves SQL through the ordinary Config API and passes it
to `prepare(sql)`, which returns a reusable statement supplier. In this example,
`config` is the application root Config and `jdbcClient` is the selected client:

```java
String sql = config.get("application.contacts.find-by-id").asString().get();
Supplier<JdbcClient.Statement> findById = jdbcClient.prepare(sql);

Optional<Contact> contact = findById.get()
        .bind("id", 100L)
        .map(row -> new Contact(row.get("id", Long.class),
                                row.get("name", String.class)))
        .optional();
```

The examples use `Supplier` from `java.util.function` and `List` and `Optional` from
`java.util`. The Config lookup uses `get()` to require a value, so JDBC receives resolved
SQL without interpreting `${...}` or retaining Config. The supplier creates statements
that support named SQL through `bind(name, value)` and positional SQL through
`bind(index, value)`, using indexes that start at one. Statements without markers are
also supported.

The overload `prepare(sql, inputNames)` makes the expected inputs explicit and allows
positional SQL to be bound by logical name. Here, `orElse(...)` supplies a positional
fallback when the key is absent, and the input list declares the expected value order:

```java
String sql = config.get("application.contacts.rename").asString()
        .orElse("UPDATE CONTACT SET NAME = ? WHERE ID = ?");
Supplier<JdbcClient.Statement> rename = jdbcClient.prepare(sql, List.of("name", "id"));

long updated = rename.get()
        .bind("id", 100L)
        .bind("name", "Updated name")
        .execute();
```

Both SQL forms satisfy this input contract. Named SQL must contain exactly the supplied
names, although their markers can appear in any order or repeat. For positional SQL,
the marker count must equal the list size, with `name` identifying position one and
`id` position two. Names must be unique and follow Java identifier character rules.
Helidon copies the list to protect the definition from later changes. Indexed bindings
still address physical JDBC positions, including individual occurrences of repeated
named markers.

Retaining the supplier allows the application to reuse the parsed SQL. Each call to
`prepare(...)` scans the statement, but each subsequent supplier call creates independent
binding state without another scan, configuration lookup, or JDBC work. The supplier
holds the definition, and the client retains no history of these definitions. Their
plans are separate from the [parameter count cache](data-jdbc.md#parameter-count-cache)
used by `JdbcClient.create(sql)`.

Unlike generated repository bindings, imperative value bindings require nonnull scalars
and provide no typed SQL NULL operation. Null handling remains explicit in the SQL, as
described in [Parameter Binding](imperative.md#parameter-binding). Applications can also
continue to use `JdbcClient.create(sql)` for positional statements when they do not need
a reusable definition.

## Binding and Validation

Moving SQL into configuration preserves the existing
[SQL safety rules](data-jdbc.md#sql-guidelines). A statement may contain named markers,
positional markers, or no markers, but it cannot mix named and positional styles.
Text inside supported strings, quoted identifiers, and comments does not introduce
inputs. When SQL has no markers, the repository method must have no parameters, or the
ordered input list must be empty.

Validation is divided between compilation and initialization because deployment SQL
is not available to the annotation processor. During compilation, Helidon checks the
reference syntax, parameter types and reliable names, aliases, return types, execution
contracts, and markers in known inline defaults. It reads neither deployment
configuration nor database metadata at this stage.

The actual SQL is validated when the repository is activated or the imperative
application calls `prepare(...)`. At that point, Helidon checks that the string is
nonblank, its markers use valid syntax and a single style, and any declared input names
or counts match. Preparing the definition acquires no JDBC resources, although
repository activation may first resolve or activate a client or data source. A failure
in that setup can therefore occur before SQL validation.

During binding, each physical JDBC position must receive exactly one assignment,
either directly or through a logical name. Unknown names, invalid positions, and
duplicate assignments fail immediately. A named binding validates all matching positions
before assigning any of them, and missing bindings are rejected before the operation
acquires a connection. Once execution begins, the database validates SQL grammar and
schema references, while the existing mapping, cardinality, and generated key checks
apply to its results.

## Lifetime and Configuration Changes

A prepared definition remains associated with its client and resolved SQL. Repository
instances retain their own method definitions, with independent binding state across
calls, clients, and Service Registries. Suppliers can therefore be shared, although
each statement they create permits one execution and must not be mutated concurrently.

These definitions hold no JDBC resources and capture no transaction context. The
terminal operation uses the transaction active when it executes and follows the usual
[resource ownership rules](imperative.md#resource-management). If generated key
processing or cleanup fails, the application should let the exception escape the owning
method or `Tx.transaction` callback so Helidon can roll back.

A failure that escapes a joined transactional invocation marks the transaction for
rollback even if its caller catches it afterward. Catching a failure inside the owning
method or callback before it crosses a transaction boundary does not itself mark the
transaction for rollback and can allow the update to commit. Outside a local transaction,
the update may already be committed when key processing fails. A failure alone therefore
does not establish that retrying is safe.

Because configuration is resolved when a definition is created, existing definitions
continue to use their original SQL after configuration changes. An imperative
application adopts newly loaded configuration by preparing a new supplier. A declarative
application adopts it through a new repository and Service Registry, or by restarting.
A new client is unnecessary if its connection source and settings remain unchanged.

Replacing SQL without recompiling still requires the replacement to preserve the
input types, execution kind, mapped columns, result cardinality, generated key
expectations, and transaction rules. Named bindings identify inputs by name, which
allows replacement SQL to reorder or repeat their markers while preserving the required
input names. Positional bindings depend on method declaration or input list order, so
a replacement must preserve both the marker count and the meaning of each position.
Helidon can check the count, but it cannot detect a semantic change caused by swapping
two positions when that count remains the same.

## Confidentiality

Helidon omits SQL text, defaults, and bound values when rendering JDBC suppliers,
statements, and infrastructure diagnostics. This follows the existing rules for
[errors and JDBC warnings](data-jdbc.md#errors-and-jdbc-warnings) and does not change
how application exceptions are handled.

This diagnostic protection does not make configuration a secret store. SQL values are
ordinary Config values and are not automatically marked confidential, so application
logging can still expose them. Inline defaults also remain visible in source,
classfiles, and generated literals. Confidential diagnostics therefore provide neither
encryption nor secret storage.
