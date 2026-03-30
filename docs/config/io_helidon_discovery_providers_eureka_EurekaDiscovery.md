# io.helidon.discovery.providers.eureka.EurekaDiscovery

## Description

Prototypical state for

EurekaDiscovery

instances.

## Usages

## Configuration options

| Key | Kind | Type | Description |
|----|----|----|----|
| <span id="a916bb-cache"></span> [`cache`](../config/io_helidon_discovery_providers_eureka_CacheConfig.md) | `VALUE` | `i.h.d.p.e.CacheConfig` | The `CacheConfig` to use controlling how a local cache of Eureka server information is used |
| <span id="ade7b3-client"></span> [`client`](../config/io_helidon_webclient_http1_Http1Client.md) | `VALUE` | `i.h.w.h.Http1Client` | The `Http1Client` to use to communicate with the Eureka server |
| <span id="ac3cce-prefer-ip-address"></span> `prefer-ip-address` | `VALUE` | `Boolean` | Whether the host component of any `java.net.URI URI` should be set to the IP address stored by Eureka, or the hostname; `false` by default |

See the [manifest](../config/manifest.md) for all available types.
