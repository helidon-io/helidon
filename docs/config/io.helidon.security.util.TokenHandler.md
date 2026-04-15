# io.helidon.security.util.TokenHandler

## Description

Extracts a security token from request or updates headers with the token

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
<td><code>regexp</code></td>
<td><code>String</code></td>
<td>Set the token pattern (Regular expression) to extract the token</td>
</tr>
<tr>
<td><code>prefix</code></td>
<td><code>String</code></td>
<td>Set the prefix of header value to extract the token</td>
</tr>
<tr>
<td><code>format</code></td>
<td><code>String</code></td>
<td>Token format for creating outbound tokens</td>
</tr>
<tr>
<td><code>header</code></td>
<td><code>String</code></td>
<td>Set the name of header to look into to extract the token</td>
</tr>
</tbody>
</table>


## Usages

- [`security.providers.google-login.token`](io.helidon.security.providers.google.login.GoogleTokenProvider.md#token)
- [`security.providers.header-atn.atn-token`](io.helidon.security.providers.header.HeaderAtnProvider.md#atn-token)
- [`security.providers.header-atn.outbound-token`](io.helidon.security.providers.header.HeaderAtnProvider.md#outbound-token)
- [`security.providers.idcs-role-mapper.idcs-app-name-handler`](io.helidon.security.providers.IdcsRoleMapperConfig.md#idcs-app-name-handler)
- [`security.providers.idcs-role-mapper.idcs-tenant-handler`](io.helidon.security.providers.IdcsRoleMapperConfig.md#idcs-tenant-handler)
- [`security.providers.idcs-role-mapper.oidc-config.header-token`](io.helidon.security.providers.oidc.common.OidcConfig.md#header-token)
- [`security.providers.jwt.atn-token.handler`](io.helidon.security.providers.jwt.AtnTokenConfig.md#handler)
- [`security.providers.oidc.header-token`](io.helidon.security.providers.oidc.OidcProvider.md#header-token)
- [`server.features.security.security.providers.google-login.token`](io.helidon.security.providers.google.login.GoogleTokenProvider.md#token)
- [`server.features.security.security.providers.header-atn.atn-token`](io.helidon.security.providers.header.HeaderAtnProvider.md#atn-token)
- [`server.features.security.security.providers.header-atn.outbound-token`](io.helidon.security.providers.header.HeaderAtnProvider.md#outbound-token)
- [`server.features.security.security.providers.idcs-role-mapper.idcs-app-name-handler`](io.helidon.server.features.security.security.providers.IdcsRoleMapperConfig.md#idcs-app-name-handler)
- [`server.features.security.security.providers.idcs-role-mapper.idcs-tenant-handler`](io.helidon.server.features.security.security.providers.IdcsRoleMapperConfig.md#idcs-tenant-handler)
- [`server.features.security.security.providers.idcs-role-mapper.oidc-config.header-token`](io.helidon.security.providers.oidc.common.OidcConfig.md#header-token)
- [`server.features.security.security.providers.jwt.atn-token.handler`](io.helidon.server.features.security.security.providers.jwt.AtnTokenConfig.md#handler)
- [`server.features.security.security.providers.oidc.header-token`](io.helidon.security.providers.oidc.OidcProvider.md#header-token)

---

See the [manifest](manifest.md) for all available types.
