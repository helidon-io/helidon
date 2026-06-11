# io.helidon.server.requestedUriDiscovery.trustedProxies.DenyConfig

## Description

Configuration for server.requested-uri-discovery.trusted-proxies.deny

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

- [`server.requested-uri-discovery.trusted-proxies.deny`](io.helidon.common.configurable.AllowList.md#deny)

---

See the [manifest](manifest.md) for all available types.
