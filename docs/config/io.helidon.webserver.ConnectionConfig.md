# io.helidon.webserver.ConnectionConfig

## Description

Configuration of a server connection (for each connection created by clients)

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
<td>Connect timeout</td>
</tr>
<tr>
<td><code>keep-alive</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Configure socket keep alive</td>
</tr>
<tr>
<td><code>read-timeout</code></td>
<td><code>Duration</code></td>
<td><code>PT30S</code></td>
<td>Read timeout</td>
</tr>
<tr>
<td><code>receive-buffer-size</code></td>
<td><code>Integer</code></td>
<td><code>32768</code></td>
<td>Socket receive buffer size</td>
</tr>
<tr>
<td><code>reuse-address</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Socket reuse address</td>
</tr>
<tr>
<td><code>send-buffer-size</code></td>
<td><code>Integer</code></td>
<td><code>32768</code></td>
<td>Socket send buffer size</td>
</tr>
<tr>
<td><code>tcp-no-delay</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Disable &lt;a href&#x3D;&quot;https://en.wikipedia.org/wiki/Nagle%27s_algorithm&quot;&gt;Nagle&#x27;s algorithm&lt;/a&gt; by setting TCP_NODELAY to true</td>
</tr>
</tbody>
</table>


## Usages

- [`server.connection-config`](io.helidon.ServerConfig.md#connection-config)
- [`server.sockets.connection-config`](io.helidon.webserver.ListenerConfig.md#connection-config)

---

See the [manifest](manifest.md) for all available types.
