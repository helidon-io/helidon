# io.helidon.webserver.staticcontent.StaticContentFeature

## Description

Configuration of Static content feature

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
<td><a id="path"></a><a href="io.helidon.webserver.staticcontent.FileSystemHandlerConfig.md"><code>path</code></a></td>
<td><code>List&lt;FileSystemHandlerConfig&gt;</code></td>
<td></td>
<td>List of file system based static content handlers</td>
</tr>
<tr>
<td><a id="temporary-storage"></a><a href="io.helidon.webserver.staticcontent.TemporaryStorage.md"><code>temporary-storage</code></a></td>
<td><code>TemporaryStorage</code></td>
<td></td>
<td>Temporary storage to use across all classpath handlers</td>
</tr>
<tr>
<td><code>content-types</code></td>
<td><code>Map&lt;String, StaticContentMethods&gt;</code></td>
<td></td>
<td>Maps a filename extension to the response content type</td>
</tr>
<tr>
<td><a id="classpath"></a><a href="io.helidon.webserver.staticcontent.ClasspathHandlerConfig.md"><code>classpath</code></a></td>
<td><code>List&lt;ClasspathHandlerConfig&gt;</code></td>
<td></td>
<td>List of classpath based static content handlers</td>
</tr>
<tr>
<td><a id="memory-cache"></a><a href="io.helidon.webserver.staticcontent.MemoryCache.md"><code>memory-cache</code></a></td>
<td><code>MemoryCache</code></td>
<td></td>
<td>Memory cache shared by the whole feature</td>
</tr>
<tr>
<td><code>weight</code></td>
<td><code>Double</code></td>
<td><code>95.0</code></td>
<td>Weight of the static content feature</td>
</tr>
<tr>
<td><code>sockets</code></td>
<td><code>List&lt;String&gt;</code></td>
<td></td>
<td>Sockets names (listeners) that will host static content handlers, defaults to all configured sockets</td>
</tr>
<tr>
<td><code>welcome</code></td>
<td><code>String</code></td>
<td></td>
<td>Welcome-file name</td>
</tr>
<tr>
<td><code>enabled</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether this feature is enabled, defaults to &lt;code&gt;true&lt;/code&gt;</td>
</tr>
</tbody>
</table>


## Usages

- [`server.features.static-content`](io.helidon.webserver.spi.ServerFeature.md#static-content)

---

See the [manifest](manifest.md) for all available types.
