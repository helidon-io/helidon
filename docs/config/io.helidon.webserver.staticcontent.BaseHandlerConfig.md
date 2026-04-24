# io.helidon.webserver.staticcontent.BaseHandlerConfig

## Description

Configuration of static content handlers that is common for classpath and file system based handlers

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
<code>cached-files</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>A set of files that are cached in memory at startup</td>
</tr>
<tr>
<td>
<code>content-types</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="Map&lt;String, BaseMethods&gt;">Map&lt;String, BaseMethods&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Maps a filename extension to the response content type</td>
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
<td>Handles will use memory cache configured on <code>StaticContentConfig#memoryCache()</code> by default</td>
</tr>
<tr>
<td>
<code>context</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">/</code>
</td>
<td>Context that will serve this handler's static resources, defaults to <code>/</code></td>
</tr>
<tr>
<td>
<code>record-cache-capacity</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
</td>
<td>Configure capacity of cache used for resources</td>
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
<td>Sockets names (listeners) that will host this static content handler, defaults to all configured sockets</td>
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
<td>Whether this handle is enabled, defaults to <code>true</code></td>
</tr>
</tbody>
</table>



## Dependent Types

- [io.helidon.webserver.staticcontent.ClasspathHandlerConfig](io.helidon.webserver.staticcontent.ClasspathHandlerConfig.md)
- [io.helidon.webserver.staticcontent.FileSystemHandlerConfig](io.helidon.webserver.staticcontent.FileSystemHandlerConfig.md)

---

See the [manifest](manifest.md) for all available types.
