# io.<wbr>helidon.<wbr>webserver.<wbr>Proxy<wbr>Protocol<wbr>Config

## Description

Configuration of PROXY protocol support

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
<a id="trusted-proxies"></a>
<a href="io.helidon.common.configurable.AllowList.md">
<code>trusted-<wbr>proxies</code>
</a>
</td>
<td>
<code>Allow<wbr>List</code>
</td>
<td>
</td>
<td>Trusted proxy allow list; required when PROXY protocol support is enabled</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether PROXY protocol support is enabled</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.webserver.WebServer.md#proxy-protocol"><code>server.<wbr>proxy-<wbr>protocol</code></a>
- <a href="io.helidon.webserver.ListenerConfig.md#proxy-protocol"><code>server.<wbr>sockets.<wbr>proxy-<wbr>protocol</code></a>

---

See the [manifest](manifest.md) for all available types.
