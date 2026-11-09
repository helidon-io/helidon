<!--@frontmatter
description: "Execute prepared DML batches and inspect individual outcomes"
-->
# Batch Processing

Use a batch when several items supply values for the same `INSERT`, `UPDATE`, or
`DELETE` statement. Helidon captures a complete parameter set for each item, then
executes one prepared JDBC batch. Generated repositories and imperative applications
use the same execution path and return the same immutable outcome representation.

A batch can reuse one prepared statement while preserving an outcome for each input
item. If the work fits one set-based SQL statement, that statement may be simpler.
Individual operations remain useful when items need different SQL or execution policies.
One JDBC batch invocation does not guarantee one network exchange.

Batch operations follow the existing [SQL safety rules](data-jdbc.md#sql-guidelines),
[scalar type mappings](declarative.md#supported-scalar-types), and
[resource ownership rules](imperative.md#resource-management). The source containers,
binding callbacks, and per-item outcomes described here extend those ordinary operations.

## Import Contacts

Use a Java 27 application with the Data JDBC runtime, YAML support, and MySQL driver
shown in [Maven Coordinates](data-jdbc.md#maven-coordinates). Configure the processors
shown in [Annotation Processing](declarative.md#annotation-processing), using the same
Helidon version for the runtime and processors. The examples acknowledge the Data
preview API and JDBC incubating APIs with focused warning suppressions.

The combined example uses repository generation and transaction interception for both
run commands. An imperative application without repositories does not require the
Data JDBC repository generator.

Prepare this table once in a MySQL database named `contacts`, outside the batch and its
transaction. The example uses InnoDB so the inserts can be rolled back:

```sql [schema.sql]
CREATE TABLE CONTACT (
    ID BIGINT PRIMARY KEY,
    NAME VARCHAR(100) NOT NULL,
    EMAIL VARCHAR(255),
    STATUS VARCHAR(20) NOT NULL
) ENGINE=InnoDB;
```

Save this configuration as `src/main/resources/application.yaml`, adjusting the URL
and credentials for that database:

```yaml [application.yaml]
data:
  clients:
    jdbc:
      - name: "contacts"
        connection:
          url: "jdbc:mysql://localhost:3306/contacts"
          username: "user"
          password: "password"
```

The named client follows the ordinary [client configuration](data-jdbc.md#configuration).
The application supplies identifiers 201 and 202; the batch does not request generated
keys. Run one programming model against a dataset where these identifiers are absent.
Running the import again without resetting those rows causes a primary-key failure.

Save this application input record as `src/main/java/example/ContactChange.java`:

```java [ContactChange.java]
package example;

public record ContactChange(long id, String name, String email) {
}
```

An input record describes values to bind. Its supported reference components can be
null, unlike the `Optional` components used to represent nullable columns in
[mapped result records](declarative.md#scalars-and-records). The example supplies a name
because the table requires it, and permits a null email.

### Declarative Usage

Declare `BATCH` explicitly on every batch method. The return type is either
`JdbcClient.BatchResult` or `void`. A void method discards a successful result, while
failures still carry available outcomes.

Helidon does not infer batching from a collection parameter or a `BatchResult` return
type. A `void` method without explicit `BATCH` selection remains an ordinary update.

Save the repository as `src/main/java/example/ContactBatchRepository.java`:

```java [ContactBatchRepository.java]
package example;

import io.helidon.common.Api;
import io.helidon.data.Data;
import io.helidon.data.jdbc.Jdbc;
import io.helidon.data.jdbc.JdbcClient;

@SuppressWarnings({Api.SUPPRESS_INCUBATING, Api.SUPPRESS_PREVIEW})
@Data.Repository
@Data.Provider("jdbc")
@Jdbc.Client("contacts")
public interface ContactBatchRepository {
    @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
    @Jdbc.Statement("""
            INSERT INTO CONTACT (ID, NAME, EMAIL, STATUS)
            VALUES (:id, :name, :email, :state)
            """)
    JdbcClient.BatchResult insertAll(Iterable<ContactChange> contacts,
                                    @Data.Param("state") String status);

    @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
    @Jdbc.Statement("UPDATE CONTACT SET EMAIL = :emails WHERE ID = :ids OR ID = :ids")
    JdbcClient.BatchResult updateEmails(@Data.Param("emails") String[] replacements,
                                       @Data.Param("ids") long... identifiers);

    @Jdbc.Execution(Jdbc.ExecutionType.BATCH)
    @Jdbc.Statement("UPDATE CONTACT SET STATUS = ? WHERE ID = ?")
    void updateStatus(String status, long... identifiers);
}
```

`insertAll` expands each record into its `id`, `name`, and `email` components and
repeats the scalar `status` for every item. Its alias supplies the `:state` marker.
The generator binds a null email with the JDBC type for `String`.

The other methods demonstrate scalar arrays, varargs, repeated named markers, and
positional SQL. They are available for later updates and are not invoked by the import
below. `updateEmails` requires one identifier for each replacement email; both `:ids`
occurrences use that item's identifier. A replacement email can be null.

### Imperative Usage

`Statement.executeBatch(items, binder)` takes a finite source and a `BatchBinder<T>`
callback. Helidon invokes the callback once per encountered item. Its `BatchBindings`
argument captures physical JDBC positions, starting at one, without exposing a
connection or prepared statement.

The following managed service provides both import methods. Each method declares an
explicit local transaction so that its transactional DML succeeds or rolls back as a
unit when a failure leaves the method. The batch terminal itself creates no transaction.
Save it as `src/main/java/example/ContactImport.java`:

```java [ContactImport.java]
package example;

import java.sql.JDBCType;
import java.util.List;
import java.util.Objects;

import io.helidon.common.Api;
import io.helidon.data.Data;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.service.registry.Service;
import io.helidon.transaction.Tx;

@SuppressWarnings({Api.SUPPRESS_INCUBATING, Api.SUPPRESS_PREVIEW})
@Service.Singleton
public class ContactImport {
    private final JdbcClient jdbcClient;
    private final ContactBatchRepository repository;

    @Service.Inject
    ContactImport(@Data.ProviderType("jdbc") @Service.Named("contacts") JdbcClient jdbcClient,
                  ContactBatchRepository repository) {
        this.jdbcClient = jdbcClient;
        this.repository = repository;
    }

    @Tx.Required
    public JdbcClient.BatchResult declarative(List<ContactChange> contacts) {
        Objects.requireNonNull(contacts, "The contacts must not be null.");
        return repository.insertAll(contacts, "ACTIVE");
    }

    @Tx.Required
    public JdbcClient.BatchResult imperative(List<ContactChange> contacts) {
        Objects.requireNonNull(contacts, "The contacts must not be null.");
        return jdbcClient.create("""
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
    }
}
```

The imperative binder can read any nonnull application item and surrounding constants.
Its source, binder, and items must be nonnull. `bind` requires a nonnull
[supported scalar](declarative.md#supported-scalar-types); `bindNull` supplies SQL NULL
with an explicit nonnull `JDBCType`.

### Run Either Import

Obtain the service through the Service Registry so its transaction annotations take
effect. Save this entry point as `src/main/java/example/BatchMain.java`:

```java [BatchMain.java]
package example;

import java.util.List;
import java.util.Objects;

import io.helidon.common.Api;
import io.helidon.data.jdbc.JdbcBatchException;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.service.registry.ServiceRegistryManager;

@SuppressWarnings(Api.SUPPRESS_INCUBATING)
public final class BatchMain {
    public static void main(String[] args) {
        Objects.requireNonNull(args, "The arguments must not be null.");
        if (args.length != 1 || !List.of("declarative", "imperative").contains(args[0])) {
            throw new IllegalArgumentException("Choose declarative or imperative as the import model.");
        }
        List<ContactChange> contacts = List.of(
                new ContactChange(201L, "Ada", null),
                new ContactChange(202L, "Lin", "lin@example.test"));
        ServiceRegistryManager manager = ServiceRegistryManager.start();
        try {
            ContactImport importer = manager.registry().get(ContactImport.class);
            JdbcClient.BatchResult result = switch (args[0]) {
            case "declarative" -> importer.declarative(contacts);
            case "imperative" -> importer.imperative(contacts);
            default -> throw new IllegalArgumentException("The import model is unsupported.");
            };
            report(result);
        } catch (RuntimeException failure) {
            Throwable cause = failure;
            while (!(cause instanceof JdbcBatchException) && cause.getCause() != null) {
                cause = cause.getCause();
            }
            if (cause instanceof JdbcBatchException batch) {
                report(batch.result());
            }
            throw failure;
        } finally {
            manager.shutdown();
        }
    }

    private static void report(JdbcClient.BatchResult result) {
        for (JdbcClient.BatchResult.Entry entry : result.entries()) {
            String count = entry.updateCount().isPresent()
                    ? Long.toString(entry.updateCount().getAsLong()) : "not supplied";
            System.out.printf("Item %d: %s, update count %s.%n", entry.itemIndex(), entry.status(), count);
        }
        result.totalUpdateCount().ifPresentOrElse(
                total -> System.out.printf("Exact total: %d.%n", total),
                () -> System.out.println("An exact total is unavailable."));
    }
}
```

Run one of these commands from the Maven application project containing the four Java
files and `application.yaml`. The commands select the Maven exec plugin explicitly:

```shell [Terminal]
mvn compile org.codehaus.mojo:exec-maven-plugin:3.6.3:exec -Dexec.executable=java -Dexec.args="-classpath %classpath example.BatchMain declarative"
```

```shell [Terminal]
mvn compile org.codehaus.mojo:exec-maven-plugin:3.6.3:exec -Dexec.executable=java -Dexec.args="-classpath %classpath example.BatchMain imperative"
```

After the selected import and transaction complete successfully, these rows are present:

| ID | NAME | EMAIL | STATUS |
| --- | --- | --- | --- |
| 201 | Ada | SQL NULL | ACTIVE |
| 202 | Lin | lin@example.test | ACTIVE |

Other contacts are unchanged. Verify the imported values with:

```sql [Verify the import]
SELECT ID, NAME, EMAIL, STATUS FROM CONTACT WHERE ID IN (201, 202) ORDER BY ID;
```

The result has two successful entries. A driver can report exact counts or
`SUCCESS_NO_INFO`, so the printed counts and availability of an exact total can vary.
The entry index identifies an input item, not its database identifier.

## Sources and Parameter Binding

A declarative batch requires at least one source contributing a SQL value. Each
logical item combines one element from every source and the ordinary scalar constants.

| Input | Behavior |
| --- | --- |
| `Iterable<T>`, including collections | Obtains one iterator and traverses it once. Elements are supported scalars or flat records. |
| `Iterator<T>` | Consumes the supplied iterator directly. It does not promise only one `hasNext()` call per item. |
| One-dimensional arrays and varargs | Traverse their elements in array order. Primitive arrays are supported. |
| An ordinary scalar parameter | Repeats its value for every logical item. A declarative reference constant may be null. |
| Non-varargs `byte[]` | Supplies one binary scalar constant. `byte...` supplies repeated byte scalars. `Iterable<byte[]>` can supply binary values as source elements. |

Multiple sources advance together. Unequal lengths throw `IllegalArgumentException`
during capture before batch JDBC acquisition, even if a matching prefix was already
captured. No prefix is executed. All sources must be empty for an empty result.
Sources remain application-owned and are not closed by the provider.

Scalar reference elements may be null and become typed SQL NULL. Containers and record
elements must be nonnull. Record components must be supported scalars; supported
reference components can be null. Each record accessor is evaluated once per item,
even when a marker repeats. Raw or unresolved source types, wildcard elements,
multidimensional arrays, nested records, arbitrary beans, and stream or publisher
sources are unsupported.

Named scalar inputs use their explicit or inherited `@Data.Param` alias, falling back
to the resolved Java name. Flat-record sources use component names without a record
prefix. A record source cannot declare `@Data.Param`. An alias that collides with a
record component, or two record sources with the same component name, makes named SQL
ambiguous and fails compilation. Positional SQL can use duplicate component names:
parameter declaration order followed by record-component order determines the slots.

Every bindable input must be used. Repeated named markers reuse its captured value at
separate physical positions. Named and positional markers cannot be mixed in one
statement. For positional SQL, the number of markers must equal the number of
expanded scalar values.

An alias is case-sensitive, omits the colon, and follows Java identifier character
rules without whitespace. Aliases require named SQL and do not change JDBC types.
Inherited aliases remain available without `-parameters`. An override without an
alias preserves the inherited alias; an explicit alias replaces it. Conflicting aliases
from unrelated parents require an explicit child resolution. Without an alias, named
scalar inputs need reliable Java parameter names, so a published parent must preserve
those names with `-parameters` or use positional SQL.

See [Statement Parameters](declarative.md#statement-parameters) for SQL NULL predicate
semantics and marker rules, and [Supported Scalar Types](declarative.md#supported-scalar-types)
for canonical null types. Source containers contribute values to each batch item;
they do not expand an SQL `IN` list within a single execution.

## Capture and Binding Lifetime

The imperative terminal first validates its reference arguments and statement state,
then claims the statement for its single terminal operation. It traverses the input,
invokes the binder, validates each complete parameter set, and captures all bindings
before acquiring a batch connection.

Each callback must assign every physical position exactly once. A duplicate,
out-of-range position, or unsupported value throws `IllegalArgumentException`.
Incomplete coverage throws `IllegalStateException`. A statement with existing
ordinary bindings is rejected with `IllegalArgumentException` before traversal and
remains usable for ordinary execution. Once capture starts, source or binder failures
consume the batch statement. Previously selected row or generated-key stages cannot
execute after a batch has claimed their statement.

The binding view is valid only during its callback on the calling thread. It expires
when the callback returns or throws. Later use or use from another thread throws
`IllegalStateException`; null reference arguments are rejected before those checks.
Never retain the view or use it for asynchronous work.

Capture copies mutable byte arrays, SQL `Date`, `Time`, and `Timestamp` values when
bound, preserving timestamp nanoseconds. It captures bound values rather than copying
an entire application object graph. Concurrent mutation of source data remains the
application's responsibility.

`bindNull` records a JDBC type without calling the driver. The type must represent SQL
NULL without a database type name. The provider rejects `NULL`, `REF_CURSOR`, `ARRAY`,
`DISTINCT`, `JAVA_OBJECT`, `REF`, and `STRUCT`. Generated repositories
choose the type from the declared Java scalar rather than database metadata or the
first item.

An empty source consumes the terminal and returns no entries with an exact total of
zero. It invokes no binder and acquires no batch JDBC resources. Caller code, including
an enclosing transaction, still follows its own lifecycle.

## Outcomes and Partial Failures

`BatchResult.entries()` is an immutable list with one entry per captured logical item
in encounter order. Each zero-based `itemIndex()` equals its position in that list.
Results retain neither the original items nor JDBC resources and can be retained or
shared after execution completes.

| Status | `updateCount()` | Meaning |
| --- | --- | --- |
| `EXACT_COUNT` | Present, including zero | The driver reported an exact nonnegative affected-row count. |
| `SUCCESS_NO_INFO` | Empty | The driver reported success without an exact count. |
| `EXECUTE_FAILED` | Empty | The driver explicitly reported item failure. |
| `NOT_REPORTED` | Empty | Reliable item-level evidence is unavailable. It does not establish whether the item was attempted, skipped, committed, or rolled back. |

Successful terminal returns contain only `EXACT_COUNT` and `SUCCESS_NO_INFO` entries.
The executor widens integer JDBC counts to `long`; the return type does not
promise counts beyond the driver's integer range. `totalUpdateCount()` is present
only when all counts are exact and their sum fits in a `long`. It never returns a
partial sum. Empty input has exact total zero.

JDBC acquisition, preparation, binding, execution, unusable driver counts, or cleanup
failures produce a sanitized `JdbcBatchException`, a subtype of `DataException`.
Its `result()` preserves attributable evidence for every captured item. Missing counts
become `NOT_REPORTED`. Drivers may stop at a failed item or report later successes.
A setup failure has no item-level evidence, while a cleanup failure after successful
execution can carry only successful entries.

The exception preserves supported SQLSTATE and vendor-code diagnostics while excluding
SQL, bound values, credentials, and raw driver messages. Source traversal and application
binder failures instead propagate unchanged from the client terminal before batch JDBC
work. An application-authored exception is not automatically redacted.

The entry point above searches the cause chain because a transaction boundary can
wrap a batch failure in `TxException`. It reports the evidence and rethrows the original
failure. Reporting or retaining a result never establishes commit or rollback.

## Transactions and Cleanup

A nonempty batch uses one connection lease and one prepared statement, adds one complete
parameter set per item, and calls JDBC `executeBatch()` once. Helidon closes the statement
and releases the lease before returning or throwing. Operation-owned connections are
closed; participating connections remain under transaction completion ownership.
Cleanup still attempts later releases when an earlier cleanup action fails.

Batching creates no implicit transaction. Registry-managed clients can participate in
the existing local JDBC transaction; standalone clients keep their independent
connection ownership. The example's explicit transaction annotations apply because
the service is obtained from its registry. Both imports use the same registry-managed
client and transaction provider.

Outside a transaction, earlier items may become durable before a later failure. Inside
a transaction, successful counts can describe work that is subsequently rolled back.
Use transactional DML and let failures reach the transaction owner when the batch must
be atomic. Keep DDL and transaction-control SQL out of the batch.

A failure escaping a joined transactional invocation marks the transaction for rollback,
even if its caller catches the failure afterward. Catching a client failure inside the
owning method or transaction callback before it crosses a transaction boundary does
not itself request rollback. The driver can nevertheless abort the transaction or
leave its connection unusable. A capture failure caught inside an otherwise healthy
transaction does not independently prevent other operations from committing.

A later failure during transaction completion follows the transaction exception contract.
It does not retroactively change an existing batch result. Verify database state before
retrying when completion is uncertain. See [Local Transactions](data-jdbc.md#local-transactions)
for propagation and provider restrictions, and [Errors and JDBC Warnings](data-jdbc.md#errors-and-jdbc-warnings)
for shared diagnostic rules.

## Batch Size and Limits

Inputs must be finite and bounded by the application. Accepting an iterator does not
make JDBC execution lazy: Helidon retains every complete binding set and copied payload
before execution. The driver may retain additional batch storage, and detached outcomes
also require space proportional to the number of items.

Partition large inputs explicitly and call a fresh batch terminal for each partition.
Each invocation has its own result indices and failure boundary. Earlier partitions
may already be committed when a later one fails outside a transaction. An explicit
surrounding transaction can group their effects. There is no automatic batch-size
setting, hidden chunking, or retry policy.

Batching executes DML and does not map query rows, return generated keys, derive SQL,
or perform entity persistence. Batch SQL must be literal repository SQL or supplied
through `JdbcClient.create(sql)`. Configured repository statements and reusable
definitions created through `JdbcClient.prepare(...)` do not support batching.
Use the ordinary [statement execution](imperative.md#statement-execution)
and [generated-key operations](imperative.md#return-generated-keys) for those result shapes.
Alternate JDBC client providers may reject the batch terminal with `UnsupportedOperationException`
after reference validation without consuming input. Driver-specific SQL and transaction behavior
still need to match the databases supported by the application.
