# io.helidon.security.providers.oidc.common.PkceChallengeMethod

## Description

This type is an enumeration.

## Usages

- [`security.providers.idcs-role-mapper.oidc-config.pkce-challenge-method`](io_helidon_security_providers_oidc_common_OidcConfig.md#ade967-pkce-challenge-method)
- [`security.providers.oidc.pkce-challenge-method`](io_helidon_security_providers_oidc_OidcProvider.md#a6ccfb-pkce-challenge-method)
- [`server.features.security.security.providers.idcs-role-mapper.oidc-config.pkce-challenge-method`](io_helidon_security_providers_oidc_common_OidcConfig.md#ade967-pkce-challenge-method)
- [`server.features.security.security.providers.oidc.pkce-challenge-method`](io_helidon_security_providers_oidc_OidcProvider.md#a6ccfb-pkce-challenge-method)

## Allowed Values

| Value   | Description                                          |
|---------|------------------------------------------------------|
| `PLAIN` | No hashing will be applied                           |
| `S256`  | SHA-256 algorithm is used to hash the verifier value |

See the [manifest](../config/manifest.md) for all available types.
