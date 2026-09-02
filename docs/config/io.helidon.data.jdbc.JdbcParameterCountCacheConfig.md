# io.<wbr>helidon.<wbr>data.<wbr>jdbc.<wbr>Jdbc<wbr>Parameter<wbr>Count<wbr>Cache<wbr>Config

## Description

Configuration blueprint for the positional parameter count cache

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
<code>max-<wbr>sql-<wbr>length</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>4096</code>
</td>
<td>Maximum SQL string length admitted to the cache in UTF-16 code units</td>
</tr>
<tr>
<td>
<code>capacity</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>256</code>
</td>
<td>Maximum number of SQL marker counts retained by one JDBC client</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.data.jdbc.JdbcProviderPropertiesConfig.md#parameter-count-cache"><code>data.<wbr>clients.<wbr>jdbc.<wbr>properties.<wbr>jdbc.<wbr>parameter-<wbr>count-<wbr>cache</code></a>

---

See the [manifest](manifest.md) for all available types.
