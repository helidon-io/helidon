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
<a id="span-limits"></a>
<a href="io.helidon.telemetry.otelconfig.SpanLimitsConfig.md">
<code>span-<wbr>limits</code>
</a>
</td>
<td>
<code>Span<wbr>Limits<wbr>Config</code>
</td>
<td>Tracing span limits</td>
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
<a id="processors"></a>
<a href="io.helidon.telemetry.otelconfig.ProcessorConfig.md">
<code>processors</code>
</a>
</td>
<td>
<code>List&lt;<wbr>Processor<wbr>Config&gt;</code>
</td>
<td>Settings for span processors</td>
</tr>
<tr>
<td>
<code>exporters</code>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> Span<wbr>Exporter&gt;</code>
</td>
<td>Span exporters</td>
</tr>
<tr>
<td>
<a id="sampler"></a>
<a href="io.helidon.telemetry.otelconfig.SamplerConfig.md">
<code>sampler</code>
</a>
</td>
<td>
<code>Sampler<wbr>Config</code>
</td>
<td>Tracing sampler</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.telemetry.SignalsConfig.md#tracing"><code>telemetry.<wbr>signals.<wbr>tracing</code></a>

---

See the [manifest](manifest.md) for all available types.
