# io.helidon.data.sql.datasource.ucp.UcpDataSourceConfig

## Description

UCP specific configuration for &lt;code&gt;javax.sql.DataSource&lt;/code&gt;

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }
</style>

<table>
<thead>
<tr>
<th>Key</th><th>Type</th><th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>connection-repurpose-threshold</code></td>
<td><code>Integer</code></td>
<td>Sets the connection repurpose threshold for the pool</td>
</tr>
<tr>
<td><code>min-idle</code></td>
<td><code>Integer</code></td>
<td>Sets the minimum number of idle connections</td>
</tr>
<tr>
<td><code>role-name</code></td>
<td><code>String</code></td>
<td>Sets the data source role name</td>
</tr>
<tr>
<td><code>ons-configuration</code></td>
<td><code>String</code></td>
<td>Sets the configuration string used for remote ONS subscription</td>
</tr>
<tr>
<td><code>description</code></td>
<td><code>String</code></td>
<td>Sets the data source description</td>
</tr>
<tr>
<td><code>connection-harvest-max-count</code></td>
<td><code>Integer</code></td>
<td>Sets the maximum number of connections that may be harvested when the connection harvesting occurs</td>
</tr>
<tr>
<td><code>connection-factory-class-name</code></td>
<td><code>String</code></td>
<td>Sets the connection factory class name</td>
</tr>
<tr>
<td><code>connection-harvest-trigger-count</code></td>
<td><code>Integer</code></td>
<td>Sets the number of available connections below which the connection pool&#x27;s connection harvesting will occur</td>
</tr>
<tr>
<td><code>query-timeout</code></td>
<td><code>Integer</code></td>
<td>Sets the number of seconds the driver will wait for a Statement object to execute to the given number of seconds</td>
</tr>
<tr>
<td><code>max-connection-reuse-count</code></td>
<td><code>Integer</code></td>
<td>Sets the maximum connection reuse count</td>
</tr>
<tr>
<td><code>read-only-instance-allowed</code></td>
<td><code>Boolean</code></td>
<td>Sets the read-only instance allowed value on the datasource</td>
</tr>
<tr>
<td><code>server-name</code></td>
<td><code>String</code></td>
<td>Sets the database server name</td>
</tr>
<tr>
<td><code>timeout-check-interval</code></td>
<td><code>Integer</code></td>
<td>Sets the timeout check interval in seconds</td>
</tr>
<tr>
<td><code>max-connections-per-shard</code></td>
<td><code>Integer</code></td>
<td>Sets the max number of connections that can be created per shard from this connection pool</td>
</tr>
<tr>
<td><code>validate-connection-on-borrow</code></td>
<td><code>Boolean</code></td>
<td>Makes the pool validate the connection before returning it to the user by calling the JDBC API &lt;code&gt;isValid&lt;/code&gt;</td>
</tr>
<tr>
<td><code>max-idle-time</code></td>
<td><code>Integer</code></td>
<td>Sets the maximum idle time for available connections in the pool in seconds</td>
</tr>
<tr>
<td><code>max-statements</code></td>
<td><code>Integer</code></td>
<td>Sets the maximum number of statements that may be pooled or cached on a connection</td>
</tr>
<tr>
<td><code>connection-wait-duration</code></td>
<td><code>Duration</code></td>
<td>Configures how much time a connection request call may wait before it either successfully returns a connection or throws an exception</td>
</tr>
<tr>
<td><code>property-cycle</code></td>
<td><code>Integer</code></td>
<td>Sets the property cycle in seconds</td>
</tr>
<tr>
<td><code>connection-validation-timeout</code></td>
<td><code>Integer</code></td>
<td>Sets the connection validation timeout in seconds</td>
</tr>
<tr>
<td><code>high-cost-connection-reuse-threshold</code></td>
<td><code>Integer</code></td>
<td>Sets the high-cost connection reuse threshold for connection labeling</td>
</tr>
<tr>
<td><code>fast-connection-failover-enabled</code></td>
<td><code>Boolean</code></td>
<td>Enables Fast Connection Failover (FCF) for the connection pool accessed using this pool-enabled data source</td>
</tr>
<tr>
<td><code>sharding-mode</code></td>
<td><code>Boolean</code></td>
<td>Change the mode of UCP when UCP is using a Sharded Database</td>
</tr>
<tr>
<td><code>connection-labeling-high-cost</code></td>
<td><code>Integer</code></td>
<td>Sets the cost value which identifies a connection as &quot;high-cost&quot; for connection labeling</td>
</tr>
<tr>
<td><code>network-protocol</code></td>
<td><code>String</code></td>
<td>Sets the data source network protocol</td>
</tr>
<tr>
<td><code>commit-on-connection-return</code></td>
<td><code>Boolean</code></td>
<td>Sets the &lt;code&gt;boolean&lt;/code&gt; value for the property that controls the behavior of UCP when a connection is released back to the pool with pending uncommitted changes in an active transaction</td>
</tr>
<tr>
<td><code>min-pool-size</code></td>
<td><code>Integer</code></td>
<td>Sets the minimum number of connections</td>
</tr>
<tr>
<td><code>initial-pool-size</code></td>
<td><code>Integer</code></td>
<td>Sets the initial pool size</td>
</tr>
<tr>
<td><code>abandoned-connection-timeout</code></td>
<td><code>Integer</code></td>
<td>Sets the abandoned connection timeout</td>
</tr>
<tr>
<td><code>seconds-to-trust-idle-connection</code></td>
<td><code>Integer</code></td>
<td>Sets the time to trust an idle connection to skip a validation test in seconds</td>
</tr>
<tr>
<td><code>xa-data-source</code></td>
<td><code>Boolean</code></td>
<td>Support for distributed transactions</td>
</tr>
<tr>
<td><code>port-number</code></td>
<td><code>Integer</code></td>
<td>Sets the database port number</td>
</tr>
<tr>
<td><code>connection-properties</code></td>
<td><code>Map&lt;String, String&gt;</code></td>
<td>Sets the connection properties on the connection factory</td>
</tr>
<tr>
<td><code>time-to-live-connection-timeout</code></td>
<td><code>Integer</code></td>
<td>Sets the maximum time a connection may remain in-use in seconds</td>
</tr>
<tr>
<td><code>max-pool-size</code></td>
<td><code>Integer</code></td>
<td>Sets the maximum number of connections</td>
</tr>
<tr>
<td><code>connection-factory-properties</code></td>
<td><code>Map&lt;String, String&gt;</code></td>
<td>Sets the connection factory properties on the connection factory</td>
</tr>
<tr>
<td><code>max-connection-reuse-time</code></td>
<td><code>Long</code></td>
<td>Sets the maximum connection reuse time in seconds</td>
</tr>
<tr>
<td><code>sql-for-validate-connection</code></td>
<td><code>String</code></td>
<td>Sets the SQL statement to validate the database connection</td>
</tr>
<tr>
<td><code>database-name</code></td>
<td><code>String</code></td>
<td>Sets the database name</td>
</tr>
<tr>
<td><code>connection-pool-name</code></td>
<td><code>String</code></td>
<td>Sets the connection pool name</td>
</tr>
<tr>
<td><code>data-source-name</code></td>
<td><code>String</code></td>
<td>Sets the data source name</td>
</tr>
<tr>
<td><code>inactive-connection-timeout</code></td>
<td><code>Integer</code></td>
<td>Sets the inactive connection timeout</td>
</tr>
<tr>
<td><code>create-connection-in-borrow-thread</code></td>
<td><code>Boolean</code></td>
<td>Set this flag to &lt;code&gt;true&lt;/code&gt; to make UCP use the borrowing thread to create new connections</td>
</tr>
</tbody>
</table>


## Usages

- [`data.sources.sql.provider.ucp`](io.helidon.data.sql.datasource.ProviderConfig.md#ucp)

---

See the [manifest](manifest.md) for all available types.
