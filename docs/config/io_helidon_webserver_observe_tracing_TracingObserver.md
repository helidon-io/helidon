# io.helidon.webserver.observe.tracing.TracingObserver

## Description

Configuration of Tracing observer.

## Usages

- [`server.features.observe.observers.tracing`](../config/io_helidon_webserver_observe_spi_Observer.md#a743b9-tracing)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="af6949-paths"></span> `paths` | `LIST` | `i.h.w.o.t.PathTracingConfig` |   | Path specific configuration of tracing |
| <span id="a2456e-wait-tracing-enabled"></span> `wait-tracing-enabled` | `VALUE` | `Boolean` | `false` | Whether waiting due to concurrency limit constraints should be traced |
| <span id="a9c34b-weight"></span> `weight` | `VALUE` | `Double` | `900.0` | Weight of the feature registered with WebServer |

See the [manifest](../config/manifest.md) for all available types.
