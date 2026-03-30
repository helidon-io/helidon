# io.helidon.webserver.staticcontent.ClasspathHandlerConfig

## Description

Classpath based static content handler configuration.

## Usages

- [`server.features.static-content.classpath`](../config/io_helidon_webserver_staticcontent_StaticContentFeature.md#a0df4c-classpath)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="abf798-cached-files"></span> `cached-files` | `LIST` | `String` |   | A set of files that are cached in memory at startup |
| <span id="a884b4-content-types"></span> `content-types` | `MAP` | `i.h.w.s.S.BaseMethods` |   | Maps a filename extension to the response content type |
| <span id="a18e6f-context"></span> `context` | `VALUE` | `String` | `/` | Context that will serve this handler's static resources, defaults to `/` |
| <span id="ac734e-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Whether this handle is enabled, defaults to `true` |
| <span id="a61681-location"></span> `location` | `VALUE` | `String` |   | The location on classpath that contains the root of the static content |
| <span id="aa63a5-memory-cache"></span> [`memory-cache`](../config/io_helidon_webserver_staticcontent_MemoryCache.md) | `VALUE` | `i.h.w.s.MemoryCache` |   | Handles will use memory cache configured on `StaticContentConfig#memoryCache()` by default |
| <span id="ae5ce0-record-cache-capacity"></span> `record-cache-capacity` | `VALUE` | `Integer` |   | Configure capacity of cache used for resources |
| <span id="aa6d56-single-file"></span> `single-file` | `VALUE` | `Boolean` |   | Classpath content usually starts from a `ClasspathHandlerConfig#location()` on classpath, and resolves all requested paths against this content root |
| <span id="a9bf78-sockets"></span> `sockets` | `LIST` | `String` |   | Sockets names (listeners) that will host this static content handler, defaults to all configured sockets |
| <span id="a19deb-temporary-storage"></span> [`temporary-storage`](../config/io_helidon_webserver_staticcontent_TemporaryStorage.md) | `VALUE` | `i.h.w.s.TemporaryStorage` |   | Customization of temporary storage configuration |
| <span id="a0fa0b-welcome"></span> `welcome` | `VALUE` | `String` |   | Welcome-file name |

See the [manifest](../config/manifest.md) for all available types.
