# io.helidon.security.util.TokenHandler

## Description

Extracts a security token from request or updates headers with the token.

## Usages

- [`security.providers.google-login.token`](../config/io_helidon_security_providers_google_login_GoogleTokenProvider.md#af185f-token)
- [`security.providers.header-atn.atn-token`](../config/io_helidon_security_providers_header_HeaderAtnProvider.md#a9672f-atn-token)
- [`security.providers.header-atn.outbound-token`](../config/io_helidon_security_providers_header_HeaderAtnProvider.md#ad5021-outbound-token)
- [`security.providers.idcs-role-mapper.idcs-app-name-handler`](../config/io_helidon_security_providers_idcs_mapper_IdcsMtRoleMapperProvider.md#a89a70-idcs-app-name-handler)
- [`security.providers.idcs-role-mapper.idcs-tenant-handler`](../config/io_helidon_security_providers_idcs_mapper_IdcsMtRoleMapperProvider.md#af8920-idcs-tenant-handler)
- [`security.providers.idcs-role-mapper.oidc-config.header-token`](../config/io_helidon_security_providers_oidc_common_OidcConfig.md#a8d5cc-header-token)
- [`security.providers.jwt.atn-token.handler`](../config/io_helidon_security_providers_jwt_JwtProvider.md#a31c89-atn-token-handler)
- [`security.providers.oidc.header-token`](../config/io_helidon_security_providers_oidc_OidcProvider.md#abf3fb-header-token)
- [`server.features.security.security.providers.google-login.token`](../config/io_helidon_security_providers_google_login_GoogleTokenProvider.md#af185f-token)
- [`server.features.security.security.providers.header-atn.atn-token`](../config/io_helidon_security_providers_header_HeaderAtnProvider.md#a9672f-atn-token)
- [`server.features.security.security.providers.header-atn.outbound-token`](../config/io_helidon_security_providers_header_HeaderAtnProvider.md#ad5021-outbound-token)
- [`server.features.security.security.providers.idcs-role-mapper.idcs-app-name-handler`](../config/io_helidon_security_providers_idcs_mapper_IdcsMtRoleMapperProvider.md#a89a70-idcs-app-name-handler)
- [`server.features.security.security.providers.idcs-role-mapper.idcs-tenant-handler`](../config/io_helidon_security_providers_idcs_mapper_IdcsMtRoleMapperProvider.md#af8920-idcs-tenant-handler)
- [`server.features.security.security.providers.idcs-role-mapper.oidc-config.header-token`](../config/io_helidon_security_providers_oidc_common_OidcConfig.md#a8d5cc-header-token)
- [`server.features.security.security.providers.jwt.atn-token.handler`](../config/io_helidon_security_providers_jwt_JwtProvider.md#a31c89-atn-token-handler)
- [`server.features.security.security.providers.oidc.header-token`](../config/io_helidon_security_providers_oidc_OidcProvider.md#abf3fb-header-token)

## Configuration options

| Key | Kind | Type | Description |
|----|----|----|----|
| <span id="aedd56-format"></span> `format` | `VALUE` | `String` | Token format for creating outbound tokens |
| <span id="a589ba-header"></span> `header` | `VALUE` | `String` | Set the name of header to look into to extract the token |
| <span id="a36302-prefix"></span> `prefix` | `VALUE` | `String` | Set the prefix of header value to extract the token |
| <span id="a4e209-regexp"></span> `regexp` | `VALUE` | `String` | Set the token pattern (Regular expression) to extract the token |

See the [manifest](../config/manifest.md) for all available types.
