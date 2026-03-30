# io.helidon.security.providers.oidc.common.OidcConfig.ClientAuthentication

## Description

This type is an enumeration.

## Usages

- [`security.providers.idcs-role-mapper.oidc-config.tenants.token-endpoint-auth`](io_helidon_security_providers_oidc_common_TenantConfig.md#aa5a6b-token-endpoint-auth)
- [`security.providers.idcs-role-mapper.oidc-config.token-endpoint-auth`](io_helidon_security_providers_oidc_common_OidcConfig.md#aad441-token-endpoint-auth)
- [`security.providers.oidc.tenants.token-endpoint-auth`](io_helidon_security_providers_oidc_common_TenantConfig.md#aa5a6b-token-endpoint-auth)
- [`server.features.security.security.providers.idcs-role-mapper.oidc-config.tenants.token-endpoint-auth`](io_helidon_security_providers_oidc_common_TenantConfig.md#aa5a6b-token-endpoint-auth)
- [`server.features.security.security.providers.idcs-role-mapper.oidc-config.token-endpoint-auth`](io_helidon_security_providers_oidc_common_OidcConfig.md#aad441-token-endpoint-auth)
- [`server.features.security.security.providers.oidc.tenants.token-endpoint-auth`](io_helidon_security_providers_oidc_common_TenantConfig.md#aa5a6b-token-endpoint-auth)

## Allowed Values

| Value | Description |
|----|----|
| `CLIENT_SECRET_BASIC` | Clients that have received a client_secret value from the Authorization Server authenticate with the Authorization Server in accordance with Section 2.3.1 of OAuth 2.0 \[RFC6749 using the HTTP Basic authentication scheme\] |
| `CLIENT_SECRET_POST` | Clients that have received a client_secret value from the Authorization Server, authenticate with the Authorization Server in accordance with Section 2.3.1 of OAuth 2.0 \[RFC6749 by including the Client Credentials in the request body\] |
| `CLIENT_SECRET_JWT` | Clients that have received a client_secret value from the Authorization Server create a JWT using an HMAC SHA algorithm, such as HMAC SHA-256 |
| `PRIVATE_KEY_JWT` | Clients that have registered a public key sign a JWT using that key |
| `CLIENT_CERTIFICATE` | Authentication is done via the client certificate used with MTLS |
| `NONE` | The Client does not authenticate itself at the Token Endpoint, either because it uses only the Implicit Flow (and so does not use the Token Endpoint) or because it is a Public Client with no Client Secret or other authentication mechanism |

See the [manifest](../config/manifest.md) for all available types.
