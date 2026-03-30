# io.helidon.security.providers.idcs.mapper.IdcsMtRoleMapperProvider

## Description

Multitenant IDCS role mapping provider.

## Usages

- [`security.providers.idcs-role-mapper`](../config/io_helidon_security_spi_SecurityProvider.md#a4d248-idcs-role-mapper)
- [`server.features.security.security.providers.idcs-role-mapper`](../config/io_helidon_security_spi_SecurityProvider.md#a4d248-idcs-role-mapper)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a6bc4c-cache-config"></span> [`cache-config`](../config/io_helidon_security_providers_common_EvictableCache.md) | `VALUE` | `i.h.s.p.c.EvictableCache` |   | Use explicit `io.helidon.security.providers.common.EvictableCache` for role caching |
| <span id="a75027-default-idcs-subject-type"></span> `default-idcs-subject-type` | `VALUE` | `String` | `user` | Configure subject type to use when requesting roles from IDCS |
| <span id="a89a70-idcs-app-name-handler"></span> [`idcs-app-name-handler`](../config/io_helidon_security_util_TokenHandler.md) | `VALUE` | `i.h.s.u.TokenHandler` |   | Configure token handler for IDCS Application name |
| <span id="af8920-idcs-tenant-handler"></span> [`idcs-tenant-handler`](../config/io_helidon_security_util_TokenHandler.md) | `VALUE` | `i.h.s.u.TokenHandler` |   | Configure token handler for IDCS Tenant ID |
| <span id="a2275a-oidc-config"></span> [`oidc-config`](../config/io_helidon_security_providers_oidc_common_OidcConfig.md) | `VALUE` | `i.h.s.p.o.c.OidcConfig` |   | Use explicit `io.helidon.security.providers.oidc.common.OidcConfig` instance, e.g |
| <span id="ab2c38-subject-types"></span> [`subject-types`](../config/io_helidon_security_SubjectType.md) | `LIST` | `i.h.s.SubjectType` | `USER` | Add a supported subject type |

See the [manifest](../config/manifest.md) for all available types.
