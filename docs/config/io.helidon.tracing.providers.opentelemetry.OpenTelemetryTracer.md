# io.helidon.tracing.providers.opentelemetry.OpenTelemetryTracer

## Description

Settings for OpenTelemetry tracer configuration under the <code>OpenTelemetryTracerConfig#TRACING_CONFIG_KEY</code> config key

## Configuration options

<style>
    table.cm-table code {
        white-space: nowrap !important;
    }

    table.cm-table .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


<table class="cm-table">
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
<code>exporter-type</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="OtlpExporterProtocolType">OtlpExporterProtocolType</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">GRPC</code>
</td>
<td>Type of OTLP exporter to use for pushing span data</td>
</tr>
<tr>
<td>
<code>propagators</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;CustomMethods&gt;">List&lt;CustomMethods&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Context propagators</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
