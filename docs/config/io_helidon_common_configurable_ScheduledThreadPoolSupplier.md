# io.helidon.common.configurable.ScheduledThreadPoolSupplier

## Description

Supplier of a custom scheduled thread pool.

## Usages

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a5779a-core-pool-size"></span> `core-pool-size` | `VALUE` | `Integer` | `16` | Core pool size of the thread pool executor |
| <span id="af6a75-is-daemon"></span> `is-daemon` | `VALUE` | `Boolean` | `true` | Is daemon of the thread pool executor |
| <span id="af5d90-prestart"></span> `prestart` | `VALUE` | `Boolean` | `false` | Whether to prestart core threads in this thread pool executor |
| <span id="a59c17-thread-name-prefix"></span> `thread-name-prefix` | `VALUE` | `String` | `helidon-` | Name prefix for threads in this thread pool executor |
| <span id="aa7632-virtual-threads"></span> `virtual-threads` | `VALUE` | `Boolean` |   | When configured to `true`, an unbounded virtual executor service (project Loom) will be used |

See the [manifest](../config/manifest.md) for all available types.
