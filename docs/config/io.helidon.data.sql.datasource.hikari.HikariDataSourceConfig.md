# io.helidon.data.sql.datasource.hikari.HikariDataSourceConfig

## Description

Hikari connection pool specific configuration for <code>javax.<wbr>sql.<wbr>Data<wbr>Source</code>

## Configuration options


<table>
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>register-<wbr>mbeans</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>Configures whether HikariCP self-registers the <code>com.<wbr>zaxxer.<wbr>hikari.<wbr>Hikari<wbr>Config<wbr>MXBean</code> and <code>com.<wbr>zaxxer.<wbr>hikari.<wbr>Hikari<wbr>Pool<wbr>MXBean</code> in JMX</td>
</tr>
<tr>
<td>
<code>schema</code>
</td>
<td>
<code>String</code>
</td>
<td>Set the default schema name to be set on connections</td>
</tr>
<tr>
<td>
<code>auto-<wbr>commit</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>Set the default auto-commit behavior of connections in the pool</td>
</tr>
<tr>
<td>
<code>pool-<wbr>name</code>
</td>
<td>
<code>String</code>
</td>
<td>Set the name of the connection pool</td>
</tr>
<tr>
<td>
<code>catalog</code>
</td>
<td>
<code>String</code>
</td>
<td>Set the default catalog name to be set on connections</td>
</tr>
<tr>
<td>
<code>minimum-<wbr>idle</code>
</td>
<td>
<code>Integer</code>
</td>
<td>The property controls the minimum number of idle connections that HikariCP tries to maintain in the pool, including both idle and in-use connections</td>
</tr>
<tr>
<td>
<code>connection-<wbr>test-<wbr>query</code>
</td>
<td>
<code>String</code>
</td>
<td>Set the SQL query to be executed to test the validity of connections</td>
</tr>
<tr>
<td>
<code>maximum-<wbr>pool-<wbr>size</code>
</td>
<td>
<code>Integer</code>
</td>
<td>The property controls the maximum size that the pool is allowed to reach, including both idle and in-use connections</td>
</tr>
<tr>
<td>
<code>leak-<wbr>detection-<wbr>threshold</code>
</td>
<td>
<code>Long</code>
</td>
<td>This property controls the amount of time that a connection can be out of the pool before a message is logged indicating a possible connection leak</td>
</tr>
<tr>
<td>
<code>connection-<wbr>init-<wbr>sql</code>
</td>
<td>
<code>String</code>
</td>
<td>Set the SQL string that will be executed on all new connections when they are created, before they are added to the pool</td>
</tr>
<tr>
<td>
<code>connection-<wbr>timeout</code>
</td>
<td>
<code>Long</code>
</td>
<td>Set the maximum number of milliseconds that a client will wait for a connection from the pool</td>
</tr>
<tr>
<td>
<code>allow-<wbr>pool-<wbr>suspension</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>Set whether pool suspension is allowed</td>
</tr>
<tr>
<td>
<code>keepalive-<wbr>time</code>
</td>
<td>
<code>Long</code>
</td>
<td>This property controls the keepalive interval for a connection in the pool</td>
</tr>
<tr>
<td>
<code>max-<wbr>lifetime</code>
</td>
<td>
<code>Long</code>
</td>
<td>This property controls the maximum lifetime of a connection in the pool</td>
</tr>
<tr>
<td>
<code>validation-<wbr>timeout</code>
</td>
<td>
<code>Long</code>
</td>
<td>Sets the maximum number of milliseconds that the pool will wait for a connection to be validated as alive</td>
</tr>
<tr>
<td>
<code>health-<wbr>check-<wbr>properties</code>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> String&gt;</code>
</td>
<td>Add properties (name/value pair) that will be used to configure the connection pool health check</td>
</tr>
<tr>
<td>
<code>isolate-<wbr>internal-<wbr>queries</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>Configure whether internal pool queries, principally aliveness checks, will be isolated in their own transaction via <code>java.<wbr>sql.<wbr>Connection#<wbr>rollback(<wbr>)</code></td>
</tr>
<tr>
<td>
<code>idle-<wbr>timeout</code>
</td>
<td>
<code>Long</code>
</td>
<td>This property controls the maximum amount of time that a connection is allowed to sit idle in the pool</td>
</tr>
<tr>
<td>
<code>read-<wbr>only</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>Configures the Connections to be added to the pool as read-only Connections</td>
</tr>
<tr>
<td>
<a id="transaction-isolation"></a>
<a href="io.helidon.data.sql.datasource.TransactionIsolation.md">
<code>transaction-<wbr>isolation</code>
</a>
</td>
<td>
<code>Transaction<wbr>Isolation</code>
</td>
<td>Set the default transaction isolation level</td>
</tr>
<tr>
<td>
<code>initialization-<wbr>fail-<wbr>timeout</code>
</td>
<td>
<code>Long</code>
</td>
<td>Set the pool initialization failure timeout</td>
</tr>
<tr>
<td>
<code>properties</code>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> String&gt;</code>
</td>
<td>Add properties (name/value pair) that will be used to configure the DataSource/Driver</td>
</tr>
</tbody>
</table>



## Usages

- [`data.sources.sql.provider.hikari`](io.helidon.data.sql.datasource.ProviderConfig.md#hikari)

---

See the [manifest](manifest.md) for all available types.
