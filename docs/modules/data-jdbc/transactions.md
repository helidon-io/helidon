<!--@frontmatter
description: "Manage local JDBC transactions and resource lifetimes"
-->
# Transactions and Resources

## Local Transactions

Generated repositories and `JdbcClient` services managed by the Service Registry can
participate in Helidon local JDBC transactions. A transaction can begin when a managed
service method carries an annotation such as `@Tx.Required`, or when the application
calls `Tx.transaction`. A standalone client created with `JdbcClient.builder()` always
uses its own connection and does not join the local transaction.

Local JDBC transactions are synchronous and remain associated with the thread that
starts them. All Data JDBC operations in a local transaction must use the same data
source. An operation that selects another data source fails before accessing it.

When a local transaction is active, `REQUIRED`, `MANDATORY`, and `SUPPORTED` operations
join it. A failure that escapes a joined transaction scope marks the transaction for
rollback, even when its caller subsequently catches the reported exception. See
[Failure Handling](#failure-handling) for failures caught before that boundary.

A `NEW` operation temporarily suspends the current transaction and starts an independent
transaction with another connection. It does not create a savepoint. An `UNSUPPORTED`
operation also suspends the transaction, but executes with a connection owned by that
operation and with automatic commits enabled. Helidon resumes the suspended transaction
after either operation completes.

[Transaction Types and Annotations](../data.md#transaction-types-and-annotations)
describes the available propagation modes.

Data JDBC participates only in transactions owned by the local JDBC transaction
provider. It does not enlist a connection in JTA, XA, or Jakarta Persistence
transactions that manage their own local resources. If another transaction provider is
active, the JDBC operation fails before acquiring a connection.

If commit, rollback, or connection cleanup fails and Helidon cannot determine the
transaction outcome, verify the resulting database state before retrying the operation.
Guidance for each programming model is available in
[Transactions](transactions.md#declarative-transactions) and [Transaction
Participation](transactions.md#imperative-transactions).

## Declarative Transactions

Repository methods can participate in [local JDBC
transactions](transactions.md#local-transactions). A transaction annotation on a
repository method controls the propagation of that operation:

```java
@Tx.Required
@Jdbc.Statement("UPDATE CONTACT SET STATUS = :status WHERE ID = :id")
@Jdbc.Execution(Jdbc.ExecutionType.UPDATE)
long updateStatus(long id, String status);
```

The Service Registry applies the transaction annotation when it invokes the generated
repository as a managed service. Method transaction annotations are retained only in
source. When a child repository is compiled separately from its parent, the child must
redeclare an inherited method and its transaction annotation.

Data JDBC also recognizes a transaction annotation declared directly on the repository
type. Helidon does not inherit these annotations from parent interfaces, resolve
conflicting annotations, or define precedence between an annotation on the repository
type and one on a repository method.

An application service provides the clearest transaction boundary when several
repository or client operations must succeed or fail together. Placing the transaction
annotation on the service method also avoids the current inheritance and precedence
limitations.

Local transactions are intended for data operations rather than schema changes. [Schema
Management](transactions.md#schema-management) explains how DDL fits into an application
that uses local transactions.

## Imperative Transactions

A client obtained from the Service Registry can participate in a local JDBC transaction.
Transaction annotations take effect on intercepted methods of service instances obtained
from the Service Registry. They do not take effect on directly constructed instances.

Within a registry-managed service, a call to another intercepted method on the same
instance also applies the called method's transaction annotation. For example, an
internal call to an intercepted `@Tx.New` method starts a new transaction, while an
internal call to an intercepted `@Tx.Unsupported` method suspends the current
transaction.

Private methods are not intercepted. A call to a private method stays in the caller's
transaction context and does not apply the private method's transaction annotations.
Annotation processing rejects a transaction annotation declared on a private method when
no interception annotation applies to the service type. When the service type carries an
interception annotation, private methods are excluded from interception and their
transaction annotations are ignored.

In the following example, both statements share the connection associated with the
transaction. If an exception leaves the method, Helidon rolls back their work:

```java
@Tx.Required
public ContactStatus changeStatus(long id, String status) {
    jdbcClient.create("UPDATE CONTACT SET STATUS = ? WHERE ID = ?")
            .bind(1, status)
            .bind(2, id)
            .execute();

    return jdbcClient.create("""
            SELECT ID AS id, NAME AS name, STATUS AS status
            FROM CONTACT
            WHERE ID = ?
            """)
            .bind(1, id)
            .map(row -> new ContactStatus(row.get("id", Long.class),
                                          row.get("name", String.class),
                                          row.get("status", String.class)))
            .one();
}
```

A transaction can also begin programmatically:

```java
ContactStatus contact = Tx.transaction(() -> changeStatus(id, status));
```

Local JDBC transactions are synchronous and remain associated with the thread that
starts them. Every operation in a transaction uses the same data source, while
standalone clients remain outside the transaction.

[Local Transactions](transactions.md#local-transactions) describes propagation modes and
provider restrictions. Because DDL can cause implicit commits, schema changes run
separately as explained in [Schema Management](transactions.md#schema-management).

## Resource Ownership

Helidon materializes each result before returning it and closes the result set and
prepared statement. Outside a local transaction, Helidon also closes the connection used
by the operation. A local transaction retains its connection until the transaction
completes. The API does not return streams, cursors, or iterators that expose JDBC
resources.

`JdbcClient` is safe to share. Each imperative operation begins with a new call to
`JdbcClient.create`. A mutable statement stage accepts one terminal operation and is not
safe for concurrent use. Materialized values and immutable batch results can outlive the
JDBC resources used to produce them.

A nonempty batch closes its prepared statement and releases its connection lease before
returning or throwing. Participating connections remain owned by their transaction.
See [Transactions and Cleanup](batch.md#transactions-and-cleanup).

Resources are also released when database access, mapping, or result validation fails.
[Errors and JDBC Warnings](data-jdbc.md#errors-and-jdbc-warnings) describes the
resulting diagnostic information.

## Failure Handling

A failure escaping a joined transaction scope marks that transaction for rollback.
Catching a client failure inside the scope before it reaches that boundary does not by
itself request rollback. A JDBC error can nevertheless abort a transaction or make its
connection unusable, depending on the failure and database.

Batch source and binder callbacks run during parameter capture, before batch JDBC
acquisition. A capture failure consumes the claimed statement and propagates unchanged
from the client terminal. If caught inside an otherwise healthy transaction, it does
not independently prevent earlier or later valid operations from committing.

An operation outside a local transaction may already have committed before result
processing fails. A batch can also report successful item execution followed by rollback
or failed cleanup. Execution counts do not establish commit or rollback. Let batch
failures leave the owning transaction boundary when atomic effects are required, and
use transactional DML. Verify database state before retrying when the outcome is
uncertain. See [Generated Keys](queries.md#generated-keys) for failures while
processing inserted identifiers and [Errors and JDBC
Warnings](data-jdbc.md#errors-and-jdbc-warnings) for diagnostics.

## Schema Management

Database migration or deployment tools are the preferred way to create and update the
schema. Keeping schema changes separate from transactions that modify application data
also avoids differences in how databases commit DDL.

Data JDBC can execute one DDL statement for isolated setup work, such as creating a test
fixture. It is not a replacement for a database migration or deployment tool. Such a
statement should run outside a local JDBC transaction. `@Tx.Never` rejects the operation
when a transaction is active, while `@Tx.Unsupported` suspends the active transaction
until the statement finishes.

A database can commit DDL or another command implicitly, so a later rollback might not
reverse the resulting changes. The statement API is not suitable for SQL that controls
transactions or changes connection or session state.

