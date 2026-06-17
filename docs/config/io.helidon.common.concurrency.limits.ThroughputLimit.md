# io.<wbr>helidon.<wbr>common.<wbr>concurrency.<wbr>limits.<wbr>Throughput<wbr>Limit

## Description

Configuration of <code>Throughput<wbr>Limit</code>

## Configuration options


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
<code>duration</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT1S</code>
</td>
<td>Duration of the time window over which operations will be counted</td>
</tr>
<tr>
<td>
<code>queue-<wbr>timeout</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT1S</code>
</td>
<td>How long to wait for a permit when enqueued</td>
</tr>
<tr>
<td>
<code>amount</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>0</code>
</td>
<td>Number of operations to allow during the relevant time window</td>
</tr>
<tr>
<td>
<a id="rate-limiting-algorithm"></a>
<a href="io.helidon.common.concurrency.limits.RateLimitingAlgorithmType.md">
<code>rate-<wbr>limiting-<wbr>algorithm</code>
</a>
</td>
<td>
<code>Rate<wbr>Limiting<wbr>Algorithm<wbr>Type</code>
</td>
<td>
<code>TOKEN_<wbr>BUCKET</code>
</td>
<td>The rate limiting algorithm to apply</td>
</tr>
<tr>
<td>
<code>enable-<wbr>metrics</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Whether to collect metrics for the throughput limit implementation</td>
</tr>
<tr>
<td>
<code>fair</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Whether the <code>java.<wbr>util.<wbr>concurrent.<wbr>Semaphore</code> should be <code>java.<wbr>util.<wbr>concurrent.<wbr>Semaphore#<wbr>isFair(<wbr>)</code></td>
</tr>
<tr>
<td>
<code>queue-<wbr>length</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>0</code>
</td>
<td>How many requests can be enqueued waiting for a permit</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.common.concurrency.limits.Limit.md#throughput"><code>server.<wbr>concurrency-<wbr>limit.<wbr>throughput</code></a>
- <a href="io.helidon.common.concurrency.limits.Limit.md#throughput"><code>server.<wbr>features.<wbr>limits.<wbr>concurrency-<wbr>limit.<wbr>throughput</code></a>
- <a href="io.helidon.common.concurrency.limits.Limit.md#throughput"><code>server.<wbr>sockets.<wbr>concurrency-<wbr>limit.<wbr>throughput</code></a>

---

See the [manifest](manifest.md) for all available types.
