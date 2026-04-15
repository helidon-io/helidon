# io.helidon.tracing.providers.opentelemetry.OpenTelemetryTracer

## Description

Settings for OpenTelemetry tracer configuration under the &lt;code&gt;OpenTelemetryTracerConfig#TRACING_CONFIG_KEY&lt;/code&gt; config key

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }
</style>

<table>
<thead>
<tr>
<th>Key</th><th>Type</th><th>Default Value</th><th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><a id="exporter-type"></a><a href="io.helidon.tracing.providers.opentelemetry.OtlpExporterProtocolType.md"><code>exporter-type</code></a></td>
<td><code>OtlpExporterProtocolType</code></td>
<td><code>GRPC</code></td>
<td>Type of OTLP exporter to use for pushing span data</td>
</tr>
<tr>
<td><code>propagators</code></td>
<td><code>List&lt;CustomMethods&gt;</code></td>
<td></td>
<td>Context propagators</td>
</tr>
</tbody>
</table>


---

See the [manifest](manifest.md) for all available types.
