# io.helidon.data.sql.datasource.ucp.UcpDataSourceConfig

## Description

UCP specific configuration for

javax.sql.DataSource

.

## Usages

- [`data.sources.sql.provider.ucp`](../config/io_helidon_data_sql_datasource_ProviderConfig.md#a41404-ucp)

## Configuration options

| Key | Kind | Type | Description |
|----|----|----|----|
| <span id="ab6f9f-abandoned-connection-timeout"></span> `abandoned-connection-timeout` | `VALUE` | `Integer` | Sets the abandoned connection timeout |
| <span id="a7959c-commit-on-connection-return"></span> `commit-on-connection-return` | `VALUE` | `Boolean` | Sets the `boolean` value for the property that controls the behavior of UCP when a connection is released back to the pool with pending uncommitted changes in an active transaction |
| <span id="ad6ff7-connection-factory-class-name"></span> `connection-factory-class-name` | `VALUE` | `String` | Sets the connection factory class name |
| <span id="adbdcf-connection-factory-properties"></span> `connection-factory-properties` | `MAP` | `String` | Sets the connection factory properties on the connection factory |
| <span id="a6cf47-connection-harvest-max-count"></span> `connection-harvest-max-count` | `VALUE` | `Integer` | Sets the maximum number of connections that may be harvested when the connection harvesting occurs |
| <span id="ad7c4a-connection-harvest-trigger-count"></span> `connection-harvest-trigger-count` | `VALUE` | `Integer` | Sets the number of available connections below which the connection pool's connection harvesting will occur |
| <span id="a14d3b-connection-labeling-high-cost"></span> `connection-labeling-high-cost` | `VALUE` | `Integer` | Sets the cost value which identifies a connection as "high-cost" for connection labeling |
| <span id="a466d3-connection-pool-name"></span> `connection-pool-name` | `VALUE` | `String` | Sets the connection pool name |
| <span id="aa15ec-connection-properties"></span> `connection-properties` | `MAP` | `String` | Sets the connection properties on the connection factory |
| <span id="aba71e-connection-repurpose-threshold"></span> `connection-repurpose-threshold` | `VALUE` | `Integer` | Sets the connection repurpose threshold for the pool |
| <span id="a840c0-connection-validation-timeout"></span> `connection-validation-timeout` | `VALUE` | `Integer` | Sets the connection validation timeout in seconds |
| <span id="afca0e-connection-wait-duration"></span> `connection-wait-duration` | `VALUE` | `Duration` | Configures how much time a connection request call may wait before it either successfully returns a connection or throws an exception |
| <span id="aa0a95-create-connection-in-borrow-thread"></span> `create-connection-in-borrow-thread` | `VALUE` | `Boolean` | Set this flag to `true` to make UCP use the borrowing thread to create new connections |
| <span id="a44540-data-source-name"></span> `data-source-name` | `VALUE` | `String` | Sets the data source name |
| <span id="a3a656-database-name"></span> `database-name` | `VALUE` | `String` | Sets the database name |
| <span id="a3c845-description"></span> `description` | `VALUE` | `String` | Sets the data source description |
| <span id="abd820-fast-connection-failover-enabled"></span> `fast-connection-failover-enabled` | `VALUE` | `Boolean` | Enables Fast Connection Failover (FCF) for the connection pool accessed using this pool-enabled data source |
| <span id="a94a9f-high-cost-connection-reuse-threshold"></span> `high-cost-connection-reuse-threshold` | `VALUE` | `Integer` | Sets the high-cost connection reuse threshold for connection labeling |
| <span id="a6f65c-inactive-connection-timeout"></span> `inactive-connection-timeout` | `VALUE` | `Integer` | Sets the inactive connection timeout |
| <span id="a5fa8f-initial-pool-size"></span> `initial-pool-size` | `VALUE` | `Integer` | Sets the initial pool size |
| <span id="a6177e-max-connection-reuse-count"></span> `max-connection-reuse-count` | `VALUE` | `Integer` | Sets the maximum connection reuse count |
| <span id="afabbd-max-connection-reuse-time"></span> `max-connection-reuse-time` | `VALUE` | `Long` | Sets the maximum connection reuse time in seconds |
| <span id="aaa3bd-max-connections-per-shard"></span> `max-connections-per-shard` | `VALUE` | `Integer` | Sets the max number of connections that can be created per shard from this connection pool |
| <span id="acb1b7-max-idle-time"></span> `max-idle-time` | `VALUE` | `Integer` | Sets the maximum idle time for available connections in the pool in seconds |
| <span id="af5090-max-pool-size"></span> `max-pool-size` | `VALUE` | `Integer` | Sets the maximum number of connections |
| <span id="a52631-max-statements"></span> `max-statements` | `VALUE` | `Integer` | Sets the maximum number of statements that may be pooled or cached on a connection |
| <span id="ad3eef-min-idle"></span> `min-idle` | `VALUE` | `Integer` | Sets the minimum number of idle connections |
| <span id="a911c1-min-pool-size"></span> `min-pool-size` | `VALUE` | `Integer` | Sets the minimum number of connections |
| <span id="a5d7b5-network-protocol"></span> `network-protocol` | `VALUE` | `String` | Sets the data source network protocol |
| <span id="aa72f8-ons-configuration"></span> `ons-configuration` | `VALUE` | `String` | Sets the configuration string used for remote ONS subscription |
| <span id="ad1e91-port-number"></span> `port-number` | `VALUE` | `Integer` | Sets the database port number |
| <span id="a68c39-property-cycle"></span> `property-cycle` | `VALUE` | `Integer` | Sets the property cycle in seconds |
| <span id="a305ec-query-timeout"></span> `query-timeout` | `VALUE` | `Integer` | Sets the number of seconds the driver will wait for a Statement object to execute to the given number of seconds |
| <span id="a641da-read-only-instance-allowed"></span> `read-only-instance-allowed` | `VALUE` | `Boolean` | Sets the read-only instance allowed value on the datasource |
| <span id="ae67a2-role-name"></span> `role-name` | `VALUE` | `String` | Sets the data source role name |
| <span id="ab18a8-seconds-to-trust-idle-connection"></span> `seconds-to-trust-idle-connection` | `VALUE` | `Integer` | Sets the time to trust an idle connection to skip a validation test in seconds |
| <span id="af4691-server-name"></span> `server-name` | `VALUE` | `String` | Sets the database server name |
| <span id="a831cb-sharding-mode"></span> `sharding-mode` | `VALUE` | `Boolean` | Change the mode of UCP when UCP is using a Sharded Database |
| <span id="abc610-sql-for-validate-connection"></span> `sql-for-validate-connection` | `VALUE` | `String` | Sets the SQL statement to validate the database connection |
| <span id="a2d7c9-time-to-live-connection-timeout"></span> `time-to-live-connection-timeout` | `VALUE` | `Integer` | Sets the maximum time a connection may remain in-use in seconds |
| <span id="a35c8c-timeout-check-interval"></span> `timeout-check-interval` | `VALUE` | `Integer` | Sets the timeout check interval in seconds |
| <span id="a60838-validate-connection-on-borrow"></span> `validate-connection-on-borrow` | `VALUE` | `Boolean` | Makes the pool validate the connection before returning it to the user by calling the JDBC API `isValid` |
| <span id="a9896b-xa-data-source"></span> `xa-data-source` | `VALUE` | `Boolean` | Support for distributed transactions |

See the [manifest](../config/manifest.md) for all available types.
