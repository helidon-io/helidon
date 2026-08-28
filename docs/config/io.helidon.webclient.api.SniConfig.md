# io.<wbr>helidon.<wbr>webclient.<wbr>api.<wbr>SniConfig

## Description

Client-side TLS SNI configuration

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
<a id="mode"></a>
<a href="io.helidon.webclient.api.SniMode.md">
<code>mode</code>
</a>
</td>
<td>
<code>Sni<wbr>Mode</code>
</td>
<td>
<code>URI_<wbr>HOST</code>
</td>
<td>TLS peer host source mode for SNI and endpoint identification: <code>uri-<wbr>host</code>, <code>host-<wbr>header</code>, <code>explicit</code>, or <code>disabled</code></td>
</tr>
<tr>
<td>
<code>host</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Explicit TLS peer host used when <code>mode</code> is <code>explicit</code></td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.webclient.api.WebClient.md#sni"><code>clients.<wbr>sni</code></a>
- <a href="io.helidon.webclient.api.WebClient.md#sni"><code>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient.<wbr>sni</code></a>
- <a href="io.helidon.webclient.api.WebClient.md#sni"><code>security.<wbr>providers.<wbr>oidc.<wbr>webclient.<wbr>sni</code></a>
- <a href="io.helidon.webclient.api.WebClient.md#sni"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient.<wbr>sni</code></a>
- <a href="io.helidon.webclient.api.WebClient.md#sni"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>oidc.<wbr>webclient.<wbr>sni</code></a>

---

See the [manifest](manifest.md) for all available types.
