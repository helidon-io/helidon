# io.helidon.webserver.staticcontent.ClasspathHandlerConfig

## Description

Classpath based static content handler configuration

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
<td><code>cached-files</code></td>
<td><code>List&lt;String&gt;</code></td>
<td></td>
<td>A set of files that are cached in memory at startup</td>
</tr>
<tr>
<td><code>single-file</code></td>
<td><code>Boolean</code></td>
<td></td>
<td>Classpath content usually starts from a &lt;code&gt;ClasspathHandlerConfig#location()&lt;/code&gt; on classpath, and resolves all requested paths against this content root</td>
</tr>
<tr>
<td><a id="temporary-storage"></a><a href="io.helidon.webserver.staticcontent.TemporaryStorage.md"><code>temporary-storage</code></a></td>
<td><code>TemporaryStorage</code></td>
<td></td>
<td>Customization of temporary storage configuration</td>
</tr>
<tr>
<td><code>content-types</code></td>
<td><code>Map&lt;String, BaseMethods&gt;</code></td>
<td></td>
<td>Maps a filename extension to the response content type</td>
</tr>
<tr>
<td><a id="memory-cache"></a><a href="io.helidon.webserver.staticcontent.MemoryCache.md"><code>memory-cache</code></a></td>
<td><code>MemoryCache</code></td>
<td></td>
<td>Handles will use memory cache configured on &lt;code&gt;StaticContentConfig#memoryCache()&lt;/code&gt; by default</td>
</tr>
<tr>
<td><code>context</code></td>
<td><code>String</code></td>
<td><code>/</code></td>
<td>Context that will serve this handler&#x27;s static resources, defaults to &lt;code&gt;/&lt;/code&gt;</td>
</tr>
<tr>
<td><code>record-cache-capacity</code></td>
<td><code>Integer</code></td>
<td></td>
<td>Configure capacity of cache used for resources</td>
</tr>
<tr>
<td><code>location</code></td>
<td><code>String</code></td>
<td></td>
<td>The location on classpath that contains the root of the static content</td>
</tr>
<tr>
<td><code>sockets</code></td>
<td><code>List&lt;String&gt;</code></td>
<td></td>
<td>Sockets names (listeners) that will host this static content handler, defaults to all configured sockets</td>
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
<td>Whether this handle is enabled, defaults to &lt;code&gt;true&lt;/code&gt;</td>
</tr>
</tbody>
</table>


## Usages

- [`server.features.static-content.classpath`](io.helidon.webserver.staticcontent.StaticContentFeature.md#classpath)

---

See the [manifest](manifest.md) for all available types.
