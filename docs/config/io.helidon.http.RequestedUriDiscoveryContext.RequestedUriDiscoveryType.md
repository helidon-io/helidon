# io.helidon.http.RequestedUriDiscoveryContext.RequestedUriDiscoveryType

## Description

This type is an enumeration.

## Allowed Values

<style>
    code {
        white-space: nowrap !important;
    }
</style>

<table>
<thead>
<tr>
<th>Value</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>FORWARDED</code></td>
<td>The &lt;code&gt;io.helidon.http.Header#FORWARDED&lt;/code&gt; header is used to discover the original requested URI</td>
</tr>
<tr>
<td><code>X_FORWARDED</code></td>
<td>The &lt;code&gt;io.helidon.http.Header#X_FORWARDED_PROTO&lt;/code&gt;, &lt;code&gt;io.helidon.http.Header#X_FORWARDED_HOST&lt;/code&gt;, &lt;code&gt;io.helidon.http.Header#X_FORWARDED_PORT&lt;/code&gt;, &lt;code&gt;io.helidon.http.Header#X_FORWARDED_PREFIX&lt;/code&gt; headers are used to discover the original requested URI</td>
</tr>
<tr>
<td><code>HOST</code></td>
<td>This is the default, only the &lt;code&gt;io.helidon.http.Header#HOST&lt;/code&gt; header is used to discover requested URI</td>
</tr>
</tbody>
</table>

## Usages

- [`server.protocols.http_1_1.requested-uri-discovery.types`](io.helidon.http.RequestedUriDiscoveryContext.md#types)
- [`server.protocols.http_2.requested-uri-discovery.types`](io.helidon.http.RequestedUriDiscoveryContext.md#types)
- [`server.requested-uri-discovery.types`](io.helidon.http.RequestedUriDiscoveryContext.md#types)
- [`server.sockets.protocols.http_1_1.requested-uri-discovery.types`](io.helidon.http.RequestedUriDiscoveryContext.md#types)
- [`server.sockets.protocols.http_2.requested-uri-discovery.types`](io.helidon.http.RequestedUriDiscoveryContext.md#types)
- [`server.sockets.requested-uri-discovery.types`](io.helidon.http.RequestedUriDiscoveryContext.md#types)

---

See the [manifest](manifest.md) for all available types.
