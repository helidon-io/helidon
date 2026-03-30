# io.helidon.webclient.grpc.GrpcClient

## Description

Configuration of a grpc client.

## Usages

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a3c19b-enable-metrics"></span> `enable-metrics` | `VALUE` | `Boolean` | `false` | Whether to collect metrics for gRPC client calls |
| <span id="ad79cf-grpc-services"></span> [`grpc-services`](../config/io_helidon_webclient_grpc_spi_GrpcClientService.md) | `LIST` | `i.h.w.g.s.GrpcClientService` |   | gRPC client services |
| <span id="aa82aa-grpc-services-discover-services"></span> `grpc-services-discover-services` | `VALUE` | `Boolean` | `false` | Whether to enable automatic service discovery for `grpc-services` |
| <span id="a9c084-protocol-config"></span> [`protocol-config`](../config/io_helidon_webclient_grpc_GrpcClientProtocolConfig.md) | `VALUE` | `i.h.w.g.GrpcClientProtocolConfig` | `create()` | gRPC specific configuration |

See the [manifest](../config/manifest.md) for all available types.
