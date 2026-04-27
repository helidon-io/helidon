# io.helidon.webserver.observe.log.LogStreamConfig

## Description

Log stream configuration for Log Observer

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
<code>idle-message-timeout</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">PT5S</code>
</td>
<td>How long to wait before we send the idle message, to make sure we keep the stream alive</td>
</tr>
<tr>
<td>
<code>queue-size</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">100</code>
</td>
<td>Length of the in-memory queue that buffers log messages from loggers before sending them over the network</td>
</tr>
<tr>
<td>
<code>content-type</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="HttpMediaType">HttpMediaType</code>
</td>
<td class="cm-default-cell">
</td>
<td><code>N/A</code></td>
</tr>
<tr>
<td>
<code>idle-string</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">%
</code>
</td>
<td>String sent when there are no log messages within the <code>#idleMessageTimeout()</code></td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether stream is enabled</td>
</tr>
</tbody>
</table>



## Usages

- [`server.features.observe.observers.log.stream`](io.helidon.webserver.observe.log.LogObserver.md#stream)

---

See the [manifest](manifest.md) for all available types.
