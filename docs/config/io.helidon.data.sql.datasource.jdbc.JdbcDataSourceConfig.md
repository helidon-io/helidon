# io.<wbr>helidon.<wbr>data.<wbr>sql.<wbr>datasource.<wbr>jdbc.<wbr>Jdbc<wbr>Data<wbr>Source<wbr>Config

## Description

JDBC Data source configuration

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
<td>Set the default auto-commit behavior of create connections</td>
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
<code>read-<wbr>only</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>Whether the connection should be read only</td>
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

- <a href="io.helidon.data.sql.datasource.ProviderConfig.md#jdbc"><code>data.<wbr>sources.<wbr>sql.<wbr>provider.<wbr>jdbc</code></a>

---

See the [manifest](manifest.md) for all available types.
