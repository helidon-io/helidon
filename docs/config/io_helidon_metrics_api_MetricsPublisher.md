# io.helidon.metrics.api.MetricsPublisher

## Description

This type is a provider contract.

## Usages

- [`metrics.publishers`](io_helidon_webserver_observe_metrics_MetricsObserver.md#ab5d8d-publishers)
- [`server.features.observe.observers.metrics.publishers`](io_helidon_webserver_observe_metrics_MetricsObserver.md#ab5d8d-publishers)

## Implementations

| Key | Type | Description |
|----|----|----|
| <span id="aa8a11-otlp"></span> [`otlp`](io_helidon_metrics_providers_micrometer_OtlpPublisher.md) | `i.h.m.p.m.OtlpPublisher` | Settings for an OTLP publisher |
| <span id="a62230-prometheus"></span> [`prometheus`](io_helidon_metrics_providers_micrometer_PrometheusPublisher.md) | `i.h.m.p.m.PrometheusPublisher` | Settings for a Micrometer Prometheus meter registry |

See the [manifest](../config/manifest.md) for all available types.
