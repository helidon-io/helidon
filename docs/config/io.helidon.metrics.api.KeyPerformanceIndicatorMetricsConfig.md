# io.helidon.metrics.api.KeyPerformanceIndicatorMetricsConfig

## Description

Config bean for KPI metrics configuration

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
<code>extended</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Whether KPI extended metrics are enabled</td>
</tr>
<tr>
<td>
<a id="long-running-requests"></a>
<a href="io.helidon.metrics.keyPerformanceIndicators.LongRunningRequestsConfig.md">
<code>long-running-requests</code>
</a>
</td>
<td class="cm-type-cell">
</td>
<td class="cm-default-cell">
</td>
<td>Configuration for long-running-requests</td>
</tr>
</tbody>
</table>



## Usages

- [`metrics.key-performance-indicators`](io.helidon.MetricsConfig.md#key-performance-indicators)
- [`server.features.observe.observers.metrics.key-performance-indicators`](io.helidon.webserver.observe.metrics.MetricsObserver.md#key-performance-indicators)

---

See the [manifest](manifest.md) for all available types.
