# io.<wbr>helidon.<wbr>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>jwt.<wbr>AtnToken<wbr>Config

## Description

Configuration for server.features.security.security.providers.jwt.atn-token

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
<a id="handler"></a>
<a href="io.helidon.security.util.TokenHandler.md">
<code>handler</code>
</a>
</td>
<td>
<code>Token<wbr>Handler</code>
</td>
<td>
</td>
<td>Token handler to extract username from request</td>
</tr>
<tr>
<td>
<a id="jwk"></a>
<a href="io.helidon.server.features.security.security.providers.jwt.atnToken.JwkConfig.md">
<code>jwk</code>
</a>
</td>
<td>
</td>
<td>
</td>
<td>Configuration for jwk</td>
</tr>
<tr>
<td>
<code>jwt-<wbr>audience</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Audience expected in inbound JWTs. Required when authentication is enabled and signature verification is disabled</td>
</tr>
<tr>
<td>
<code>jwt-<wbr>issuer</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Issuer expected in inbound JWTs. Required when authentication is enabled and signature verification is disabled</td>
</tr>
<tr>
<td>
<code>verify-<wbr>signature</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to verify inbound JWT signatures. If set to false while authentication is enabled, atn-token.jwt-issuer and atn-token.jwt-audience are required</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.security.providers.jwt.JwtProvider.md#atn-token"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>jwt.<wbr>atn-<wbr>token</code></a>

---

See the [manifest](manifest.md) for all available types.
