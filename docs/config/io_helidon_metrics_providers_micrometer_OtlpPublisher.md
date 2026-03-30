# io.helidon.metrics.providers.micrometer.OtlpPublisher

## Description

Settings for an OTLP publisher.

## Usages

- [`metrics.publishers.otlp`](../config/io_helidon_metrics_api_MetricsPublisher.md#aa8a11-otlp)
- [`server.features.observe.observers.metrics.publishers.otlp`](../config/io_helidon_metrics_api_MetricsPublisher.md#aa8a11-otlp)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a5a031-aggregation-temporality"></span> [`aggregation-temporality`](../config/io_micrometer_registry_otlp_AggregationTemporality.md) | `VALUE` | `i.m.r.o.AggregationTemporality` | `CUMULATIVE` | Algorithm to use for adjusting values before transmission |
| <span id="a726ba-base-time-unit"></span> [`base-time-unit`](../config/java_util_concurrent_TimeUnit.md) | `VALUE` | `TimeUnit` | `java.util.concurrent.TimeUnit.MILLISECONDS` | Base time unit for timers |
| <span id="ace1fb-batch-size"></span> `batch-size` | `VALUE` | `Integer` | `10000` | Number of measurements to send in a single request to the backend |
| <span id="a6b5d5-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Whether the configured publisher is enabled |
| <span id="a821e5-headers"></span> `headers` | `MAP` | `String` |   | Headers to add to each transmission message |
| <span id="afbfb5-interval"></span> `interval` | `VALUE` | `Duration` | `PT60s` | Interval between successive transmissions of metrics data |
| <span id="a65cf0-max-bucket-count"></span> `max-bucket-count` | `VALUE` | `Integer` | `160` | Maximum bucket count to apply to statistical histogram |
| <span id="a11feb-max-buckets-per-meter"></span> `max-buckets-per-meter` | `MAP` | `Integer` |   | Maximum number of buckets to use for specific meters |
| <span id="a52180-max-scale"></span> `max-scale` | `VALUE` | `Integer` | `20` | Maximum scale value to apply to statistical histogram |
| <span id="a00636-name"></span> `name` | `VALUE` | `String` |   | `N/A` |
| <span id="a64095-prefix"></span> `prefix` | `VALUE` | `String` | `otlp` | The prefix for settings |
| <span id="afb329-properties"></span> `properties` | `MAP` | `String` |   | Property values to be returned by the OTLP meter registry configuration |
| <span id="a5f081-resource-attributes"></span> `resource-attributes` | `MAP` | `String` |   | Attribute name/value pairs to be associated with all metrics transmissions |
| <span id="a1f8b3-url"></span> `url` | `VALUE` | `String` | `http://localhost:4318/v1/metrics` | URL to which to send metrics telemetry |

See the [manifest](../config/manifest.md) for all available types.
