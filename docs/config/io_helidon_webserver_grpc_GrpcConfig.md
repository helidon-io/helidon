# io.helidon.webserver.grpc.GrpcConfig

## Description

N/A

.

## Usages

- [`server.protocols.grpc`](../config/io_helidon_webserver_spi_ProtocolConfig.md#ab66d9-grpc)
- [`server.sockets.protocols.grpc`](../config/io_helidon_webserver_spi_ProtocolConfig.md#ab66d9-grpc)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a892c2-enable-compression"></span> `enable-compression` | `VALUE` | `Boolean` | `true` | Whether to support compression if requested by a client |
| <span id="ab0d5d-enable-metrics"></span> `enable-metrics` | `VALUE` | `Boolean` | `false` | Whether to collect metrics for gRPC server calls |
| <span id="ae155b-grpc-services"></span> [`grpc-services`](../config/io_helidon_webserver_grpc_spi_GrpcServerService.md) | `LIST` | `i.h.w.g.s.GrpcServerService` |   | gRPC server services |
| <span id="a94a42-grpc-services-discover-services"></span> `grpc-services-discover-services` | `VALUE` | `Boolean` | `false` | Whether to enable automatic service discovery for `grpc-services` |
| <span id="ad5a86-max-read-buffer-size"></span> `max-read-buffer-size` | `VALUE` | `Integer` | `2097152` | Max size of gRPC reading buffer |

See the [manifest](../config/manifest.md) for all available types.
