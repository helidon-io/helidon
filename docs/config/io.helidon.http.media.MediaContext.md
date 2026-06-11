# io.helidon.http.media.MediaContext

## Description

Media context to obtain readers and writers of various supported content types

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
<code>media-<wbr>supports-<wbr>discover-<wbr>services</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to enable automatic service discovery for <code>media-<wbr>supports</code></td>
</tr>
<tr>
<td>
<code>register-<wbr>defaults</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Should we register defaults of Helidon, such as String media support</td>
</tr>
<tr>
<td>
<a id="media-supports"></a>
<a href="io.helidon.http.media.MediaSupport.md">
<code>media-<wbr>supports</code>
</a>
</td>
<td>
<code>List&lt;<wbr>Media<wbr>Support&gt;</code>
</td>
<td>
</td>
<td>Media supports to use</td>
</tr>
<tr>
<td>
<a id="fallback"></a>
<a href="io.helidon.http.media.MediaContext.md">
<code>fallback</code>
</a>
</td>
<td>
<code>Media<wbr>Context</code>
</td>
<td>
</td>
<td>Existing context to be used as a fallback for this context</td>
</tr>
</tbody>
</table>



## Usages

- [`server.media-context`](io.helidon.webserver.WebServer.md#media-context)
- [`server.media-context.fallback`](io.helidon.http.media.MediaContext.md#fallback)
- [`server.sockets.media-context`](io.helidon.webserver.ListenerConfig.md#media-context)
- [`server.sockets.media-context.fallback`](io.helidon.http.media.MediaContext.md#fallback)

---

See the [manifest](manifest.md) for all available types.
