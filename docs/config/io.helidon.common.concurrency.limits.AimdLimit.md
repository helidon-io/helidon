# io.<wbr>helidon.<wbr>common.<wbr>concurrency.<wbr>limits.<wbr>Aimd<wbr>Limit

## Description

Configuration of <code>io.<wbr>helidon.<wbr>common.<wbr>concurrency.<wbr>limits.<wbr>Aimd<wbr>Limit</code>

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
<code>min-<wbr>limit</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>20</code>
</td>
<td>Minimal limit</td>
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
<td>Whether to collect metrics for the AIMD implementation</td>
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
<code>initial-<wbr>limit</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>20</code>
</td>
<td>Initial limit</td>
</tr>
<tr>
<td>
<code>max-<wbr>limit</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>200</code>
</td>
<td>Maximal limit</td>
</tr>
<tr>
<td>
<code>timeout</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT5S</code>
</td>
<td>Timeout that when exceeded is the same as if the task failed</td>
</tr>
<tr>
<td>
<code>backoff-<wbr>ratio</code>
</td>
<td>
<code>Double</code>
</td>
<td>
<code>0.<wbr>9</code>
</td>
<td>Backoff ratio to use for the algorithm</td>
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
<td>How many requests can be enqueued waiting for a permit after the max limit is reached</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.common.concurrency.limits.Limit.md#aimd"><code>server.<wbr>concurrency-<wbr>limit.<wbr>aimd</code></a>
- <a href="io.helidon.common.concurrency.limits.Limit.md#aimd"><code>server.<wbr>features.<wbr>limits.<wbr>concurrency-<wbr>limit.<wbr>aimd</code></a>
- <a href="io.helidon.common.concurrency.limits.Limit.md#aimd"><code>server.<wbr>sockets.<wbr>concurrency-<wbr>limit.<wbr>aimd</code></a>

---

See the [manifest](manifest.md) for all available types.
