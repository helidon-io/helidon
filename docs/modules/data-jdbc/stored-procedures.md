<!--@frontmatter
description: "Call stored procedures and functions, read outputs, and materialize REF_CURSOR results"
-->
# Stored Procedures and Functions

Data JDBC calls database routines through generated repository methods or the
imperative `JdbcClient` API. A call can supply input values, read scalar outputs,
materialize cursor rows, and process a sequence of result sets and update counts.

Both programming models use JDBC `CallableStatement`. SQL syntax, available output
types, and cursor support depend on the database and driver. The examples below use
Oracle and run against the schema owned by the configured database user.

Helidon supports two JDBC escape forms. It does not determine whether the target
routine is a database procedure or function. The application supplies the SQL and
driver connection settings that its database requires.

| Escape form | Parameter positions |
| --- | --- |
| `{call ROUTINE(...)}` | Arguments begin at JDBC position one. There is no leading return registration. |
| `{? = call ROUTINE(...)}` | The leading return is OUT at JDBC position one. Arguments begin at position two. |

A leading return must be registered even when the application discards its value.
For example, `Call.execute()` discards a registered return, while `Call.outputs()`
captures it. Without a leading return, values exposed by the driver as direct result
sets follow the normal result mapping rules. Support for a particular routine and
escape form depends on the driver. Helidon preserves the selected form and reports
driver failures without trying another form.

> [!NOTE]
> Helidon Data JDBC is incubating. Its APIs and behavior may change incompatibly
> or be removed in any release.

## Prepare the Examples

