# io.helidon.faulttolerance.Bulkhead

## Description

&lt;code&gt;Bulkhead&lt;/code&gt; configuration bean

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
<td><code>limit</code></td>
<td><code>Integer</code></td>
<td><code>10</code></td>
<td>Maximal number of parallel requests going through this bulkhead</td>
</tr>
<tr>
<td><code>enable-metrics</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Flag to enable metrics for this instance</td>
</tr>
<tr>
<td><code>queue-length</code></td>
<td><code>Integer</code></td>
<td><code>10</code></td>
<td>Maximal number of enqueued requests waiting for processing</td>
</tr>
</tbody>
</table>


## Usages

- [`fault-tolerance.bulkheads`](io.helidon.FaultToleranceConfig.md#bulkheads)

---

See the [manifest](manifest.md) for all available types.
