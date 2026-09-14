# io.<wbr>helidon.<wbr>http.<wbr>Requested<wbr>UriDiscovery<wbr>Context.<wbr>Requested<wbr>UriDiscovery<wbr>Type

## Description

This type is an enumeration.

## Allowed Values

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
<td>The <code>io.<wbr>helidon.<wbr>http.<wbr>Header#<wbr>FORWARDED</code> header is used to discover the original requested URI</td>
</tr>
<tr>
<td><code>X_<wbr>FORWARDED</code></td>
<td>The <code>io.<wbr>helidon.<wbr>http.<wbr>Header#<wbr>X_FORWARDED_<wbr>PROTO</code>, <code>io.<wbr>helidon.<wbr>http.<wbr>Header#<wbr>X_FORWARDED_<wbr>HOST</code>, <code>io.<wbr>helidon.<wbr>http.<wbr>Header#<wbr>X_FORWARDED_<wbr>PORT</code>, <code>io.<wbr>helidon.<wbr>http.<wbr>Header#<wbr>X_FORWARDED_<wbr>PREFIX</code> headers are used to discover the original requested URI</td>
</tr>
<tr>
<td><code>HOST</code></td>
<td>This is the default, only the <code>io.<wbr>helidon.<wbr>http.<wbr>Header#<wbr>HOST</code> header is used to discover requested URI</td>
</tr>
</tbody>
</table>

## Usages

- <a href="io.helidon.http.RequestedUriDiscoveryContext.md#types"><code>server.<wbr>protocols.<wbr>http_<wbr>1_1.<wbr>requested-<wbr>uri-<wbr>discovery.<wbr>types</code></a>
- <a href="io.helidon.http.RequestedUriDiscoveryContext.md#types"><code>server.<wbr>protocols.<wbr>http_<wbr>2.requested-<wbr>uri-<wbr>discovery.<wbr>types</code></a>
- <a href="io.helidon.http.RequestedUriDiscoveryContext.md#types"><code>server.<wbr>requested-<wbr>uri-<wbr>discovery.<wbr>types</code></a>
- <a href="io.helidon.http.RequestedUriDiscoveryContext.md#types"><code>server.<wbr>sockets.<wbr>protocols.<wbr>http_<wbr>1_1.<wbr>requested-<wbr>uri-<wbr>discovery.<wbr>types</code></a>
- <a href="io.helidon.http.RequestedUriDiscoveryContext.md#types"><code>server.<wbr>sockets.<wbr>protocols.<wbr>http_<wbr>2.requested-<wbr>uri-<wbr>discovery.<wbr>types</code></a>
- <a href="io.helidon.http.RequestedUriDiscoveryContext.md#types"><code>server.<wbr>sockets.<wbr>requested-<wbr>uri-<wbr>discovery.<wbr>types</code></a>

---

See the [manifest](manifest.md) for all available types.
