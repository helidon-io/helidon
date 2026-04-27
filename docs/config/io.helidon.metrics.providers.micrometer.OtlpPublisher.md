# io.helidon.metrics.providers.micrometer.OtlpPublisher

## Description

Settings for an OTLP publisher

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
<code>headers</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="Map&lt;String, String&gt;">Map&lt;String, String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Headers to add to each transmission message</td>
</tr>
<tr>
<td>
<a id="base-time-unit"></a>
<a href="java.util.concurrent.TimeUnit.md">
<code>base-time-unit</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">TimeUnit</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value" title="java.util.concurrent.TimeUnit.MILLISECONDS">java.util.concurrent.TimeUnit.MILLISECONDS</code>
</td>
<td>Base time unit for timers</td>
</tr>
<tr>
<td>
<code>prefix</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">otlp</code>
</td>
<td>The prefix for settings</td>
</tr>
<tr>
<td>
<a id="aggregation-temporality"></a>
<a href="io.micrometer.registry.otlp.AggregationTemporality.md">
<code>aggregation-temporality</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="AggregationTemporality">AggregationTemporality</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">CUMULATIVE</code>
</td>
<td>Algorithm to use for adjusting values before transmission</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether the configured publisher is enabled</td>
</tr>
<tr>
<td>
<code>url</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value" title="http://localhost:4318/v1/metrics">http://localhost:4318/v1/metrics</code>
</td>
<td>URL to which to send metrics telemetry</td>
</tr>
<tr>
<td>
<code>max-scale</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">20</code>
</td>
<td>Maximum scale value to apply to statistical histogram</td>
</tr>
<tr>
<td>
<code>batch-size</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">10000</code>
</td>
<td>Number of measurements to send in a single request to the backend</td>
</tr>
<tr>
<td>
<code>max-bucket-count</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">160</code>
</td>
<td>Maximum bucket count to apply to statistical histogram</td>
</tr>
<tr>
<td>
<code>name</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td><code>N/A</code></td>
</tr>
<tr>
<td>
<code>interval</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">PT60s</code>
</td>
<td>Interval between successive transmissions of metrics data</td>
</tr>
<tr>
<td>
<code>max-buckets-per-meter</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="Map&lt;String, Integer&gt;">Map&lt;String, Integer&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Maximum number of buckets to use for specific meters</td>
</tr>
<tr>
<td>
<code>properties</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="Map&lt;String, String&gt;">Map&lt;String, String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Property values to be returned by the OTLP meter registry configuration</td>
</tr>
<tr>
<td>
<code>resource-attributes</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="Map&lt;String, String&gt;">Map&lt;String, String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Attribute name/value pairs to be associated with all metrics transmissions</td>
</tr>
</tbody>
</table>



## Usages

- [`metrics.publishers.otlp`](io.helidon.metrics.api.MetricsPublisher.md#otlp)
- [`server.features.observe.observers.metrics.publishers.otlp`](io.helidon.metrics.api.MetricsPublisher.md#otlp)

---

See the [manifest](manifest.md) for all available types.
