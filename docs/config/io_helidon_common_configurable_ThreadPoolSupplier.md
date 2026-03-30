# io.helidon.common.configurable.ThreadPoolSupplier

## Description

Supplier of a custom thread pool.

## Usages

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a6c135-core-pool-size"></span> `core-pool-size` | `VALUE` | `Integer` | `10` | Core pool size of the thread pool executor |
| <span id="a2fc0e-growth-rate"></span> `growth-rate` | `VALUE` | `Integer` | `0` | The percentage of task submissions that should result in adding threads, expressed as a value from 1 to 100 |
| <span id="a5144e-growth-threshold"></span> `growth-threshold` | `VALUE` | `Integer` | `1000` | The queue size above which pool growth will be considered if the pool is not fixed size |
| <span id="afe85b-is-daemon"></span> `is-daemon` | `VALUE` | `Boolean` | `true` | Is daemon of the thread pool executor |
| <span id="a67293-keep-alive"></span> `keep-alive` | `VALUE` | `Duration` | `PT3M` | Keep alive of the thread pool executor |
| <span id="a4b670-max-pool-size"></span> `max-pool-size` | `VALUE` | `Integer` | `50` | Max pool size of the thread pool executor |
| <span id="a3f3b8-name"></span> `name` | `VALUE` | `String` |   | Name of this thread pool executor |
| <span id="a7af0a-queue-capacity"></span> `queue-capacity` | `VALUE` | `Integer` | `10000` | Queue capacity of the thread pool executor |
| <span id="a62892-should-prestart"></span> `should-prestart` | `VALUE` | `Boolean` | `true` | Whether to prestart core threads in this thread pool executor |
| <span id="ac6f9f-thread-name-prefix"></span> `thread-name-prefix` | `VALUE` | `String` |   | Name prefix for threads in this thread pool executor |
| <span id="a5bf04-virtual-threads"></span> `virtual-threads` | `VALUE` | `Boolean` |   | When configured to `true`, an unbounded virtual executor service (project Loom) will be used |

See the [manifest](../config/manifest.md) for all available types.
