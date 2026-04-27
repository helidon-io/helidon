# io.helidon.telemetry.otelconfig.OpenTelemetryMetricsConfig

## Description

OpenTelemetry metrics settings

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
<code>readers</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;CustomMethods&gt;">List&lt;CustomMethods&gt;</code>
</td>
<td>Settings for metric readers</td>
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
<code>views</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;CustomMethods&gt;">List&lt;CustomMethods&gt;</code>
</td>
<td>Metric view information, configurable using <code>io.helidon.telemetry.otelconfig.ViewRegistrationConfig</code></td>
</tr>
<tr>
<td>
<code>exporters</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="Map&lt;String, CustomMethods&gt;">Map&lt;String, CustomMethods&gt;</code>
</td>
<td>Metric exporter configurations, configurable using <code>io.helidon.telemetry.otelconfig.MetricExporterConfig</code></td>
</tr>
</tbody>
</table>



## Usages

- [`telemetry.signals.metrics`](io.helidon.telemetry.SignalsConfig.md#metrics)

---

See the [manifest](manifest.md) for all available types.
