# io.<wbr>helidon.<wbr>telemetry.<wbr>otelconfig.<wbr>View<wbr>Registration<wbr>Config

## Description

Settings for an OpenTelemetry metrics view registration

## Configuration options


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
<code>cardinality-<wbr>limit</code>
</td>
<td>
<code>Integer</code>
</td>
<td>Cardinality limit</td>
</tr>
<tr>
<td>
<a id="instrument-selector"></a>
<a href="io.helidon.telemetry.otelconfig.InstrumentSelectorConfig.md">
<code>instrument-<wbr>selector</code>
</a>
</td>
<td>
<code>Instrument<wbr>Selector<wbr>Config</code>
</td>
<td>Instrument selector, configurable using <code>io.<wbr>helidon.<wbr>telemetry.<wbr>otelconfig.<wbr>Instrument<wbr>Selector<wbr>Config</code></td>
</tr>
<tr>
<td>
<code>attribute-<wbr>filter</code>
</td>
<td>
<code>Predicate</code>
</td>
<td>Attribute name filter, configurable as a string compiled as a regular expression using <code>java.<wbr>util.<wbr>regex.<wbr>Pattern</code></td>
</tr>
<tr>
<td>
<code>name</code>
</td>
<td>
<code>String</code>
</td>
<td>Metrics view name</td>
</tr>
<tr>
<td>
<code>description</code>
</td>
<td>
<code>String</code>
</td>
<td>Metric view description</td>
</tr>
<tr>
<td>
<code>aggregation</code>
</td>
<td>
<code>Aggregation</code>
</td>
<td>Aggregation for the metric view, configurable as an <code>io.<wbr>helidon.<wbr>telemetry.<wbr>otelconfig.<wbr>Aggregation<wbr>Type</code>: <code>DROP,<wbr> DEFAULT,<wbr> SUM,<wbr> LAST_<wbr>VALUE,<wbr> EXPLICIT_<wbr>BUCKET_<wbr>HISTOGRAM,<wbr> BASE2_<wbr>EXPONENTIAL_<wbr>BUCKET_<wbr>HISTOGRAM</code></td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.telemetry.otelconfig.OpenTelemetryMetricsConfig.md#views"><code>telemetry.<wbr>signals.<wbr>metrics.<wbr>views</code></a>

---

See the [manifest](manifest.md) for all available types.
