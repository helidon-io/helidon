# io.helidon.metrics.providers.micrometer.OtlpPublisher

## Description

Settings for an OTLP publisher

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
<td><code>headers</code></td>
<td><code>Map&lt;String, String&gt;</code></td>
<td></td>
<td>Headers to add to each transmission message</td>
</tr>
<tr>
<td><a id="base-time-unit"></a><a href="java.util.concurrent.TimeUnit.md"><code>base-time-unit</code></a></td>
<td><code>TimeUnit</code></td>
<td><code>java.util.concurrent.TimeUnit.MILLISECONDS</code></td>
<td>Base time unit for timers</td>
</tr>
<tr>
<td><code>prefix</code></td>
<td><code>String</code></td>
<td><code>otlp</code></td>
<td>The prefix for settings</td>
</tr>
<tr>
<td><a id="aggregation-temporality"></a><a href="io.micrometer.registry.otlp.AggregationTemporality.md"><code>aggregation-temporality</code></a></td>
<td><code>AggregationTemporality</code></td>
<td><code>CUMULATIVE</code></td>
<td>Algorithm to use for adjusting values before transmission</td>
</tr>
<tr>
<td><code>enabled</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether the configured publisher is enabled</td>
</tr>
<tr>
<td><code>url</code></td>
<td><code>String</code></td>
<td><code>http://localhost:4318/v1/metrics</code></td>
<td>URL to which to send metrics telemetry</td>
</tr>
<tr>
<td><code>max-scale</code></td>
<td><code>Integer</code></td>
<td><code>20</code></td>
<td>Maximum scale value to apply to statistical histogram</td>
</tr>
<tr>
<td><code>batch-size</code></td>
<td><code>Integer</code></td>
<td><code>10000</code></td>
<td>Number of measurements to send in a single request to the backend</td>
</tr>
<tr>
<td><code>max-bucket-count</code></td>
<td><code>Integer</code></td>
<td><code>160</code></td>
<td>Maximum bucket count to apply to statistical histogram</td>
</tr>
<tr>
<td><code>name</code></td>
<td><code>String</code></td>
<td></td>
<td>&lt;code&gt;N/A&lt;/code&gt;</td>
</tr>
<tr>
<td><code>interval</code></td>
<td><code>Duration</code></td>
<td><code>PT60s</code></td>
<td>Interval between successive transmissions of metrics data</td>
</tr>
<tr>
<td><code>max-buckets-per-meter</code></td>
<td><code>Map&lt;String, Integer&gt;</code></td>
<td></td>
<td>Maximum number of buckets to use for specific meters</td>
</tr>
<tr>
<td><code>properties</code></td>
<td><code>Map&lt;String, String&gt;</code></td>
<td></td>
<td>Property values to be returned by the OTLP meter registry configuration</td>
</tr>
<tr>
<td><code>resource-attributes</code></td>
<td><code>Map&lt;String, String&gt;</code></td>
<td></td>
<td>Attribute name/value pairs to be associated with all metrics transmissions</td>
</tr>
</tbody>
</table>


## Usages

- [`metrics.publishers.otlp`](io.helidon.metrics.api.MetricsPublisher.md#otlp)
- [`server.features.observe.observers.metrics.publishers.otlp`](io.helidon.metrics.api.MetricsPublisher.md#otlp)

---

See the [manifest](manifest.md) for all available types.
