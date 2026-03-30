# io.helidon.security.providers.common.EvictableCache

## Description

Generic cache with eviction support.

## Usages

- [`security.providers.idcs-role-mapper.cache-config`](../config/io_helidon_security_providers_idcs_mapper_IdcsMtRoleMapperProvider.md#a6bc4c-cache-config)
- [`server.features.security.security.providers.idcs-role-mapper.cache-config`](../config/io_helidon_security_providers_idcs_mapper_IdcsMtRoleMapperProvider.md#a6bc4c-cache-config)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a0e194-cache-enabled"></span> `cache-enabled` | `VALUE` | `Boolean` | `true` | If the cacheEnabled is set to false, no caching will be done |
| <span id="a1d886-cache-evict-delay-millis"></span> `cache-evict-delay-millis` | `VALUE` | `Long` | `60000` | Delay from the creation of the cache to first eviction |
| <span id="a467bf-cache-evict-period-millis"></span> `cache-evict-period-millis` | `VALUE` | `Long` | `300000` | How often to evict records |
| <span id="a20d20-cache-overall-timeout-millis"></span> `cache-overall-timeout-millis` | `VALUE` | `Long` | `3600000` | Configure record timeout since its creation |
| <span id="a826be-cache-timeout-millis"></span> `cache-timeout-millis` | `VALUE` | `Long` | `3600000` | Configure record timeout since last access |
| <span id="aab860-evictor-class"></span> `evictor-class` | `VALUE` | `Class` |   | Configure evictor to check if a record is still valid |
| <span id="a4e78a-max-size"></span> `max-size` | `VALUE` | `Long` | `100000` | Configure maximal cache size |
| <span id="a7e708-parallelism-threshold"></span> `parallelism-threshold` | `VALUE` | `Long` | `10000` | Configure parallelism threshold |

See the [manifest](../config/manifest.md) for all available types.
