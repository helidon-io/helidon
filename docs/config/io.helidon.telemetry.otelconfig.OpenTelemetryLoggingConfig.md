# io.<wbr>helidon.<wbr>telemetry.<wbr>otelconfig.<wbr>Open<wbr>Telemetry<wbr>Logging<wbr>Config

## Description

Configuration settings for OpenTelemetry logging

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
<code>trace-<wbr>based</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>Whether to include <em>only</em> log records from traces which are sampled</td>
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
<a id="minimum-severity"></a>
<a href="io.opentelemetry.api.logs.Severity.md">
<code>minimum-<wbr>severity</code>
</a>
</td>
<td>
<code>Severity</code>
</td>
<td>Minimum severity level of log records to process</td>
</tr>
<tr>
<td>
<a id="processors"></a>
<a href="io.helidon.telemetry.otelconfig.ProcessorConfig.md">
<code>processors</code>
</a>
</td>
<td>
<code>List&lt;<wbr>Processor<wbr>Config&gt;</code>
</td>
<td>Settings for logging processors</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>Whether the OpenTelemetry logger should be enabled</td>
</tr>
<tr>
<td>
<a id="log-limits"></a>
<a href="io.helidon.telemetry.otelconfig.LogLimitsConfig.md">
<code>log-<wbr>limits</code>
</a>
</td>
<td>
<code>Log<wbr>Limits<wbr>Config</code>
</td>
<td>Log limits to apply to log transmission</td>
</tr>
<tr>
<td>
<code>exporters</code>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> Log<wbr>Record<wbr>Exporter&gt;</code>
</td>
<td>Log record exporters</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.telemetry.SignalsConfig.md#logging"><code>telemetry.<wbr>signals.<wbr>logging</code></a>

---

See the [manifest](manifest.md) for all available types.
