# io.<wbr>helidon.<wbr>http.<wbr>media.<wbr>json.<wbr>smile.<wbr>Smile<wbr>Support

## Description

Configuration of Smile media support

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
<code>json-<wbr>binding</code>
</td>
<td>
<code>Json<wbr>Binding<wbr>Config</code>
</td>
<td>JSON binding instance to use for serialization and deserialization</td>
</tr>
<tr>
<td>
<a id="smile-config"></a>
<a href="io.helidon.json.smile.SmileConfig.md">
<code>smile-<wbr>config</code>
</a>
</td>
<td>
<code>Smile<wbr>Config</code>
</td>
<td>Smile codec configuration whose replay limit, when set, takes precedence over the JSON binding limit</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.http.media.MediaSupport.md#smile"><code>clients.<wbr>media-<wbr>context.<wbr>media-<wbr>supports.<wbr>smile</code></a>
- <a href="io.helidon.http.media.MediaSupport.md#smile"><code>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient.<wbr>media-<wbr>context.<wbr>media-<wbr>supports.<wbr>smile</code></a>
- <a href="io.helidon.http.media.MediaSupport.md#smile"><code>security.<wbr>providers.<wbr>oidc.<wbr>webclient.<wbr>media-<wbr>context.<wbr>media-<wbr>supports.<wbr>smile</code></a>
- <a href="io.helidon.http.media.MediaSupport.md#smile"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient.<wbr>media-<wbr>context.<wbr>media-<wbr>supports.<wbr>smile</code></a>
- <a href="io.helidon.http.media.MediaSupport.md#smile"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>oidc.<wbr>webclient.<wbr>media-<wbr>context.<wbr>media-<wbr>supports.<wbr>smile</code></a>
- <a href="io.helidon.http.media.MediaSupport.md#smile"><code>server.<wbr>media-<wbr>context.<wbr>media-<wbr>supports.<wbr>smile</code></a>
- <a href="io.helidon.http.media.MediaSupport.md#smile"><code>server.<wbr>sockets.<wbr>media-<wbr>context.<wbr>media-<wbr>supports.<wbr>smile</code></a>

---

See the [manifest](manifest.md) for all available types.
