# io.<wbr>helidon.<wbr>http.<wbr>media.<wbr>jsonb.<wbr>Jsonb<wbr>Support

## Description

Configuration of the <code>Jsonb<wbr>Support</code>

## Configuration options


<table>
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>boolean-<wbr>properties</code>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> Boolean&gt;</code>
</td>
<td>Jsonb <code>boolean</code> configuration properties</td>
</tr>
<tr>
<td>
<code>class-<wbr>properties</code>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> Class&gt;</code>
</td>
<td>Jsonb <code>Class</code> configuration properties</td>
</tr>
<tr>
<td>
<code>accepted-<wbr>media-<wbr>types</code>
</td>
<td>
<code>List&lt;<wbr>Custom<wbr>Methods&gt;</code>
</td>
<td>Types accepted by this media support</td>
</tr>
<tr>
<td>
<code>name</code>
</td>
<td>
<code>String</code>
</td>
<td>Name of the support</td>
</tr>
<tr>
<td>
<code>content-<wbr>type</code>
</td>
<td>
<code>Custom<wbr>Methods</code>
</td>
<td>Content type to use if not configured (in response headers for server, and in request headers for client)</td>
</tr>
<tr>
<td>
<code>properties</code>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> String&gt;</code>
</td>
<td>Jsonb <code>String</code> configuration properties</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.http.media.MediaSupport.md#jsonb"><code>clients.<wbr>media-<wbr>context.<wbr>media-<wbr>supports.<wbr>jsonb</code></a>
- <a href="io.helidon.http.media.MediaSupport.md#jsonb"><code>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient.<wbr>media-<wbr>context.<wbr>media-<wbr>supports.<wbr>jsonb</code></a>
- <a href="io.helidon.http.media.MediaSupport.md#jsonb"><code>security.<wbr>providers.<wbr>oidc.<wbr>webclient.<wbr>media-<wbr>context.<wbr>media-<wbr>supports.<wbr>jsonb</code></a>
- <a href="io.helidon.http.media.MediaSupport.md#jsonb"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient.<wbr>media-<wbr>context.<wbr>media-<wbr>supports.<wbr>jsonb</code></a>
- <a href="io.helidon.http.media.MediaSupport.md#jsonb"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>oidc.<wbr>webclient.<wbr>media-<wbr>context.<wbr>media-<wbr>supports.<wbr>jsonb</code></a>
- <a href="io.helidon.http.media.MediaSupport.md#jsonb"><code>server.<wbr>media-<wbr>context.<wbr>media-<wbr>supports.<wbr>jsonb</code></a>
- <a href="io.helidon.http.media.MediaSupport.md#jsonb"><code>server.<wbr>sockets.<wbr>media-<wbr>context.<wbr>media-<wbr>supports.<wbr>jsonb</code></a>

---

See the [manifest](manifest.md) for all available types.
