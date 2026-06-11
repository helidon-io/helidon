# io.helidon.webserver.staticcontent.FileSystemHandlerConfig

## Description

File system based static content handler configuration

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
<code>cached-<wbr>files</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
</td>
<td>A set of files that are cached in memory at startup</td>
</tr>
<tr>
<td>
<code>content-<wbr>types</code>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> Base<wbr>Methods&gt;</code>
</td>
<td>
</td>
<td>Maps a filename extension to the response content type</td>
</tr>
<tr>
<td>
<a id="memory-cache"></a>
<a href="io.helidon.webserver.staticcontent.MemoryCache.md">
<code>memory-<wbr>cache</code>
</a>
</td>
<td>
<code>Memory<wbr>Cache</code>
</td>
<td>
</td>
<td>Handles will use memory cache configured on <code>Static<wbr>Content<wbr>Config#<wbr>memory<wbr>Cache(<wbr>)</code> by default</td>
</tr>
<tr>
<td>
<code>context</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>/</code>
</td>
<td>Context that will serve this handler's static resources, defaults to <code>/</code></td>
</tr>
<tr>
<td>
<code>record-<wbr>cache-<wbr>capacity</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
</td>
<td>Configure capacity of cache used for resources</td>
</tr>
<tr>
<td>
<code>location</code>
</td>
<td>
<code>Path</code>
</td>
<td>
</td>
<td>The directory (or a single file) that contains the root of the static content</td>
</tr>
<tr>
<td>
<code>sockets</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
</td>
<td>Sockets names (listeners) that will host this static content handler, defaults to all configured sockets</td>
</tr>
<tr>
<td>
<code>welcome</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Welcome-file name</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether this handle is enabled, defaults to <code>true</code></td>
</tr>
</tbody>
</table>



## Usages

- [`server.features.static-content.path`](io.helidon.webserver.staticcontent.StaticContentFeature.md#path)

---

See the [manifest](manifest.md) for all available types.
