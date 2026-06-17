# io.<wbr>helidon.<wbr>http.<wbr>media.<wbr>Media<wbr>Context

## Description

Media context to obtain readers and writers of various supported content types

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
<code>media-<wbr>supports-<wbr>discover-<wbr>services</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to enable automatic service discovery for <code>media-<wbr>supports</code></td>
</tr>
<tr>
<td>
<code>register-<wbr>defaults</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Should we register defaults of Helidon, such as String media support</td>
</tr>
<tr>
<td>
<a id="media-supports"></a>
<a href="io.helidon.http.media.MediaSupport.md">
<code>media-<wbr>supports</code>
</a>
</td>
<td>
<code>List&lt;<wbr>Media<wbr>Support&gt;</code>
</td>
<td>
</td>
<td>Media supports to use</td>
</tr>
<tr>
<td>
<a id="fallback"></a>
<a href="io.helidon.http.media.MediaContext.md">
<code>fallback</code>
</a>
</td>
<td>
<code>Media<wbr>Context</code>
</td>
<td>
</td>
<td>Existing context to be used as a fallback for this context</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.webclient.api.WebClient.md#media-context"><code>clients.<wbr>media-<wbr>context</code></a>
- <a href="io.helidon.http.media.MediaContext.md#fallback"><code>clients.<wbr>media-<wbr>context.<wbr>fallback</code></a>
- <a href="io.helidon.webclient.api.WebClient.md#media-context"><code>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient.<wbr>media-<wbr>context</code></a>
- <a href="io.helidon.http.media.MediaContext.md#fallback"><code>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient.<wbr>media-<wbr>context.<wbr>fallback</code></a>
- <a href="io.helidon.webclient.api.WebClient.md#media-context"><code>security.<wbr>providers.<wbr>oidc.<wbr>webclient.<wbr>media-<wbr>context</code></a>
- <a href="io.helidon.http.media.MediaContext.md#fallback"><code>security.<wbr>providers.<wbr>oidc.<wbr>webclient.<wbr>media-<wbr>context.<wbr>fallback</code></a>
- <a href="io.helidon.webclient.api.WebClient.md#media-context"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient.<wbr>media-<wbr>context</code></a>
- <a href="io.helidon.http.media.MediaContext.md#fallback"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient.<wbr>media-<wbr>context.<wbr>fallback</code></a>
- <a href="io.helidon.webclient.api.WebClient.md#media-context"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>oidc.<wbr>webclient.<wbr>media-<wbr>context</code></a>
- <a href="io.helidon.http.media.MediaContext.md#fallback"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>oidc.<wbr>webclient.<wbr>media-<wbr>context.<wbr>fallback</code></a>
- <a href="io.helidon.webserver.WebServer.md#media-context"><code>server.<wbr>media-<wbr>context</code></a>
- <a href="io.helidon.http.media.MediaContext.md#fallback"><code>server.<wbr>media-<wbr>context.<wbr>fallback</code></a>
- <a href="io.helidon.webserver.ListenerConfig.md#media-context"><code>server.<wbr>sockets.<wbr>media-<wbr>context</code></a>
- <a href="io.helidon.http.media.MediaContext.md#fallback"><code>server.<wbr>sockets.<wbr>media-<wbr>context.<wbr>fallback</code></a>

---

See the [manifest](manifest.md) for all available types.
