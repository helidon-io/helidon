# io.helidon.common.concurrency.limits.ThroughputLimit

## Description

Configuration of &lt;code&gt;ThroughputLimit&lt;/code&gt;

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
<td><code>duration</code></td>
<td><code>Duration</code></td>
<td><code>PT1S</code></td>
<td>Duration of the time window over which operations will be counted</td>
</tr>
<tr>
<td><code>queue-timeout</code></td>
<td><code>Duration</code></td>
<td><code>PT1S</code></td>
<td>How long to wait for a permit when enqueued</td>
</tr>
<tr>
<td><code>amount</code></td>
<td><code>Integer</code></td>
<td><code>0</code></td>
<td>Number of operations to allow during the relevant time window</td>
</tr>
<tr>
<td><a id="rate-limiting-algorithm"></a><a href="io.helidon.common.concurrency.limits.RateLimitingAlgorithmType.md"><code>rate-limiting-algorithm</code></a></td>
<td><code>RateLimitingAlgorithmType</code></td>
<td><code>TOKEN_BUCKET</code></td>
<td>The rate limiting algorithm to apply</td>
</tr>
<tr>
<td><code>enable-metrics</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Whether to collect metrics for the throughput limit implementation</td>
</tr>
<tr>
<td><code>fair</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Whether the &lt;code&gt;java.util.concurrent.Semaphore&lt;/code&gt; should be &lt;code&gt;java.util.concurrent.Semaphore#isFair()&lt;/code&gt;</td>
</tr>
<tr>
<td><code>queue-length</code></td>
<td><code>Integer</code></td>
<td><code>0</code></td>
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
