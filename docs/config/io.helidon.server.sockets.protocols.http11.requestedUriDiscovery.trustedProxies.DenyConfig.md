# io.helidon.server.sockets.protocols.http11.requestedUriDiscovery.trustedProxies.DenyConfig

## Description

Configuration for server.sockets.protocols.http_1_1.requested-uri-discovery.trusted-proxies.deny

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }

    .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


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
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td>Exact strings to deny</td>
</tr>
<tr>
<td>
<code>pattern</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;Pattern&gt;">List&lt;Pattern&gt;</code>
</td>
<td>Patterns specifying strings to deny</td>
</tr>
<tr>
<td>
<code>prefix</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td>Prefixes specifying strings to deny</td>
</tr>
<tr>
<td>
<code>suffix</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td>Suffixes specifying strings to deny</td>
</tr>
</tbody>
</table>



## Usages

- [`server.sockets.protocols.http_1_1.requested-uri-discovery.trusted-proxies.deny`](io.helidon.common.configurable.AllowList.md#deny)

---

See the [manifest](manifest.md) for all available types.
