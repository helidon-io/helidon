# io.helidon.common.concurrency.limits.AimdLimit

## Description

Configuration of &lt;code&gt;io.helidon.common.concurrency.limits.AimdLimit&lt;/code&gt;

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
<td><code>queue-timeout</code></td>
<td><code>Duration</code></td>
<td><code>PT1S</code></td>
<td>How long to wait for a permit when enqueued</td>
</tr>
<tr>
<td><code>min-limit</code></td>
<td><code>Integer</code></td>
<td><code>20</code></td>
<td>Minimal limit</td>
</tr>
<tr>
<td><code>enable-metrics</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Whether to collect metrics for the AIMD implementation</td>
</tr>
<tr>
<td><code>fair</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Whether the &lt;code&gt;java.util.concurrent.Semaphore&lt;/code&gt; should be &lt;code&gt;java.util.concurrent.Semaphore#isFair()&lt;/code&gt;</td>
</tr>
<tr>
<td><code>initial-limit</code></td>
<td><code>Integer</code></td>
<td><code>20</code></td>
<td>Initial limit</td>
</tr>
<tr>
<td><code>max-limit</code></td>
<td><code>Integer</code></td>
<td><code>200</code></td>
<td>Maximal limit</td>
</tr>
<tr>
<td><code>timeout</code></td>
<td><code>Duration</code></td>
<td><code>PT5S</code></td>
<td>Timeout that when exceeded is the same as if the task failed</td>
</tr>
<tr>
<td><code>backoff-ratio</code></td>
<td><code>Double</code></td>
<td><code>0.9</code></td>
<td>Backoff ratio to use for the algorithm</td>
</tr>
<tr>
<td><code>queue-length</code></td>
<td><code>Integer</code></td>
<td><code>0</code></td>
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
