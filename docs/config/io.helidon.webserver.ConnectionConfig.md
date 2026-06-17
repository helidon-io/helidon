# io.<wbr>helidon.<wbr>webserver.<wbr>Connection<wbr>Config

## Description

Configuration of a server connection (for each connection created by clients)

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
<td>Connect timeout</td>
</tr>
<tr>
<td>
<code>keep-<wbr>alive</code>
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
<code>read-<wbr>timeout</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT30S</code>
</td>
<td>Read timeout</td>
</tr>
<tr>
<td>
<code>receive-<wbr>buffer-<wbr>size</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>32768</code>
</td>
<td>Socket receive buffer size</td>
</tr>
<tr>
<td>
<code>reuse-<wbr>address</code>
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
<code>send-<wbr>buffer-<wbr>size</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>32768</code>
</td>
<td>Socket send buffer size</td>
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
<td>Disable <a href="https://en.wikipedia.org/wiki/Nagle%27s_algorithm">Nagle's algorithm</a> by setting TCP_NODELAY to true</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.webserver.WebServer.md#connection-config"><code>server.<wbr>connection-<wbr>config</code></a>
- <a href="io.helidon.webserver.ListenerConfig.md#connection-config"><code>server.<wbr>sockets.<wbr>connection-<wbr>config</code></a>

---

See the [manifest](manifest.md) for all available types.
