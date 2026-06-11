# io.helidon.common.concurrency.limits.FixedLimit

## Description

Configuration of <code>Fixed<wbr>Limit</code>

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
<code>permits</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>0</code>
</td>
<td>Number of permit to allow</td>
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
<td>Whether to collect metrics for the fixed limit implementation</td>
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

- [`server.concurrency-limit.fixed`](io.helidon.common.concurrency.limits.Limit.md#fixed)
- [`server.features.limits.concurrency-limit.fixed`](io.helidon.common.concurrency.limits.Limit.md#fixed)
- [`server.sockets.concurrency-limit.fixed`](io.helidon.common.concurrency.limits.Limit.md#fixed)

---

See the [manifest](manifest.md) for all available types.
