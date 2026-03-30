# io.helidon.security.providers.common.OutboundTarget

## Description

Configuration of outbound target.

## Usages

- [`security.providers.google-login.outbound.outbound`](../config/io_helidon_security_providers_common_OutboundConfig.md#a54601-outbound)
- [`security.providers.header-atn.outbound`](../config/io_helidon_security_providers_header_HeaderAtnProvider.md#aa4f36-outbound)
- [`security.providers.http-basic-auth.outbound`](../config/io_helidon_security_providers_httpauth_HttpBasicAuthProvider.md#aade93-outbound)
- [`security.providers.jwt.sign-token.outbound`](../config/io_helidon_security_providers_common_OutboundConfig.md#a54601-outbound)
- [`security.providers.oidc.outbound`](../config/io_helidon_security_providers_oidc_OidcProvider.md#acf040-outbound)
- [`server.features.security.security.providers.google-login.outbound.outbound`](../config/io_helidon_security_providers_common_OutboundConfig.md#a54601-outbound)
- [`server.features.security.security.providers.header-atn.outbound`](../config/io_helidon_security_providers_header_HeaderAtnProvider.md#aa4f36-outbound)
- [`server.features.security.security.providers.http-basic-auth.outbound`](../config/io_helidon_security_providers_httpauth_HttpBasicAuthProvider.md#aade93-outbound)
- [`server.features.security.security.providers.jwt.sign-token.outbound`](../config/io_helidon_security_providers_common_OutboundConfig.md#a54601-outbound)
- [`server.features.security.security.providers.oidc.outbound`](../config/io_helidon_security_providers_oidc_OidcProvider.md#acf040-outbound)

## Configuration options

| Key | Kind | Type | Description |
|----|----|----|----|
| <span id="a18a07-hosts"></span> `hosts` | `LIST` | `String` | Add supported host for this target |
| <span id="ae8e21-methods"></span> `methods` | `LIST` | `String` | Add supported method for this target |
| <span id="a18020-name"></span> `name` | `VALUE` | `String` | Configure the name of this outbound target |
| <span id="ac8317-paths"></span> `paths` | `LIST` | `String` | Add supported paths for this target |
| <span id="af7d0d-transport"></span> `transport` | `LIST` | `String` | Add supported transports for this target |

See the [manifest](../config/manifest.md) for all available types.
