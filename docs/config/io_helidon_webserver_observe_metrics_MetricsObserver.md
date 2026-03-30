# io.helidon.webserver.observe.metrics.MetricsObserver

## Description

Metrics Observer configuration.

## Usages

- [`metrics`](../config/config_reference.md#a01ebb-metrics)
- [`server.features.observe.observers.metrics`](../config/io_helidon_webserver_observe_spi_Observer.md#a574f4-metrics)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a13a6e-app-name"></span> `app-name` | `VALUE` | `String` |   | Value for the application tag to be added to each meter ID |
| <span id="aebf9d-app-tag-name"></span> `app-tag-name` | `VALUE` | `String` |   | Name for the application tag to be added to each meter ID |
| <span id="a4b719-auto-http-metrics"></span> [`auto-http-metrics`](../config/io_helidon_webserver_observe_metrics_AutoHttpMetricsConfig.md) | `VALUE` | `i.h.w.o.m.AutoHttpMetricsConfig` |   | Automatic metrics collection settings |
| <span id="a44315-built-in-meter-name-format"></span> [`built-in-meter-name-format`](../config/io_helidon_metrics_api_BuiltInMeterNameFormat.md) | `VALUE` | `i.h.m.a.BuiltInMeterNameFormat` | `CAMEL` | Output format for built-in meter names |
| <span id="ab3498-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Whether metrics functionality is enabled |
| <span id="ae3beb-endpoint"></span> `endpoint` | `VALUE` | `String` | `metrics` | `N/A` |
| <span id="a86e3a-key-performance-indicators"></span> [`key-performance-indicators`](../config/io_helidon_metrics_api_KeyPerformanceIndicatorMetricsConfig.md) | `VALUE` | `i.h.m.a.KeyPerformanceIndicatorMetricsConfig` |   | Key performance indicator metrics settings |
| <span id="abefe6-permit-all"></span> `permit-all` | `VALUE` | `Boolean` | `true` | Whether to allow anybody to access the endpoint |
| <span id="ab5d8d-publishers"></span> [`publishers`](../config/io_helidon_metrics_api_MetricsPublisher.md) | `LIST` | `i.h.m.a.MetricsPublisher` |   | Metrics publishers which make the metrics data available to external systems |
| <span id="a10d58-publishers-discover-services"></span> `publishers-discover-services` | `VALUE` | `Boolean` | `false` | Whether to enable automatic service discovery for `publishers` |
| <span id="a3f64d-rest-request-enabled"></span> `rest-request.enabled` | `VALUE` | `Boolean` | `false` | Whether automatic REST request metrics should be measured |
| <span id="a9cef2-roles"></span> `roles` | `LIST` | `String` | `observe` | Hints for role names the user is expected to be in |
| <span id="a2d4d9-scoping"></span> [`scoping`](../config/io_helidon_metrics_api_ScopingConfig.md) | `VALUE` | `i.h.m.a.ScopingConfig` |   | Settings related to scoping management |
| <span id="afa8bf-tags"></span> `tags` | `LIST` | `i.h.m.a.MetricsConfigSupport` |   | Global tags |
| <span id="a5e0d0-timers-json-units-default"></span> [`timers.json-units-default`](../config/java_util_concurrent_TimeUnit.md) | `VALUE` | `TimeUnit` |   | Default units for timer output in JSON if not specified on a given timer |
| <span id="a5fe3b-virtual-threads-enabled"></span> `virtual-threads.enabled` | `VALUE` | `Boolean` | `false` | Whether Helidon should expose meters related to virtual threads |
| <span id="a4c0ba-virtual-threads-pinned-threshold"></span> `virtual-threads.pinned.threshold` | `VALUE` | `Duration` | `PT0.020S` | Threshold for sampling pinned virtual threads to include in the pinned threads meter |
| <span id="ad5bff-warn-on-multiple-registries"></span> `warn-on-multiple-registries` | `VALUE` | `Boolean` | `true` | Whether to log warnings when multiple registries are created |

### Deprecated Options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="ac15af-gc-time-type"></span> [`gc-time-type`](../config/io_helidon_metrics_api_GcTimeType.md) | `VALUE` | `i.h.m.a.GcTimeType` | `COUNTER` | Whether the `gc.time` meter should be registered as a gauge (vs |
| <span id="a58bc4-rest-request-enabled"></span> `rest-request-enabled` | `VALUE` | `Boolean` |   | Whether automatic REST request metrics should be measured (as indicated by the deprecated config key `rest-request-enabled`, the config key using a hyphen instead of a dot separator) |

See the [manifest](../config/manifest.md) for all available types.
