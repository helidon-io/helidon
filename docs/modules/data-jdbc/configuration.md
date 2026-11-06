<!--@frontmatter
description: "Configure JDBC clients, connections, and data sources"
-->
# Clients and Data Sources

## Client Configuration

Helidon creates a `JdbcClient` service for each entry under `data.clients.jdbc`. A
client can use direct JDBC connection properties or refer to an SQL data source
registered with the Service Registry. An imperative application can also create a
standalone client from an existing `DataSource`, in which case the application continues
to own the data source. [Standalone Clients](configuration.md#standalone-clients)
describes that option.

Client names distinguish configurations when an application connects to more than one
data source. The direct connection example creates `contacts` and `audit` clients. A
configuration without a `name` becomes the `@default` client. Repositories use this
default unless `@Jdbc.Client` selects another client, while injected clients use their
configured names as Service Registry qualifiers.

## Direct JDBC Connections

The following configuration creates clients named `contacts` and `audit`:

```yaml [application.yaml]
data:
  clients:
    jdbc:
      - name: "contacts"
        connection:
          username: "user"
          password: "password"
          url: "jdbc:mysql://localhost:3306/contacts"
          jdbc-driver-class-name: "com.mysql.cj.jdbc.Driver"
      - name: "audit"
        connection:
          username: "user"
          password: "password"
          url: "jdbc:mysql://localhost:3306/audit"
          jdbc-driver-class-name: "com.mysql.cj.jdbc.Driver"
```

Every direct connection requires a JDBC URL, while the driver class name can be omitted
when the driver registers itself and recognizes the configured URL.

Although `data.clients.jdbc` is a list, it can contain a single client. In a properties
file, the client indexes begin at zero:

```properties [application.properties]
data.clients.jdbc.0.name=contacts
data.clients.jdbc.0.connection.username=user
data.clients.jdbc.0.connection.password=password
data.clients.jdbc.0.connection.url=jdbc:mysql://localhost:3306/contacts
data.clients.jdbc.0.connection.jdbc-driver-class-name=com.mysql.cj.jdbc.Driver
```

## Registered Data Sources

When several components need to share a data source or connection pool, the JDBC client
can refer to an SQL data source registered with the Service Registry. The following
example uses the HikariCP provider. Its dependency version is supplied by the Helidon
application parent or `helidon-dependencies`, just like the Helidon dependencies shown
in [Maven Coordinates](getting-started.md#dependencies).

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.data.sql.datasource</groupId>
  <artifactId>helidon-data-sql-datasource-hikari</artifactId>
</dependency>
```

Once the data source is registered, its name connects the JDBC client to it:

```yaml [application.yaml]
data:
  url: "jdbc:mysql://localhost:3306/contacts"
  sources:
    sql:
      - name: "contacts-datasource"
        provider.hikari:
          username: "user"
          password: "changeit"
          url: "${data.url}"
  clients:
    jdbc:
      - data-source: "contacts-datasource"
```

The `data-source` value identifies an SQL data source in the Service Registry and must
match its registered name. This example omits the client `name`, so Helidon makes the
client available as `@default`.

Imperative applications can also configure clients managed by the Service Registry in
code or create standalone clients. These options are described in [JDBC Client
Configuration](configuration.md#client-configuration).

## Injected Clients

Clients configured under `data.clients.jdbc` can be injected by provider type and
configured name. The service acknowledges the incubating status of `JdbcClient` with the
focused suppression described in [Annotation
Processing](declarative.md#annotation-processing):

```java
import io.helidon.common.Api;
import io.helidon.data.jdbc.JdbcClient;

@SuppressWarnings(Api.SUPPRESS_INCUBATING)
@Service.Singleton
final class ContactStore {
    private final JdbcClient jdbcClient;

    @Service.Inject
    ContactStore(@Data.ProviderType("jdbc")
                 @Service.Named("contacts")
                 JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }
}
```

The `contacts` qualifier selects the client with the matching configured name.
[Configuration](configuration.md#client-configuration) shows how those names and
connection sources are declared. Because the client is managed by the Service Registry,
it can use the connection associated with a local JDBC transaction.

## Standalone Clients

A standalone client has exactly one connection source. When the application already owns
a `DataSource`, the builder can use it directly:

```java
JdbcClient jdbcClient = JdbcClient.builder()
        .dataSource(contactsDataSource)
        .build();
```

The application continues to own the `DataSource`, while Helidon closes every connection
that it obtains from that source.

The builder can instead refer to an SQL data source registered with the Service
Registry:

```java
JdbcClient jdbcClient = JdbcClient.builder()
        .dataSourceName("contacts-datasource")
        .build();
```

Direct JDBC settings provide the third option:

```java
JdbcClient jdbcClient = JdbcClient.builder()
        .connection(connection -> connection
                .url("jdbc:postgresql://database.example/contacts")
                .jdbcDriverClassName("org.postgresql.Driver"))
        .build();
```

When an application needs to retain or share an immutable client configuration,
`buildPrototype()` produces a `JdbcClientConfig` that can later be supplied to
`JdbcClient.create`:

```java
JdbcClientConfig config = JdbcClient.builder()
        .dataSource(contactsDataSource)
        .buildPrototype();

JdbcClient jdbcClient = JdbcClient.create(config);
```

A standalone client obtains and closes a connection for every operation. Referring to a
named data source does not turn it into a client managed by the Service Registry, so it
remains outside annotated transactions.

## Programmatic Configuration

Client configurations can also be supplied in Java. They need to be registered before
the first lookup of `JdbcClientConfig` or `JdbcClient`. The Service Registry rejects a
later replacement with `ServiceRegistryException`:

```java
JdbcClientConfig contacts = JdbcClient.builder()
        .name("contacts")
        .dataSourceName("contacts-datasource")
        .buildPrototype();

JdbcClientConfig audit = JdbcClient.builder()
        .name("audit")
        .connection(connection -> connection
                .url("jdbc:postgresql://database.example/audit")
                .jdbcDriverClassName("org.postgresql.Driver"))
        .buildPrototype();

Services.set(JdbcClientConfig.class, contacts, audit);
```

A single `Services.set` call receives the complete configuration set. This set replaces
any client configurations loaded from `application.yaml` rather than merging with them.

## Health Checks

Enable health discovery independently under `data.clients.jdbc[].health-check` or
`data.sources.sql[].health-check`. Both default to disabled. Health SQL and timeouts
are separate from connection pool validation settings. See [Data Source and JDBC
Client Health Checks](health-checks.md#enable-checks-through-configuration) for settings
and examples using direct connections, named sources, and sources without a client.

## Parameter Count Cache

`JdbcClient` keeps a bounded cache of parameter counts for SQL that it executes
repeatedly. The default cache holds entries for 256 statements and can be configured
under `properties.jdbc.parameter-count-cache`:

```yaml [application.yaml]
data:
  clients:
    jdbc:
      - name: "contacts"
        data-source: "contacts-datasource"
        properties:
          jdbc:
            parameter-count-cache:
              capacity: 512
              max-sql-length: 8192
```

The cache applies only to SQL supplied through imperative `JdbcClient` operations. It
stores parameter counts rather than prepared statements. SQL longer than
`max-sql-length` remains valid even though its count is not retained.

| Setting | Default | Accepted values |
|---------|---------|-----------------|
| `capacity` | `256` | An integer from `0` through `4096`. A value of `0` disables the cache. |
| `max-sql-length` | `4096` | A positive integer for which `capacity` multiplied by `max-sql-length` does not exceed `16,777,216`. |

Helidon validates this combined limit for each client.
