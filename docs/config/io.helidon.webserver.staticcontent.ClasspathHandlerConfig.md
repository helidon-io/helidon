# io.<wbr>helidon.<wbr>webserver.<wbr>staticcontent.<wbr>Classpath<wbr>Handler<wbr>Config

## Description

Classpath based static content handler configuration

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
<code>single-<wbr>file</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
</td>
<td>Classpath content usually starts from a <code>Classpath<wbr>Handler<wbr>Config#<wbr>location(<wbr>)</code> on classpath, and resolves all requested paths against this content root</td>
</tr>
<tr>
<td>
<a id="temporary-storage"></a>
<a href="io.helidon.webserver.staticcontent.TemporaryStorage.md">
<code>temporary-<wbr>storage</code>
</a>
</td>
<td>
<code>Temporary<wbr>Storage</code>
</td>
<td>
</td>
<td>Customization of temporary storage configuration</td>
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
<code>pre-<wbr>compressed-<wbr>cross-<wbr>origin-<wbr>sourcing-<wbr>enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
</td>
<td>Whether sidecar resources may be sourced from a different classpath origin than the logical resource; when not configured, feature-registered handlers inherit the feature-level value and direct services default to <code>false</code></td>
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
<code>location</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>The location on classpath that contains the root of the static content</td>
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
</tbody>
</table>



## Usages

- <a href="io.helidon.webserver.staticcontent.StaticContentFeature.md#classpath"><code>server.<wbr>features.<wbr>static-<wbr>content.<wbr>classpath</code></a>

---

See the [manifest](manifest.md) for all available types.
