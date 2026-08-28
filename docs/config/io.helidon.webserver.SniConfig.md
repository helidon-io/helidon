# io.<wbr>helidon.<wbr>webserver.<wbr>SniConfig

## Description

Listener-scoped server TLS SNI policy configuration

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
<a id="fallback-authority"></a>
<a href="io.helidon.webserver.SniAuthorityPolicy.md">
<code>fallback-<wbr>authority</code>
</a>
</td>
<td>
<code>Sni<wbr>Authority<wbr>Policy</code>
</td>
<td>
<code>REJECT</code>
</td>
<td>Policy for HTTP requests that use default listener TLS but claim a configured virtual-host authority</td>
</tr>
<tr>
<td>
<a id="authority-mismatch"></a>
<a href="io.helidon.webserver.SniAuthorityPolicy.md">
<code>authority-<wbr>mismatch</code>
</a>
</td>
<td>
<code>Sni<wbr>Authority<wbr>Policy</code>
</td>
<td>
<code>REJECT</code>
</td>
<td>Policy for HTTP requests whose authority differs from the client-presented SNI host</td>
</tr>
<tr>
<td>
<a id="missing"></a>
<a href="io.helidon.webserver.SniSelectionPolicy.md">
<code>missing</code>
</a>
</td>
<td>
<code>Sni<wbr>Selection<wbr>Policy</code>
</td>
<td>
<code>FALLBACK</code>
</td>
<td>Policy for TLS connections without an SNI host; <code>Sni<wbr>Selection<wbr>Policy#<wbr>REJECT</code> requires at least one listener virtual host</td>
</tr>
<tr>
<td>
<a id="unmatched"></a>
<a href="io.helidon.webserver.SniSelectionPolicy.md">
<code>unmatched</code>
</a>
</td>
<td>
<code>Sni<wbr>Selection<wbr>Policy</code>
</td>
<td>
<code>FALLBACK</code>
</td>
<td>Policy for TLS connections with an SNI host that does not match any configured virtual host; <code>Sni<wbr>Selection<wbr>Policy#<wbr>REJECT</code> requires at least one listener virtual host</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.webserver.WebServer.md#sni"><code>server.<wbr>sni</code></a>
- <a href="io.helidon.webserver.ListenerConfig.md#sni"><code>server.<wbr>sockets.<wbr>sni</code></a>

---

See the [manifest](manifest.md) for all available types.
