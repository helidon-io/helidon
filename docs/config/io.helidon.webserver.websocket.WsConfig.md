# io.helidon.webserver.websocket.WsConfig

## Description

WebSocket protocol configuration

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
<code>name</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>websocket</code>
</td>
<td>Name of this configuration</td>
</tr>
<tr>
<td>
<code>origins</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
</td>
<td>WebSocket origins</td>
</tr>
<tr>
<td>
<code>max-<wbr>frame-<wbr>length</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>1048576</code>
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
