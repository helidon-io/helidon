# io.helidon.telemetry.otelconfig.AggregationType

## Description

This type is an enumeration.

## Allowed Values

<style>
    code {
        white-space: nowrap !important;
    }
</style>

<table>
<thead>
<tr>
<th>Value</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>DROP</code></td>
<td>Drops all metrics; exports no metrics</td>
</tr>
<tr>
<td><code>DEFAULT</code></td>
<td>Default aggregation for a given instrument type</td>
</tr>
<tr>
<td><code>SUM</code></td>
<td>Aggregates measurements into a double sum or long sum</td>
</tr>
<tr>
<td><code>LAST_VALUE</code></td>
<td>Records the last seen measurement as a double aauge or long gauge</td>
</tr>
<tr>
<td><code>EXPLICIT_BUCKET_HISTOGRAM</code></td>
<td>Aggregates measurements into a histogram using default or explicit bucket boundaries</td>
</tr>
<tr>
<td><code>BASE2_EXPONENTIAL_BUCKET_HISTOGRAM</code></td>
<td>Aggregates measurements into a base-2 exponential histogram using default or explicit maximum number of buckets and maximum scale</td>
</tr>
</tbody>
</table>

---

See the [manifest](manifest.md) for all available types.
