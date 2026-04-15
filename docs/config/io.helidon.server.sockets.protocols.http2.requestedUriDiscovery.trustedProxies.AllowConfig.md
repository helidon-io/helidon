# io.helidon.server.sockets.protocols.http2.requestedUriDiscovery.trustedProxies.AllowConfig

## Description

Configuration for server.sockets.protocols.http_2.requested-uri-discovery.trusted-proxies.allow

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
<td><code>all</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>Allows all strings to match (subject to &quot;deny&quot; conditions)</td>
</tr>
<tr>
<td><code>exact</code></td>
<td><code>List&lt;String&gt;</code></td>
<td></td>
<td>Exact strings to allow</td>
</tr>
<tr>
<td><code>pattern</code></td>
<td><code>List&lt;Pattern&gt;</code></td>
<td></td>
<td>&lt;code&gt;Pattern&lt;/code&gt;s specifying strings to allow</td>
</tr>
<tr>
<td><code>prefix</code></td>
<td><code>List&lt;String&gt;</code></td>
<td></td>
<td>Prefixes specifying strings to allow</td>
</tr>
<tr>
<td><code>suffix</code></td>
<td><code>List&lt;String&gt;</code></td>
<td></td>
<td>Suffixes specifying strings to allow</td>
</tr>
</tbody>
</table>


## Usages

- [`server.sockets.protocols.http_2.requested-uri-discovery.trusted-proxies.allow`](io.helidon.common.configurable.AllowList.md#allow)

---

See the [manifest](manifest.md) for all available types.
