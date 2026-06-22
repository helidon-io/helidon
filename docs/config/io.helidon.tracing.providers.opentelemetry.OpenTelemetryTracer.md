# io.<wbr>helidon.<wbr>tracing.<wbr>providers.<wbr>opentelemetry.<wbr>Open<wbr>Telemetry<wbr>Tracer

## Description

Settings for OpenTelemetry tracer configuration under the <code>Open<wbr>Telemetry<wbr>Tracer<wbr>Config#<wbr>TRACING_<wbr>CONFIG_<wbr>KEY</code> config key

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
<a id="exporter-type"></a>
<a href="io.helidon.tracing.providers.opentelemetry.OtlpExporterProtocolType.md">
<code>exporter-<wbr>type</code>
</a>
</td>
<td>
<code>Otlp<wbr>Exporter<wbr>Protocol<wbr>Type</code>
</td>
<td>
<code>GRPC</code>
</td>
<td>Type of OTLP exporter to use for pushing span data</td>
</tr>
<tr>
<td>
<code>propagators</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
</td>
<td>Context propagators</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
