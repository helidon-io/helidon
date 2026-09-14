# io.<wbr>helidon.<wbr>security.<wbr>providers.<wbr>common.<wbr>Outbound<wbr>Config

## Description

Outbound configuration for outbound security

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
<a id="jwk"></a>
<a href="io.helidon.security.providers.jwt.signToken.JwkConfig.md">
<code>jwk</code>
</a>
</td>
<td>
</td>
<td>Configuration for jwk</td>
</tr>
<tr>
<td>
<code>jwt-<wbr>issuer</code>
</td>
<td>
<code>String</code>
</td>
<td>Issuer used to create new JWTs</td>
</tr>
<tr>
<td>
<a id="outbound"></a>
<a href="io.helidon.security.providers.common.OutboundTarget.md">
<code>outbound</code>
</a>
</td>
<td>
<code>List&lt;<wbr>Outbound<wbr>Target&gt;</code>
</td>
<td>Add a new target configuration</td>
</tr>
</tbody>
</table>



## Dependent Types

- [io.<wbr>helidon.<wbr>security.<wbr>providers.<wbr>oidc.<wbr>Oidc<wbr>Provider](io.helidon.security.providers.oidc.OidcProvider.md)

## Usages

- <a href="io.helidon.security.providers.google.login.GoogleTokenProvider.md#outbound"><code>security.<wbr>providers.<wbr>google-<wbr>login.<wbr>outbound</code></a>
- <a href="io.helidon.security.providers.jwt.JwtProvider.md#sign-token"><code>security.<wbr>providers.<wbr>jwt.<wbr>sign-<wbr>token</code></a>
- <a href="io.helidon.security.providers.google.login.GoogleTokenProvider.md#outbound"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>google-<wbr>login.<wbr>outbound</code></a>
- <a href="io.helidon.security.providers.jwt.JwtProvider.md#sign-token"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>jwt.<wbr>sign-<wbr>token</code></a>

---

See the [manifest](manifest.md) for all available types.
