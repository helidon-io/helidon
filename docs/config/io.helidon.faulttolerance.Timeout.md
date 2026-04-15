# io.helidon.faulttolerance.Timeout

## Description

&lt;code&gt;Timeout&lt;/code&gt; configuration bean

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
<td><code>current-thread</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Flag to indicate that code must be executed in current thread instead of in an executor&#x27;s thread</td>
</tr>
<tr>
<td><code>enable-metrics</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Flag to enable metrics for this instance</td>
</tr>
<tr>
<td><code>timeout</code></td>
<td><code>Duration</code></td>
<td><code>PT10S</code></td>
<td>Duration to wait before timing out</td>
</tr>
</tbody>
</table>


## Usages

- [`fault-tolerance.timeouts`](io.helidon.FaultToleranceConfig.md#timeouts)

---

See the [manifest](manifest.md) for all available types.
