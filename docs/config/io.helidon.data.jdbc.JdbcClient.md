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
<td>Configuration of a direct connection to a database, with exactly one of <code>connection</code> and <code>data-<wbr>source</code> required</td>
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
<td>Name of the <code>javax.<wbr>sql.<wbr>Data<wbr>Source</code>, with exactly one of <code>connection</code> and <code>data-<wbr>source</code> required</td>
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
<a id="properties"></a>
<a href="io.helidon.data.clients.jdbc.PropertiesConfig.md">
<code>properties</code>
</a>
</td>
<td>
</td>
<td>
</td>
<td>Configuration for properties</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.data.ClientsConfig.md#jdbc"><code>data.<wbr>clients.<wbr>jdbc</code></a>

---

See the [manifest](manifest.md) for all available types.
