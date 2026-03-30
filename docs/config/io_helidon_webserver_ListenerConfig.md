# io.helidon.webserver.ListenerConfig

## Description

Configuration of a server listener (server socket).

## Usages

- [`server.sockets`](../config/io_helidon_webserver_WebServer.md#a03604-sockets)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a28ec4-backlog"></span> `backlog` | `VALUE` | `Integer` | `1024` | Accept backlog |
| <span id="ae88dd-bind-address"></span> `bind-address` | `VALUE` | `i.h.w.W.ListenerCustomMethods` |   | The address to bind to |
| <span id="a87b14-concurrency-limit"></span> [`concurrency-limit`](../config/io_helidon_common_concurrency_limits_Limit.md) | `VALUE` | `i.h.c.c.l.Limit` |   | Concurrency limit to use to limit concurrent execution of incoming requests |
| <span id="a6894c-concurrency-limit-discover-services"></span> `concurrency-limit-discover-services` | `VALUE` | `Boolean` | `false` | Whether to enable automatic service discovery for `concurrency-limit` |
| <span id="a251be-connection-options"></span> [`connection-options`](../config/io_helidon_common_socket_SocketOptions.md) | `VALUE` | `i.h.c.s.SocketOptions` |   | Options for connections accepted by this listener |
| <span id="a8f3cb-content-encoding"></span> [`content-encoding`](../config/io_helidon_http_encoding_ContentEncodingContext.md) | `VALUE` | `i.h.h.e.ContentEncodingContext` |   | Configure the listener specific `io.helidon.http.encoding.ContentEncodingContext` |
| <span id="ae3bc5-enable-proxy-protocol"></span> `enable-proxy-protocol` | `VALUE` | `Boolean` | `false` | Enable proxy protocol support for this socket |
| <span id="ad35b9-error-handling"></span> [`error-handling`](../config/io_helidon_webserver_ErrorHandling.md) | `VALUE` | `i.h.w.ErrorHandling` |   | Configuration for this listener's error handling |
| <span id="a42f53-host"></span> `host` | `VALUE` | `String` | `0.0.0.0` | Host of the default socket |
| <span id="a41ee6-idle-connection-period"></span> `idle-connection-period` | `VALUE` | `Duration` | `PT2M` | How often should we check for `#idleConnectionTimeout()` |
| <span id="a3bd03-idle-connection-timeout"></span> `idle-connection-timeout` | `VALUE` | `Duration` | `PT5M` | How long should we wait before closing a connection that has no traffic on it |
| <span id="a7dc8e-ignore-invalid-named-routing"></span> `ignore-invalid-named-routing` | `VALUE` | `Boolean` |   | If set to `true`, any named routing configured that does not have an associated named listener will NOT cause an exception to be thrown (default behavior is to throw an exception) |
| <span id="a9f377-max-concurrent-requests"></span> `max-concurrent-requests` | `VALUE` | `Integer` | `-1` | Limits the number of requests that can be executed at the same time (the number of active virtual threads of requests) |
| <span id="ab3ae6-max-in-memory-entity"></span> `max-in-memory-entity` | `VALUE` | `Integer` | `131072` | If the entity is expected to be smaller that this number of bytes, it would be buffered in memory to optimize performance when writing it |
| <span id="a4888e-max-payload-size"></span> `max-payload-size` | `VALUE` | `Long` | `-1` | Maximal number of bytes an entity may have |
| <span id="a7d31c-max-tcp-connections"></span> `max-tcp-connections` | `VALUE` | `Integer` | `-1` | Limits the number of connections that can be opened at a single point in time |
| <span id="ad860a-media-context"></span> [`media-context`](../config/io_helidon_http_media_MediaContext.md) | `VALUE` | `i.h.h.m.MediaContext` |   | Configure the listener specific `io.helidon.http.media.MediaContext` |
| <span id="a60579-name"></span> `name` | `VALUE` | `String` | `@default` | Name of this socket |
| <span id="a10d44-port"></span> `port` | `VALUE` | `Integer` | `0` | Port of the default socket |
| <span id="ad0b48-protocols"></span> [`protocols`](../config/io_helidon_webserver_spi_ProtocolConfig.md) | `LIST` | `i.h.w.s.ProtocolConfig` |   | Configuration of protocols |
| <span id="ab5174-protocols-discover-services"></span> `protocols-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `protocols` |
| <span id="a1c079-requested-uri-discovery"></span> [`requested-uri-discovery`](../config/io_helidon_http_RequestedUriDiscoveryContext.md) | `VALUE` | `i.h.h.RequestedUriDiscoveryContext` |   | Requested URI discovery context |
| <span id="a5759a-restore-response-headers"></span> `restore-response-headers` | `VALUE` | `Boolean` | `true` | Copy and restore response headers before and after passing a request to Jersey for processing |
| <span id="a04bbe-shutdown-grace-period"></span> `shutdown-grace-period` | `VALUE` | `Duration` | `PT0.5S` | Grace period in ISO 8601 duration format to allow running tasks to complete before listener's shutdown |
| <span id="a97b9d-smart-async-writes"></span> `smart-async-writes` | `VALUE` | `Boolean` | `false` | If enabled and `#writeQueueLength()` is greater than 1, then start with async writes but possibly switch to sync writes if async queue size is always below a certain threshold |
| <span id="aed6f6-tls"></span> [`tls`](../config/io_helidon_common_tls_Tls.md) | `VALUE` | `i.h.c.t.Tls` |   | Listener TLS configuration |
| <span id="af7543-use-nio"></span> `use-nio` | `VALUE` | `Boolean` | `true` | If set to `true`, use NIO socket channel, instead of a socket |
| <span id="a72396-write-buffer-size"></span> `write-buffer-size` | `VALUE` | `Integer` | `4096` | Initial buffer size in bytes of `java.io.BufferedOutputStream` created internally to write data to a socket connection |
| <span id="abf0f7-write-queue-length"></span> `write-queue-length` | `VALUE` | `Integer` | `0` | Number of buffers queued for write operations |

### Deprecated Options

| Key | Kind | Type | Description |
|----|----|----|----|
| <span id="ac4449-connection-config"></span> [`connection-config`](../config/io_helidon_webserver_ConnectionConfig.md) | `VALUE` | `i.h.w.ConnectionConfig` | Configuration of a connection (established from client against our server) |
| <span id="abe538-receive-buffer-size"></span> `receive-buffer-size` | `VALUE` | `Integer` | Listener receive buffer size |

See the [manifest](../config/manifest.md) for all available types.
