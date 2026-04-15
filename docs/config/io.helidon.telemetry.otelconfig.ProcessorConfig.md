# io.helidon.telemetry.otelconfig.ProcessorConfig

## Description

Generic configuration for a processor such as a &lt;code&gt;io.opentelemetry.sdk.trace.SpanProcessor&lt;/code&gt;, linked to an exporter such as a &lt;code&gt;io.opentelemetry.sdk.trace.export.SpanExporter&lt;/code&gt; by its name in the configuration

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }
</style>

<table>
<thead>
<tr>
<th>Key</th><th>Type</th><th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><a id="type"></a><a href="io.helidon.telemetry.otelconfig.ProcessorType.md"><code>type</code></a></td>
<td><code>ProcessorType</code></td>
<td>Processor type</td>
</tr>
<tr>
<td><code>exporters</code></td>
<td><code>List&lt;String&gt;</code></td>
<td>Name(s) of the  exporter(s) this processor should use; specifying no names uses all configured exporters (or if no exporters are configured, the default OpenTelemetry exporter(s))</td>
</tr>
</tbody>
</table>


## Dependent Types

- [io.helidon.telemetry.otelconfig.BatchProcessorConfig](io.helidon.telemetry.otelconfig.BatchProcessorConfig.md)

---

See the [manifest](manifest.md) for all available types.
