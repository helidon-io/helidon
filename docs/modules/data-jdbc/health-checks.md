<!--@frontmatter
description: "Check data source and JDBC client readiness through Helidon Health"
-->
# Data Source and JDBC Client Health Checks

Use `DataSourceHealthCheck` to check an existing SQL data source. Use
`JdbcClientHealthCheck` to check the actual connection source of an existing Helidon
`JdbcClient`. Each call acquires an independent connection, executes a validation
SELECT, drains its results, and releases its JDBC resources.

Client checks support direct connection settings, registered named data sources, and
application-supplied `DataSource` instances. A data source check needs no JDBC client.
Both checks default to readiness. A database outage can prevent the application from
serving requests while its process continues to run.

> [!NOTE]
> Data JDBC is incubating and is not production ready. Its APIs and behavior may change
> incompatibly or be removed in any release. Before relying on connection recovery with
> HikariCP and MySQL, read the [pool recovery limitation](#pool-recovery-limitation).

## Dependencies

`JdbcClientHealthCheck` is in `io.helidon.data.jdbc` and is included in the
`helidon-data-jdbc` dependency. See [Maven Coordinates](data-jdbc.md#maven-coordinates)
for the runtime and JDBC driver dependencies.

For data source checks without a JDBC client, use the data source support
artifact. `DataSourceHealthCheck` is in `io.helidon.data.sql.datasource`:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.data.sql.datasource</groupId>
  <artifactId>helidon-data-sql-datasource</artifactId>
</dependency>
```

Add the Health observer dependency to expose either check over HTTP:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.webserver.observe</groupId>
  <artifactId>helidon-webserver-observe-health</artifactId>
</dependency>
```

The Helidon application parent or BOM supplies these versions. YAML examples also
require `helidon-config-yaml`. A pooled data source needs its provider dependency, such
as the one shown in [Registered Data Sources](data-jdbc.md#registered-data-sources).
Neither check requires a separate JDBC health artifact. Calling a check directly does
not require a webserver.

## Enable Checks Through Configuration

Discovery is disabled by default for both data sources and clients. Enable checks for
the resources the application needs to monitor. When Observe and Health service
discovery are enabled, the Health observer includes those checks automatically.
See [Enabling Health Support](../health.md#enabling-health-support-and-built-in-health-checks-in-your-application)
for server setup.

### Directly Configured Client

This configuration creates the `contacts` client and a readiness check named `contacts`:

```yaml [application.yaml]
data:
  clients:
    jdbc:
      - name: "contacts"
        connection:
          url: "jdbc:mysql://localhost:3306/contacts"
          username: "contacts"
          password: "contacts-pass"
        health:
          enabled: true
```

The check uses the client's resolved connection source. Direct settings require no
`data.sources.sql` definition or `data-source` reference. The default query is
`SELECT 1`; custom validation SQL is optional.

### Data Source Without a Client

This configuration enables a readiness check named `contacts-source` for a Hikari data
source, without a JDBC client:

```yaml [application.yaml]
data:
  sources:
    sql:
      - name: "contacts-source"
        provider.hikari:
          url: "jdbc:mysql://localhost:3306/contacts"
          username: "contacts"
          password: "contacts-pass"
        health:
          enabled: true
```

The single-object data source configuration form also supports `health`.
Pool validation settings, including Hikari's `connection-test-query` and
`health-check-properties`, do not enable or configure these observer checks.

### Client Using a Named Data Source

To check a client that uses `contacts-source`, add this client definition alongside the
preceding data source definition:

```yaml [application.yaml]
data:
  clients:
    jdbc:
      - name: "contacts"
        data-source: "contacts-source"
        health:
          enabled: true
          query: "SELECT 1"
          query-timeout: "PT3S"
```

Merge the two examples under one `data` key. They enable two readiness checks:
`contacts-source` and `contacts`. Each readiness aggregate request executes both
independently. Clients inherit no health enablement, SQL, timeout, membership, or name
from a data source check. Several clients sharing a source can each have their own
check. Enable only the checks that should contribute to application health.

If only data source health is enabled and client health is omitted or disabled, only
the source's health routes are registered. No client check or alias is created, and
requests do not trigger an additional client probe.

Discovered checks use resource registration names: `contacts` and `contacts-source`
in these examples. If a JDBC client registration has several concrete
`@Service.Named` qualifiers, the check uses the first name in lexicographic order.
A registration without a concrete name uses the logical client name.
A `health.name` entry is ignored. To use a custom health name, construct and register
the check explicitly as shown below.

Duplicate names are allowed. Aggregates execute every applicable check. Named routes
select the first matching check from their own membership list. Choose distinct
resource names or explicitly named checks when you need separate routes.

### Health Settings

These settings belong under either definition's `health` node:

| Setting | Default | Meaning |
| --- | --- | --- |
| `enabled` | `false` | Enables registry discovery for this definition. |
| `query` | `SELECT 1` | Trusted validation SELECT, without bind parameters. |
| `query-timeout` | `PT2S` | Positive whole seconds, up to `2,147,483,647` seconds. |
| `type` | `readiness` | Health membership. Select `liveness` explicitly when required. |

`DataSourceConfig.health()` and `JdbcClientConfig.health()` return non-null policies,
using the defaults above when `health` is omitted. Their builders accept a typed policy
or a builder consumer. Policies hold health settings without capturing a source,
client, or connection configuration. Given an existing `DataSource contactsDataSource`,
either builder form can configure a client:

```java
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.data.jdbc.JdbcClientHealthConfig;

JdbcClientHealthConfig policy = JdbcClientHealthConfig.builder()
        .enabled(true)
        .query("SELECT 1")
        .build();

JdbcClient client = JdbcClient.builder()
        .name("contacts")
        .dataSource(contactsDataSource)
        .health(policy)
        .build();

JdbcClient anotherClient = JdbcClient.builder()
        .name("another")
        .dataSource(contactsDataSource)
        .health(health -> health.enabled(true))
        .build();
```

Building a standalone client publishes no global services. For supported clients,
registry discovery reads the selected client's policy through `prototype().health()`.
If a replacement client is selected, its enablement, SQL, timeout, and membership
apply. A named registration still determines the health name when the replacement's
logical name differs. Data source health policy comes from the selected
`DataSourceConfig`, even when the source instance is replaced.

Each eligible enabled registration publishes one unqualified `HealthCheck` in its owning registry.
Application-wide discovery considers singleton and per lookup JDBC clients.
Data source discovery requests only enabled source names. A source remains available
for ordinary use when its health check is disabled. Client discovery may activate
clients and pools before reading their health policies. A disabled client policy
publishes no check and executes no health SQL.

If a JDBC client provider fails during discovery, Helidon logs a sanitized warning.
When a lookup reaches the failed provider before finding a matching client, Helidon
omits the check for that lookup, even if a lower-priority provider has a match.
Checks for clients that still resolve and unrelated liveness checks remain available.
Aggregate readiness evaluates the registered readiness checks, so it can still report `UP`.

Explicit check builders expose `name`, `query`, `queryTimeout`, and `type`. Their
`config` method applies configuration to the builder. The `enabled` setting controls
discovery and is not a property of these builders. A constructed check runs whenever
its `call()` method is invoked. The `health` node replaces the experimental `health-check`
node. Provider-specific `health-check-properties` remain pool settings.

## Register Checks Programmatically

Create a check for an existing client and add it to a Health observer:

```java
import io.helidon.data.jdbc.JdbcClientHealthCheck;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.health.HealthObserver;

JdbcClientHealthCheck check = JdbcClientHealthCheck.builder()
        .client(jdbcClient)
        .name("contacts-db")
        .build();

HealthObserver observer = HealthObserver.builder()
        .useSystemServices(false)
        .addCheck(check)
        .build();

WebServer server = WebServer.builder()
        .port(8080)
        .featuresDiscoverServices(false)
        .addFeature(ObserveFeature.just(observer))
        .build()
        .start();
```

Here, `jdbcClient` can be a managed client or a standalone client built as shown in
[JDBC Client Configuration](imperative.md#jdbc-client-configuration). This example
supplies its server features and health checks explicitly. `useSystemServices(false)`
excludes discovered checks. Leave discovery enabled when the observer should also
include them. Avoid registering the same logical check both manually and through
configuration.

For an application-owned `contactsDataSource`, construct a source check instead and
pass it to `addCheck`:

```java
import io.helidon.data.sql.datasource.DataSourceHealthCheck;

DataSourceHealthCheck check = DataSourceHealthCheck.builder()
        .dataSource(contactsDataSource)
        .name("contacts-pool")
        .build();
```

Multiple application-supplied data sources need no registry registration. Given
existing `DataSource ordersSource` and `DataSource billingSource`, give their explicit
checks separate names:

```java
var ordersCheck = DataSourceHealthCheck.builder()
        .dataSource(ordersSource)
        .name("orders-db")
        .build();
var billingCheck = DataSourceHealthCheck.builder()
        .dataSource(billingSource)
        .name("billing-db")
        .build();
var observer = HealthObserver.builder()
        .useSystemServices(false)
        .addCheck(ordersCheck)
        .addCheck(billingCheck)
        .build();
```

Attach this observer as in the server example above. Its named readiness and generic
routes end in `/orders-db` and `/billing-db`. The application remains responsible for
the lifecycle of both sources.

An explicitly constructed client check defaults to the supported client's logical
name. An explicitly constructed data source check defaults to `DataSourceHealthCheck`.
Names supplied through either builder must be nonblank. These checks use their own
SQL, timeout, and membership settings. They do not apply a registration policy
automatically.

Construction validates SQL, timeout, and naming without acquiring a connection. A
client check retains the client's actual source. It does not construct another client
or data source. Custom implementations of `JdbcClient` are rejected at construction.
Provider activation can still initialize a pool under its existing lifecycle; this is
separate from constructing or invoking a health check.

## Invoke Checks in a Declarative Application

A repository using `@Jdbc.Client("contacts")` and the direct configuration above uses
the same client whose health check is named `contacts`. Keep health invocation in
an application service. Repository methods continue to describe application SQL.

The registry publishes individual, unqualified `HealthCheck` services. Inject the list
and select a check by its health name:

```java [ContactHealth.java]
package example;

import java.util.List;

import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;
import io.helidon.service.registry.Service;

@Service.Singleton
final class ContactHealth {
    private final List<HealthCheck> checks;

    @Service.Inject
    ContactHealth(List<HealthCheck> checks) {
        this.checks = List.copyOf(checks);
    }

    HealthCheckResponse databaseStatus() {
        return checks.stream()
                .filter(check -> check.name().equals("contacts"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("The contacts database check is not registered."))
                .call();
    }
}
```

Each `databaseStatus()` call runs the check. The observer uses the same registered
check services. A health name does not create a `@Service.Named` qualifier. Resolution
stays within the owning registry, and each check retains the source or client selected
at construction. Checks do not automatically rebind when resources are replaced.

Both check classes also support direct invocation:

```java
import io.helidon.health.HealthCheckResponse;

HealthCheckResponse response = check.call();
```

The call returns the current response without an HTTP request. Checks do not poll,
cache results, or retry failures.

## Validation SQL and Timeout

An explicit query replaces `SELECT 1`. Invalid or failing explicit SQL never falls
back to the default. Supply SQL supported by the database, such as `SELECT 1 FROM DUAL`
where Oracle requires it.

Validation uses these portable lexical rules:

- The first token after whitespace and recognized comments must be `SELECT`.
  Bind markers and leading `WITH`, `CALL`, or `EXPLAIN` are rejected.
- The scanner recognizes `--` line comments when the second dash is followed by
  whitespace, a control character, or the end of the query.
- It recognizes `/* ... */` block comments, including optimizer hints. Nested block
  comments are rejected.
- MySQL executable comments (`/*! ... */`), including versioned forms, are rejected.
  MySQL `#` line comments are not recognized.
- The scanner treats semicolons as statement separators only outside the quoted
  regions and comments it recognizes. Only one trailing separator is allowed,
  followed only by whitespace or recognized comments. It is removed before preparation.

Validation does not guarantee that SQL is free of side effects. Use trusted
SQL and appropriate database permissions.

Each check applies `Statement.setQueryTimeout` before execution and fails if the
timeout cannot be applied. It accepts zero or more rows and drains them without
reading column values. Update counts, missing result sets, and additional result sets
are rejected.

The query timeout does not bound connection acquisition, network operations, or the
whole HTTP request. Configure pool acquisition and driver timeouts separately.

Builders and immutable configurations hide SQL in their text representations, including
policies nested in source or client configurations. The `query()` accessor still returns
the SQL. Configuration for a data source check also hides the captured source, whose
own `toString()` could contain connection information.

## Readiness, Liveness, and HTTP Responses

The default `readiness` membership checks whether the database dependency is usable
for serving requests. Selecting `liveness` explicitly assigns the result to the
application's liveness policy. Both memberships execute the same probe. The choice
determines which aggregate and named routes include it. See [Kubernetes
Probes](../health.md#kubernetes-probes) for the general distinction.

For a uniquely named readiness check `contacts`, the default observer exposes:

| Route | What it evaluates |
| --- | --- |
| `/observe/health/ready/contacts` | The named check through a fresh probe. |
| `/observe/health/check/contacts` | The same named check through another fresh probe. |
| `/observe/health/ready` | All readiness checks. |
| `/observe/health` | All health checks. |

Aggregate readiness can be DOWN while the named database check is UP because another
readiness check failed. For a uniquely named check with `type: liveness`, the named
readiness route returns 404, while `/observe/health/live/contacts` and the generic
named route are available.

An explicit or discovered check name must fit in a single decoded path segment to
have a named HTTP route. A name containing a slash, such as `orders/primary`, still
works for direct calls and appears in aggregate response details. Encoding the slash
as `%2F` does not make the name routable.

There is no bare `/observe/health/check` route. These paths use the default Observe
prefix and Health endpoint. The application name is not automatically prepended.

Inspect the response status and headers with:

```shell [Terminal]
curl -i http://localhost:8080/observe/health/ready/contacts
```

By default, a healthy check returns **204 with no response body**, so a browser can
show a blank page. Enable observer details to return JSON with a healthy 200 response:

```yaml [application.yaml]
server:
  features:
    observe:
      observers:
        health:
          details: true
```

For an explicitly constructed observer, apply this configuration to its builder or
call `details(true)`. Expected database, timeout, traversal, and cleanup failures return
status-only DOWN and HTTP 503. These responses expose no SQL, values, credentials,
driver messages, SQLSTATE, or vendor codes. Enabling observer details does not add
those diagnostics.

Unexpected observer exceptions retain the existing ERROR/500 behavior. Fatal errors
propagate. See [Health](../health.md) for observer configuration and response handling.

## Connections, Transactions, and Pools

Each check acquires its own connection and releases its statements, results, and lease.
It never closes the data source. Client checks preserve supported caller transactions,
including pending work, rollback state, connection association, and completion ownership.
They do not join, start, suspend, or complete the caller's transaction. Managed client
checks reject failed or foreign transaction contexts before acquisition.

The acquired connection must already have auto-commit enabled. A check never calls
`setAutoCommit`, `commit`, or `rollback`, including during failure cleanup.

A data source check has no client transaction manager. Sources that associate
connections with external transactions must supply independent connections themselves.
Calling `getConnection()` twice does not establish independence if the source returns
a caller's associated connection.

Pools must discard entries for unsafe physical connections that have been aborted.

Each invocation needs pool capacity in addition to any caller-held connection. Pool
exhaustion returns DOWN without borrowing that connection or changing caller work.
Concurrent probes use separate acquisitions, although a pool can reuse a physical
connection across completed invocations. See [Local Transactions](data-jdbc.md#local-transactions).

### Pool Recovery Limitation

With HikariCP 7.1.0 and MySQL Connector/J 26.7.0, aborting and closing an unsafe
connection may leave its terminated physical connection available for reuse in the
pool. A subsequent health check or ordinary query can therefore fail even while the
database is available. This limitation also applies to data sources created by
Helidon's Hikari provider.
