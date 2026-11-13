<!--@frontmatter
description: "Execute prepared DML batches and inspect individual outcomes"
-->
# Batch Processing

Use a batch when several items supply values for the same `INSERT`, `UPDATE`, or
`DELETE` statement. Helidon captures a complete parameter set for each item, then
executes one prepared JDBC batch. Generated repositories and imperative applications
use the same execution path. `BatchResult` provides immutable outcomes in input order.

Batching reuses one prepared statement. It creates no transaction and does not
guarantee one network exchange. When the work fits one set-based SQL statement,
that statement may be simpler.

The examples use a CONTACT table with application-supplied identifiers. Client setup
and repository generation follow the existing [configuration](data-jdbc.md#configuration)
and [annotation processing](declarative.md#annotation-processing) guidance.

## Declarative Repositories

With the default `AUTO` execution, a method returning exactly `JdbcClient.BatchResult`
selects batching unless row mapping or generated keys select another operation.
Omitting `@Jdbc.Execution` has the same effect as selecting `AUTO`. Explicit execution
choices retain their meaning. Batch SQL can be literal or loaded from application
configuration. Every batch method requires at least one source that supplies a bound value.

```java
import io.helidon.common.Api;
import io.helidon.data.Data;
import io.helidon.data.jdbc.Jdbc;
import io.helidon.data.jdbc.JdbcClient;

@SuppressWarnings({Api.SUPPRESS_INCUBATING, Api.SUPPRESS_PREVIEW})
@Data.Repository
@Data.Provider("jdbc")
@Jdbc.Client("contacts")
interface ContactBatchRepository {
    @Jdbc.Statement("""
            INSERT INTO CONTACT (ID, NAME, EMAIL, STATUS)
            VALUES (:id, :name, :email, :state)
            """)
    JdbcClient.BatchResult insertAll(Iterable<ContactChange> contacts,
                                    @Data.Param("state") String status);

    @Jdbc.Statement("UPDATE CONTACT SET EMAIL = :emails WHERE ID = :ids OR ID = :ids")
    JdbcClient.BatchResult updateEmails(@Data.Param("emails") String[] replacements,
                                       @Data.Param("ids") long... identifiers);

    @Jdbc.Statement("UPDATE CONTACT SET STATUS = ? WHERE ID = ?")
    @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
    void updateStatus(String status, long... identifiers);
}

record ContactChange(long id, String name, String email) {
}
```

`insertAll` expands each record into its `id`, `name`, and `email` components and
repeats `status` for every item. Its alias supplies the `:state` marker. A null email
becomes typed SQL NULL using the JDBC type for `String`.

`updateEmails` aligns its array and varargs sources. Each replacement email has one
identifier, reused at both `:ids` positions. `updateStatus` uses positional binding,
repeating `status` for every identifier.

Return `JdbcClient.BatchResult` to inspect per-item statuses and update counts, and
the exact total when available. The only other supported batch return type is `void`,
which discards a successful result and requires explicit `BATCH` selection. Failures
still expose available outcomes through `JdbcBatchException`. Batch methods cannot
return counts directly as `int`, `long`, `int[]`, or `long[]`.

`BATCH` cannot be combined with `@Jdbc.GeneratedKeys` or `@Jdbc.RowMapper`. These
combinations fail compilation. Flat-record sources supply input bindings and do not
enable result mapping. Use ordinary [generated-key operations](declarative.md#return-generated-keys)
or [query result mapping](declarative.md#result-mapping) when those outputs are required.

Collections, wrapped results such as `Optional<JdbcClient.BatchResult>`, and subtypes
of `BatchResult` do not independently select batching. See
[Execution Types](declarative.md#execution-types) for the shared selection rules.

### SQL From Configuration

A batch method can select SQL through a complete configuration reference:

```java
@Jdbc.Statement("${application.contacts.insert-all}")
JdbcClient.BatchResult insertAll(Iterable<ContactChange> contacts,
                                @Data.Param("state") String status);
```

This declaration uses the record and imports above. Its configured SQL must use named
markers for `id`, `name`, `email`, and `state` because `@Data.Param` requires named SQL.
Markers may repeat or appear in a different order. See
[SQL In Configuration](configured-statements.md#declarative-repositories) for lookup
rules and optional defaults.

Known defaults are checked during compilation. Deployment SQL is resolved and validated
when the repository is activated, before source traversal or batch JDBC acquisition,
including for empty input. Activation can be lazy, and client or data source activation
can happen first. The resolved SQL remains fixed for that repository instance; see
[Lifetime and Configuration Changes](configured-statements.md#lifetime-and-configuration-changes).

## Imperative Applications

`Statement.executeBatch(items, binder)` accepts a finite source and a `BatchBinder<T>`
callback. Helidon invokes the binder once per item on the calling thread. Its
`BatchBindings` argument captures values by physical JDBC position, starting at one,
or by input name from a prepared definition. It exposes no connection or prepared statement.

Here, `jdbcClient` is an existing client. The snippet uses the `ContactChange` record
above and supplies all four values for each insert:

```java
import java.sql.JDBCType;
import java.util.List;
import java.util.Objects;

import io.helidon.data.jdbc.JdbcClient;

List<ContactChange> contacts = List.of(
        new ContactChange(201L, "Ada", null),
        new ContactChange(202L, "Lin", "lin@example.test"));

JdbcClient.BatchResult result = jdbcClient.create("""
        INSERT INTO CONTACT (ID, NAME, EMAIL, STATUS)
        VALUES (?, ?, ?, ?)
        """).executeBatch(contacts, (bindings, contact) -> {
            Objects.requireNonNull(bindings, "The batch bindings must not be null.");
            Objects.requireNonNull(contact, "The batch item must not be null.");
            bindings.bind(1, contact.id())
                    .bind(2, contact.name());
            String email = contact.email();
            if (email == null) {
                bindings.bindNull(3, JDBCType.VARCHAR);
            } else {
                bindings.bind(3, email);
            }
            bindings.bind(4, "ACTIVE");
        });
```

The source, binder, and items must not be `null`. The binder can read application items
and surrounding constants. `bind` requires a [supported scalar](declarative.md#supported-scalar-types)
that is not `null`. `bindNull` supplies SQL NULL and requires an explicit `JDBCType`
argument that is not `null`.

Prepared definitions also support batching. Here, `config` is the application root
`Config`, and `contacts` is the list above. The key `application.contacts.update-status`
contains `UPDATE CONTACT SET STATUS = :state WHERE ID = :id`:

```java
import java.util.Objects;
import java.util.function.Supplier;

import io.helidon.data.jdbc.JdbcClient;

String sql = config.get("application.contacts.update-status").asString().get();
Supplier<JdbcClient.Statement> updateStatus = jdbcClient.prepare(sql);

JdbcClient.BatchResult updated = updateStatus.get().executeBatch(contacts, (bindings, contact) -> {
    Objects.requireNonNull(bindings, "The batch bindings must not be null.");
    Objects.requireNonNull(contact, "The batch item must not be null.");
    bindings.bind("id", contact.id())
            .bind("state", "ACTIVE");
});
```

`prepare(sql)` takes input names from named markers. `prepare(sql, inputNames)` can
also assign names to positional inputs. A named `bind` or `bindNull` assigns every
position associated with that input, including repeated markers. Unknown names throw
`IllegalArgumentException`. A statement created through `create(sql)` has no input
names, so named batch binding throws `IllegalStateException`. See
[Imperative Applications](configured-statements.md#imperative-applications) for the
shared preparation and input-name rules.

## Sources and Parameter Binding

Each declarative batch item combines one element from every source and the ordinary
scalar constants. Sources contribute values to repeated executions. They do not
expand an SQL `IN` list within one execution.

| Input | Behavior |
| --- | --- |
| `Iterable<T>`, including collections | Obtains one iterator and traverses it once. Elements are supported scalars or flat records. |
| `Iterator<T>` | Consumes the supplied iterator directly. It can call `hasNext()` more than once per item. |
| One-dimensional arrays and varargs | Traverse supported scalar or flat record elements in array order, including arrays of supported primitives. |
| An ordinary scalar parameter | Repeats its value for every logical item. A declarative reference constant may be null. |
| Non-varargs `byte[]` | Supplies one binary scalar constant. `byte...` supplies repeated byte scalars. `Iterable<byte[]>` supplies binary source elements. |

Multiple sources advance together. Unequal lengths throw `IllegalArgumentException`
during capture before JDBC acquisition. No matching prefix is executed. All sources
must be empty for an empty result. Sources remain application-owned and are not closed
by Helidon.

Scalar reference elements and supported reference record components may be null and
become typed SQL NULL. Containers and record elements must not be `null`. Each record
accessor is evaluated once per item, even when a marker repeats. Raw or unresolved
source types, wildcard elements, multidimensional arrays, nested records, arbitrary
beans, and stream or publisher sources are unsupported.

Named scalar inputs use their explicit or inherited `@Data.Param` alias, falling back
to the resolved Java name. Flat records use component names without a record prefix.
A record source cannot declare `@Data.Param`. Named SQL requires a known, unique name
for every scalar input. Collisions between components or scalar inputs make named SQL
ambiguous. Literal SQL and known defaults are checked at compilation; deployment SQL
is checked at repository activation. A configured method without aliases can still
use positional SQL when names collide or Java names were not retained.

A configured method using `@Data.Param` must provide known, unique names for every
input. A declaration that cannot support named SQL fails compilation.

Positional SQL binds parameters in declaration order and record components in component
order, allowing record sources with duplicate component names. Every bindable input
must be used, and the marker count must match the expanded scalar values. Repeated
named markers reuse the captured value at each physical position. Named and positional
markers cannot be mixed. `@Data.Param` requires named SQL in both defaults and
deployment replacements.

See [Statement Parameters](declarative.md#statement-parameters) for alias syntax,
inheritance, Java parameter name requirements, and SQL NULL predicates, and
[Supported Scalar Types](declarative.md#supported-scalar-types) for canonical null types.

## Capture and Binding Lifetime

Helidon checks the source, binder, and statement state before traversing the source.
The statement becomes unavailable for another execution before capture begins.
All bindings are validated before JDBC resources are acquired.

Each callback must assign every physical position exactly once. A named assignment can
fill several positions. Assigning a position by both name and index is a duplicate.
A duplicate assignment, unknown name, out-of-range position, or unsupported value
throws `IllegalArgumentException`. Leaving any SQL parameter position unbound throws
`IllegalStateException`. A statement with existing ordinary bindings is rejected with
`IllegalArgumentException` before traversal and remains usable for ordinary execution.
Once capture starts, the statement cannot execute again, including after source or
binder failure. Previously selected row or generated-key stages cannot execute afterward.
Create a fresh statement through the same client or prepared definition for later operations.

The binding view is valid only during its callback on the calling thread. It expires
when the callback returns or throws. Later use or use from another thread throws
`IllegalStateException`. Null reference arguments are rejected first. Do not retain
the view or use it for asynchronous work.

Capture copies mutable byte arrays and SQL `Date`, `Time`, and `Timestamp` values when
bound, preserving timestamp nanoseconds. It copies bound values rather than the whole
application object graph. The application remains responsible for concurrent source
mutation.

`bindNull` captures a JDBC type without calling the driver. The type must support SQL
NULL without a database type name. `NULL`, `REF_CURSOR`, `ARRAY`, `DISTINCT`,
`JAVA_OBJECT`, `REF`, and `STRUCT` are rejected. Generated repositories choose the type
from the declared Java scalar rather than database metadata or the first item.

Empty input uses the statement's single execution and returns no entries with an exact
total of zero. It invokes no binder and acquires no batch JDBC resources. An enclosing
transaction still follows its own lifecycle.

## Outcomes and Partial Failures

`JdbcClient.BatchResult` contains execution statuses and update counts. It does not
contain generated identifiers or mapped result rows.

`BatchResult.entries()` is an immutable list with one entry per captured item in
encounter order. Each zero-based `itemIndex()` identifies an input position, not a
database identifier. Results retain neither the original items nor JDBC resources
and can be retained after execution.

| Status | `updateCount()` | Meaning |
| --- | --- | --- |
| `EXACT_COUNT` | Present, including zero | The driver reported an exact nonnegative affected-row count. |
| `SUCCESS_NO_INFO` | Empty | The driver reported success without an exact count. |
| `EXECUTE_FAILED` | Empty | The driver explicitly reported item failure. |
| `NOT_REPORTED` | Empty | The outcome of this item is unknown. It does not establish whether the item was attempted, skipped, committed, or rolled back. |

Successful returns contain only `EXACT_COUNT` and `SUCCESS_NO_INFO` entries.
`totalUpdateCount()` is present only when every count is exact and the sum fits in a
`long`. It never returns a partial sum. Empty input has exact total zero. JDBC integer
counts are widened to `long`, which does not extend the range supplied by the driver.

A `JdbcBatchException` preserves available outcomes in `result()`. For example, with
an existing `ContactBatchRepository repository` and the `contacts` list above:

```java
import io.helidon.data.jdbc.JdbcBatchException;
import io.helidon.data.jdbc.JdbcClient;

try {
    JdbcClient.BatchResult outcome = repository.insertAll(contacts, "ACTIVE");
    outcome.totalUpdateCount().ifPresent(total ->
            System.out.printf("Affected rows: %d%n", total));
} catch (JdbcBatchException failure) {
    for (JdbcClient.BatchResult.Entry entry : failure.result().entries()) {
        System.out.printf("Item %d: %s%n", entry.itemIndex(), entry.status());
    }
    throw failure;
}
```

Runtime failures during JDBC acquisition, preparation, binding, execution, or cleanup,
and invalid driver counts, produce a sanitized `JdbcBatchException`, a subtype of
`DataException`. Its result has an entry for every captured item, with missing evidence
represented by `NOT_REPORTED`. Drivers can stop at a failure or report later successes.
A setup failure has no item-level evidence. A cleanup failure after successful
execution can carry only successful entries.

The exception preserves supported SQLSTATE and vendor-code diagnostics while excluding
SQL, bound values, credentials, and raw driver messages. Source traversal and application
binder failures propagate unchanged before batch JDBC work and are not automatically
redacted. An `Error` follows the existing cleanup and exception precedence rules. See
[Errors and JDBC Warnings](data-jdbc.md#errors-and-jdbc-warnings) for shared diagnostic rules.

A transaction boundary can wrap a batch failure in `TxException`. Inspect its cause
chain to find the batch result while preserving the original failure. Execution
outcomes never establish commit or rollback.

## Transactions and Cleanup

A nonempty batch uses one connection lease and one prepared statement, adds one complete
parameter set per item, and calls JDBC `executeBatch()` once. Helidon closes the
statement and releases the lease before returning or throwing, attempting later releases
even if an earlier cleanup action fails. Operation-owned connections are closed.
The transaction owner remains responsible for committing, rolling back, and releasing
a participating connection.

Batching creates no implicit transaction. Registry-managed clients participate in the
existing local JDBC transaction. Standalone clients retain independent connection
ownership. Outside a transaction, earlier items may become durable before a later
failure. Inside one, successful counts can describe work that is subsequently rolled
back. Use transactional DML and let failures reach the transaction owner when the batch
must be atomic. Keep DDL and transaction-control SQL out of the batch.

A failure escaping a joined transactional invocation marks the transaction for rollback,
even if its caller catches it afterward. Catching a client failure inside the owning
method or callback does not itself request rollback, although the driver can abort the
transaction or leave the connection unusable. A capture failure caught inside an otherwise
healthy transaction does not independently prevent other operations from committing.

Transaction completion can fail after batch execution. This does not change the existing
batch result. Verify database state before retrying when completion is uncertain. See
[Local Transactions](data-jdbc.md#local-transactions) for propagation and provider restrictions,
and [Resource Management](imperative.md#resource-management) for shared ownership rules.

## Batch Size and Limits

Inputs must be finite and bounded by the application. An iterator does not make JDBC
execution lazy: Helidon retains all complete bindings and copied payloads before
execution. The driver can retain additional batch storage, and detached outcomes
require space proportional to the number of items.

Partition large inputs explicitly and use a fresh statement for each partition. Each
invocation has its own result indices and failure boundary. Earlier partitions may
already be committed when a later one fails outside a transaction. An explicit
surrounding transaction can group their effects. There is no automatic batch-size
setting, hidden chunking, or retry policy.

Batching uses prepared DML under the existing [SQL guidelines](data-jdbc.md#sql-guidelines).
It does not map query rows, return generated keys, derive SQL, or perform entity
persistence. Literal and configured repository SQL support batching. Imperative batches
can use `JdbcClient.create(sql)` or a fresh statement from `JdbcClient.prepare(...)`.
Use ordinary [statement execution](imperative.md#statement-execution) and
[generated-key operations](imperative.md#return-generated-keys) for those result shapes.
Generated configured batches require a client from the Helidon JDBC provider.
Alternate client providers may reject the terminal with `UnsupportedOperationException`
after reference validation without consuming input. SQL and transaction behavior still
need to match the databases supported by the application.
