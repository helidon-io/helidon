# io.helidon.webserver.staticcontent.StaticContentFeature

## Description

Configuration of Static content feature

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
<a id="path"></a>
<a href="io.helidon.webserver.staticcontent.FileSystemHandlerConfig.md">
<code>path</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;FileSystemHandlerConfig&gt;">List&lt;FileSystemHandlerConfig&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>List of file system based static content handlers</td>
</tr>
<tr>
<td>
<a id="temporary-storage"></a>
<a href="io.helidon.webserver.staticcontent.TemporaryStorage.md">
<code>temporary-storage</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="TemporaryStorage">TemporaryStorage</code>
</td>
<td class="cm-default-cell">
</td>
<td>Temporary storage to use across all classpath handlers</td>
</tr>
<tr>
<td>
<code>content-types</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="Map&lt;String, StaticContentMethods&gt;">Map&lt;String, StaticContentMethods&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Maps a filename extension to the response content type</td>
</tr>
<tr>
<td>
<a id="classpath"></a>
<a href="io.helidon.webserver.staticcontent.ClasspathHandlerConfig.md">
<code>classpath</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;ClasspathHandlerConfig&gt;">List&lt;ClasspathHandlerConfig&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>List of classpath based static content handlers</td>
</tr>
<tr>
<td>
<a id="memory-cache"></a>
<a href="io.helidon.webserver.staticcontent.MemoryCache.md">
<code>memory-cache</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="MemoryCache">MemoryCache</code>
</td>
<td class="cm-default-cell">
</td>
<td>Memory cache shared by the whole feature</td>
</tr>
<tr>
<td>
<code>weight</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Double</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">95.0</code>
</td>
<td>Weight of the static content feature</td>
</tr>
<tr>
<td>
<code>sockets</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Sockets names (listeners) that will host static content handlers, defaults to all configured sockets</td>
</tr>
<tr>
<td>
<code>welcome</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Welcome-file name</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether this feature is enabled, defaults to <code>true</code></td>
</tr>
</tbody>
</table>



## Usages

- [`server.features.static-content`](io.helidon.webserver.spi.ServerFeature.md#static-content)

---

See the [manifest](manifest.md) for all available types.
