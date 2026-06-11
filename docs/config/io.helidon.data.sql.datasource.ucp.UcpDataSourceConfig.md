# io.helidon.data.sql.datasource.ucp.UcpDataSourceConfig

## Description

UCP specific configuration for <code>javax.<wbr>sql.<wbr>Data<wbr>Source</code>

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
<code>connection-<wbr>repurpose-<wbr>threshold</code>
</td>
<td>
<code>Integer</code>
</td>
<td>Sets the connection repurpose threshold for the pool</td>
</tr>
<tr>
<td>
<code>min-<wbr>idle</code>
</td>
<td>
<code>Integer</code>
</td>
<td>Sets the minimum number of idle connections</td>
</tr>
<tr>
<td>
<code>role-<wbr>name</code>
</td>
<td>
<code>String</code>
</td>
<td>Sets the data source role name</td>
</tr>
<tr>
<td>
<code>ons-<wbr>configuration</code>
</td>
<td>
<code>String</code>
</td>
<td>Sets the configuration string used for remote ONS subscription</td>
</tr>
<tr>
<td>
<code>description</code>
</td>
<td>
<code>String</code>
</td>
<td>Sets the data source description</td>
</tr>
<tr>
<td>
<code>connection-<wbr>harvest-<wbr>max-<wbr>count</code>
</td>
<td>
<code>Integer</code>
</td>
<td>Sets the maximum number of connections that may be harvested when the connection harvesting occurs</td>
</tr>
<tr>
<td>
<code>connection-<wbr>factory-<wbr>class-<wbr>name</code>
</td>
<td>
<code>String</code>
</td>
<td>Sets the connection factory class name</td>
</tr>
<tr>
<td>
<code>connection-<wbr>harvest-<wbr>trigger-<wbr>count</code>
</td>
<td>
<code>Integer</code>
</td>
<td>Sets the number of available connections below which the connection pool's connection harvesting will occur</td>
</tr>
<tr>
<td>
<code>query-<wbr>timeout</code>
</td>
<td>
<code>Integer</code>
</td>
<td>Sets the number of seconds the driver will wait for a Statement object to execute to the given number of seconds</td>
</tr>
<tr>
<td>
<code>max-<wbr>connection-<wbr>reuse-<wbr>count</code>
</td>
<td>
<code>Integer</code>
</td>
<td>Sets the maximum connection reuse count</td>
</tr>
<tr>
<td>
<code>read-<wbr>only-<wbr>instance-<wbr>allowed</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>Sets the read-only instance allowed value on the datasource</td>
</tr>
<tr>
<td>
<code>server-<wbr>name</code>
</td>
<td>
<code>String</code>
</td>
<td>Sets the database server name</td>
</tr>
<tr>
<td>
<code>timeout-<wbr>check-<wbr>interval</code>
</td>
<td>
<code>Integer</code>
</td>
<td>Sets the timeout check interval in seconds</td>
</tr>
<tr>
<td>
<code>max-<wbr>connections-<wbr>per-<wbr>shard</code>
</td>
<td>
<code>Integer</code>
</td>
<td>Sets the max number of connections that can be created per shard from this connection pool</td>
</tr>
<tr>
<td>
<code>validate-<wbr>connection-<wbr>on-borrow</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>Makes the pool validate the connection before returning it to the user by calling the JDBC API <code>is<wbr>Valid</code></td>
</tr>
<tr>
<td>
<code>max-<wbr>idle-<wbr>time</code>
</td>
<td>
<code>Integer</code>
</td>
<td>Sets the maximum idle time for available connections in the pool in seconds</td>
</tr>
<tr>
<td>
<code>max-<wbr>statements</code>
</td>
<td>
<code>Integer</code>
</td>
<td>Sets the maximum number of statements that may be pooled or cached on a connection</td>
</tr>
<tr>
<td>
<code>connection-<wbr>wait-<wbr>duration</code>
</td>
<td>
<code>Duration</code>
</td>
<td>Configures how much time a connection request call may wait before it either successfully returns a connection or throws an exception</td>
</tr>
<tr>
<td>
<code>property-<wbr>cycle</code>
</td>
<td>
<code>Integer</code>
</td>
<td>Sets the property cycle in seconds</td>
</tr>
<tr>
<td>
<code>connection-<wbr>validation-<wbr>timeout</code>
</td>
<td>
<code>Integer</code>
</td>
<td>Sets the connection validation timeout in seconds</td>
</tr>
<tr>
<td>
<code>high-<wbr>cost-<wbr>connection-<wbr>reuse-<wbr>threshold</code>
</td>
<td>
<code>Integer</code>
</td>
<td>Sets the high-cost connection reuse threshold for connection labeling</td>
</tr>
<tr>
<td>
<code>fast-<wbr>connection-<wbr>failover-<wbr>enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>Enables Fast Connection Failover (FCF) for the connection pool accessed using this pool-enabled data source</td>
</tr>
<tr>
<td>
<code>sharding-<wbr>mode</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>Change the mode of UCP when UCP is using a Sharded Database</td>
</tr>
<tr>
<td>
<code>connection-<wbr>labeling-<wbr>high-<wbr>cost</code>
</td>
<td>
<code>Integer</code>
</td>
<td>Sets the cost value which identifies a connection as "high-cost" for connection labeling</td>
</tr>
<tr>
<td>
<code>network-<wbr>protocol</code>
</td>
<td>
<code>String</code>
</td>
<td>Sets the data source network protocol</td>
</tr>
<tr>
<td>
<code>commit-<wbr>on-connection-<wbr>return</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>Sets the <code>boolean</code> value for the property that controls the behavior of UCP when a connection is released back to the pool with pending uncommitted changes in an active transaction</td>
</tr>
<tr>
<td>
<code>min-<wbr>pool-<wbr>size</code>
</td>
<td>
<code>Integer</code>
</td>
<td>Sets the minimum number of connections</td>
</tr>
<tr>
<td>
<code>initial-<wbr>pool-<wbr>size</code>
</td>
<td>
<code>Integer</code>
</td>
<td>Sets the initial pool size</td>
</tr>
<tr>
<td>
<code>abandoned-<wbr>connection-<wbr>timeout</code>
</td>
<td>
<code>Integer</code>
</td>
<td>Sets the abandoned connection timeout</td>
</tr>
<tr>
<td>
<code>seconds-<wbr>to-trust-<wbr>idle-<wbr>connection</code>
</td>
<td>
<code>Integer</code>
</td>
<td>Sets the time to trust an idle connection to skip a validation test in seconds</td>
</tr>
<tr>
<td>
<code>xa-<wbr>data-<wbr>source</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>Support for distributed transactions</td>
</tr>
<tr>
<td>
<code>port-<wbr>number</code>
</td>
<td>
<code>Integer</code>
</td>
<td>Sets the database port number</td>
</tr>
<tr>
<td>
<code>connection-<wbr>properties</code>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> String&gt;</code>
</td>
<td>Sets the connection properties on the connection factory</td>
</tr>
<tr>
<td>
<code>time-<wbr>to-live-<wbr>connection-<wbr>timeout</code>
</td>
<td>
<code>Integer</code>
</td>
<td>Sets the maximum time a connection may remain in-use in seconds</td>
</tr>
<tr>
<td>
<code>max-<wbr>pool-<wbr>size</code>
</td>
<td>
<code>Integer</code>
</td>
<td>Sets the maximum number of connections</td>
</tr>
<tr>
<td>
<code>connection-<wbr>factory-<wbr>properties</code>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> String&gt;</code>
</td>
<td>Sets the connection factory properties on the connection factory</td>
</tr>
<tr>
<td>
<code>max-<wbr>connection-<wbr>reuse-<wbr>time</code>
</td>
<td>
<code>Long</code>
</td>
<td>Sets the maximum connection reuse time in seconds</td>
</tr>
<tr>
<td>
<code>sql-<wbr>for-<wbr>validate-<wbr>connection</code>
</td>
<td>
<code>String</code>
</td>
<td>Sets the SQL statement to validate the database connection</td>
</tr>
<tr>
<td>
<code>database-<wbr>name</code>
</td>
<td>
<code>String</code>
</td>
<td>Sets the database name</td>
</tr>
<tr>
<td>
<code>connection-<wbr>pool-<wbr>name</code>
</td>
<td>
<code>String</code>
</td>
<td>Sets the connection pool name</td>
</tr>
<tr>
<td>
<code>data-<wbr>source-<wbr>name</code>
</td>
<td>
<code>String</code>
</td>
<td>Sets the data source name</td>
</tr>
<tr>
<td>
<code>inactive-<wbr>connection-<wbr>timeout</code>
</td>
<td>
<code>Integer</code>
</td>
<td>Sets the inactive connection timeout</td>
</tr>
<tr>
<td>
<code>create-<wbr>connection-<wbr>in-borrow-<wbr>thread</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>Set this flag to <code>true</code> to make UCP use the borrowing thread to create new connections</td>
</tr>
</tbody>
</table>



## Usages

- [`data.sources.sql.provider.ucp`](io.helidon.data.sql.datasource.ProviderConfig.md#ucp)

---

See the [manifest](manifest.md) for all available types.
