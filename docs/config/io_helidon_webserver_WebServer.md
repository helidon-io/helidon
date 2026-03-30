# io.helidon.webserver.WebServer

## Description

WebServer configuration bean.

## Usages

- [`server`](../config/config_reference.md#a3be6d-server)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="acb486-backlog"></span> `backlog` | `VALUE` | `Integer` | `1024` | Accept backlog |
| <span id="a4fc52-bind-address"></span> `bind-address` | `VALUE` | `i.h.w.W.ListenerCustomMethods` |   | The address to bind to |
| <span id="a32e67-concurrency-limit"></span> [`concurrency-limit`](../config/io_helidon_common_concurrency_limits_Limit.md) | `VALUE` | `i.h.c.c.l.Limit` |   | Concurrency limit to use to limit concurrent execution of incoming requests |
| <span id="a3f7e3-concurrency-limit-discover-services"></span> `concurrency-limit-discover-services` | `VALUE` | `Boolean` | `false` | Whether to enable automatic service discovery for `concurrency-limit` |
| <span id="ac9c91-connection-options"></span> [`connection-options`](../config/io_helidon_common_socket_SocketOptions.md) | `VALUE` | `i.h.c.s.SocketOptions` |   | Options for connections accepted by this listener |
| <span id="a511a0-content-encoding"></span> [`content-encoding`](../config/io_helidon_http_encoding_ContentEncodingContext.md) | `VALUE` | `i.h.h.e.ContentEncodingContext` |   | Configure the listener specific `io.helidon.http.encoding.ContentEncodingContext` |
| <span id="aa0fb8-enable-proxy-protocol"></span> `enable-proxy-protocol` | `VALUE` | `Boolean` | `false` | Enable proxy protocol support for this socket |
| <span id="a92b62-error-handling"></span> [`error-handling`](../config/io_helidon_webserver_ErrorHandling.md) | `VALUE` | `i.h.w.ErrorHandling` |   | Configuration for this listener's error handling |
| <span id="ae9df6-features"></span> [`features`](../config/io_helidon_webserver_spi_ServerFeature.md) | `LIST` | `i.h.w.s.ServerFeature` |   | Server features allow customization of the server, listeners, or routings |
| <span id="a4431f-features-discover-services"></span> `features-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `features` |
| <span id="a47500-host"></span> `host` | `VALUE` | `String` | `0.0.0.0` | Host of the default socket |
| <span id="a570c4-idle-connection-period"></span> `idle-connection-period` | `VALUE` | `Duration` | `PT2M` | How often should we check for `#idleConnectionTimeout()` |
| <span id="abfcba-idle-connection-timeout"></span> `idle-connection-timeout` | `VALUE` | `Duration` | `PT5M` | How long should we wait before closing a connection that has no traffic on it |
| <span id="acfc0a-ignore-invalid-named-routing"></span> `ignore-invalid-named-routing` | `VALUE` | `Boolean` |   | If set to `true`, any named routing configured that does not have an associated named listener will NOT cause an exception to be thrown (default behavior is to throw an exception) |
| <span id="a71146-max-concurrent-requests"></span> `max-concurrent-requests` | `VALUE` | `Integer` | `-1` | Limits the number of requests that can be executed at the same time (the number of active virtual threads of requests) |
| <span id="a23186-max-in-memory-entity"></span> `max-in-memory-entity` | `VALUE` | `Integer` | `131072` | If the entity is expected to be smaller that this number of bytes, it would be buffered in memory to optimize performance when writing it |
| <span id="a6e9f1-max-payload-size"></span> `max-payload-size` | `VALUE` | `Long` | `-1` | Maximal number of bytes an entity may have |
| <span id="ac255e-max-tcp-connections"></span> `max-tcp-connections` | `VALUE` | `Integer` | `-1` | Limits the number of connections that can be opened at a single point in time |
| <span id="a847a9-media-context"></span> [`media-context`](../config/io_helidon_http_media_MediaContext.md) | `VALUE` | `i.h.h.m.MediaContext` |   | Configure the listener specific `io.helidon.http.media.MediaContext` |
| <span id="a390dc-name"></span> `name` | `VALUE` | `String` | `@default` | Name of this socket |
| <span id="a9d956-port"></span> `port` | `VALUE` | `Integer` | `0` | Port of the default socket |
| <span id="abdf05-protocols"></span> [`protocols`](../config/io_helidon_webserver_spi_ProtocolConfig.md) | `LIST` | `i.h.w.s.ProtocolConfig` |   | Configuration of protocols |
| <span id="a4b6cc-protocols-discover-services"></span> `protocols-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `protocols` |
| <span id="aaf9ce-requested-uri-discovery"></span> [`requested-uri-discovery`](../config/io_helidon_http_RequestedUriDiscoveryContext.md) | `VALUE` | `i.h.h.RequestedUriDiscoveryContext` |   | Requested URI discovery context |
| <span id="aa99af-restore-response-headers"></span> `restore-response-headers` | `VALUE` | `Boolean` | `true` | Copy and restore response headers before and after passing a request to Jersey for processing |
| <span id="a875ae-shutdown-grace-period"></span> `shutdown-grace-period` | `VALUE` | `Duration` | `PT0.5S` | Grace period in ISO 8601 duration format to allow running tasks to complete before listener's shutdown |
| <span id="aa36d3-shutdown-hook"></span> `shutdown-hook` | `VALUE` | `Boolean` | `true` | When true the webserver registers a shutdown hook with the JVM Runtime |
| <span id="a3378e-smart-async-writes"></span> `smart-async-writes` | `VALUE` | `Boolean` | `false` | If enabled and `#writeQueueLength()` is greater than 1, then start with async writes but possibly switch to sync writes if async queue size is always below a certain threshold |
| <span id="a03604-sockets"></span> [`sockets`](../config/io_helidon_webserver_ListenerConfig.md) | `MAP` | `i.h.w.ListenerConfig` |   | Socket configurations |
| <span id="ac9efa-tls"></span> [`tls`](../config/io_helidon_common_tls_Tls.md) | `VALUE` | `i.h.c.t.Tls` |   | Listener TLS configuration |
| <span id="a5f9ab-use-nio"></span> `use-nio` | `VALUE` | `Boolean` | `true` | If set to `true`, use NIO socket channel, instead of a socket |
| <span id="a57ab6-write-buffer-size"></span> `write-buffer-size` | `VALUE` | `Integer` | `4096` | Initial buffer size in bytes of `java.io.BufferedOutputStream` created internally to write data to a socket connection |
| <span id="adda19-write-queue-length"></span> `write-queue-length` | `VALUE` | `Integer` | `0` | Number of buffers queued for write operations |

### Deprecated Options

| Key | Kind | Type | Description |
|----|----|----|----|
| <span id="a20877-connection-config"></span> [`connection-config`](../config/io_helidon_webserver_ConnectionConfig.md) | `VALUE` | `i.h.w.ConnectionConfig` | Configuration of a connection (established from client against our server) |
| <span id="ab275b-receive-buffer-size"></span> `receive-buffer-size` | `VALUE` | `Integer` | Listener receive buffer size |

See the [manifest](../config/manifest.md) for all available types.
