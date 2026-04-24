# io.helidon.common.configurable.ScheduledThreadPoolSupplier

## Description

Supplier of a custom scheduled thread pool

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
<code>core-pool-size</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">16</code>
</td>
<td>Core pool size of the thread pool executor</td>
</tr>
<tr>
<td>
<code>prestart</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Whether to prestart core threads in this thread pool executor</td>
</tr>
<tr>
<td>
<code>thread-name-prefix</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">helidon-</code>
</td>
<td>Name prefix for threads in this thread pool executor</td>
</tr>
<tr>
<td>
<code>virtual-threads</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>When configured to <code>true</code>, an unbounded virtual executor service (project Loom) will be used</td>
</tr>
<tr>
<td>
<code>is-daemon</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Is daemon of the thread pool executor</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
