# io.helidon.webclient.grpc.GrpcClientProtocolConfig

## Description

Configuration of a gRPC client.

## Usages

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a76ff7-abort-poll-time-expired"></span> `abort-poll-time-expired` | `VALUE` | `Boolean` | `false` | Whether to continue retrying after a poll wait timeout expired or not |
| <span id="a6ed8e-heartbeat-period"></span> `heartbeat-period` | `VALUE` | `Duration` | `PT0S` | How often to send a heartbeat (HTTP/2 ping) to check if the connection is still alive |
| <span id="a2b249-init-buffer-size"></span> `init-buffer-size` | `VALUE` | `Integer` | `2048` | Initial buffer size used to serialize gRPC request payloads |
| <span id="ac8065-name"></span> `name` | `VALUE` | `String` | `grpc` | Name identifying this client protocol |
| <span id="a133d3-next-request-wait-time"></span> `next-request-wait-time` | `VALUE` | `Duration` | `PT1S` | When data has been received from the server but not yet requested by the client (i.e., listener), the implementation will wait for this duration before signaling an error |
| <span id="ad5f5d-poll-wait-time"></span> `poll-wait-time` | `VALUE` | `Duration` | `PT10S` | How long to wait for the next HTTP/2 data frame to arrive in underlying stream |

See the [manifest](../config/manifest.md) for all available types.
