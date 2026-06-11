# io.helidon.http.RequestedUriDiscoveryContext

## Description

Requested URI discovery settings for a socket

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
<a id="types"></a>
<a href="io.helidon.http.RequestedUriDiscoveryContext.RequestedUriDiscoveryType.md">
<code>types</code>
</a>
</td>
<td>
<code>List&lt;<wbr>Requested<wbr>UriDiscovery<wbr>Type&gt;</code>
</td>
<td>
</td>
<td>Sets the discovery types for requested URI discovery for requests arriving on the socket</td>
</tr>
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
<td>Sets the trusted proxies for requested URI discovery for requests arriving on the socket</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true if &#39;types&#39; or &#39;trusted-<wbr>proxies&#39; is set; false otherwise</code>
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
