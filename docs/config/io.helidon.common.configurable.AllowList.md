# io.<wbr>helidon.<wbr>common.<wbr>configurable.<wbr>Allow<wbr>List

## Description

<code>Allow<wbr>List</code> defines a list of allowed and/or denied matches and tests if a particular value conforms to the conditions

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
<code>allow.<wbr>all</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Allows all strings to match (subject to "deny" conditions)</td>
</tr>
<tr>
<td>
<code>allow.<wbr>exact</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
</td>
<td>Exact strings to allow</td>
</tr>
<tr>
<td>
<code>allow.<wbr>pattern</code>
</td>
<td>
<code>List&lt;<wbr>Pattern&gt;</code>
</td>
<td>
</td>
<td><code>Pattern</code>s specifying strings to allow</td>
</tr>
<tr>
<td>
<code>allow.<wbr>prefix</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
</td>
<td>Prefixes specifying strings to allow</td>
</tr>
<tr>
<td>
<code>allow.<wbr>suffix</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
</td>
<td>Suffixes specifying strings to allow</td>
</tr>
<tr>
<td>
<code>deny.<wbr>exact</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
</td>
<td>Exact strings to deny</td>
</tr>
<tr>
<td>
<code>deny.<wbr>pattern</code>
</td>
<td>
<code>List&lt;<wbr>Pattern&gt;</code>
</td>
<td>
</td>
<td>Patterns specifying strings to deny</td>
</tr>
<tr>
<td>
<code>deny.<wbr>prefix</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
</td>
<td>Prefixes specifying strings to deny</td>
</tr>
<tr>
<td>
<code>deny.<wbr>suffix</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
</td>
<td>Suffixes specifying strings to deny</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.security.providers.oidc.common.OidcConfig.md#frontend-uri-allow-list"><code>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>frontend-<wbr>uri-<wbr>allow-<wbr>list</code></a>
- <a href="io.helidon.security.providers.oidc.OidcProvider.md#frontend-uri-allow-list"><code>security.<wbr>providers.<wbr>oidc.<wbr>frontend-<wbr>uri-<wbr>allow-<wbr>list</code></a>
- <a href="io.helidon.security.providers.oidc.common.OidcConfig.md#frontend-uri-allow-list"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>frontend-<wbr>uri-<wbr>allow-<wbr>list</code></a>
- <a href="io.helidon.security.providers.oidc.OidcProvider.md#frontend-uri-allow-list"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>oidc.<wbr>frontend-<wbr>uri-<wbr>allow-<wbr>list</code></a>
- <a href="io.helidon.http.RequestedUriDiscoveryContext.md#trusted-proxies"><code>server.<wbr>protocols.<wbr>http_<wbr>1_1.<wbr>requested-<wbr>uri-<wbr>discovery.<wbr>trusted-<wbr>proxies</code></a>
- <a href="io.helidon.http.RequestedUriDiscoveryContext.md#trusted-proxies"><code>server.<wbr>protocols.<wbr>http_<wbr>2.requested-<wbr>uri-<wbr>discovery.<wbr>trusted-<wbr>proxies</code></a>
- <a href="io.helidon.webserver.ProxyProtocolConfig.md#trusted-proxies"><code>server.<wbr>proxy-<wbr>protocol.<wbr>trusted-<wbr>proxies</code></a>
- <a href="io.helidon.http.RequestedUriDiscoveryContext.md#trusted-proxies"><code>server.<wbr>requested-<wbr>uri-<wbr>discovery.<wbr>trusted-<wbr>proxies</code></a>
- <a href="io.helidon.http.RequestedUriDiscoveryContext.md#trusted-proxies"><code>server.<wbr>sockets.<wbr>protocols.<wbr>http_<wbr>1_1.<wbr>requested-<wbr>uri-<wbr>discovery.<wbr>trusted-<wbr>proxies</code></a>
- <a href="io.helidon.http.RequestedUriDiscoveryContext.md#trusted-proxies"><code>server.<wbr>sockets.<wbr>protocols.<wbr>http_<wbr>2.requested-<wbr>uri-<wbr>discovery.<wbr>trusted-<wbr>proxies</code></a>
- <a href="io.helidon.webserver.ProxyProtocolConfig.md#trusted-proxies"><code>server.<wbr>sockets.<wbr>proxy-<wbr>protocol.<wbr>trusted-<wbr>proxies</code></a>
- <a href="io.helidon.http.RequestedUriDiscoveryContext.md#trusted-proxies"><code>server.<wbr>sockets.<wbr>requested-<wbr>uri-<wbr>discovery.<wbr>trusted-<wbr>proxies</code></a>

---

See the [manifest](manifest.md) for all available types.
