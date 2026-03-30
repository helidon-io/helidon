# io.helidon.webserver.cors.CorsFeature

## Description

Configuration of CORS feature.

## Usages

- [`cors`](../config/config_reference.md#ad017c-cors)
- [`server.features.cors`](../config/io_helidon_webserver_spi_ServerFeature.md#a9ee5f-cors)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="ae53cb-add-defaults"></span> `add-defaults` | `VALUE` | `Boolean` | `true` | Whether to add a default path configuration, that matches all paths, `GET, HEAD, POST` methods, and allows all origins, methods, and headers |
| <span id="a6b476-enabled"></span> `enabled` | `VALUE` | `Boolean` |   | This feature can be disabled |
| <span id="a44bb0-paths"></span> [`paths`](../config/io_helidon_webserver_cors_CorsPathConfig.md) | `LIST` | `i.h.w.c.CorsPathConfig` |   | Per path configuration |
| <span id="a29c5b-paths-discover-services"></span> `paths-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `paths` |
| <span id="a93acb-sockets"></span> `sockets` | `LIST` | `String` |   | List of sockets to register this feature on |
| <span id="a96481-weight"></span> `weight` | `VALUE` | `Double` | `850.0` | Weight of the CORS feature |

See the [manifest](../config/manifest.md) for all available types.
