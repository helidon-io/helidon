# io.helidon.faulttolerance.CircuitBreaker

## Description

Configuration of a circuit breaker

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
<td><code>volume</code></td>
<td><code>Integer</code></td>
<td><code>10</code></td>
<td>Rolling window size used to calculate ratio of failed requests</td>
</tr>
<tr>
<td><code>delay</code></td>
<td><code>Duration</code></td>
<td><code>PT5S</code></td>
<td>How long to wait before transitioning from open to half-open state</td>
</tr>
<tr>
<td><code>error-ratio</code></td>
<td><code>Integer</code></td>
<td><code>60</code></td>
<td>How many failures out of 100 will trigger the circuit to open</td>
</tr>
<tr>
<td><code>enable-metrics</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Flag to enable metrics for this instance</td>
</tr>
<tr>
<td><code>success-threshold</code></td>
<td><code>Integer</code></td>
<td><code>1</code></td>
<td>How many successful calls will close a half-open circuit</td>
</tr>
</tbody>
</table>


## Usages

- [`fault-tolerance.circuit-breakers`](io.helidon.FaultToleranceConfig.md#circuit-breakers)

---

See the [manifest](manifest.md) for all available types.
