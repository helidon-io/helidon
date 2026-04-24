# io.helidon.faulttolerance.Retry

## Description

<code>Retry</code> configuration bean

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
<code>overall-timeout</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">PT1S</code>
</td>
<td>Overall timeout of all retries combined</td>
</tr>
<tr>
<td>
<code>delay</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">PT0.2S</code>
</td>
<td>Base delay between try and retry</td>
</tr>
<tr>
<td>
<code>jitter</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">PT-1S</code>
</td>
<td>Jitter for <code>Retry.JitterRetryPolicy</code></td>
</tr>
<tr>
<td>
<code>calls</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">3</code>
</td>
<td>Number of calls (first try + retries)</td>
</tr>
<tr>
<td>
<code>delay-factor</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Double</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">-1.0</code>
</td>
<td>Delay retry policy factor</td>
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
</tbody>
</table>



## Usages

- [`fault-tolerance.retries`](io.helidon.FaultToleranceConfig.md#retries)

---

See the [manifest](manifest.md) for all available types.
