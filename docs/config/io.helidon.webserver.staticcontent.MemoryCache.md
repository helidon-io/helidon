# io.helidon.webserver.staticcontent.MemoryCache

## Description

Configuration of memory cache for static content

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
<code>enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether the cache is enabled, defaults to <code>true</code></td>
</tr>
<tr>
<td>
<code>capacity</code>
</td>
<td>
<code>Size</code>
</td>
<td>
<code>50 m<wbr>B</code>
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
