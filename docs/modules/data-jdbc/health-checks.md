<!--@frontmatter
description: "Check data source and JDBC client readiness through Helidon Health"
-->
# Data Source and JDBC Client Health Checks

Use `DataSourceHealthCheck` to check an existing SQL data source, or
`JdbcClientHealthCheck` to check the connection source used by an existing Helidon
`JdbcClient`. Each invocation obtains an independent connection, executes a SELECT,
drains its results, and releases its resources before returning UP or DOWN.

Client checks support direct connection settings, registered named data sources, and
application-supplied `DataSource` instances. A data source check needs no JDBC client.
Both checks default to readiness because a database outage can prevent the application
from serving requests while the application process continues to run.

> [!NOTE]
> Data JDBC is incubating and is not production ready. Its APIs and behavior may change
> incompatibly or be removed in any release. Review the [pool recovery limitation](#pool-recovery-limitation)
> before relying on unsafe-connection recovery with HikariCP and MySQL.

## Dependencies

`JdbcClientHealthCheck` is in `io.helidon.data.jdbc` and is included in the existing
`helidon-data-jdbc` dependency. See [Maven Coordinates](data-jdbc.md#maven-coordinates)
for the runtime and JDBC driver dependencies.

For data source checks without a JDBC client, use the existing data source support
artifact. `DataSourceHealthCheck` is in `io.helidon.data.sql.datasource`:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.data.sql.datasource</groupId>
  <artifactId>helidon-data-sql-datasource</artifactId>
</dependency>
```

Add the existing Health observer dependency to expose either check over HTTP:

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

Discovery is disabled by default at both data source and client scope. Enable the
definitions that the application needs. With the existing Observe and Health service
discovery enabled, the Health observer includes the resulting checks automatically.
See [Enabling Health Support](../health.md#enabling-health-support-and-built-in-health-checks-in-your-application)
for server setup.

### Directly Configured Client

This creates the `contacts` client and enables its readiness check as `contacts-db`:

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
          name: "contacts-db"
```

The check uses the client's resolved connection source. Direct settings require no
`data.sources.sql` definition or `data-source` reference. The default query is
`SELECT 1`; custom validation SQL is optional.

### Data Source Without a Client

This enables `contacts-pool` over a Hikari data source without defining a JDBC client:

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
          name: "contacts-pool"
```

The existing single-object data source configuration form also supports `health`.
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
          name: "contacts-db"
          query: "SELECT 1"
          query-timeout: "PT3S"
```

Merge the two examples under one `data` key. They enable two readiness checks:
`contacts-pool` and `contacts-db`. An aggregate request executes both independently.
Client checks inherit no enablement, SQL, timeout, membership, or name from a data source
check. Several clients sharing a source can each have their own check. Enable only the
definitions that should contribute to application health.

The service names `contacts` and `contacts-source` select the client and source.
The health names `contacts-db` and `contacts-pool` identify checks and endpoint paths.
An explicit `health.name` takes precedence. Otherwise, a discovered check uses its
owning resource's registration name: `contacts` or `contacts-source` in these examples.
Names need not be unique. An aggregate executes every applicable check, while each
named handler selects the first matching check from its own membership list. Use
distinct names when each check needs an independently addressable route.

Enabling only the data source check creates no client check or alias. For example,
with source health enabled and the client `health` node omitted, only the source's
health routes are registered. Requests do not cause an additional client probe.

### Health Settings

These settings belong under either definition's `health` node:

| Setting | Default | Meaning |
| --- | --- | --- |
| `enabled` | `false` | Enables registry discovery for this definition. |
| `name` | Owning registration name | An optional, nonblank name for the check and its route. |
| `query` | `SELECT 1` | Trusted validation SELECT, without bind parameters. |
| `query-timeout` | `PT2S` | Positive whole seconds, up to `2,147,483,647` seconds. |
| `type` | `readiness` | Health membership. Select `liveness` explicitly when required. |

`DataSourceConfig.health()` and `JdbcClientConfig.health()` return non-null policies,
including when `health` is omitted. Their builders accept a typed policy or a builder
consumer. These policies hold health settings without capturing a source, client, or
connection configuration. Given an existing `DataSource contactsDataSource`, either
builder form can configure a client:

```java
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.data.jdbc.JdbcClientHealthConfig;

JdbcClientHealthConfig policy = JdbcClientHealthConfig.builder()
        .enabled(true)
        .name("contacts-db")
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
        .health(health -> health.enabled(true).name("another-db"))
        .build();
```

Standalone client construction does not publish checks into a registry, even with an
enabled policy. Registry discovery binds an enabled policy to the preferred effective
resource within that registry, preserving the owning definition's policy and name
when the resource is replaced. It publishes each check once as an unqualified
`HealthCheck` service.

Executable check builders separately expose `name`, `query`, `queryTimeout`, and
`type`; `config` applies configuration to a builder. `enabled` controls discovery
and is not an executable check builder property. A constructed check runs whenever
its `call()` method is invoked. The experimental `health-check` node is replaced by
`health`; provider-specific `health-check-properties` keep their existing meaning.

## Register Checks Programmatically

Given an existing client, construct its check and add it to a Health observer:

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

Without an explicit name, an imperative client check uses the supported client's
logical name. A standalone data source check has no registration metadata and retains
`DataSourceHealthCheck` as its fallback name. Names supplied through either builder
must be nonblank. Explicit check builders use their own SQL, timeout, and membership
settings. They do not apply a registration policy automatically.

Construction validates SQL, timeout, and naming without acquiring a connection. The
client check retains the client's actual source; it does not construct another client
or data source. Custom implementations of `JdbcClient` are rejected at construction.
Provider activation can still initialize a pool under its existing lifecycle; this is
separate from constructing or invoking a health check.

## Invoke Checks in a Declarative Application

A repository using `@Jdbc.Client("contacts")` and the direct configuration above uses
the same client whose health check is named `contacts-db`. Keep health invocation in
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
                .filter(check -> check.name().equals("contacts-db"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("The contacts database check is not registered."))
                .call();
    }
}
```

The check runs on each `databaseStatus()` call. Its health name does not create a
`@Service.Named` qualifier. The observer consumes the same registered check services.
Resolution stays within the owning registry; each check retains the source or client
selected when it was constructed, with no automatic rebinding.

Both check classes also support direct invocation:

```java
import io.helidon.health.HealthCheckResponse;

HealthCheckResponse response = check.call();
```

This returns the current response without an HTTP request. It does not poll, cache a
previous result, or retry a failed probe.

## Validation SQL and Timeout

An explicit query replaces `SELECT 1`. Invalid or failing explicit SQL never falls
back to the default. Supply SQL supported by the database, such as `SELECT 1 FROM DUAL`
where Oracle requires it.

The first token after whitespace and comments must be SELECT. Bind markers, multiple
statements, and leading WITH, CALL, or EXPLAIN are rejected. A single trailing semicolon
is allowed and removed before preparation. Quoted text and comments use the existing
JDBC lexical rules. This validation does not establish that SQL is free of side effects;
use trusted SQL and appropriate database permissions.

The probe applies `Statement.setQueryTimeout` before execution and fails if the timeout
cannot be applied. It accepts zero or more rows, drains them without reading column
values, and rejects update counts, missing result sets, and additional result sets.
The query timeout does not bound connection acquisition, network operations, or the
whole HTTP request. Configure pool acquisition and driver timeouts separately.

Generated builders and immutable configurations conceal SQL when rendered as text,
including policies nested in their owning source or client configurations.
The query accessor retains the real SQL. Source-check configuration also conceals the
captured data source, whose own `toString()` could contain connection information.

## Readiness, Liveness, and HTTP Responses

The default `readiness` membership answers whether the database dependency is usable
for serving requests. Explicit `liveness` membership assigns the result to the
application's liveness policy. Both memberships execute the same probe; the choice
changes which aggregate and named routes include it. See [Kubernetes
Probes](../health.md#kubernetes-probes) for the general distinction.

For the readiness check named `contacts-db`, the default observer exposes:

| Route | What it evaluates |
| --- | --- |
| `/observe/health/ready/contacts-db` | The named check through a fresh probe. |
| `/observe/health/check/contacts-db` | The same named check through another fresh probe. |
| `/observe/health/ready` | All readiness checks. |
| `/observe/health` | All health checks. |

Aggregate readiness can be DOWN while the named database check is UP because another
readiness check failed. For a uniquely named check with `type: liveness`, the named
readiness route returns 404, while `/observe/health/live/contacts-db` and the generic
named route are available.
There is no bare `/observe/health/check` route. These paths use the default Observe
prefix and Health endpoint; the application name is not automatically prepended.

Inspect the response status and headers with:

```shell [Terminal]
curl -i http://localhost:8080/observe/health/ready/contacts-db
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
status-only DOWN and HTTP 503. They expose no SQL, values, credentials, driver messages,
SQLSTATE, or vendor codes. Enabling observer details does not add those diagnostics.
Unexpected observer exceptions retain the existing ERROR/500 behavior. Fatal errors
propagate. See [Health](../health.md) for observer configuration and response handling.

## Connections, Transactions, and Pools

Checks acquire their own connections and release their statements, results, and leases.
They never close the data source. Client probes preserve supported caller transactions,
including pending work, rollback state, connection association, and completion ownership.
They do not join, start, suspend, or complete the caller's transaction. Managed client
checks reject failed or foreign transaction contexts before acquisition.

The acquired connection must already have auto-commit enabled. The probe never calls
`setAutoCommit`, `commit`, or `rollback`, including during failure cleanup. A data source
check has no client transaction manager; external transaction-aware sources must supply
independent connections themselves. A second `getConnection()` call alone does not
establish independence if the source returns a caller's associated connection.

Each invocation needs pool capacity in addition to any caller-held connection. Pool
exhaustion returns DOWN without borrowing that connection or changing caller work.
Concurrent probes use separate acquisitions, although a pool can reuse a physical
connection across completed invocations. See [Local Transactions](data-jdbc.md#local-transactions).

### Pool Recovery Limitation

Unsafe connections must be discarded from the pool. The current abort-and-close
sequence does not guarantee immediate healthy reuse for unwrapped HikariCP 7.1.0 pools
with MySQL Connector/J 26.7.0 after an unsafe acquisition is rejected. Helidon's current
Hikari provider also returns an unwrapped Hikari data source. Ordinary successful
checks work, but reliable recovery in this fault scenario remains unresolved. Do not
assume that releasing the logical lease proves that the physical pool entry was
discarded. External sources must provide reliable invalidation as well as independent
acquisition.
