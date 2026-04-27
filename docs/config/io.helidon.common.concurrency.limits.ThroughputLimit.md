# io.helidon.common.concurrency.limits.ThroughputLimit

## Description

Configuration of <code>ThroughputLimit</code>

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
<code>duration</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">PT1S</code>
</td>
<td>Duration of the time window over which operations will be counted</td>
</tr>
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
<code>amount</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">0</code>
</td>
<td>Number of operations to allow during the relevant time window</td>
</tr>
<tr>
<td>
<a id="rate-limiting-algorithm"></a>
<a href="io.helidon.common.concurrency.limits.RateLimitingAlgorithmType.md">
<code>rate-limiting-algorithm</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="RateLimitingAlgorithmType">RateLimitingAlgorithmType</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value" title="TOKEN_BUCKET">TOKEN_BUCKET</code>
</td>
<td>The rate limiting algorithm to apply</td>
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
<td>Whether to collect metrics for the throughput limit implementation</td>
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

- [`server.concurrency-limit.throughput`](io.helidon.common.concurrency.limits.Limit.md#throughput)
- [`server.features.limits.concurrency-limit.throughput`](io.helidon.common.concurrency.limits.Limit.md#throughput)
- [`server.sockets.concurrency-limit.throughput`](io.helidon.common.concurrency.limits.Limit.md#throughput)

---

See the [manifest](manifest.md) for all available types.
