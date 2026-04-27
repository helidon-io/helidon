# io.helidon.security.providers.oidc.common.OidcConfig.ClientAuthentication

## Description

This type is an enumeration.

## Allowed Values

<style>
    table.cm-table code {
        white-space: nowrap !important;
    }
</style>

<table class="cm-table">
<thead>
<tr>
<th>Value</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><code>CLIENT_SECRET_BASIC</code></td>
<td>Clients that have received a client_secret value from the Authorization Server authenticate with the Authorization Server in accordance with Section 2.3.1 of OAuth 2.0 [RFC6749] using the HTTP Basic authentication scheme</td>
</tr>
<tr>
<td><code>CLIENT_SECRET_POST</code></td>
<td>Clients that have received a client_secret value from the Authorization Server, authenticate with the Authorization Server in accordance with Section 2.3.1 of OAuth 2.0 [RFC6749] by including the Client Credentials in the request body</td>
</tr>
<tr>
<td><code>CLIENT_SECRET_JWT</code></td>
<td>Clients that have received a client_secret value from the Authorization Server create a JWT using an HMAC SHA algorithm, such as HMAC SHA-256</td>
</tr>
<tr>
<td><code>PRIVATE_KEY_JWT</code></td>
<td>Clients that have registered a public key sign a JWT using that key</td>
</tr>
<tr>
<td><code>CLIENT_CERTIFICATE</code></td>
<td>Authentication is done via the client certificate used with MTLS</td>
</tr>
<tr>
<td><code>NONE</code></td>
<td>The Client does not authenticate itself at the Token Endpoint, either because it uses only the Implicit Flow (and so does not use the Token Endpoint) or because it is a Public Client with no Client Secret or other authentication mechanism</td>
</tr>
</tbody>
</table>

## Usages

- [`security.providers.idcs-role-mapper.oidc-config.tenants.token-endpoint-auth`](io.helidon.security.providers.oidc.common.TenantConfig.md#token-endpoint-auth)
- [`security.providers.idcs-role-mapper.oidc-config.token-endpoint-auth`](io.helidon.security.providers.oidc.common.OidcConfig.md#token-endpoint-auth)
- [`security.providers.oidc.tenants.token-endpoint-auth`](io.helidon.security.providers.oidc.common.TenantConfig.md#token-endpoint-auth)
- [`server.features.security.security.providers.idcs-role-mapper.oidc-config.tenants.token-endpoint-auth`](io.helidon.security.providers.oidc.common.TenantConfig.md#token-endpoint-auth)
- [`server.features.security.security.providers.idcs-role-mapper.oidc-config.token-endpoint-auth`](io.helidon.security.providers.oidc.common.OidcConfig.md#token-endpoint-auth)
- [`server.features.security.security.providers.oidc.tenants.token-endpoint-auth`](io.helidon.security.providers.oidc.common.TenantConfig.md#token-endpoint-auth)

---

See the [manifest](manifest.md) for all available types.
