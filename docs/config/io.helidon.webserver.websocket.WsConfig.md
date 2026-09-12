# io.<wbr>helidon.<wbr>webserver.<wbr>websocket.<wbr>WsConfig

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
<code>max-<wbr>buffered-<wbr>message-<wbr>size</code>
</td>
<td>
<code>Size</code>
</td>
<td>
<code>1 Mi<wbr>B</code>
</td>
<td>Maximum size of a WebSocket message buffered for delivery to a listener</td>
</tr>
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

- <a href="io.helidon.webserver.spi.ProtocolConfig.md#websocket"><code>server.<wbr>protocols.<wbr>websocket</code></a>
- <a href="io.helidon.webserver.spi.ProtocolConfig.md#websocket"><code>server.<wbr>sockets.<wbr>protocols.<wbr>websocket</code></a>

---

See the [manifest](manifest.md) for all available types.
