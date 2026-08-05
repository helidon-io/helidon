# io.<wbr>helidon.<wbr>telemetry.<wbr>otelconfig.<wbr>Open<wbr>Telemetry<wbr>Metrics<wbr>Config

## Description

OpenTelemetry metrics settings

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
<code>readers</code>
</td>
<td>
<code>List&lt;<wbr>Custom<wbr>Methods&gt;</code>
</td>
<td>Settings for metric readers</td>
</tr>
<tr>
<td>
<code>attributes</code>
</td>
<td>
<code>Custom<wbr>Methods</code>
</td>
<td>Name/value pairs passed to OpenTelemetry</td>
</tr>
<tr>
<td>
<code>views</code>
</td>
<td>
<code>List&lt;<wbr>Custom<wbr>Methods&gt;</code>
</td>
<td>Metric view information, configurable using <code>io.<wbr>helidon.<wbr>telemetry.<wbr>otelconfig.<wbr>View<wbr>Registration<wbr>Config</code></td>
</tr>
<tr>
<td>
<code>exporters</code>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> Custom<wbr>Methods&gt;</code>
</td>
<td>Metric exporter configurations, configurable using <code>io.<wbr>helidon.<wbr>telemetry.<wbr>otelconfig.<wbr>Metric<wbr>Exporter<wbr>Config</code></td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.telemetry.SignalsConfig.md#metrics"><code>telemetry.<wbr>signals.<wbr>metrics</code></a>

---

See the [manifest](manifest.md) for all available types.
