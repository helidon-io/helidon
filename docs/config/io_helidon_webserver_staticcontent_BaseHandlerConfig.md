# io.helidon.webserver.staticcontent.BaseHandlerConfig

## Description

Configuration of static content handlers that is common for classpath and file system based handlers.

## Usages

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a3acdb-cached-files"></span> `cached-files` | `LIST` | `String` |   | A set of files that are cached in memory at startup |
| <span id="ad9373-content-types"></span> `content-types` | `MAP` | `i.h.w.s.S.BaseMethods` |   | Maps a filename extension to the response content type |
| <span id="a87be0-context"></span> `context` | `VALUE` | `String` | `/` | Context that will serve this handler's static resources, defaults to `/` |
| <span id="a5ce49-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Whether this handle is enabled, defaults to `true` |
| <span id="a50625-memory-cache"></span> [`memory-cache`](../config/io_helidon_webserver_staticcontent_MemoryCache.md) | `VALUE` | `i.h.w.s.MemoryCache` |   | Handles will use memory cache configured on `StaticContentConfig#memoryCache()` by default |
| <span id="ae1233-record-cache-capacity"></span> `record-cache-capacity` | `VALUE` | `Integer` |   | Configure capacity of cache used for resources |
| <span id="abbe74-sockets"></span> `sockets` | `LIST` | `String` |   | Sockets names (listeners) that will host this static content handler, defaults to all configured sockets |
| <span id="adce1b-welcome"></span> `welcome` | `VALUE` | `String` |   | Welcome-file name |

See the [manifest](../config/manifest.md) for all available types.
