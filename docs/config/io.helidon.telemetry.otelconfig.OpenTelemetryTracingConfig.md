# io.helidon.telemetry.otelconfig.OpenTelemetryTracingConfig

## Description

OpenTelemetry tracer settings

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
<code>span-limits</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="CustomMethods">CustomMethods</code>
</td>
<td>Tracing span limits</td>
</tr>
<tr>
<td>
<code>attributes</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="CustomMethods">CustomMethods</code>
</td>
<td>Name/value pairs passed to OpenTelemetry</td>
</tr>
<tr>
<td>
<code>processors</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;CustomMethods&gt;">List&lt;CustomMethods&gt;</code>
</td>
<td>Settings for span processors</td>
</tr>
<tr>
<td>
<code>exporters</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="Map&lt;String, CustomMethods&gt;">Map&lt;String, CustomMethods&gt;</code>
</td>
<td>Span exporters</td>
</tr>
<tr>
<td>
<code>sampler</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="CustomMethods">CustomMethods</code>
</td>
<td>Tracing sampler</td>
</tr>
</tbody>
</table>



## Usages

- [`telemetry.signals.tracing`](io.helidon.telemetry.SignalsConfig.md#tracing)

---

See the [manifest](manifest.md) for all available types.
