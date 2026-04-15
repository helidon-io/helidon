# io.helidon.security.providers.jwt.AtnTokenConfig

## Description

Configuration for security.providers.jwt.atn-token

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }
</style>

<table>
<thead>
<tr>
<th>Key</th><th>Type</th><th>Default Value</th><th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><a id="handler"></a><a href="io.helidon.security.util.TokenHandler.md"><code>handler</code></a></td>
<td><code>TokenHandler</code></td>
<td></td>
<td>Token handler to extract username from request</td>
</tr>
<tr>
<td><a id="jwk"></a><a href="io.helidon.security.providers.jwt.atnToken.JwkConfig.md"><code>jwk</code></a></td>
<td></td>
<td></td>
<td>Configuration for jwk</td>
</tr>
<tr>
<td><code>jwt-audience</code></td>
<td><code>String</code></td>
<td></td>
<td>Audience expected in inbound JWTs</td>
</tr>
<tr>
<td><code>verify-signature</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Configure whether to verify signatures</td>
</tr>
</tbody>
</table>


## Usages

- [`security.providers.jwt.atn-token`](io.helidon.security.providers.jwt.JwtProvider.md#atn-token)

---

See the [manifest](manifest.md) for all available types.
