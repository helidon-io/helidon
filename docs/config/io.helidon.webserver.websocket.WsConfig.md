# io.helidon.webserver.websocket.WsConfig

## Description

WebSocket protocol configuration

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
<code>name</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">websocket</code>
</td>
<td>Name of this configuration</td>
</tr>
<tr>
<td>
<code>origins</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>WebSocket origins</td>
</tr>
<tr>
<td>
<code>max-frame-length</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">1048576</code>
</td>
<td>Max WebSocket frame size supported by the server on a read operation</td>
</tr>
</tbody>
</table>



## Usages

- [`server.protocols.websocket`](io.helidon.webserver.spi.ProtocolConfig.md#websocket)
- [`server.sockets.protocols.websocket`](io.helidon.webserver.spi.ProtocolConfig.md#websocket)

---

See the [manifest](manifest.md) for all available types.
