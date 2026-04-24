# io.helidon.telemetry.otelconfig.ProcessorConfig

## Description

Generic configuration for a processor such as a <code>io.opentelemetry.sdk.trace.SpanProcessor</code>, linked to an exporter such as a <code>io.opentelemetry.sdk.trace.export.SpanExporter</code> by its name in the configuration

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }

    .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


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
<a id="type"></a>
<a href="io.helidon.telemetry.otelconfig.ProcessorType.md">
<code>type</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="ProcessorType">ProcessorType</code>
</td>
<td>Processor type</td>
</tr>
<tr>
<td>
<code>exporters</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td>Name(s) of the  exporter(s) this processor should use; specifying no names uses all configured exporters (or if no exporters are configured, the default OpenTelemetry exporter(s))</td>
</tr>
</tbody>
</table>



## Dependent Types

- [io.helidon.telemetry.otelconfig.BatchProcessorConfig](io.helidon.telemetry.otelconfig.BatchProcessorConfig.md)

---

See the [manifest](manifest.md) for all available types.
