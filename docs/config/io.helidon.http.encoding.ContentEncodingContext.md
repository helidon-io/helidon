# io.helidon.http.encoding.ContentEncodingContext

## Description

Content encoding support to obtain encoders and decoders

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
<td><a id="content-encodings"></a><a href="io.helidon.http.encoding.ContentEncoding.md"><code>content-encodings</code></a></td>
<td><code>List&lt;ContentEncoding&gt;</code></td>
<td></td>
<td>List of content encodings that should be used</td>
</tr>
<tr>
<td><code>content-encodings-discover-services</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether to enable automatic service discovery for &lt;code&gt;content-encodings&lt;/code&gt;</td>
</tr>
</tbody>
</table>


## Usages

- [`server.content-encoding`](io.helidon.ServerConfig.md#content-encoding)
- [`server.sockets.content-encoding`](io.helidon.webserver.ListenerConfig.md#content-encoding)

---

See the [manifest](manifest.md) for all available types.
