# io.helidon.security.spi.SecurityProvider

## Description

This type is a provider contract.

## Usages

- [`security.providers`](io_helidon_security_Security.md#a56406-providers)
- [`server.features.security.security.providers`](io_helidon_security_Security.md#a56406-providers)

## Implementations

| Key | Type | Description |
|----|----|----|
| <span id="a4ca40-abac"></span> [`abac`](io_helidon_security_providers_abac_AbacProvider.md) | `i.h.s.p.a.AbacProvider` | Attribute Based Access Control provider |
| <span id="ad4a0b-config-vault"></span> [`config-vault`](io_helidon_security_providers_config_vault_ConfigVaultProvider.md) | `i.h.s.p.c.v.ConfigVaultProvider` | Secrets and Encryption provider using just configuration |
| <span id="a29106-google-login"></span> [`google-login`](io_helidon_security_providers_google_login_GoogleTokenProvider.md) | `i.h.s.p.g.l.GoogleTokenProvider` | Google Authentication provider |
| <span id="a58e34-header-atn"></span> [`header-atn`](io_helidon_security_providers_header_HeaderAtnProvider.md) | `i.h.s.p.h.HeaderAtnProvider` | Security provider that extracts a username (or service name) from a header |
| <span id="addbd3-http-basic-auth"></span> [`http-basic-auth`](io_helidon_security_providers_httpauth_HttpBasicAuthProvider.md) | `i.h.s.p.h.HttpBasicAuthProvider` | HTTP Basic Authentication provider |
| <span id="a0d898-http-digest-auth"></span> [`http-digest-auth`](io_helidon_security_providers_httpauth_HttpDigestAuthProvider.md) | `i.h.s.p.h.HttpDigestAuthProvider` | Http digest authentication security provider |
| <span id="a4d248-idcs-role-mapper"></span> [`idcs-role-mapper`](io_helidon_security_providers_idcs_mapper_IdcsMtRoleMapperProvider.md) | `i.h.s.p.i.m.IdcsMtRoleMapperProvider` | Multitenant IDCS role mapping provider |
| <span id="af9608-idcs-role-mapper"></span> [`idcs-role-mapper`](io_helidon_security_providers_idcs_mapper_IdcsRoleMapperProvider.md) | `i.h.s.p.i.m.IdcsRoleMapperProvider` | IDCS role mapping provider |
| <span id="ad2fae-jwt"></span> [`jwt`](io_helidon_security_providers_jwt_JwtProvider.md) | `i.h.s.p.j.JwtProvider` | JWT authentication provider |
| <span id="aab762-oidc"></span> [`oidc`](io_helidon_security_providers_oidc_OidcProvider.md) | `i.h.s.p.o.OidcProvider` | Open ID Connect security provider |

See the [manifest](../config/manifest.md) for all available types.
