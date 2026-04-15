# io.helidon.security.providers.common.OutboundConfig

## Description

Outbound configuration for outbound security

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }
</style>

<table>
<thead>
<tr>
<th>Key</th><th>Type</th><th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><a id="jwk"></a><a href="io.helidon.security.providers.jwt.signToken.JwkConfig.md"><code>jwk</code></a></td>
<td></td>
<td>Configuration for jwk</td>
</tr>
<tr>
<td><code>jwt-issuer</code></td>
<td><code>String</code></td>
<td>Issuer used to create new JWTs</td>
</tr>
<tr>
<td><a id="outbound"></a><a href="io.helidon.security.providers.common.OutboundTarget.md"><code>outbound</code></a></td>
<td><code>List&lt;OutboundTarget&gt;</code></td>
<td>Add a new target configuration</td>
</tr>
</tbody>
</table>


## Dependent Types

- [io.helidon.security.providers.oidc.OidcProvider](io.helidon.security.providers.oidc.OidcProvider.md)

## Usages

- [`security.providers.google-login.outbound`](io.helidon.security.providers.google.login.GoogleTokenProvider.md#outbound)
- [`security.providers.jwt.sign-token`](io.helidon.security.providers.jwt.JwtProvider.md#sign-token)
- [`server.features.security.security.providers.google-login.outbound`](io.helidon.security.providers.google.login.GoogleTokenProvider.md#outbound)
- [`server.features.security.security.providers.jwt.sign-token`](io.helidon.security.providers.jwt.JwtProvider.md#sign-token)

---

See the [manifest](manifest.md) for all available types.
