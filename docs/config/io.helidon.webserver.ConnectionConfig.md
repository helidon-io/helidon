# io.helidon.webserver.ConnectionConfig

## Description

Configuration of a server connection (for each connection created by clients)

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
<code>connect-timeout</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">PT10S</code>
</td>
<td>Connect timeout</td>
</tr>
<tr>
<td>
<code>keep-alive</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Configure socket keep alive</td>
</tr>
<tr>
<td>
<code>read-timeout</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">PT30S</code>
</td>
<td>Read timeout</td>
</tr>
<tr>
<td>
<code>receive-buffer-size</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">32768</code>
</td>
<td>Socket receive buffer size</td>
</tr>
<tr>
<td>
<code>reuse-address</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Socket reuse address</td>
</tr>
<tr>
<td>
<code>send-buffer-size</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">32768</code>
</td>
<td>Socket send buffer size</td>
</tr>
<tr>
<td>
<code>tcp-no-delay</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Disable <a href="https://en.wikipedia.org/wiki/Nagle%27s_algorithm">Nagle's algorithm</a> by setting TCP_NODELAY to true</td>
</tr>
</tbody>
</table>



## Usages

- [`server.connection-config`](io.helidon.webserver.WebServer.md#connection-config)
- [`server.sockets.connection-config`](io.helidon.webserver.ListenerConfig.md#connection-config)

---

See the [manifest](manifest.md) for all available types.
