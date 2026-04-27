# io.helidon.webserver.staticcontent.MemoryCache

## Description

Configuration of memory cache for static content

## Configuration options

<style>
    table.cm-table code {
        white-space: nowrap !important;
    }

    table.cm-table .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


<table class="cm-table">
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
<code>enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether the cache is enabled, defaults to <code>true</code></td>
</tr>
<tr>
<td>
<code>capacity</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Size</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">50 mB</code>
</td>
<td>Capacity of the cached bytes of file content</td>
</tr>
</tbody>
</table>



## Usages

- [`server.features.static-content.classpath.memory-cache`](io.helidon.webserver.staticcontent.ClasspathHandlerConfig.md#memory-cache)
- [`server.features.static-content.memory-cache`](io.helidon.webserver.staticcontent.StaticContentFeature.md#memory-cache)
- [`server.features.static-content.path.memory-cache`](io.helidon.webserver.staticcontent.FileSystemHandlerConfig.md#memory-cache)

---

See the [manifest](manifest.md) for all available types.