Use the runtime dependencies described in [Maven Coordinates](data-jdbc.md#maven-coordinates)
and the compiler setup in [Annotation Processing](declarative.md#annotation-processing),
with an Oracle JDBC driver instead of the MySQL driver. Configure an `orders` client
connected to the example schema as described in [Configuration](data-jdbc.md#configuration).
Calls use the same client configuration as queries and updates; no additional Helidon callable
settings are required.

Create these tables and rows once, outside an application data transaction:

```sql
-- An order has a status and zero or more line items.
CREATE TABLE CUSTOMER_ORDER (
    ID NUMBER(19) PRIMARY KEY,
    STATUS VARCHAR2(20 CHAR) NOT NULL
);

CREATE TABLE ORDER_LINE (
    ID NUMBER(19) PRIMARY KEY,
    ORDER_ID NUMBER(19) NOT NULL REFERENCES CUSTOMER_ORDER(ID),
    PRODUCT VARCHAR2(100 CHAR) NOT NULL,
    QUANTITY NUMBER(9) NOT NULL,
    UNIT_PRICE NUMBER(12, 2) NOT NULL
);

-- Order 42 has two lines totaling 44.90. Order 43 has no lines.
INSERT INTO CUSTOMER_ORDER VALUES (42, 'NEW');
INSERT INTO CUSTOMER_ORDER VALUES (43, 'SHIPPED');
INSERT INTO ORDER_LINE VALUES (101, 42, 'Notebook', 2, 19.95);
INSERT INTO ORDER_LINE VALUES (102, 42, 'Pen', 1, 5.00);
COMMIT;
```

Run the routine definitions below with a database tool such as SQL*Plus. The `/` after
each definition executes the PL/SQL buffer in that tool; it is not part of the SQL
passed to `JdbcClient`.

Add the declarative method snippets to this repository interface:

```java
import java.math.BigDecimal;
import java.sql.JDBCType;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import io.helidon.common.Api;
import io.helidon.data.Data;
import io.helidon.data.jdbc.Jdbc;
import io.helidon.data.jdbc.JdbcClient;

@SuppressWarnings(Api.SUPPRESS_INCUBATING)
@Data.Repository
@Data.Provider("jdbc")
@Jdbc.Client("orders")
public interface OrderRepository {
    // Add the repository methods shown below.
}
```

Application snippets assume that `repository` and `client` refer to this repository
and the `orders` client obtained from the Service Registry. Place those snippets in
application methods. The records shown below are application result types in the
repository package.

## IN Parameters and a Scalar OUT Parameter

An IN parameter supplies a value to the routine. An OUT parameter provides a value
back to the caller. This procedure reads the status of one existing order:

```sql
CREATE OR REPLACE PROCEDURE READ_STATUS (
    P_ORDER_ID IN NUMBER,
    P_STATUS OUT VARCHAR2
) AS
BEGIN
    SELECT STATUS INTO P_STATUS
    FROM CUSTOMER_ORDER WHERE ID = P_ORDER_ID;
END;
/
```

The repository method declares the output by its SQL marker name:

```java
@Jdbc.Statement("{call READ_STATUS(:orderId, :status)}")
@Jdbc.OutParameter("status")
String status(long orderId);
```

The `orderId` argument supplies the IN value. There is no `status` argument, so that
position is OUT only. Helidon infers the output registration type `VARCHAR` from the
`String` return type. `repository.status(42L)` returns `"NEW"`.

The equivalent imperative call registers the output explicitly:

```java
JdbcClient.OutParameters outputs = client.create("{call READ_STATUS(?, ?)}")
        .bind(1, 42L)
        .call()
        .out("status", 2, JDBCType.VARCHAR)
        .outputs();

// Read the captured value after callable cleanup, without accessing JDBC.
String status = outputs.get("status", String.class);
```

Positions start at one and follow SQL marker order. The name `"status"` is a logical
name used to retrieve the output; it need not match the database parameter name
`P_STATUS`. Helidon binds IN values through JDBC setters and registers OUT values
through `registerOutParameter`. JDBC does not require a separate IN registration.
See the Java SE 27 [CallableStatement contract](https://docs.oracle.com/en/java/javase/27/docs/api/java.sql/java/sql/CallableStatement.html).

## INOUT Parameters

An INOUT parameter receives an initial value and returns a replacement. This procedure
increments a caller-supplied check count without storing it in a table:

```sql
CREATE OR REPLACE PROCEDURE INCREMENT_CHECKS (P_CHECKS IN OUT PLS_INTEGER) AS
BEGIN
    P_CHECKS := P_CHECKS + 1;
END;
/
```

The method argument and output declaration name the same marker:

```java
@Jdbc.Statement("{call INCREMENT_CHECKS(:checks)}")
@Jdbc.OutParameter("checks")
int incrementChecks(int checks);
```

`repository.incrementChecks(3)` returns `4`. Helidon binds the initial value and
registers the same position as an `INTEGER` output. The Java argument itself is not
modified; the method return provides the new value.

The imperative API also binds and registers the same position:

```java
int updatedChecks = client.create("{call INCREMENT_CHECKS(?)}")
        .bind(1, 3)
        .call()
        .out("checks", 1, JDBCType.INTEGER)
        .outputs()
        .get("checks", Integer.class);
```

For declarative calls, an SQL-bound argument supplies an IN value. A declared output
without a matching input is OUT. An input and output with the same effective name form
INOUT. The effective input name comes from `@Data.Param` when present, or the Java
argument name otherwise. Output markers must occur exactly once. Ordinary declarative OUT and INOUT
arguments require named SQL markers, including when SQL comes from configuration.
Imperative calls retain explicit positional registration. Helidon validates that the
input type is compatible with the output registration.

## REF_CURSOR Results

A REF_CURSOR identifies an open database query result. Oracle provides the predefined
`SYS_REFCURSOR` type, so these examples need no `CREATE TYPE` statement. Data JDBC reads
the cursor, maps its rows to Java values, and closes it before returning the
materialized result.

This procedure opens an ordered query for the requested order. A null or negative
identifier returns no cursor, while an order with no matching lines returns an open
cursor containing zero rows:

```sql
CREATE OR REPLACE PROCEDURE READ_ORDER_DETAILS (
    P_ORDER_ID IN NUMBER,
    P_DETAILS OUT SYS_REFCURSOR
) AS
BEGIN
    IF P_ORDER_ID IS NULL OR P_ORDER_ID < 0 THEN
        P_DETAILS := NULL;
        RETURN;
    END IF;

    OPEN P_DETAILS FOR
        SELECT ID, PRODUCT, QUANTITY, UNIT_PRICE AS unitPrice
        FROM ORDER_LINE WHERE ORDER_ID = P_ORDER_ID ORDER BY ID;
END;
/
```

Declare the row type and repository method:

```java
record OrderLine(long id, String product, int quantity, BigDecimal unitPrice) {
}
```

```java
@Jdbc.Statement("{call READ_ORDER_DETAILS(:orderId, :details)}")
@Jdbc.OutParameter("details")
List<OrderLine> details(long orderId);
```

The `List<OrderLine>` return type lets Helidon infer `REF_CURSOR`. Column labels match
the record components without regard to case. Each cursor row produces one `OrderLine`.

```java
List<OrderLine> lines = repository.details(42L);
// Process detached rows after the repository method returns.
lines.forEach(System.out::println);
```

The list contains two detached values. It remains usable after the call and its JDBC
resources have closed. `repository.details(43L)` returns an empty list.

The imperative equivalent supplies a row mapper and materializes the selected cursor:

```java
JdbcClient.RowMapper<OrderLine> lineMapper = row ->
        new OrderLine(row.get("id", Long.class),
                      row.get("product", String.class),
                      row.get("quantity", Integer.class),
                      row.get("unitPrice", BigDecimal.class));

List<OrderLine> lines = client.create("{call READ_ORDER_DETAILS(?, ?)}")
        .bind(1, 42L)
        .call()
        .out("details", 2, JDBCType.REF_CURSOR)
        .map(result -> {
            result.skipRemainingResults();
            return result.cursor("details", lineMapper).list();
        });

lines.forEach(System.out::println);
```

`skipRemainingResults()` advances past direct result sets and update counts before
output retrieval. It does not discard registered cursor outputs. `list()` reads all
cursor rows into memory, so constrain large results in the routine's query.

### Optional Cursors and Row Cardinality

Use `Optional<List<T>>` to distinguish an absent cursor from an empty cursor:

```java
@Jdbc.Statement("{call READ_ORDER_DETAILS(:orderId, :details)}")
@Jdbc.OutParameter("details")
Optional<List<OrderLine>> optionalDetails(Long orderId);
```

`repository.optionalDetails(null)` returns `Optional.empty()`. The generated method
binds the null reference as typed SQL `NULL`. `repository.optionalDetails(43L)` returns
a present optional containing an empty list.

In imperative code, `result.optionalCursor("details", lineMapper)` returns an
`Optional<JdbcClient.Rows<OrderLine>>`. Materialize it inside the call callback with
`.map(rows -> rows.list())`; the row stage cannot escape the callback.

| Declarative cursor result | Meaning |
| --- | --- |
| `T` | Requires a nonnull cursor containing exactly one row. |
| `Optional<T>` | Requires a nonnull cursor containing zero or one row. |
| `List<T>` | Requires a nonnull cursor and materializes all rows. |
| `Optional<List<T>>` | Accepts a null cursor, or materializes all rows of a present cursor. |

The corresponding imperative terminals are `one()`, `optional()`, and `list()`.
For a required cursor, SQL `NULL` fails with `IllegalArgumentException`. A missing row
for `one()` raises `NoResultException`, and more than one row for `one()` or
`optional()` raises `NonUniqueResultException`.

A method returning one cursor can select a row mapper with
`@Jdbc.RowMapper`. See [Custom Row Mapping](declarative.md#custom-row-mapping) for service
registration and mapper selection. A singular scalar cursor, such as an `Integer`
read from one cursor row, needs an explicit `type = JDBCType.REF_CURSOR` to distinguish
it from a scalar OUT value.

## Stored Function Returns

A stored function has a return value in addition to its arguments. This function
calculates an order total. An existing order with no lines totals zero; an unknown or
null identifier produces SQL `NULL`:

```sql
CREATE OR REPLACE FUNCTION ORDER_TOTAL (P_ORDER_ID IN NUMBER) RETURN NUMBER AS
    TOTAL NUMBER(12, 2);
BEGIN
    SELECT NVL(SUM(L.QUANTITY * L.UNIT_PRICE), 0) INTO TOTAL
    FROM CUSTOMER_ORDER O LEFT JOIN ORDER_LINE L ON L.ORDER_ID = O.ID
    WHERE O.ID = P_ORDER_ID GROUP BY O.ID;
    RETURN TOTAL;
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        RETURN NULL;
END;
/
```

The leading `? = call` in JDBC escape syntax reserves position one for the function
return. An unnamed method-level `@Jdbc.OutParameter` registers that position with the
logical name `return`:

```java
@Jdbc.Statement("{? = call ORDER_TOTAL(:orderId)}")
@Jdbc.OutParameter
Optional<BigDecimal> total(Long orderId);
```

Helidon infers `DECIMAL` from `Optional<BigDecimal>`. The input starts at position two.
`repository.total(42L)` contains `44.90`, while `repository.total(null)` is empty.

The imperative call makes those positions explicit:

```java
Optional<BigDecimal> total = client.create("{? = call ORDER_TOTAL(?)}")
        .bind(2, 42L)
        .call()
        .out("return", 1, JDBCType.DECIMAL)
        .outputs()
        .optional("return", BigDecimal.class);
```

An unnamed method output is valid only when the SQL has a leading return slot. An
ordinary OUT parameter names its marker, as in `@Jdbc.OutParameter("status")`.

## Multiple Outputs in a Record

This procedure combines a scalar OUT value, an INOUT counter, and a cursor OUT value:

```sql
CREATE OR REPLACE PROCEDURE READ_ORDER_STATUS (
    P_ORDER_ID IN NUMBER,
    P_STATUS OUT VARCHAR2,
    P_CHECKS IN OUT PLS_INTEGER,
    P_DETAILS OUT SYS_REFCURSOR
) AS
BEGIN
    SELECT STATUS INTO P_STATUS
    FROM CUSTOMER_ORDER WHERE ID = P_ORDER_ID;
    P_CHECKS := P_CHECKS + 1;
    OPEN P_DETAILS FOR
        SELECT ID, PRODUCT, QUANTITY, UNIT_PRICE AS unitPrice
        FROM ORDER_LINE WHERE ORDER_ID = P_ORDER_ID ORDER BY ID;
END;
/
```

An unannotated record holds the detached outputs. Declare each output on the repository
method, using the exact name of its matching record component:

```java
record MaterializedOrderStatus(
        String status,
        int checks,
        List<OrderLine> details) {
}
```

```java
@Jdbc.Statement("{call READ_ORDER_STATUS(:orderId, :status, :checks, :details)}")
@Jdbc.OutParameter("status")
@Jdbc.OutParameter("checks")
@Jdbc.OutParameter("details")
MaterializedOrderStatus readStatus(long orderId, int checks);
```

`orderId` is IN, `status` and `details` are OUT, and `checks` is INOUT because the method
argument matches its output marker. The component types infer `VARCHAR`, `INTEGER`,
and `REF_CURSOR`. The output annotations supply registrations; the record supplies
the Java types and constructor order.

```java
MaterializedOrderStatus status = repository.readStatus(42L, 0);
System.out.println(status.status()); // Prints NEW.
System.out.println(status.checks()); // Prints 1.
status.details().forEach(System.out::println);
```

The equivalent imperative call constructs the same record explicitly. It reuses the
`lineMapper` from the cursor example:

```java
MaterializedOrderStatus status = client.create("{call READ_ORDER_STATUS(?, ?, ?, ?)}")
        .bind(1, 42L)
        .bind(3, 0)
        .call()
        .out("status", 2, JDBCType.VARCHAR)
        .out("checks", 3, JDBCType.INTEGER)
        .out("details", 4, JDBCType.REF_CURSOR)
        .map(result -> {
            result.skipRemainingResults();
            String orderStatus = result.get("status", String.class);
            int checks = result.get("checks", Integer.class);
            List<OrderLine> details = result.cursor("details", lineMapper).list();
            return new MaterializedOrderStatus(orderStatus, checks, details);
        });
```

Read scalar outputs before selecting cursors. Materialize each cursor before selecting
another output. Generated output-record mapping follows this order automatically.
The resulting record contains detached values and does not own a database connection.

Automatic aggregation requires at least two named method outputs. Their names must
match every record component exactly, including case, with no missing or extra outputs.
SQL marker order determines JDBC positions, and record component order determines
constructor arguments. Annotation order does not change either order.

Result records do not declare outputs or select cursor row mappers. Use a `CallMapper`
callback when output names differ from component names, when wrapping a single scalar
in a record, or when aggregate cursors need custom row mappers. Declare those outputs
on the method and supply each mapper to `result.cursor(...)` inside the callback.
A declarative leading callable return must be the only output declaration. Imperative
callbacks and snapshots can still combine explicitly registered outputs where the
database and driver support them.

## Callable APIs and Execution Selection

| Declarative API | Purpose |
| --- | --- |
| `@Jdbc.Statement` | Supplies literal or configured call SQL. Ordinary argument outputs require named markers. |
| `@Jdbc.Execution(Jdbc.ExecutionType.CALL)` | Explicitly selects callable execution, including calls with only IN parameters. |
| `@Jdbc.OutParameter` | Declares an output on a repository method. Its members are `value`, `type`, `typeName`, and `scale`. |
| `@Jdbc.OutParameterDeclarations` | Holds repeated output declarations. Repeating `@Jdbc.OutParameter` is equivalent. |
| `@Jdbc.RowMapper` | Selects a row mapper for a method returning one cursor. |
| `Consumer<JdbcClient.CallResult>` | Gives a `void` method a synchronous callback for processing callable results. |
| `JdbcClient.CallMapper<R>` | Gives a method returning `R` a callback that constructs its detached result. |

Output declarations select callable execution automatically. Without outputs, declare
`CALL` explicitly; Helidon does not select callable execution by inspecting the SQL.
Callable methods cannot request generated keys or explicitly select `QUERY` or `UPDATE`.

In imperative code, `client.create(sql)` and `bind(...)` configure the statement.
`call()` selects callable execution, and `out(name, index, type)` registers an output.
Neither operation accesses the database. Additional `out` overloads accept a database
type name or a nonnegative `NUMERIC` or `DECIMAL` scale. Output names and positions
must be unique. Bind only the IN side of each parameter, including every INOUT value.

Prepared definitions also support `out(name, type)`. The name resolves a named SQL
marker or a name supplied through the explicit list passed to `prepare(sql, names)`.
`returns(type)` registers a leading callable return at position one with the lookup
name `"return"`. It also works with literal positional calls. Both registration
forms provide overloads for a database type name or a nonnegative `NUMERIC` or `DECIMAL`
scale. See [SQL In Configuration](configured-statements.md#stored-procedures-and-functions)
for parameter-style rules and matching declarative and imperative examples.

Configured repository SQL must match the method's declaration of a leading return.
Helidon rejects a missing or unexpected leading return during repository activation,
even when marker counts or logical names otherwise match. Ordinary arguments and
outputs named `return` remain valid when there is no leading return slot.

To obtain leading return validation during activation, regenerate repositories using
matching versions of the JDBC code generator and runtime. Existing compiled
repositories retain their generated preparation behavior.

| Call terminal | Behavior |
| --- | --- |
| `execute()` | Executes once and discards direct results and outputs. It returns no update count. |
| `outputs()` | Discards direct results and returns an immutable scalar `OutParameters` snapshot. |
| `consume(action)` | Invokes a synchronous callback with a scoped `CallResult`. |
| `map(mapper)` | Invokes a synchronous callback and returns its nonnull detached value. |

A scalar snapshot supports canonical boolean, numeric, text, binary, and local temporal
values. It excludes cursors, named database types, JDBC locators, and offset temporal
values. Its getters require the canonical Java class for the registration, such as
`BigDecimal.class` for `DECIMAL`; they do not convert a captured value to another type.
Use `Call.map` when the result combines cursor rows with scalar outputs.

### Result Ownership and Lifetime

Detached results remain usable after callable cleanup. Scoped views require the
active call and must stay on the invocation thread inside its callback.

| Result form | Contents | Lifetime and cleanup |
| --- | --- | --- |
| `JdbcClient.OutParameters` | Captured canonical scalar values. | Reads perform no JDBC work and remain valid after the call. Helidon releases the callable resources; the caller does not close the snapshot. |
| `JdbcClient.CallResult` | Scoped access to direct results, counts, scalar outputs, and cursors. | Use it only during the synchronous callback. Helidon owns cleanup. Row views and row stages also have limited lifetimes. |
| Materialized scalars, records, and cursor lists | Java values read or constructed before the call ends. | Detached contents can be retained and processed after the call. The caller does not close them to release JDBC resources. |

For example, `Call.map` can construct an application record inside the callback and
return it for later use:

```java
record StatusResult(String status) {
}

StatusResult status = client.create("{call READ_STATUS(?, ?)}")
        .bind(1, 42L)
        .call()
        .out("status", 2, JDBCType.VARCHAR)
        .map(result -> {
            result.skipRemainingResults();
            return new StatusResult(result.get("status", String.class));
        });

// The record contains a detached String and remains usable after callable cleanup.
System.out.println(status.status());
```

Cursor materialization follows the same rule. The `list()` terminal in the
[REF_CURSOR example](#ref_cursor-results) maps every row to an `OrderLine` before
closing the selected cursor. The returned list contains application values rather
than an open cursor.

Capturing a reference to `CallResult` does not extend its lifetime. Access after the
callback returns, or from another thread, fails with `IllegalStateException`. Row
views expire when their mapper returns, and direct row stages expire when the call
advances to another event.

Detachment does not make an application object graph deeply immutable. Custom
mappers must return detached values and must not retain `CallResult`, row views,
or row stages inside returned records or collections. Helidon does not recursively
inspect application objects to remove borrowed resources.

### Output Type Inference

For one automatically mapped method output, Helidon derives an omitted JDBC type from
the resolved return type. For an automatic output record, it derives each registration
from the component matched by the method output name. `Optional<T>` uses the underlying
scalar type; supported cursor shapes infer `REF_CURSOR` as shown above. Inference
happens during compilation and does not inspect routine metadata, callback bodies, or
input values.

Scalar outputs use the canonical mappings listed in [Supported Scalar
Types](declarative.md#supported-scalar-types), including `String` to `VARCHAR`, `int` to
`INTEGER`, and `BigDecimal` to `DECIMAL`. Primitive and wrapper equivalents use the
same registration. An explicit `type` overrides inference, even when it is
`JDBCType.NULL`.

An absent type does not default to `VARCHAR`. That registration describes character
data and would make numeric, temporal, binary, and cursor outputs depend on driver
conversions or fail. `VARCHAR` is inferred when the Java result is `String`. The
annotation's default `JDBCType.NULL` marks an omitted type; Helidon does not use it as
an inferred registration.

Declare explicit types for callbacks, scalar snapshots, `void` methods, multiple
method-level outputs without an automatic aggregate, or outputs with a nonempty
`typeName`. Those forms do not provide one automatically mapped Java output type for
each declaration. The imperative `out(...)` methods always require a JDBC type.
The optional `scale` applies only to
`NUMERIC` or `DECIMAL` and cannot be combined with `typeName`.

### Repository Result Selection

| Method contract | Selected result |
| --- | --- |
| `void` without a callback | Executes and discards results. Output declarations still require explicit types. |
| `Consumer<JdbcClient.CallResult>` parameter | Processes results in the callback; the method returns `void`. |
| `JdbcClient.CallMapper<R>` parameter | Returns the callback's detached value of type `R`. |
| `JdbcClient.OutParameters` return | Captures explicit scalar registrations in a snapshot. |
| Record with at least two named method outputs | Matches output names to components and reads scalar and cursor values. |
| One method-level output | Maps that scalar or cursor to the method return type. |
| No output declarations and a mapped return | Maps the first direct result set after any initial update counts. |

A callable method accepts at most one fully typed callback parameter. That parameter
is a control argument, not an SQL input. Multiple method-level outputs return through
a scalar snapshot, a callback, or an automatic record aggregate.
SQL inputs must be supported scalar types. Reference arguments can be null and are
bound as typed SQL `NULL`; imperative `bind` arguments must be nonnull.

Inherited declarations retain their callable annotations and resolve generic input and
result types before generation. Conflicting JDBC contracts from unrelated parents
fail compilation. An explicit or inherited `@Data.Param` alias supplies an input name
without requiring `-parameters`. Each unaliased input needs a reliable Java name for
named or configured SQL. For separately compiled
parents, preserve those names with `-parameters` or redeclare the method in the
repository with explicit names or aliases and the required callable annotations.
Partially aliased methods still need reliable names for their unaliased inputs.

Literal positional calls without output declarations and calls with one
leading return need no parameter name metadata. Their IN arguments bind in declaration
order, starting at position one without a leading return and position two with one.
Aliases apply to input and INOUT matching and do not rename output declarations or the
leading return. Aliased inputs require named SQL.
See [Repository Interface Inheritance](declarative.md#repository-interface-inheritance)
and [Declarative Transactions](declarative.md#transactions) for the general inheritance
and transaction-annotation rules.

## Scoped Callbacks and Result Traversal

Use a callback when application code needs to choose which outputs to read or process
direct result sets and update counts. For example, the cursor procedure can expose
its results to a declarative callback:

```java
@Jdbc.Statement("{call READ_ORDER_DETAILS(:orderId, :details)}")
@Jdbc.OutParameter(value = "details", type = JDBCType.REF_CURSOR)
void consumeDetails(long orderId, Consumer<JdbcClient.CallResult> action);
```

```java
repository.consumeDetails(42L, result -> {
    result.skipRemainingResults();
    List<OrderLine> lines = result.cursor("details", lineMapper).list();
    lines.forEach(System.out::println);
});
```

The imperative equivalent uses `Call.consume` with the same callback. Both forms
materialize rows through `list()`. A callback does not expose a Java stream or a live
JDBC `ResultSet`, and its `CallResult`, row stages, and row views must not escape.

`CallResult.resultKind()` identifies the current direct event:

| Event | Available operation |
| --- | --- |
| `ROWS` | Select the result with `rows(mapper)`, then call `one()`, `optional()`, or `list()`. |
| `UPDATE_COUNT` | Read `updateCount()`, including a count of zero. |
| `END` | Read registered scalar outputs or select registered cursors. |

Call `next()` to close the current direct result and advance. Select a row stage only
once, and finish its terminal before advancing. `skipRemainingResults()` discards
unneeded direct events until `END`. When a callback finishes successfully, Helidon
discards any remaining direct events and releases its resources.

Routine support for direct results and update counts varies by driver. A `REF_CURSOR`
OUT parameter is a separate output channel, not a direct `ROWS` event. Consume direct
events before reading outputs, read scalar outputs first, and consume cursor outputs
sequentially.

## Resources, Transactions, and Database Support

Helidon owns the callable statement and its result sets. It closes them on success
and failure. Outside a local transaction, it also closes the operation's connection.
A managed local transaction retains its connection until completion. Returned scalars,
records, lists, and snapshots must have detached contents as described in
[Result Ownership and Lifetime](#result-ownership-and-lifetime).

Callable cleanup is separate from transaction completion. A detached result can be
returned before the surrounding transaction commits. Having the result does not
establish that database changes have been committed.

Callable callbacks run synchronously on the invocation thread. Each row view expires
when its row mapper returns. The call retains its connection reservation until the
outer callback and cleanup finish, even after a cursor terminal has closed its rows.
Do not run another operation on the same reserved transaction connection or suspend
that transaction from inside the callback.

JDBC and mapping failures retained during callable processing still fail the operation
when a callback catches them. In a joined local transaction, they mark it for rollback.
Ordinary argument-validation errors that occur before processing are recoverable.
See [Errors and JDBC Warnings](data-jdbc.md#errors-and-jdbc-warnings) for diagnostics.

Callable failure retention differs from ordinary imperative queries and generated-key
processing. Catching those direct client failures inside a transaction task can allow
the transaction to commit. A failure that escapes a joined transaction boundary marks
the transaction for rollback, even if an outer caller catches it afterward.

Stored routines that commit, roll back, or implicitly end a transaction are unsupported
inside a Helidon local JDBC transaction. Helidon cannot reliably detect or reverse
such database actions. Outside a transaction, database changes may already be committed
when output processing fails, so a failure does not establish that retrying is safe.

Oracle supports the `SYS_REFCURSOR` examples on this page. PostgreSQL refcursor access
requires an active database transaction; use a managed client in a Helidon local
transaction. For PostgreSQL routines that mix procedure and function calls, the driver
setting `escapeSyntaxCallMode=callIfNoReturn` distinguishes calls by their leading
return slot. A PostgreSQL function with multiple OUT parameters instead uses
`escapeSyntaxCallMode=select` and a call without a leading return slot. Use a client
configured for that function profile; `callIfNoReturn` sends the same call form to a
procedure. These are driver connection settings. See the
[pgJDBC routine guide](https://jdbc.postgresql.org/documentation/callproc/).
MySQL Connector/J does not support
`REF_CURSOR` output registration. Use the routine's direct result sets when it provides
them; Data JDBC does not substitute direct results for an unsupported cursor output.

Unsupported registrations, retrievals, or conversions fail rather than returning empty
results. A driver-reported `SQLException` becomes a Data `DataException`. Database
routines, call syntax, and driver capabilities must match the deployed database.
Creating or replacing the routines remains schema setup, separate from application
data transactions.
