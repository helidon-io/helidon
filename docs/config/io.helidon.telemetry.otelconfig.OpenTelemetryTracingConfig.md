# io.<wbr>helidon.<wbr>telemetry.<wbr>otelconfig.<wbr>Open<wbr>Telemetry<wbr>Tracing<wbr>Config

## Description

OpenTelemetry tracer settings

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
<code>span-<wbr>limits</code>
</td>
<td>
<code>Custom<wbr>Methods</code>
</td>
<td>Tracing span limits</td>
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
<code>processors</code>
</td>
<td>
<code>List&lt;<wbr>Custom<wbr>Methods&gt;</code>
</td>
<td>Settings for span processors</td>
</tr>
<tr>
<td>
<code>exporters</code>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> Custom<wbr>Methods&gt;</code>
</td>
<td>Span exporters</td>
</tr>
<tr>
<td>
<code>sampler</code>
</td>
<td>
<code>Custom<wbr>Methods</code>
</td>
<td>Tracing sampler</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.telemetry.SignalsConfig.md#tracing"><code>telemetry.<wbr>signals.<wbr>tracing</code></a>

---

See the [manifest](manifest.md) for all available types.
