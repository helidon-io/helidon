# io.helidon.telemetry.otelconfig.MetricExporterConfig

## Description

OpenTelemetry metric exporter settings.

## Usages

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a54672-certificate"></span> [`certificate`](../config/io_helidon_common_configurable_Resource.md) | `VALUE` | `i.h.c.c.Resource` |   | Trusted certificates |
| <span id="a34754-client-certificate"></span> [`client.certificate`](../config/io_helidon_common_configurable_Resource.md) | `VALUE` | `i.h.c.c.Resource` |   | TLS certificate |
| <span id="ab930c-client-key"></span> [`client.key`](../config/io_helidon_common_configurable_Resource.md) | `VALUE` | `i.h.c.c.Resource` |   | TLS client key |
| <span id="a1de7a-compression"></span> [`compression`](../config/io_helidon_telemetry_otelconfig_CompressionType.md) | `VALUE` | `i.h.t.o.CompressionType` |   | Compression the exporter uses |
| <span id="ac5879-connect-timeout"></span> `connect-timeout` | `VALUE` | `Duration` |   | Connection timeout |
| <span id="ae5004-default-histogram-aggregation"></span> `default-histogram-aggregation` | `VALUE` | `i.h.t.o.M.CustomMethods` |   | Preferred default histogram aggregation technique, configurable as `io.helidon.telemetry.otelconfig.MetricDefaultHistogramAggregationConfig` |
| <span id="a29159-endpoint"></span> `endpoint` | `VALUE` | `URI` |   | Endpoint of the collector to which the exporter should transmit |
| <span id="a9e00c-headers"></span> `headers` | `MAP` | `String` |   | Headers added to each export message |
| <span id="ac582d-internal-telemetry-version"></span> [`internal-telemetry-version`](../config/io_opentelemetry_sdk_common_InternalTelemetryVersion.md) | `VALUE` | `i.o.s.c.InternalTelemetryVersion` |   | Self-monitoring telemetry OpenTelemetry should collect |
| <span id="a8c72a-memory-mode"></span> [`memory-mode`](../config/io_opentelemetry_sdk_common_export_MemoryMode.md) | `VALUE` | `i.o.s.c.e.MemoryMode` |   | Memory mode |
| <span id="a37375-protocol"></span> `protocol` | `VALUE` | `i.h.t.o.O.CustomMethods` | `DEFAULT` | Exporter protocol type |
| <span id="aef8f0-retry-policy"></span> `retry-policy` | `VALUE` | `i.h.t.o.O.CustomMethods` |   | Retry policy |
| <span id="a887fd-temporality-preference"></span> `temporality-preference` | `VALUE` | `i.h.t.o.M.CustomMethods` |   | Preferred output aggregation technique (how transmitted values reflect the values recorded locally), configurable as a `io.helidon.telemetry.otelconfig.MetricTemporalityPreferenceType` value: `CUMULATIVE, DELTA, LOWMEMORY` |
| <span id="a426f1-timeout"></span> `timeout` | `VALUE` | `Duration` |   | Exporter timeout |
| <span id="ab4fd4-type"></span> [`type`](../config/io_helidon_telemetry_otelconfig_MetricExporterType.md) | `VALUE` | `i.h.t.o.MetricExporterType` | `OTLP` | Metric exporter type |

See the [manifest](../config/manifest.md) for all available types.
