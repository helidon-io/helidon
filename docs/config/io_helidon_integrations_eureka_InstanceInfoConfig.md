# io.helidon.integrations.eureka.InstanceInfoConfig

## Description

A

Prototype.Api prototype

describing initial Eureka Server service instance registration details.

## Usages

- [`server.features.eureka.instance`](../config/io_helidon_integrations_eureka_EurekaRegistrationServerFeature.md#a20963-instance)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a21bc4-appGroup"></span> `appGroup` | `VALUE` | `String` | `unknown` | The app group name |
| <span id="a8056c-asgName"></span> `asgName` | `VALUE` | `String` |   | The ASG name |
| <span id="a7d309-healthCheckUrl"></span> `healthCheckUrl` | `VALUE` | `URI` |   | The health check URL |
| <span id="ae7cc8-healthCheckUrlPath"></span> `healthCheckUrlPath` | `VALUE` | `String` |   | The health check URL path (used if any health check URL is not explicitly set) |
| <span id="af4ebc-homePageUrl"></span> `homePageUrl` | `VALUE` | `URI` |   | The home page URL |
| <span id="ad24f5-homePageUrlPath"></span> `homePageUrlPath` | `VALUE` | `String` | `/` | The home page URL path (used if the homepage URL is not explicitly set) |
| <span id="afe68d-hostName"></span> `hostName` | `VALUE` | `String` |   | The hostname |
| <span id="a10ac1-instanceId"></span> `instanceId` | `VALUE` | `String` |   | The instance id |
| <span id="ae995e-ipAddr"></span> `ipAddr` | `VALUE` | `String` |   | The IP address |
| <span id="aaab1a-lease"></span> [`lease`](../config/io_helidon_integrations_eureka_LeaseInfoConfig.md) | `VALUE` | `i.h.i.e.LeaseInfoConfig` |   | The `LeaseInfoConfig` |
| <span id="a1be3b-metadata"></span> `metadata` | `MAP` | `String` |   | Metadata |
| <span id="ae3aaf-name"></span> `name` | `VALUE` | `String` | `unknown` | The app name |
| <span id="a12cb2-port"></span> [`port`](../config/io_helidon_integrations_eureka_PortInfoConfig.md) | `VALUE` | `i.h.i.e.PortInfoConfig` |   | (Non-secure) port information |
| <span id="ae6709-secureHealthCheckUrl"></span> `secureHealthCheckUrl` | `VALUE` | `URI` |   | The secure health check URL |
| <span id="aa21a8-securePort"></span> [`securePort`](../config/io_helidon_integrations_eureka_PortInfoConfig.md) | `VALUE` | `i.h.i.e.PortInfoConfig` |   | Secure port information |
| <span id="ae94ec-secureVipAddress"></span> `secureVipAddress` | `VALUE` | `String` |   | The secure VIP address |
| <span id="a04766-statusPageUrl"></span> `statusPageUrl` | `VALUE` | `URI` |   | The status page URL |
| <span id="ab626b-statusPageUrlPath"></span> `statusPageUrlPath` | `VALUE` | `String` | `/Status` | The status page URL path (used if status page URL is not explicitly set) |
| <span id="ad856f-traffic-enabled"></span> `traffic.enabled` | `VALUE` | `Boolean` | `true` | Whether traffic is enabled on startup (normally `true`) |
| <span id="a5fd26-vipAddress"></span> `vipAddress` | `VALUE` | `String` |   | The VIP address |

See the [manifest](../config/manifest.md) for all available types.
