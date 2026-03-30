# io.helidon.security.providers.idcs.mapper.IdcsRoleMapperProvider

## Description

IDCS role mapping provider.

## Usages

- [`security.providers.idcs-role-mapper`](../config/io_helidon_security_spi_SecurityProvider.md#af9608-idcs-role-mapper)
- [`server.features.security.security.providers.idcs-role-mapper`](../config/io_helidon_security_spi_SecurityProvider.md#af9608-idcs-role-mapper)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="aed3ab-cache-config"></span> [`cache-config`](../config/io_helidon_security_providers_common_EvictableCache.md) | `VALUE` | `i.h.s.p.c.EvictableCache` |   | Use explicit `io.helidon.security.providers.common.EvictableCache` for role caching |
| <span id="aa2e00-default-idcs-subject-type"></span> `default-idcs-subject-type` | `VALUE` | `String` | `user` | Configure subject type to use when requesting roles from IDCS |
| <span id="a630af-oidc-config"></span> [`oidc-config`](../config/io_helidon_security_providers_oidc_common_OidcConfig.md) | `VALUE` | `i.h.s.p.o.c.OidcConfig` |   | Use explicit `io.helidon.security.providers.oidc.common.OidcConfig` instance, e.g |
| <span id="a477d4-subject-types"></span> [`subject-types`](../config/io_helidon_security_SubjectType.md) | `LIST` | `i.h.s.SubjectType` | `USER` | Add a supported subject type |

See the [manifest](../config/manifest.md) for all available types.
