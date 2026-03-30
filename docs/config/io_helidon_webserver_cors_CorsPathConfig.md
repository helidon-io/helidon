# io.helidon.webserver.cors.CorsPathConfig

## Description

Configuration of CORS for a specific path.

## Usages

- [`cors.paths`](../config/io_helidon_webserver_cors_CorsFeature.md#a44bb0-paths)
- [`server.features.cors.paths`](../config/io_helidon_webserver_cors_CorsFeature.md#a44bb0-paths)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a63978-allow-credentials"></span> `allow-credentials` | `VALUE` | `Boolean` | `false` | Whether to allow credentials |
| <span id="abc506-allow-headers"></span> `allow-headers` | `LIST` | `String` | `*` | Set of allowed headers, defaults to all |
| <span id="a7f636-allow-methods"></span> `allow-methods` | `LIST` | `String` | `*` | Set of allowed methods, defaults to all |
| <span id="a10bcf-allow-origins"></span> `allow-origins` | `LIST` | `String` | `*` | Set of allowed origins, defaults to all |
| <span id="aeefbd-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Whether this CORS configuration should be enabled or not |
| <span id="abb307-expose-headers"></span> `expose-headers` | `LIST` | `String` |   | Set of exposed headers, defaults to none |
| <span id="a1f548-max-age"></span> `max-age` | `VALUE` | `i.h.w.c.C.PathCustomMethods` | `PT1H` | Max age as a duration |
| <span id="afe1ca-path-pattern"></span> `path-pattern` | `VALUE` | `String` |   | Path pattern to apply this configuration for |

See the [manifest](../config/manifest.md) for all available types.
