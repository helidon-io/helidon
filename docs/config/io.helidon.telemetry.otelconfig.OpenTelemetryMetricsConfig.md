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
<a id="readers"></a>
<a href="io.helidon.telemetry.otelconfig.MetricReaderConfig.md">
<code>readers</code>
</a>
</td>
<td>
<code>List&lt;<wbr>Metric<wbr>Reader<wbr>Config&gt;</code>
</td>
<td>Settings for metric readers</td>
</tr>
<tr>
<td>
<a id="attributes"></a>
<a href="io.helidon.telemetry.otelconfig.TypedAttributes.md">
<code>attributes</code>
</a>
</td>
<td>
<code>Typed<wbr>Attributes</code>
</td>
<td>Name/value pairs passed to OpenTelemetry</td>
</tr>
<tr>
<td>
<a id="views"></a>
<a href="io.helidon.telemetry.otelconfig.ViewRegistrationConfig.md">
<code>views</code>
</a>
</td>
<td>
<code>List&lt;<wbr>View<wbr>Registration<wbr>Config&gt;</code>
</td>
<td>Metric view information, configurable using <code>io.<wbr>helidon.<wbr>telemetry.<wbr>otelconfig.<wbr>View<wbr>Registration<wbr>Config</code></td>
</tr>
<tr>
<td>
<code>exporters</code>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> Metric<wbr>Exporter&gt;</code>
</td>
<td>Metric exporter configurations, configurable using <code>io.<wbr>helidon.<wbr>telemetry.<wbr>otelconfig.<wbr>Metric<wbr>Exporter<wbr>Config</code></td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.telemetry.SignalsConfig.md#metrics"><code>telemetry.<wbr>signals.<wbr>metrics</code></a>

---

See the [manifest](manifest.md) for all available types.
