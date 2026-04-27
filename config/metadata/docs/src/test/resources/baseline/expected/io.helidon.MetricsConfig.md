# io.helidon.MetricsConfig

## Description

Merged configuration for metrics

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
<code>buckets</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Published buckets</td>
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
<td>Enable metrics</td>
</tr>
<tr>
<td>
<code>endpoint</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Observation endpoint</td>
</tr>
<tr>
<td>
<code>scope</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Metrics scope</td>
</tr>
</tbody>
</table>



## Merged Types

- [com.acme.AcmeMetricsConfig](com.acme.AcmeMetricsConfig.md)
- [com.acme.AcmeMetricsObserverConfig](com.acme.AcmeMetricsObserverConfig.md)

## Usages

- [`metrics`](config_reference.md#metrics)

---

See the [manifest](manifest.md) for all available types.
