# io.<wbr>helidon.<wbr>data.<wbr>jdbc.<wbr>Jdbc<wbr>Client

## Description

Configuration for a JDBC client

## Configuration options


<table>
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Default</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<a id="connection"></a>
<a href="io.helidon.data.sql.common.ConnectionConfig.md">
<code>connection</code>
</a>
</td>
<td>
<code>Connection<wbr>Config</code>
</td>
<td>
</td>
<td>Direct JDBC connection properties</td>
</tr>
<tr>
<td>
<code>data-<wbr>source</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Name of the registered <code>Data<wbr>Source</code> to use</td>
</tr>
<tr>
<td>
<a id="health-check"></a>
<a href="io.helidon.data.jdbc.JdbcHealthCheckFactory.md">
<code>health-<wbr>check</code>
</a>
</td>
<td>
</td>
<td>
</td>
<td>Configuration for health-check</td>
</tr>
<tr>
<td>
<code>name</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>@default</code>
</td>
<td>Logical name of this client</td>
</tr>
<tr>
<td>
<code>properties.<wbr>jdbc.<wbr>parameter-<wbr>count-<wbr>cache.<wbr>capacity</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>256</code>
</td>
<td>The maximum number of SQL marker counts retained by this client must be between zero and 4096 inclusive, where zero disables retention while marker validation continues</td>
</tr>
<tr>
<td>
<code>properties.<wbr>jdbc.<wbr>parameter-<wbr>count-<wbr>cache.<wbr>max-<wbr>sql-<wbr>length</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>4096</code>
</td>
<td>The maximum SQL string length admitted to the parameter count cache must be a positive number of UTF-16 code units and its product with the cache capacity must not exceed 16,777,216 code units, but SQL longer than this value remains executable and is scanned without being retained</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.data.ClientsConfig.md#jdbc"><code>data.<wbr>clients.<wbr>jdbc</code></a>

---

See the [manifest](manifest.md) for all available types.
