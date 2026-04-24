# io.helidon.faulttolerance.Bulkhead

## Description

<code>Bulkhead</code> configuration bean

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }

    .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


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
<code>limit</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">10</code>
</td>
<td>Maximal number of parallel requests going through this bulkhead</td>
</tr>
<tr>
<td>
<code>enable-metrics</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Flag to enable metrics for this instance</td>
</tr>
<tr>
<td>
<code>queue-length</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">10</code>
</td>
<td>Maximal number of enqueued requests waiting for processing</td>
</tr>
</tbody>
</table>



## Usages

- [`fault-tolerance.bulkheads`](io.helidon.FaultToleranceConfig.md#bulkheads)

---

See the [manifest](manifest.md) for all available types.
