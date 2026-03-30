# io.helidon.cors.CrossOriginConfig

## Description

Represents information about cross origin request sharing.

## Usages

- [`openapi.cors`](../config/io_helidon_openapi_OpenApiFeature.md#ab0d30-cors)
- [`security.providers.idcs-role-mapper.oidc-config.cors`](../config/io_helidon_security_providers_oidc_common_OidcConfig.md#a72304-cors)
- [`security.providers.oidc.cors`](../config/io_helidon_security_providers_oidc_OidcProvider.md#ad1309-cors)
- [`server.features.observe.cors`](../config/io_helidon_webserver_observe_ObserveFeature.md#a25a02-cors)
- [`server.features.openapi.cors`](../config/io_helidon_openapi_OpenApiFeature.md#ab0d30-cors)
- [`server.features.security.security.providers.idcs-role-mapper.oidc-config.cors`](../config/io_helidon_security_providers_oidc_common_OidcConfig.md#a72304-cors)
- [`server.features.security.security.providers.oidc.cors`](../config/io_helidon_security_providers_oidc_OidcProvider.md#ad1309-cors)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="ace3bc-allow-credentials"></span> `allow-credentials` | `VALUE` | `Boolean` | `false` | Sets the allow credentials flag |
| <span id="aa207c-allow-headers"></span> `allow-headers` | `LIST` | `String` | `*` | Sets the allow headers |
| <span id="afadae-allow-methods"></span> `allow-methods` | `LIST` | `String` | `*` | Sets the allow methods |
| <span id="af3025-allow-origins"></span> `allow-origins` | `LIST` | `String` | `*` | Sets the allowOrigins |
| <span id="ada268-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Sets whether this config should be enabled or not |
| <span id="a3ba67-expose-headers"></span> `expose-headers` | `LIST` | `String` |   | Sets the expose headers |
| <span id="a9f0c0-max-age-seconds"></span> `max-age-seconds` | `VALUE` | `Long` | `3600` | Sets the maximum age |
| <span id="aa449b-path-pattern"></span> `path-pattern` | `VALUE` | `String` | `{+}` | Updates the path prefix for this cross-origin config |

See the [manifest](../config/manifest.md) for all available types.
