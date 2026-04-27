# io.helidon.http.RequestedUriDiscoveryContext

## Description

Requested URI discovery settings for a socket

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
<th>Type</th>
<th>Default</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<a id="types"></a>
<a href="io.helidon.http.RequestedUriDiscoveryContext.RequestedUriDiscoveryType.md">
<code>types</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;RequestedUriDiscoveryType&gt;">List&lt;RequestedUriDiscoveryType&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Sets the discovery types for requested URI discovery for requests arriving on the socket</td>
</tr>
<tr>
<td>
<a id="trusted-proxies"></a>
<a href="io.helidon.common.configurable.AllowList.md">
<code>trusted-proxies</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">AllowList</code>
</td>
<td class="cm-default-cell">
</td>
<td>Sets the trusted proxies for requested URI discovery for requests arriving on the socket</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value" title="true if &#x27;types&#x27; or &#x27;trusted-proxies&#x27; is set; false otherwise">true if &#x27;types&#x27; or &#x27;trusted-proxies&#x27; is set; false otherwise</code>
</td>
<td>Sets whether requested URI discovery is enabled for requestes arriving on the socket</td>
</tr>
</tbody>
</table>



## Usages

- [`server.protocols.http_1_1.requested-uri-discovery`](io.helidon.webserver.http1.Http1Config.md#requested-uri-discovery)
- [`server.protocols.http_2.requested-uri-discovery`](io.helidon.webserver.http2.Http2Config.md#requested-uri-discovery)
- [`server.requested-uri-discovery`](io.helidon.webserver.WebServer.md#requested-uri-discovery)
- [`server.sockets.protocols.http_1_1.requested-uri-discovery`](io.helidon.webserver.http1.Http1Config.md#requested-uri-discovery)
- [`server.sockets.protocols.http_2.requested-uri-discovery`](io.helidon.webserver.http2.Http2Config.md#requested-uri-discovery)
- [`server.sockets.requested-uri-discovery`](io.helidon.webserver.ListenerConfig.md#requested-uri-discovery)

---

See the [manifest](manifest.md) for all available types.
