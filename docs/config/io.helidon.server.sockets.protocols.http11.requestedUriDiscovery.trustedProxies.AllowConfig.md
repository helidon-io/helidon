# io.helidon.server.sockets.protocols.http11.requestedUriDiscovery.trustedProxies.AllowConfig

## Description

Configuration for server.sockets.protocols.http_1_1.requested-uri-discovery.trusted-proxies.allow

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
<code>all</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Allows all strings to match (subject to "deny" conditions)</td>
</tr>
<tr>
<td>
<code>exact</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
</td>
<td>Exact strings to allow</td>
</tr>
<tr>
<td>
<code>pattern</code>
</td>
<td>
<code>List&lt;<wbr>Pattern&gt;</code>
</td>
<td>
</td>
<td><code>Pattern</code>s specifying strings to allow</td>
</tr>
<tr>
<td>
<code>prefix</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
</td>
<td>Prefixes specifying strings to allow</td>
</tr>
<tr>
<td>
<code>suffix</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
</td>
<td>Suffixes specifying strings to allow</td>
</tr>
</tbody>
</table>



## Usages

- [`server.sockets.protocols.http_1_1.requested-uri-discovery.trusted-proxies.allow`](io.helidon.common.configurable.AllowList.md#allow)

---

See the [manifest](manifest.md) for all available types.
