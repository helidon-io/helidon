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
support covers materialized queries, updates, generated keys, and stored procedure
and function calls. Calls can return materialized REF_CURSOR results where the driver
supports them. Streaming and batch processing are outside its scope.

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

Configured queries, updates, and generated key statements follow the same binding rules
as their literal equivalents. Named markers match the `@Data.Param` alias assigned to
a parameter, or its Java name when no alias is present. Repeating a marker supplies the
same argument to each occurrence. For methods without aliases, positional SQL provides
an alternative with one marker per parameter in declaration order. The `rename`
statement can therefore be replaced with the following value without recompiling the
repository:

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
are retained without `-parameters`. Literal positional queries, updates, and generated
key statements need no parameter name metadata. Callable declarations that associate inputs
by name still require reliable names for unaliased inputs.

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

The default must be nonblank and match the declared parameter contract, including
outputs for CALL. A present blank value, object, or list causes activation to fail even
when a default exists, because fallback applies only to absent keys. A missing key
without a default also fails activation.

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

## Stored Procedures and Functions

Configured calls use the same output declarations, type inference, callbacks, and
materialized results as literal calls. These examples use the Oracle routines and
the `orders` client defined in [Stored Procedures and Functions](stored-procedures.md).
Configuration supplies the SQL. Java declarations select CALL execution and define
the outputs and result mapping.

```yaml [application.yaml]
application:
  orders:
    status: "{call READ_STATUS(:orderId, :status)}"
    total: "{? = call ORDER_TOTAL(:orderId)}"
```

The repository methods register a scalar OUT parameter and a function return:

```java
@SuppressWarnings(Api.SUPPRESS_INCUBATING)
@Data.Repository
@Data.Provider("jdbc")
@Jdbc.Client("orders")
interface OrderRepository {

    @Jdbc.Statement("${application.orders.status}")
    @Jdbc.OutParameter("status")
    String status(long orderId);

    @Jdbc.Statement("${application.orders.total}")
    @Jdbc.OutParameter
    Optional<BigDecimal> total(Long orderId);
}
```

Helidon infers `VARCHAR` from `String` and `DECIMAL` from `Optional<BigDecimal>`.

Repository methods with ordinary OUT or INOUT declarations require named argument
markers. Replacing the status statement with `{call READ_STATUS(?, ?)}` is rejected
when the repository is activated, before the client acquires a connection, even though its marker
count matches. Java input order and output annotation order do not identify which
positional markers are ordinary OUT or INOUT arguments.

Both JDBC escape forms may invoke procedures or functions when supported by the driver.
The application selects suitable SQL and driver connection settings. Helidon validates
parameter structure and preserves the escape form, without classifying the database
routine or retrying a rejected call through another form.

The total method has one unnamed output for its leading callable return. Its input can
use either a named marker or a positional marker. For example, the same repository
accepts this configuration:

```yaml [application.yaml]
application:
  orders:
    status: "{call READ_STATUS(:orderId, :status)}"
    total: "{? = call ORDER_TOTAL(?)}"
```

Calls without output declarations also support positional IN arguments in Java
declaration order. Callback parameters do not count as SQL inputs. A leading callable
return occupies JDBC position one, so positional IN arguments begin at position two.
The leading return must be the only declared output. Its special `?` may precede named
IN arguments, but ordinary named and positional arguments cannot be mixed.
Effective `@Data.Param` aliases continue to require named arguments.

For imperative calls, `client` is the `orders` client and `config` is the root Config.
The examples use `JDBCType` from `java.sql` and `BigDecimal` from `java.math`:

```java
String statusSql = config.get("application.orders.status").asString().get();
Supplier<JdbcClient.Statement> readStatus = client.prepare(statusSql, List.of("orderId", "status"));

String status = readStatus.get()
        .bind("orderId", 42L)
        .call()
        .out("status", JDBCType.VARCHAR)
        .outputs()
        .get("status", String.class);
```

For positional SQL, `prepare(sql, names)` associates each name with its JDBC marker
position in list order. For CALL, the list includes every logical input and output
once, with `"return"` first when a leading return is present.
`out(name, type)` resolves a named SQL marker or a name supplied through that list.
Bind an INOUT value and register its output using the same name. An OUT slot needs no
input binding. These logical registrations require a prepared definition.
Existing indexed `out(name, index, type)` registrations remain supported.

The leading callable return has a separate registration:

```java
String totalSql = config.get("application.orders.total").asString().get();
Supplier<JdbcClient.Statement> orderTotal = client.prepare(totalSql, List.of("return", "orderId"));

Optional<BigDecimal> total = orderTotal.get()
        .bind("orderId", 42L)
        .call()
        .returns(JDBCType.DECIMAL)
        .outputs()
        .optional("return", BigDecimal.class);
```

`returns(type)` requires a leading `? = call` slot, registers JDBC position one, and
exposes the value as `"return"`. That slot cannot be bound as an input or registered
through logical `out(name, type)`. Both registration forms also provide overloads for
a database type name or a nonnegative `NUMERIC` or `DECIMAL` scale. Positional imperative
calls can instead use indexes without supplying a name list.

