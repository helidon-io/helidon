# io.helidon.common.socket.SocketOptions

## Description

Socket options

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }
</style>

<table>
<thead>
<tr>
<th>Key</th><th>Type</th><th>Default Value</th><th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>connect-timeout</code></td>
<td><code>Duration</code></td>
<td><code>PT10S</code></td>
<td>Socket connect timeout</td>
</tr>
<tr>
<td><code>socket-reuse-address</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Socket reuse address</td>
</tr>
<tr>
<td><code>socket-send-buffer-size</code></td>
<td><code>Integer</code></td>
<td></td>
<td>Socket send buffer size</td>
</tr>
<tr>
<td><code>read-timeout</code></td>
<td><code>Duration</code></td>
<td><code>PT30S</code></td>
<td>Socket read timeout</td>
</tr>
<tr>
<td><code>socket-receive-buffer-size</code></td>
<td><code>Integer</code></td>
<td></td>
<td>Socket receive buffer size</td>
</tr>
<tr>
<td><code>socket-keep-alive</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Configure socket keep alive</td>
</tr>
<tr>
<td><code>tcp-no-delay</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>This option may improve performance on some systems</td>
</tr>
</tbody>
</table>


## Usages

- [`server.connection-options`](io.helidon.ServerConfig.md#connection-options)
- [`server.sockets.connection-options`](io.helidon.webserver.ListenerConfig.md#connection-options)

---

See the [manifest](manifest.md) for all available types.
