# io.helidon.data.sql.datasource.hikari.HikariDataSourceConfig

## Description

Hikari connection pool specific configuration for <code>javax.sql.DataSource</code>

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }

    .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


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
<code>register-mbeans</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td>Configures whether HikariCP self-registers the <code>com.zaxxer.hikari.HikariConfigMXBean</code> and <code>com.zaxxer.hikari.HikariPoolMXBean</code> in JMX</td>
</tr>
<tr>
<td>
<code>schema</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>Set the default schema name to be set on connections</td>
</tr>
<tr>
<td>
<code>auto-commit</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td>Set the default auto-commit behavior of connections in the pool</td>
</tr>
<tr>
<td>
<code>pool-name</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>Set the name of the connection pool</td>
</tr>
<tr>
<td>
<code>catalog</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>Set the default catalog name to be set on connections</td>
</tr>
<tr>
<td>
<code>minimum-idle</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td>The property controls the minimum number of idle connections that HikariCP tries to maintain in the pool, including both idle and in-use connections</td>
</tr>
<tr>
<td>
<code>connection-test-query</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>Set the SQL query to be executed to test the validity of connections</td>
</tr>
<tr>
<td>
<code>maximum-pool-size</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td>The property controls the maximum size that the pool is allowed to reach, including both idle and in-use connections</td>
</tr>
<tr>
<td>
<code>leak-detection-threshold</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Long</code>
</td>
<td>This property controls the amount of time that a connection can be out of the pool before a message is logged indicating a possible connection leak</td>
</tr>
<tr>
<td>
<code>connection-init-sql</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>Set the SQL string that will be executed on all new connections when they are created, before they are added to the pool</td>
</tr>
<tr>
<td>
<code>connection-timeout</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Long</code>
</td>
<td>Set the maximum number of milliseconds that a client will wait for a connection from the pool</td>
</tr>
<tr>
<td>
<code>allow-pool-suspension</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td>Set whether pool suspension is allowed</td>
</tr>
<tr>
<td>
<code>keepalive-time</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Long</code>
</td>
<td>This property controls the keepalive interval for a connection in the pool</td>
</tr>
<tr>
<td>
<code>max-lifetime</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Long</code>
</td>
<td>This property controls the maximum lifetime of a connection in the pool</td>
</tr>
<tr>
<td>
<code>validation-timeout</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Long</code>
</td>
<td>Sets the maximum number of milliseconds that the pool will wait for a connection to be validated as alive</td>
</tr>
<tr>
<td>
<code>health-check-properties</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="Map&lt;String, String&gt;">Map&lt;String, String&gt;</code>
</td>
<td>Add properties (name/value pair) that will be used to configure the connection pool health check</td>
</tr>
<tr>
<td>
<code>isolate-internal-queries</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td>Configure whether internal pool queries, principally aliveness checks, will be isolated in their own transaction via <code>java.sql.Connection#rollback()</code></td>
</tr>
<tr>
<td>
<code>idle-timeout</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Long</code>
</td>
<td>This property controls the maximum amount of time that a connection is allowed to sit idle in the pool</td>
</tr>
<tr>
<td>
<code>read-only</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td>Configures the Connections to be added to the pool as read-only Connections</td>
</tr>
<tr>
<td>
<a id="transaction-isolation"></a>
<a href="io.helidon.data.sql.datasource.TransactionIsolation.md">
<code>transaction-isolation</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="TransactionIsolation">TransactionIsolation</code>
</td>
<td>Set the default transaction isolation level</td>
</tr>
<tr>
<td>
<code>initialization-fail-timeout</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Long</code>
</td>
<td>Set the pool initialization failure timeout</td>
</tr>
<tr>
<td>
<code>properties</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="Map&lt;String, String&gt;">Map&lt;String, String&gt;</code>
</td>
<td>Add properties (name/value pair) that will be used to configure the DataSource/Driver</td>
</tr>
</tbody>
</table>



## Usages

- [`data.sources.sql.provider.hikari`](io.helidon.data.sql.datasource.ProviderConfig.md#hikari)

---

See the [manifest](manifest.md) for all available types.
