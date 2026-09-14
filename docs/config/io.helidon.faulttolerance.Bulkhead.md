# io.<wbr>helidon.<wbr>faulttolerance.<wbr>Bulkhead

## Description

<code>Bulkhead</code> configuration bean

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
<code>limit</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>10</code>
</td>
<td>Maximal number of parallel requests going through this bulkhead</td>
</tr>
<tr>
<td>
<code>enable-<wbr>metrics</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Flag to enable metrics for this instance</td>
</tr>
<tr>
<td>
<code>queue-<wbr>length</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>10</code>
</td>
<td>Maximal number of enqueued requests waiting for processing</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.FaultToleranceConfig.md#bulkheads"><code>fault-<wbr>tolerance.<wbr>bulkheads</code></a>

---

See the [manifest](manifest.md) for all available types.
