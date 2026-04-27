# io.helidon.common.socket.SocketOptions

## Description

Socket options

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
<td>Socket connect timeout</td>
</tr>
<tr>
<td>
<code>socket-reuse-address</code>
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
<code>socket-send-buffer-size</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
</td>
<td>Socket send buffer size</td>
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
<td>Socket read timeout</td>
</tr>
<tr>
<td>
<code>socket-receive-buffer-size</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
</td>
<td>Socket receive buffer size</td>
</tr>
<tr>
<td>
<code>socket-keep-alive</code>
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
<code>tcp-no-delay</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>This option may improve performance on some systems</td>
</tr>
</tbody>
</table>



## Usages

- [`server.connection-options`](io.helidon.webserver.WebServer.md#connection-options)
- [`server.sockets.connection-options`](io.helidon.webserver.ListenerConfig.md#connection-options)

---

See the [manifest](manifest.md) for all available types.
