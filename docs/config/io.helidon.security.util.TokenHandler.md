# io.<wbr>helidon.<wbr>security.<wbr>util.<wbr>Token<wbr>Handler

## Description

Extracts a security token from request or updates headers with the token

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
<code>regexp</code>
</td>
<td>
<code>String</code>
</td>
<td>Set the token pattern (Regular expression) to extract the token</td>
</tr>
<tr>
<td>
<code>prefix</code>
</td>
<td>
<code>String</code>
</td>
<td>Set the prefix of header value to extract the token</td>
</tr>
<tr>
<td>
<code>format</code>
</td>
<td>
<code>String</code>
</td>
<td>Token format for creating outbound tokens</td>
</tr>
<tr>
<td>
<code>header</code>
</td>
<td>
<code>String</code>
</td>
<td>Set the name of header to look into to extract the token</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.security.providers.google.login.GoogleTokenProvider.md#token"><code>security.<wbr>providers.<wbr>google-<wbr>login.<wbr>token</code></a>
- <a href="io.helidon.security.providers.header.HeaderAtnProvider.md#atn-token"><code>security.<wbr>providers.<wbr>header-<wbr>atn.<wbr>atn-<wbr>token</code></a>
- <a href="io.helidon.security.providers.header.HeaderAtnProvider.md#outbound-token"><code>security.<wbr>providers.<wbr>header-<wbr>atn.<wbr>outbound-<wbr>token</code></a>
- <a href="io.helidon.security.providers.IdcsRoleMapperConfig.md#idcs-app-name-handler"><code>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>idcs-<wbr>app-<wbr>name-<wbr>handler</code></a>
- <a href="io.helidon.security.providers.IdcsRoleMapperConfig.md#idcs-tenant-handler"><code>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>idcs-<wbr>tenant-<wbr>handler</code></a>
- <a href="io.helidon.security.providers.oidc.common.OidcConfig.md#header-token"><code>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>header-<wbr>token</code></a>
- <a href="io.helidon.security.providers.jwt.AtnTokenConfig.md#handler"><code>security.<wbr>providers.<wbr>jwt.<wbr>atn-<wbr>token.<wbr>handler</code></a>
- <a href="io.helidon.security.providers.oidc.OidcProvider.md#header-token"><code>security.<wbr>providers.<wbr>oidc.<wbr>header-<wbr>token</code></a>
- <a href="io.helidon.security.providers.google.login.GoogleTokenProvider.md#token"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>google-<wbr>login.<wbr>token</code></a>
- <a href="io.helidon.security.providers.header.HeaderAtnProvider.md#atn-token"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>header-<wbr>atn.<wbr>atn-<wbr>token</code></a>
- <a href="io.helidon.security.providers.header.HeaderAtnProvider.md#outbound-token"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>header-<wbr>atn.<wbr>outbound-<wbr>token</code></a>
- <a href="io.helidon.server.features.security.security.providers.IdcsRoleMapperConfig.md#idcs-app-name-handler"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>idcs-<wbr>app-<wbr>name-<wbr>handler</code></a>
- <a href="io.helidon.server.features.security.security.providers.IdcsRoleMapperConfig.md#idcs-tenant-handler"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>idcs-<wbr>tenant-<wbr>handler</code></a>
- <a href="io.helidon.security.providers.oidc.common.OidcConfig.md#header-token"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>header-<wbr>token</code></a>
- <a href="io.helidon.server.features.security.security.providers.jwt.AtnTokenConfig.md#handler"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>jwt.<wbr>atn-<wbr>token.<wbr>handler</code></a>
- <a href="io.helidon.security.providers.oidc.OidcProvider.md#header-token"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>oidc.<wbr>header-<wbr>token</code></a>

---

See the [manifest](manifest.md) for all available types.
