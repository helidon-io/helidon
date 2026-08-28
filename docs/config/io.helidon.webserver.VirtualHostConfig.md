# io.<wbr>helidon.<wbr>webserver.<wbr>Virtual<wbr>Host<wbr>Config

## Description

TLS material for one listener virtual host selected by SNI

## Configuration options


<table>
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>host</code>
</td>
<td>
<code>String</code>
</td>
<td>Exact DNS host name or narrow wildcard pattern such as <code>*.<wbr>example.<wbr>com</code></td>
</tr>
<tr>
<td>
<a id="tls"></a>
<a href="io.helidon.common.tls.Tls.md">
<code>tls</code>
</a>
</td>
<td>
<code>Tls</code>
</td>
<td>Required enabled TLS configuration for this virtual host; virtual hosts do not inherit the listener TLS configuration</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.webserver.ListenerConfig.md#virtual-hosts"><code>server.<wbr>sockets.<wbr>virtual-<wbr>hosts</code></a>
- <a href="io.helidon.webserver.WebServer.md#virtual-hosts"><code>server.<wbr>virtual-<wbr>hosts</code></a>

---

See the [manifest](manifest.md) for all available types.
