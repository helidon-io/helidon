# io.helidon.webserver.observe.log.LogStreamConfig

## Description

Log stream configuration for Log Observer.

## Usages

- [`server.features.observe.observers.log.stream`](../config/io_helidon_webserver_observe_log_LogObserver.md#afc46a-stream)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a7b867-content-type"></span> `content-type` | `VALUE` | `i.h.h.HttpMediaType` |   | `N/A` |
| <span id="a91be0-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Whether stream is enabled |
| <span id="a34b05-idle-message-timeout"></span> `idle-message-timeout` | `VALUE` | `Duration` | `PT5S` | How long to wait before we send the idle message, to make sure we keep the stream alive |
| <span id="aec316-idle-string"></span> `idle-string` | `VALUE` | `String` | \`% \` | String sent when there are no log messages within the `#idleMessageTimeout()` |
| <span id="a4b39b-queue-size"></span> `queue-size` | `VALUE` | `Integer` | `100` | Length of the in-memory queue that buffers log messages from loggers before sending them over the network |

See the [manifest](../config/manifest.md) for all available types.
