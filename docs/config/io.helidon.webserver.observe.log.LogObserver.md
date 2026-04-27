# io.helidon.webserver.observe.log.LogObserver

## Description

Log Observer configuration

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
<code>endpoint</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">log</code>
</td>
<td><code>N/A</code></td>
</tr>
<tr>
<td>
<a id="stream"></a>
<a href="io.helidon.webserver.observe.log.LogStreamConfig.md">
<code>stream</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="LogStreamConfig">LogStreamConfig</code>
</td>
<td class="cm-default-cell">
</td>
<td>Configuration of log stream</td>
</tr>
<tr>
<td>
<code>permit-all</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>Permit all access, even when not authorized</td>
</tr>
</tbody>
</table>



## Usages

- [`server.features.observe.observers.log`](io.helidon.webserver.observe.spi.Observer.md#log)

---

See the [manifest](manifest.md) for all available types.