Register a leading return before execution even when the application ignores its
value. `Call.execute()` discards registered outputs. A call without a leading return
may expose direct result sets, which use the existing result mapping contracts.

An output declaration selects CALL for a repository method. Methods with no outputs
declare `@Jdbc.Execution(Jdbc.ExecutionType.CALL)` explicitly. Imperative calls select
`call()`. Helidon does not select callable execution from SQL keywords. REF_CURSOR
outputs retain the driver requirements, callback lifetime, and materialization rules
described in [REF_CURSOR Results](stored-procedures.md#ref_cursor-results).

## Binding and Validation

Moving SQL into configuration preserves the existing
[SQL safety rules](data-jdbc.md#sql-guidelines). A statement may contain named markers,
positional markers, or no markers. Only CALL permits a leading `? = call` return
followed by named argument markers. Other mixtures are rejected. Text inside supported
strings, quoted identifiers, and comments does not introduce parameters. Marker-free
SQL has no SQL inputs or registered outputs. Any supplied name list must be empty.
Callback controls are not SQL parameters.

Validation is divided between compilation and initialization because deployment SQL
is not available to the annotation processor. During compilation, Helidon checks the
reference syntax, parameter types and reliable names, aliases, return types, execution
contracts, and markers in known inline defaults. It reads neither deployment
configuration nor database metadata at this stage.

The actual SQL is validated when the repository is activated or the imperative
application calls `prepare(...)`. At that point, Helidon checks that the string is
nonblank, its markers use permitted syntax, and the declared parameter names or counts
match. Generated callable repositories also validate the required presence or absence
of a leading return and any requirement for named arguments during activation. A
matching marker count or an ordinary parameter named `return` does not establish the
required leading return slot.

To obtain leading return validation during activation, regenerate repositories using
matching versions of the JDBC code generator and runtime. Existing compiled
repositories retain their generated preparation behavior.

General imperative preparation does not declare output roles. Its registration checks,
including leading returns and repeated OUT markers, run when outputs are registered,
before JDBC acquisition. Other callable registration checks remain at that boundary
for generated repositories too.
Preparing the definition acquires no JDBC resources, although repository activation
may first resolve or activate a client or data source. A failure in that setup can
therefore occur before SQL validation.

During binding, each input position receives exactly one assignment, either directly
or through a logical name. Unknown names, invalid positions, and duplicate assignments
fail immediately. A named binding validates all matching positions before assigning
any of them. Queries, updates, and generated key operations require every position to
be bound before JDBC acquisition. For CALL, every position must have an input binding,
an output registration, or both for INOUT before execution can acquire a connection.
Only IN-only markers may repeat. Each OUT or INOUT marker must occur once. Once execution
begins, the database validates SQL grammar and schema references, while the existing
mapping, cardinality, and generated key checks apply to its results.

## Lifetime and Configuration Changes

A prepared definition remains associated with its client and resolved SQL. Repository
instances retain their own method definitions, with independent binding state across
calls, clients, and Service Registries. Suppliers can therefore be shared, although
each statement they create permits one execution and must not be mutated concurrently.

These definitions hold no JDBC resources and capture no transaction context. The
terminal operation follows the usual [resource ownership rules](imperative.md#resource-management)
for the selected client. Registry managed clients determine transaction participation
at execution time. Standalone clients remain independent of an enclosing managed transaction.
When an operation participates in a local transaction, the application should let
generated key processing or cleanup failures escape the owning method or
`Tx.transaction` callback so Helidon can roll back.

A failure that escapes a joined transactional invocation marks the transaction for
rollback even if its caller catches it afterward. For ordinary queries and generated
key processing, catching a processing failure inside the owning method or transaction
task before it crosses a transaction boundary can allow the transaction to commit.
Callable processing retains provider-observed failures even when a callback catches
them, as described in [Resources, Transactions, and Database Support](stored-procedures.md#resources-transactions-and-database-support).
Outside a local transaction, the update may already be committed when key processing
fails. A failure alone therefore does not establish that retrying is safe.

Because configuration is resolved when a definition is created, existing definitions
continue to use their original SQL after configuration changes. An imperative
application adopts newly loaded configuration by preparing a new supplier. A declarative
application adopts it through a new repository and Service Registry, or by restarting.
A new client is unnecessary if its connection source and settings remain unchanged.

Replacing SQL without recompiling still requires the replacement to preserve the
input types, execution kind, mapped columns, result cardinality, generated key
expectations, and transaction rules. Named bindings identify inputs by name, which
allows replacement SQL to reorder or repeat IN-only markers while preserving the required
names. CALL replacements must also preserve output identities and types and the required
leading return. For positional SQL, declarative inputs follow method declaration order.
Imperative callers can associate names with positions through the ordered list passed
to `prepare(sql, names)`. Replacement SQL must preserve both the marker count and the
meaning of each position. Helidon can check the count, but it cannot
detect a semantic change caused by swapping two positions when that count remains the same.

Helidon marker names do not rename database routine parameters. Reordering call arguments
requires an equivalent routine or database syntax that preserves their meaning.

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
