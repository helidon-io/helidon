# io.helidon.discovery.providers.eureka.CacheConfig

## Description

Prototypical state for the portion of Eureka Discovery configuration related to a local cache of Eureka server information.

## Usages

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a907eb-compute-changes"></span> `compute-changes` | `VALUE` | `Boolean` | `true` | Whether the state of the cache should be computed from changes reported by Eureka, or replaced in full; `true` by default |
| <span id="a92836-defer-sync"></span> `defer-sync` | `VALUE` | `Boolean` | `false` | Whether to defer immediate cache synchronization; `false` by default |
| <span id="a5b1d6-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Whether a local cache of Eureka information is used or not; `true` by default |
| <span id="afb3f6-fetch-thread-name"></span> `fetch-thread-name` | `VALUE` | `String` | `Eureka registry fetch thread` | The name of the `Thread` used to retrieve service information from the Eureka server; "Eureka registry fetch thread" by default |
| <span id="a9f97f-sync-interval"></span> `sync-interval` | `VALUE` | `Duration` | `PT30S` | The time between retrievals of service information from the Eureka server; 30 seconds by default |

See the [manifest](../config/manifest.md) for all available types.
