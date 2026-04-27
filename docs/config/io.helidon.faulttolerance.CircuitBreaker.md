# io.helidon.faulttolerance.CircuitBreaker

## Description

Configuration of a circuit breaker

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
<code>volume</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">10</code>
</td>
<td>Rolling window size used to calculate ratio of failed requests</td>
</tr>
<tr>
<td>
<code>delay</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">PT5S</code>
</td>
<td>How long to wait before transitioning from open to half-open state</td>
</tr>
<tr>
<td>
<code>error-ratio</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">60</code>
</td>
<td>How many failures out of 100 will trigger the circuit to open</td>
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
<code>success-threshold</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">1</code>
</td>
<td>How many successful calls will close a half-open circuit</td>
</tr>
</tbody>
</table>



## Usages

- [`fault-tolerance.circuit-breakers`](io.helidon.FaultToleranceConfig.md#circuit-breakers)

---

See the [manifest](manifest.md) for all available types.
