# io.helidon.data.sql.datasource.hikari.HikariDataSourceConfig

## Description

Hikari connection pool specific configuration for

javax.sql.DataSource

.

## Usages

- [`data.sources.sql.provider.hikari`](../config/io_helidon_data_sql_datasource_ProviderConfig.md#a671b7-hikari)

## Configuration options

| Key | Kind | Type | Description |
|----|----|----|----|
| <span id="a71a56-allow-pool-suspension"></span> `allow-pool-suspension` | `VALUE` | `Boolean` | Set whether pool suspension is allowed |
| <span id="a4af5c-auto-commit"></span> `auto-commit` | `VALUE` | `Boolean` | Set the default auto-commit behavior of connections in the pool |
| <span id="afe4a7-catalog"></span> `catalog` | `VALUE` | `String` | Set the default catalog name to be set on connections |
| <span id="a8eef8-connection-init-sql"></span> `connection-init-sql` | `VALUE` | `String` | Set the SQL string that will be executed on all new connections when they are created, before they are added to the pool |
| <span id="a80fe8-connection-test-query"></span> `connection-test-query` | `VALUE` | `String` | Set the SQL query to be executed to test the validity of connections |
| <span id="a6b039-connection-timeout"></span> `connection-timeout` | `VALUE` | `Long` | Set the maximum number of milliseconds that a client will wait for a connection from the pool |
| <span id="a49330-health-check-properties"></span> `health-check-properties` | `MAP` | `String` | Add properties (name/value pair) that will be used to configure the connection pool health check |
| <span id="ad7af6-idle-timeout"></span> `idle-timeout` | `VALUE` | `Long` | This property controls the maximum amount of time that a connection is allowed to sit idle in the pool |
| <span id="a26354-initialization-fail-timeout"></span> `initialization-fail-timeout` | `VALUE` | `Long` | Set the pool initialization failure timeout |
| <span id="adca8a-isolate-internal-queries"></span> `isolate-internal-queries` | `VALUE` | `Boolean` | Configure whether internal pool queries, principally aliveness checks, will be isolated in their own transaction via `java.sql.Connection#rollback()` |
| <span id="ab44b9-keepalive-time"></span> `keepalive-time` | `VALUE` | `Long` | This property controls the keepalive interval for a connection in the pool |
| <span id="ab4375-leak-detection-threshold"></span> `leak-detection-threshold` | `VALUE` | `Long` | This property controls the amount of time that a connection can be out of the pool before a message is logged indicating a possible connection leak |
| <span id="a565f8-max-lifetime"></span> `max-lifetime` | `VALUE` | `Long` | This property controls the maximum lifetime of a connection in the pool |
| <span id="a62eb9-maximum-pool-size"></span> `maximum-pool-size` | `VALUE` | `Integer` | The property controls the maximum size that the pool is allowed to reach, including both idle and in-use connections |
| <span id="a20ddc-minimum-idle"></span> `minimum-idle` | `VALUE` | `Integer` | The property controls the minimum number of idle connections that HikariCP tries to maintain in the pool, including both idle and in-use connections |
| <span id="a17f45-pool-name"></span> `pool-name` | `VALUE` | `String` | Set the name of the connection pool |
| <span id="a5cb9c-properties"></span> `properties` | `MAP` | `String` | Add properties (name/value pair) that will be used to configure the DataSource/Driver |
| <span id="ad5e59-read-only"></span> `read-only` | `VALUE` | `Boolean` | Configures the Connections to be added to the pool as read-only Connections |
| <span id="ae7ac0-register-mbeans"></span> `register-mbeans` | `VALUE` | `Boolean` | Configures whether HikariCP self-registers the `com.zaxxer.hikari.HikariConfigMXBean` and `com.zaxxer.hikari.HikariPoolMXBean` in JMX |
| <span id="aebab2-schema"></span> `schema` | `VALUE` | `String` | Set the default schema name to be set on connections |
| <span id="a77b10-transaction-isolation"></span> [`transaction-isolation`](../config/io_helidon_data_sql_datasource_TransactionIsolation.md) | `VALUE` | `i.h.d.s.d.TransactionIsolation` | Set the default transaction isolation level |
| <span id="a9a3a4-validation-timeout"></span> `validation-timeout` | `VALUE` | `Long` | Sets the maximum number of milliseconds that the pool will wait for a connection to be validated as alive |

See the [manifest](../config/manifest.md) for all available types.
