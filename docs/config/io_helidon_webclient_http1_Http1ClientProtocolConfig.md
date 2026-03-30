# io.helidon.webclient.http1.Http1ClientProtocolConfig

## Description

Configuration of an HTTP/1.1 client.

## Usages

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a7a44c-default-keep-alive"></span> `default-keep-alive` | `VALUE` | `Boolean` | `true` | Whether to use keep alive by default |
| <span id="a81fda-max-buffered-entity-size"></span> `max-buffered-entity-size` | `VALUE` | `i.h.c.Size` | `64 KB` | Configure the maximum size allowed for an entity that can be explicitly buffered by the application by calling `io.helidon.http.media.ReadableEntity#buffer` |
| <span id="a403a3-max-header-size"></span> `max-header-size` | `VALUE` | `Integer` | `16384` | Configure the maximum allowed header size of the response |
| <span id="ab0904-max-status-line-length"></span> `max-status-line-length` | `VALUE` | `Integer` | `256` | Configure the maximum allowed length of the status line from the response |
| <span id="a2ff23-name"></span> `name` | `VALUE` | `String` | `http_1_1` | `N/A` |
| <span id="a607dc-validate-request-headers"></span> `validate-request-headers` | `VALUE` | `Boolean` | `false` | Sets whether the request header format is validated or not |
| <span id="a21e77-validate-response-headers"></span> `validate-response-headers` | `VALUE` | `Boolean` | `true` | Sets whether the response header format is validated or not |

See the [manifest](../config/manifest.md) for all available types.
