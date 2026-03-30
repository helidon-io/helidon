# io.helidon.security.providers.jwt.JwtProvider

## Description

JWT authentication provider.

## Usages

- [`security.providers.jwt`](../config/io_helidon_security_spi_SecurityProvider.md#ad2fae-jwt)
- [`server.features.security.security.providers.jwt`](../config/io_helidon_security_spi_SecurityProvider.md#ad2fae-jwt)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a8e9cb-allow-impersonation"></span> `allow-impersonation` | `VALUE` | `Boolean` | `false` | Whether to allow impersonation by explicitly overriding username from outbound requests using `io.helidon.security.EndpointConfig#PROPERTY_OUTBOUND_ID` property |
| <span id="ac7a36-allow-unsigned"></span> `allow-unsigned` | `VALUE` | `Boolean` | `false` | Configure support for unsigned JWT |
| <span id="a31c89-atn-token-handler"></span> [`atn-token.handler`](../config/io_helidon_security_util_TokenHandler.md) | `VALUE` | `i.h.s.u.TokenHandler` |   | Token handler to extract username from request |
| <span id="ab9ed4-atn-token-jwk-resource"></span> [`atn-token.jwk.resource`](../config/io_helidon_common_configurable_Resource.md) | `VALUE` | `i.h.c.c.Resource` |   | JWK resource used to verify JWTs created by other parties |
| <span id="a7fd00-atn-token-jwt-audience"></span> `atn-token.jwt-audience` | `VALUE` | `String` |   | Audience expected in inbound JWTs |
| <span id="a1483b-atn-token-verify-signature"></span> `atn-token.verify-signature` | `VALUE` | `Boolean` | `true` | Configure whether to verify signatures |
| <span id="a2bd0c-authenticate"></span> `authenticate` | `VALUE` | `Boolean` | `true` | Whether to authenticate requests |
| <span id="ac625d-optional"></span> `optional` | `VALUE` | `Boolean` | `false` | Whether authentication is required |
| <span id="af07ea-principal-type"></span> [`principal-type`](../config/io_helidon_security_SubjectType.md) | `VALUE` | `i.h.s.SubjectType` | `USER` | Principal type this provider extracts (and also propagates) |
| <span id="a5a95f-propagate"></span> `propagate` | `VALUE` | `Boolean` | `true` | Whether to propagate identity |
| <span id="a9294b-sign-token"></span> [`sign-token`](../config/io_helidon_security_providers_common_OutboundConfig.md) | `VALUE` | `i.h.s.p.c.OutboundConfig` |   | Configuration of outbound rules |
| <span id="adc22c-sign-token-jwk-resource"></span> [`sign-token.jwk.resource`](../config/io_helidon_common_configurable_Resource.md) | `VALUE` | `i.h.c.c.Resource` |   | JWK resource used to sign JWTs created by us |
| <span id="ab60c1-sign-token-jwt-issuer"></span> `sign-token.jwt-issuer` | `VALUE` | `String` |   | Issuer used to create new JWTs |
| <span id="a8cde7-use-jwt-groups"></span> `use-jwt-groups` | `VALUE` | `Boolean` | `true` | Claim `groups` from JWT will be used to automatically add groups to current subject (may be used with `jakarta.annotation.security.RolesAllowed` annotation) |

See the [manifest](../config/manifest.md) for all available types.
