# io.helidon.webserver.http1.Http1Config

## Description

HTTP/1.1 server configuration.

## Usages

- [`server.protocols.http_1_1`](../config/io_helidon_webserver_spi_ProtocolConfig.md#ab85ee-http_1_1)
- [`server.sockets.protocols.http_1_1`](../config/io_helidon_webserver_spi_ProtocolConfig.md#ab85ee-http_1_1)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="aa11f9-continue-immediately"></span> `continue-immediately` | `VALUE` | `Boolean` | `false` | When true WebServer answers to expect continue with 100 continue immediately, not waiting for user to actually request the data |
| <span id="a137ac-max-buffered-entity-size"></span> `max-buffered-entity-size` | `VALUE` | `i.h.c.Size` | `64 KB` | Configure the maximum size allowed for an entity that can be explicitly buffered by the application by calling `io.helidon.http.media.ReadableEntity#buffer` |
| <span id="afb9ed-max-headers-size"></span> `max-headers-size` | `VALUE` | `Integer` | `16384` | Maximal size of received headers in bytes |
| <span id="a7af7b-max-prologue-length"></span> `max-prologue-length` | `VALUE` | `Integer` | `4096` | Maximal size of received HTTP prologue (GET /path HTTP/1.1) |
| <span id="adcd87-recv-log"></span> `recv-log` | `VALUE` | `Boolean` | `true` | Logging of received packets |
| <span id="afc226-requested-uri-discovery"></span> [`requested-uri-discovery`](../config/io_helidon_http_RequestedUriDiscoveryContext.md) | `VALUE` | `i.h.h.RequestedUriDiscoveryContext` |   | Requested URI discovery settings |
| <span id="ac9396-send-log"></span> `send-log` | `VALUE` | `Boolean` | `true` | Logging of sent packets |
| <span id="a04d8b-validate-path"></span> `validate-path` | `VALUE` | `Boolean` | `true` | If set to false, any path is accepted (even containing illegal characters) |
| <span id="aac83d-validate-prologue"></span> `validate-prologue` | `VALUE` | `Boolean` | `true` | If set to false, any query and fragment is accepted (even containing illegal characters) |
| <span id="a49b94-validate-request-headers"></span> `validate-request-headers` | `VALUE` | `Boolean` | `true` | Whether to validate headers |
| <span id="afa152-validate-response-headers"></span> `validate-response-headers` | `VALUE` | `Boolean` | `false` | Whether to validate headers |

### Deprecated Options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a5d203-validate-request-host-header"></span> `validate-request-host-header` | `VALUE` | `Boolean` | `true` | Request host header validation |

See the [manifest](../config/manifest.md) for all available types.
