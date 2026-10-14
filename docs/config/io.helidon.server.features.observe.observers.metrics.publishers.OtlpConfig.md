# io.<wbr>helidon.<wbr>server.<wbr>features.<wbr>observe.<wbr>observers.<wbr>metrics.<wbr>publishers.<wbr>Otlp<wbr>Config

## Description

Merged configuration for server.features.observe.observers.metrics.publishers.otlp

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
<code>enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether this publisher is enabled</td>
</tr>
<tr>
<td>
<code>endpoint</code>
</td>
<td>
<code>URI</code>
</td>
<td>
<code>http:<wbr>//localhost:<wbr>4318/<wbr>v1/metrics</code>
</td>
<td>Complete HTTP or HTTPS endpoint for metrics, including its path</td>
</tr>
<tr>
<td>
<code>headers</code>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> String&gt;</code>
</td>
<td>
</td>
<td>Additional HTTP request headers, such as authentication headers</td>
</tr>
<tr>
<td>
<code>interval</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT60S</code>
</td>
<td>Delay between successive exports</td>
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
<code>max-<wbr>request-<wbr>size</code>
</td>
<td>
<code>Size</code>
</td>
<td>
<code>64 Mi<wbr>B</code>
</td>
<td>Maximum size of an uncompressed JSON export request, measured in UTF-8 encoded bytes</td>
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
<code>name</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Name of this publisher instance</td>
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
<td>String-valued resource attributes attached to each export</td>
</tr>
<tr>
<td>
<code>service-<wbr>name</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>unknown_<wbr>service</code>
</td>
<td>Service name to use when the resource attributes do not contain <code>service.<wbr>name</code></td>
</tr>
<tr>
<td>
<code>timeout</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT10S</code>
</td>
<td>Time allowed for an export, including retries</td>
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
</tbody>
</table>



## Merged Types

- [io.<wbr>helidon.<wbr>metrics.<wbr>providers.<wbr>micrometer.<wbr>Otlp<wbr>Publisher](io.helidon.metrics.providers.micrometer.OtlpPublisher.md)
- [io.<wbr>helidon.<wbr>metrics.<wbr>publishers.<wbr>otlp.<wbr>Otlp<wbr>Publisher](io.helidon.metrics.publishers.otlp.OtlpPublisher.md)

## Usages

- <a href="io.helidon.metrics.api.MetricsPublisher.md#otlp"><code>server.<wbr>features.<wbr>observe.<wbr>observers.<wbr>metrics.<wbr>publishers.<wbr>otlp</code></a>

---

See the [manifest](manifest.md) for all available types.
