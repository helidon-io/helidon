# io.<wbr>helidon.<wbr>common.<wbr>socket.<wbr>Socket<wbr>Options

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

- <a href="io.helidon.webclient.api.WebClient.md#socket-options"><code>clients.<wbr>socket-<wbr>options</code></a>
- <a href="io.helidon.webclient.api.WebClient.md#socket-options"><code>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient.<wbr>socket-<wbr>options</code></a>
- <a href="io.helidon.webclient.api.WebClient.md#socket-options"><code>security.<wbr>providers.<wbr>oidc.<wbr>webclient.<wbr>socket-<wbr>options</code></a>
- <a href="io.helidon.webserver.WebServer.md#connection-options"><code>server.<wbr>connection-<wbr>options</code></a>
- <a href="io.helidon.webclient.api.WebClient.md#socket-options"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient.<wbr>socket-<wbr>options</code></a>
- <a href="io.helidon.webclient.api.WebClient.md#socket-options"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>oidc.<wbr>webclient.<wbr>socket-<wbr>options</code></a>
- <a href="io.helidon.webserver.ListenerConfig.md#connection-options"><code>server.<wbr>sockets.<wbr>connection-<wbr>options</code></a>

---

See the [manifest](manifest.md) for all available types.
