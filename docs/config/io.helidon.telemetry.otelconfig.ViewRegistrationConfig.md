# io.helidon.telemetry.otelconfig.ViewRegistrationConfig

## Description

Settings for an OpenTelemetry metrics view registration

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
<td><code>cardinality-limit</code></td>
<td><code>Integer</code></td>
<td>Cardinality limit</td>
</tr>
<tr>
<td><code>instrument-selector</code></td>
<td><code>CustomMethods</code></td>
<td>Instrument selector, configurable using &lt;code&gt;io.helidon.telemetry.otelconfig.InstrumentSelectorConfig&lt;/code&gt;</td>
</tr>
<tr>
<td><code>attribute-filter</code></td>
<td><code>CustomMethods</code></td>
<td>Attribute name filter, configurable as a string compiled as a regular expression using &lt;code&gt;java.util.regex.Pattern&lt;/code&gt;</td>
</tr>
<tr>
<td><code>name</code></td>
<td><code>String</code></td>
<td>Metrics view name</td>
</tr>
<tr>
<td><code>description</code></td>
<td><code>String</code></td>
<td>Metric view description</td>
</tr>
<tr>
<td><code>aggregation</code></td>
<td><code>CustomMethods</code></td>
<td>Aggregation for the metric view, configurable as an &lt;code&gt;io.helidon.telemetry.otelconfig.AggregationType&lt;/code&gt;: &lt;code&gt;DROP, DEFAULT, SUM, LAST_VALUE, EXPLICIT_BUCKET_HISTOGRAM, BASE2_EXPONENTIAL_BUCKET_HISTOGRAM&lt;/code&gt;</td>
</tr>
</tbody>
</table>


---

See the [manifest](manifest.md) for all available types.
