# io.<wbr>helidon.<wbr>data.<wbr>sql.<wbr>common.<wbr>SqlConfig

## Description

SQL specific configuration

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
<code>data-<wbr>source</code>
</td>
<td>
<code>String</code>
</td>
<td>Name of the <code>javax.<wbr>sql.<wbr>Data<wbr>Source</code>, with exactly one of <code>connection</code> and <code>data-<wbr>source</code> required</td>
</tr>
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
<td>Configuration of a direct connection to a database, with exactly one of <code>connection</code> and <code>data-<wbr>source</code> required</td>
</tr>
</tbody>
</table>



## Dependent Types

- [io.<wbr>helidon.<wbr>data.<wbr>jdbc.<wbr>Jdbc<wbr>Client](io.helidon.data.jdbc.JdbcClient.md)

---

See the [manifest](manifest.md) for all available types.
