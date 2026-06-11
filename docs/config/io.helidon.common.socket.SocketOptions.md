# io.helidon.common.socket.SocketOptions

## Description

Socket options

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
<code>connect-<wbr>timeout</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT10S</code>
</td>
<td>Socket connect timeout</td>
</tr>
<tr>
<td>
<code>socket-<wbr>reuse-<wbr>address</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Socket reuse address</td>
</tr>
<tr>
<td>
<code>socket-<wbr>send-<wbr>buffer-<wbr>size</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
</td>
<td>Socket send buffer size</td>
</tr>
<tr>
<td>
<code>read-<wbr>timeout</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT30S</code>
</td>
<td>Socket read timeout</td>
</tr>
<tr>
<td>
<code>socket-<wbr>receive-<wbr>buffer-<wbr>size</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
</td>
<td>Socket receive buffer size</td>
</tr>
<tr>
<td>
<code>socket-<wbr>keep-<wbr>alive</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Configure socket keep alive</td>
</tr>
<tr>
<td>
<code>tcp-<wbr>no-delay</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
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
