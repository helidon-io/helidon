# io.helidon.http.encoding.ContentEncodingContext

## Description

Content encoding support to obtain encoders and decoders

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
<th>Default</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<a id="content-encodings"></a>
<a href="io.helidon.http.encoding.ContentEncoding.md">
<code>content-encodings</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;ContentEncoding&gt;">List&lt;ContentEncoding&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>List of content encodings that should be used</td>
</tr>
<tr>
<td>
<code>content-encodings-discover-services</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to enable automatic service discovery for <code>content-encodings</code></td>
</tr>
</tbody>
</table>



## Usages

- [`server.content-encoding`](io.helidon.webserver.WebServer.md#content-encoding)
- [`server.sockets.content-encoding`](io.helidon.webserver.ListenerConfig.md#content-encoding)

---

See the [manifest](manifest.md) for all available types.
