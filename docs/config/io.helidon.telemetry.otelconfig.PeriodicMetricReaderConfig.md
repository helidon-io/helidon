# io.<wbr>helidon.<wbr>telemetry.<wbr>otelconfig.<wbr>Periodic<wbr>Metric<wbr>Reader<wbr>Config

## Description

Settings for OpenTelemetry periodic metric reader

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
<code>exporter</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Name of the configured metric exporter to use for this metric reader</td>
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
<td>Metric reader read interval</td>
</tr>
<tr>
<td>
<a id="type"></a>
<a href="io.helidon.telemetry.otelconfig.MetricReaderType.md">
<code>type</code>
</a>
</td>
<td>
<code>Metric<wbr>Reader<wbr>Type</code>
</td>
<td>
<code>PERIODIC</code>
</td>
<td>Metric reader type</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
