# io.<wbr>helidon.<wbr>data.<wbr>clients.<wbr>jdbc.<wbr>properties.<wbr>jdbc.<wbr>Parameter<wbr>Count<wbr>Cache<wbr>Config

## Description

Configuration for data.clients.jdbc.properties.jdbc.parameter-count-cache

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
<code>capacity</code>
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
<code>max-<wbr>sql-<wbr>length</code>
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

- <a href="io.helidon.data.clients.jdbc.properties.JdbcConfig.md#parameter-count-cache"><code>data.<wbr>clients.<wbr>jdbc.<wbr>properties.<wbr>jdbc.<wbr>parameter-<wbr>count-<wbr>cache</code></a>

---

See the [manifest](manifest.md) for all available types.
