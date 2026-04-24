# io.helidon.common.concurrency.limits.AimdLimit

## Description

Configuration of <code>io.helidon.common.concurrency.limits.AimdLimit</code>

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
<code>queue-timeout</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">PT1S</code>
</td>
<td>How long to wait for a permit when enqueued</td>
</tr>
<tr>
<td>
<code>min-limit</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">20</code>
</td>
<td>Minimal limit</td>
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
<td>Whether to collect metrics for the AIMD implementation</td>
</tr>
<tr>
<td>
<code>fair</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Whether the <code>java.util.concurrent.Semaphore</code> should be <code>java.util.concurrent.Semaphore#isFair()</code></td>
</tr>
<tr>
<td>
<code>initial-limit</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">20</code>
</td>
<td>Initial limit</td>
</tr>
<tr>
<td>
<code>max-limit</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">200</code>
</td>
<td>Maximal limit</td>
</tr>
<tr>
<td>
<code>timeout</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">PT5S</code>
</td>
<td>Timeout that when exceeded is the same as if the task failed</td>
</tr>
<tr>
<td>
<code>backoff-ratio</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Double</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">0.9</code>
</td>
<td>Backoff ratio to use for the algorithm</td>
</tr>
<tr>
<td>
<code>queue-length</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">0</code>
</td>
<td>How many requests can be enqueued waiting for a permit after the max limit is reached</td>
</tr>
</tbody>
</table>



## Usages

- [`server.concurrency-limit.aimd`](io.helidon.common.concurrency.limits.Limit.md#aimd)
- [`server.features.limits.concurrency-limit.aimd`](io.helidon.common.concurrency.limits.Limit.md#aimd)
- [`server.sockets.concurrency-limit.aimd`](io.helidon.common.concurrency.limits.Limit.md#aimd)

---

See the [manifest](manifest.md) for all available types.
