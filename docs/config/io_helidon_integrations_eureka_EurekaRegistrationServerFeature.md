# io.helidon.integrations.eureka.EurekaRegistrationServerFeature

## Description

A

Prototype.Api prototype

for

EurekaRegistrationServerFeature

io.helidon.builder.api.RuntimeType.Api runtime type

instances.

## Usages

- [`server.features.eureka`](../config/io_helidon_webserver_spi_ServerFeature.md#ae442f-eureka)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="af850c-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Whether this feature will be enabled |
| <span id="a20963-instance"></span> [`instance`](../config/io_helidon_integrations_eureka_InstanceInfoConfig.md) | `VALUE` | `i.h.i.e.InstanceInfoConfig` |   | An `InstanceInfoConfig` describing the service instance to be registered |
| <span id="a30a91-weight"></span> `weight` | `VALUE` | `Double` | `100.0` | The (zero or positive) `io.helidon.common.Weighted weight` of this instance |

See the [manifest](../config/manifest.md) for all available types.
