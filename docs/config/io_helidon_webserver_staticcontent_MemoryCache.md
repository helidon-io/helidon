# io.helidon.webserver.staticcontent.MemoryCache

## Description

Configuration of memory cache for static content.

## Usages

- [`server.features.static-content.classpath.memory-cache`](../config/io_helidon_webserver_staticcontent_ClasspathHandlerConfig.md#aa63a5-memory-cache)
- [`server.features.static-content.memory-cache`](../config/io_helidon_webserver_staticcontent_StaticContentFeature.md#a1e68a-memory-cache)
- [`server.features.static-content.path.memory-cache`](../config/io_helidon_webserver_staticcontent_FileSystemHandlerConfig.md#af8862-memory-cache)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="aaa495-capacity"></span> `capacity` | `VALUE` | `i.h.c.Size` | `50 mB` | Capacity of the cached bytes of file content |
| <span id="a9d973-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Whether the cache is enabled, defaults to `true` |

See the [manifest](../config/manifest.md) for all available types.
