# io.helidon.http.media.MediaContext

## Description

Media context to obtain readers and writers of various supported content types

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
<td><code>media-supports-discover-services</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether to enable automatic service discovery for &lt;code&gt;media-supports&lt;/code&gt;</td>
</tr>
<tr>
<td><code>register-defaults</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Should we register defaults of Helidon, such as String media support</td>
</tr>
<tr>
<td><a id="media-supports"></a><a href="io.helidon.http.media.MediaSupport.md"><code>media-supports</code></a></td>
<td><code>List&lt;MediaSupport&gt;</code></td>
<td></td>
<td>Media supports to use</td>
</tr>
<tr>
<td><a id="fallback"></a><a href="io.helidon.http.media.MediaContext.md"><code>fallback</code></a></td>
<td><code>MediaContext</code></td>
<td></td>
<td>Existing context to be used as a fallback for this context</td>
</tr>
</tbody>
</table>


## Usages

- [`server.media-context`](io.helidon.ServerConfig.md#media-context)
- [`server.media-context.fallback`](io.helidon.http.media.MediaContext.md#fallback)
- [`server.sockets.media-context`](io.helidon.webserver.ListenerConfig.md#media-context)
- [`server.sockets.media-context.fallback`](io.helidon.http.media.MediaContext.md#fallback)

---

See the [manifest](manifest.md) for all available types.
