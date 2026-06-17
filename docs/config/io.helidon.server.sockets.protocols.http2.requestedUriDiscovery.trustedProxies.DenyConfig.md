# io.<wbr>helidon.<wbr>server.<wbr>sockets.<wbr>protocols.<wbr>http2.<wbr>requested<wbr>UriDiscovery.<wbr>trusted<wbr>Proxies.<wbr>Deny<wbr>Config

## Description

Configuration for server.sockets.protocols.http_2.requested-uri-discovery.trusted-proxies.deny

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
<code>exact</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>Exact strings to deny</td>
</tr>
<tr>
<td>
<code>pattern</code>
</td>
<td>
<code>List&lt;<wbr>Pattern&gt;</code>
</td>
<td>Patterns specifying strings to deny</td>
</tr>
<tr>
<td>
<code>prefix</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>Prefixes specifying strings to deny</td>
</tr>
<tr>
<td>
<code>suffix</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>Suffixes specifying strings to deny</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.common.configurable.AllowList.md#deny"><code>server.<wbr>sockets.<wbr>protocols.<wbr>http_<wbr>2.requested-<wbr>uri-<wbr>discovery.<wbr>trusted-<wbr>proxies.<wbr>deny</code></a>

---

See the [manifest](manifest.md) for all available types.
