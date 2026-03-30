# io.helidon.webclient.http2.Http2ClientProtocolConfig

## Description

N/A

.

## Usages

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a06f7b-flow-control-block-timeout"></span> `flow-control-block-timeout` | `VALUE` | `Duration` | `PT15S` | Timeout for blocking while waiting for window update when window is depleted |
| <span id="a942dc-initial-window-size"></span> `initial-window-size` | `VALUE` | `Integer` | `65535` | Configure INITIAL_WINDOW_SIZE setting for new HTTP/2 connections |
| <span id="a2ae0e-max-buffered-entity-size"></span> `max-buffered-entity-size` | `VALUE` | `i.h.c.Size` | `64 KB` | Configure the maximum size allowed for an entity that can be explicitly buffered by the application by calling `io.helidon.http.media.ReadableEntity#buffer` |
| <span id="aecd63-max-frame-size"></span> `max-frame-size` | `VALUE` | `Integer` | `16384` | Configure initial MAX_FRAME_SIZE setting for new HTTP/2 connections |
| <span id="aa6ab2-max-header-list-size"></span> `max-header-list-size` | `VALUE` | `Long` | `-1` | Configure initial MAX_HEADER_LIST_SIZE setting for new HTTP/2 connections |
| <span id="ae847a-name"></span> `name` | `VALUE` | `String` | `h2` | `N/A` |
| <span id="ac97c5-ping"></span> `ping` | `VALUE` | `Boolean` | `false` | Check healthiness of cached connections with HTTP/2.0 ping frame |
| <span id="af75f0-ping-timeout"></span> `ping-timeout` | `VALUE` | `Duration` | `PT0.5S` | Timeout for ping probe used for checking healthiness of cached connections |
| <span id="a8e968-prior-knowledge"></span> `prior-knowledge` | `VALUE` | `Boolean` | `false` | Prior knowledge of HTTP/2 capabilities of the server |

See the [manifest](../config/manifest.md) for all available types.
