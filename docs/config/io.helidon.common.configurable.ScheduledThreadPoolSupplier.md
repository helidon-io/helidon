# io.helidon.common.configurable.ScheduledThreadPoolSupplier

## Description

Supplier of a custom scheduled thread pool

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
<td><code>core-pool-size</code></td>
<td><code>Integer</code></td>
<td><code>16</code></td>
<td>Core pool size of the thread pool executor</td>
</tr>
<tr>
<td><code>prestart</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Whether to prestart core threads in this thread pool executor</td>
</tr>
<tr>
<td><code>thread-name-prefix</code></td>
<td><code>String</code></td>
<td><code>helidon-</code></td>
<td>Name prefix for threads in this thread pool executor</td>
</tr>
<tr>
<td><code>virtual-threads</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>When configured to &lt;code&gt;true&lt;/code&gt;, an unbounded virtual executor service (project Loom) will be used</td>
</tr>
<tr>
<td><code>is-daemon</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Is daemon of the thread pool executor</td>
</tr>
</tbody>
</table>


---

See the [manifest](manifest.md) for all available types.
