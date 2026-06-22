# io.<wbr>helidon.<wbr>security.<wbr>providers.<wbr>jwt.<wbr>AtnToken<wbr>Config

## Description

Configuration for security.providers.jwt.atn-token

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
<a href="io.helidon.security.providers.jwt.atnToken.JwkConfig.md">
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
<td>Audience expected in inbound JWTs</td>
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
<td>Configure whether to verify signatures</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.security.providers.jwt.JwtProvider.md#atn-token"><code>security.<wbr>providers.<wbr>jwt.<wbr>atn-<wbr>token</code></a>

---

See the [manifest](manifest.md) for all available types.
