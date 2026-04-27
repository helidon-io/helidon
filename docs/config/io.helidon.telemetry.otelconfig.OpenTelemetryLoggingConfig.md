# io.helidon.telemetry.otelconfig.OpenTelemetryLoggingConfig

## Description

Configuration settings for OpenTelemetry logging

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
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>trace-based</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td>Whether to include <em>only</em> log records from traces which are sampled</td>
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
<a id="minimum-severity"></a>
<a href="io.opentelemetry.api.logs.Severity.md">
<code>minimum-severity</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Severity</code>
</td>
<td>Minimum severity level of log records to process</td>
</tr>
<tr>
<td>
<code>processors</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;CustomMethods&gt;">List&lt;CustomMethods&gt;</code>
</td>
<td>Settings for logging processors</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td>Whether the OpenTelemetry logger should be enabled</td>
</tr>
<tr>
<td>
<code>log-limits</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="CustomMethods">CustomMethods</code>
</td>
<td>Log limits to apply to log transmission</td>
</tr>
<tr>
<td>
<code>exporters</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="Map&lt;String, CustomMethods&gt;">Map&lt;String, CustomMethods&gt;</code>
</td>
<td>Log record exporters</td>
</tr>
</tbody>
</table>



## Usages

- [`telemetry.signals.logging`](io.helidon.telemetry.SignalsConfig.md#logging)

---

See the [manifest](manifest.md) for all available types.
