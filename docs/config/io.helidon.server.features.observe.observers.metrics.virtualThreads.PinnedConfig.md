# io.helidon.server.features.observe.observers.metrics.virtualThreads.PinnedConfig

## Description

Configuration for server.features.observe.observers.metrics.virtual-threads.pinned

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
<code>threshold</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">PT0.020S</code>
</td>
<td>Threshold for sampling pinned virtual threads to include in the pinned threads meter</td>
</tr>
</tbody>
</table>



## Usages

- [`server.features.observe.observers.metrics.virtual-threads.pinned`](io.helidon.server.features.observe.observers.metrics.VirtualThreadsConfig.md#pinned)

---

See the [manifest](manifest.md) for all available types.
