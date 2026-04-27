# io.helidon.telemetry.otelconfig.ViewRegistrationConfig

## Description

Settings for an OpenTelemetry metrics view registration

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
<code>cardinality-limit</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td>Cardinality limit</td>
</tr>
<tr>
<td>
<code>instrument-selector</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="CustomMethods">CustomMethods</code>
</td>
<td>Instrument selector, configurable using <code>io.helidon.telemetry.otelconfig.InstrumentSelectorConfig</code></td>
</tr>
<tr>
<td>
<code>attribute-filter</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="CustomMethods">CustomMethods</code>
</td>
<td>Attribute name filter, configurable as a string compiled as a regular expression using <code>java.util.regex.Pattern</code></td>
</tr>
<tr>
<td>
<code>name</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>Metrics view name</td>
</tr>
<tr>
<td>
<code>description</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>Metric view description</td>
</tr>
<tr>
<td>
<code>aggregation</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="CustomMethods">CustomMethods</code>
</td>
<td>Aggregation for the metric view, configurable as an <code>io.helidon.telemetry.otelconfig.AggregationType</code>: <code>DROP, DEFAULT, SUM, LAST_VALUE, EXPLICIT_BUCKET_HISTOGRAM, BASE2_EXPONENTIAL_BUCKET_HISTOGRAM</code></td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
