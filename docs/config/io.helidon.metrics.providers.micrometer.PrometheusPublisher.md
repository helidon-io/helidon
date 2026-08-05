# io.<wbr>helidon.<wbr>metrics.<wbr>providers.<wbr>micrometer.<wbr>Prometheus<wbr>Publisher

## Description

Settings for a Micrometer Prometheus meter registry

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
<code>prefix</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Property name prefix</td>
</tr>
<tr>
<td>
<code>name</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td><code>N/<wbr>A</code></td>
</tr>
<tr>
<td>
<code>interval</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
</td>
<td>Step size used in computing "windowed" statistics</td>
</tr>
<tr>
<td>
<code>descriptions</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
</td>
<td>Whether to include meter descriptions in Prometheus output</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether the configured publisher is enabled</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.metrics.api.MetricsPublisher.md#prometheus"><code>metrics.<wbr>publishers.<wbr>prometheus</code></a>
- <a href="io.helidon.metrics.api.MetricsPublisher.md#prometheus"><code>server.<wbr>features.<wbr>observe.<wbr>observers.<wbr>metrics.<wbr>publishers.<wbr>prometheus</code></a>

---

See the [manifest](manifest.md) for all available types.
