# io.helidon.telemetry.otelconfig.BatchProcessorConfig

## Description

Configuration for a batch processor

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
<td><code>schedule-delay</code></td>
<td><code>Duration</code></td>
<td>Delay between consecutive exports</td>
</tr>
<tr>
<td><code>max-export-batch-size</code></td>
<td><code>Integer</code></td>
<td>Maximum number of items batched for export together</td>
</tr>
<tr>
<td><a id="type"></a><a href="io.helidon.telemetry.otelconfig.ProcessorType.md"><code>type</code></a></td>
<td><code>ProcessorType</code></td>
<td>Processor type</td>
</tr>
<tr>
<td><code>max-queue-size</code></td>
<td><code>Integer</code></td>
<td>Maximum number of items retained before discarding excess unexported ones</td>
</tr>
<tr>
<td><code>timeout</code></td>
<td><code>Duration</code></td>
<td>Maximum time an export can run before being cancelled</td>
</tr>
<tr>
<td><code>exporters</code></td>
<td><code>List&lt;String&gt;</code></td>
<td>Name(s) of the  exporter(s) this processor should use; specifying no names uses all configured exporters (or if no exporters are configured, the default OpenTelemetry exporter(s))</td>
</tr>
</tbody>
</table>


---

See the [manifest](manifest.md) for all available types.
