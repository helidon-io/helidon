# io.helidon.common.configurable.AllowList

## Description

<code>AllowList</code> defines a list of allowed and/or denied matches and tests if a particular value conforms to the conditions

## Configuration options

<style>
    table.cm-table code {
        white-space: nowrap !important;
    }

    table.cm-table .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


<table class="cm-table">
<thead>
<tr>
<th>Key</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<a id="allow"></a>
<a href="io.helidon.server.protocols.http11.requestedUriDiscovery.trustedProxies.AllowConfig.md">
<code>allow</code>
</a>
</td>
<td>Configuration for allow</td>
</tr>
<tr>
<td>
<a id="deny"></a>
<a href="io.helidon.server.protocols.http11.requestedUriDiscovery.trustedProxies.DenyConfig.md">
<code>deny</code>
</a>
</td>
<td>Configuration for deny</td>
</tr>
</tbody>
</table>



## Usages

- [`server.protocols.http_1_1.requested-uri-discovery.trusted-proxies`](io.helidon.http.RequestedUriDiscoveryContext.md#trusted-proxies)
- [`server.protocols.http_2.requested-uri-discovery.trusted-proxies`](io.helidon.http.RequestedUriDiscoveryContext.md#trusted-proxies)
- [`server.requested-uri-discovery.trusted-proxies`](io.helidon.http.RequestedUriDiscoveryContext.md#trusted-proxies)
- [`server.sockets.protocols.http_1_1.requested-uri-discovery.trusted-proxies`](io.helidon.http.RequestedUriDiscoveryContext.md#trusted-proxies)
- [`server.sockets.protocols.http_2.requested-uri-discovery.trusted-proxies`](io.helidon.http.RequestedUriDiscoveryContext.md#trusted-proxies)
- [`server.sockets.requested-uri-discovery.trusted-proxies`](io.helidon.http.RequestedUriDiscoveryContext.md#trusted-proxies)

---

See the [manifest](manifest.md) for all available types.
