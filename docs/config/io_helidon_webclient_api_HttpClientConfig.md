# io.helidon.webclient.api.HttpClientConfig

## Description

This can be used by any HTTP client version, and does not act as a factory, for easy extensibility.

## Usages

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a0d85c-base-uri"></span> `base-uri` | `VALUE` | `i.h.w.a.H.HttpCustomMethods` |   | Base uri used by the client in all requests |
| <span id="a89026-connect-timeout"></span> `connect-timeout` | `VALUE` | `Duration` |   | Connect timeout |
| <span id="ad70b2-connection-cache-size"></span> `connection-cache-size` | `VALUE` | `Integer` | `256` | Maximal size of the connection cache for a single connection key |
| <span id="a6c809-content-encoding"></span> [`content-encoding`](../config/io_helidon_http_encoding_ContentEncodingContext.md) | `VALUE` | `i.h.h.e.ContentEncodingContext` |   | Configure the listener specific `io.helidon.http.encoding.ContentEncodingContext` |
| <span id="ac5f6f-cookie-manager"></span> [`cookie-manager`](../config/io_helidon_webclient_api_WebClientCookieManager.md) | `VALUE` | `i.h.w.a.WebClientCookieManager` |   | WebClient cookie manager |
| <span id="ac1c6a-default-headers"></span> `default-headers` | `MAP` | `String` |   | Default headers to be used in every request from configuration |
| <span id="a0ba0e-follow-redirects"></span> `follow-redirects` | `VALUE` | `Boolean` | `true` | Whether to follow redirects |
| <span id="ad077a-keep-alive"></span> `keep-alive` | `VALUE` | `Boolean` | `true` | Determines if connection keep alive is enabled (NOT socket keep alive, but HTTP connection keep alive, to re-use the same connection for multiple requests) |
| <span id="a69834-max-in-memory-entity"></span> `max-in-memory-entity` | `VALUE` | `Integer` | `131072` | If the entity is expected to be smaller that this number of bytes, it would be buffered in memory to optimize performance |
| <span id="a5035c-max-redirects"></span> `max-redirects` | `VALUE` | `Integer` | `10` | Max number of followed redirects |
| <span id="aad347-media-context"></span> [`media-context`](../config/io_helidon_http_media_MediaContext.md) | `VALUE` | `i.h.h.m.MediaContext` | `create()` | Configure the listener specific `io.helidon.http.media.MediaContext` |
| <span id="a60695-media-type-parser-mode"></span> [`media-type-parser-mode`](../config/io_helidon_common_media_type_ParserMode.md) | `VALUE` | `i.h.c.m.t.ParserMode` | `STRICT` | Configure media type parsing mode for HTTP `Content-Type` header |
| <span id="aaa8d1-properties"></span> `properties` | `MAP` | `String` |   | Properties configured for this client |
| <span id="a1fea8-proxy"></span> [`proxy`](../config/io_helidon_webclient_api_Proxy.md) | `VALUE` | `i.h.w.a.Proxy` |   | Proxy configuration to be used for requests |
| <span id="a15d94-read-continue-timeout"></span> `read-continue-timeout` | `VALUE` | `Duration` | `PT1S` | Socket 100-Continue read timeout |
| <span id="a9a15c-read-timeout"></span> `read-timeout` | `VALUE` | `Duration` |   | Read timeout |
| <span id="ab065e-relative-uris"></span> `relative-uris` | `VALUE` | `Boolean` | `false` | Can be set to `true` to force the use of relative URIs in all requests, regardless of the presence or absence of proxies or no-proxy lists |
| <span id="a2bcce-send-expect-continue"></span> `send-expect-continue` | `VALUE` | `Boolean` | `true` | Whether Expect-100-Continue header is sent to verify server availability before sending an entity |
| <span id="a4d56e-services"></span> [`services`](../config/io_helidon_webclient_spi_WebClientService.md) | `LIST` | `i.h.w.s.WebClientService` |   | WebClient services |
| <span id="adb5db-services-discover-services"></span> `services-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `services` |
| <span id="a4f16e-share-connection-cache"></span> `share-connection-cache` | `VALUE` | `Boolean` | `true` | Whether to share connection cache between all the WebClient instances in JVM |
| <span id="aedebe-socket-options"></span> [`socket-options`](../config/io_helidon_common_socket_SocketOptions.md) | `VALUE` | `i.h.c.s.SocketOptions` |   | Socket options for connections opened by this client |
| <span id="afc610-tls"></span> [`tls`](../config/io_helidon_common_tls_Tls.md) | `VALUE` | `i.h.c.t.Tls` |   | TLS configuration for any TLS request from this client |
| <span id="aea564-write-buffer-size"></span> `write-buffer-size` | `VALUE` | `Integer` | `4096` | Buffer size used when writing data to the underlying socket on a client TCP connection |

See the [manifest](../config/manifest.md) for all available types.
