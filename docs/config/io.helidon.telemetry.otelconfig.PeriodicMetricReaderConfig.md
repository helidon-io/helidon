# io.helidon.telemetry.otelconfig.PeriodicMetricReaderConfig

## Description

Settings for OpenTelemetry periodic metric reader

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
<code>exporter</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Name of the configured metric exporter to use for this metric reader</td>
</tr>
<tr>
<td>
<code>interval</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
</td>
<td>Metric reader read interval</td>
</tr>
<tr>
<td>
<a id="type"></a>
<a href="io.helidon.telemetry.otelconfig.MetricReaderType.md">
<code>type</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="MetricReaderType">MetricReaderType</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">PERIODIC</code>
</td>
<td>Metric reader type</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
