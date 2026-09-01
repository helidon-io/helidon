# io.<wbr>helidon.<wbr>webserver.<wbr>staticcontent.<wbr>Base<wbr>Handler<wbr>Config

## Description

Configuration of static content handlers that is common for classpath and file system based handlers

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
<code>pre-<wbr>compressed-<wbr>enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
</td>
<td>Whether pre-compressed sidecar resources should be selected for this handler; file system handlers configured with a single file require explicit enablement, other feature-registered handlers inherit the feature value when absent, and all other directly created handlers default to enabled</td>
</tr>
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
<code>pre-<wbr>compressed-<wbr>encodings</code>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> String&gt;</code>
</td>
<td>
</td>
<td>Pre-compressed content coding to file suffix mappings; handler mappings replace inherited feature-level mappings rather than merging with them, an explicit empty map disables sidecar lookups for this handler, codings must be unique concrete valid HTTP tokens other than <code>identity</code> and <code>*</code>, and suffixes have leading dots ignored and must not contain path separators</td>
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
<td>Configures the capacity of the cache used for resource metadata</td>
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



## Dependent Types

- [io.<wbr>helidon.<wbr>webserver.<wbr>staticcontent.<wbr>Classpath<wbr>Handler<wbr>Config](io.helidon.webserver.staticcontent.ClasspathHandlerConfig.md)
- [io.<wbr>helidon.<wbr>webserver.<wbr>staticcontent.<wbr>File<wbr>System<wbr>Handler<wbr>Config](io.helidon.webserver.staticcontent.FileSystemHandlerConfig.md)

---

See the [manifest](manifest.md) for all available types.
