# io.helidon.metrics.providers.micrometer.OtlpPublisher

## Description

Settings for an OTLP publisher

## Configuration options


<table>
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
<td>
<code>Map&lt;<wbr>String,<wbr> String&gt;</code>
</td>
<td>
</td>
<td>Headers to add to each transmission message</td>
</tr>
<tr>
<td>
<a id="base-time-unit"></a>
<a href="java.util.concurrent.TimeUnit.md">
<code>base-<wbr>time-<wbr>unit</code>
</a>
</td>
<td>
<code>Time<wbr>Unit</code>
</td>
<td>
<code>java.<wbr>util.<wbr>concurrent.<wbr>Time<wbr>Unit.<wbr>MILLISECONDS</code>
</td>
<td>Base time unit for timers</td>
</tr>
<tr>
<td>
<code>prefix</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>otlp</code>
</td>
<td>The prefix for settings</td>
</tr>
<tr>
<td>
<a id="aggregation-temporality"></a>
<a href="io.micrometer.registry.otlp.AggregationTemporality.md">
<code>aggregation-<wbr>temporality</code>
</a>
</td>
<td>
<code>Aggregation<wbr>Temporality</code>
</td>
<td>
<code>CUMULATIVE</code>
</td>
<td>Algorithm to use for adjusting values before transmission</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether the configured publisher is enabled</td>
</tr>
<tr>
<td>
<code>url</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>http:<wbr>//localhost:<wbr>4318/<wbr>v1/metrics</code>
</td>
<td>URL to which to send metrics telemetry</td>
</tr>
<tr>
<td>
<code>max-<wbr>scale</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>20</code>
</td>
<td>Maximum scale value to apply to statistical histogram</td>
</tr>
<tr>
<td>
<code>batch-<wbr>size</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>10000</code>
</td>
<td>Number of measurements to send in a single request to the backend</td>
</tr>
<tr>
<td>
<code>max-<wbr>bucket-<wbr>count</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>160</code>
</td>
<td>Maximum bucket count to apply to statistical histogram</td>
</tr>
<tr>
<td>
<code>name</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td><code>N/<wbr>A</code></td>
</tr>
<tr>
<td>
<code>interval</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT60s</code>
</td>
<td>Interval between successive transmissions of metrics data</td>
</tr>
<tr>
<td>
<code>max-<wbr>buckets-<wbr>per-<wbr>meter</code>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> Integer&gt;</code>
</td>
<td>
</td>
<td>Maximum number of buckets to use for specific meters</td>
</tr>
<tr>
<td>
<code>properties</code>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> String&gt;</code>
</td>
<td>
</td>
<td>Property values to be returned by the OTLP meter registry configuration</td>
</tr>
<tr>
<td>
<code>resource-<wbr>attributes</code>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> String&gt;</code>
</td>
<td>
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
