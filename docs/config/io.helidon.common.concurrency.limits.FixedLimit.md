# io.helidon.common.concurrency.limits.FixedLimit

## Description

Configuration of <code>FixedLimit</code>

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
<code>permits</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">0</code>
</td>
<td>Number of permit to allow</td>
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
<td>Whether to collect metrics for the fixed limit implementation</td>
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
<code>queue-length</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">0</code>
</td>
<td>How many requests can be enqueued waiting for a permit</td>
</tr>
</tbody>
</table>



## Usages

- [`server.concurrency-limit.fixed`](io.helidon.common.concurrency.limits.Limit.md#fixed)
- [`server.features.limits.concurrency-limit.fixed`](io.helidon.common.concurrency.limits.Limit.md#fixed)
- [`server.sockets.concurrency-limit.fixed`](io.helidon.common.concurrency.limits.Limit.md#fixed)

---

See the [manifest](manifest.md) for all available types.
