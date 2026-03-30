# io.helidon.telemetry.otelconfig.OtlpHttpExporterConfig

## Description

Settings common to HTTP-based OTLP exporters.

## Usages

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a823ee-certificate"></span> [`certificate`](../config/io_helidon_common_configurable_Resource.md) | `VALUE` | `i.h.c.c.Resource` |   | Trusted certificates |
| <span id="a1ac05-client-certificate"></span> [`client.certificate`](../config/io_helidon_common_configurable_Resource.md) | `VALUE` | `i.h.c.c.Resource` |   | TLS certificate |
| <span id="a0cc2d-client-key"></span> [`client.key`](../config/io_helidon_common_configurable_Resource.md) | `VALUE` | `i.h.c.c.Resource` |   | TLS client key |
| <span id="a21971-compression"></span> [`compression`](../config/io_helidon_telemetry_otelconfig_CompressionType.md) | `VALUE` | `i.h.t.o.CompressionType` |   | Compression the exporter uses |
| <span id="a450f0-connect-timeout"></span> `connect-timeout` | `VALUE` | `Duration` |   | Connection timeout |
| <span id="a501d1-endpoint"></span> `endpoint` | `VALUE` | `URI` |   | Endpoint of the collector to which the exporter should transmit |
| <span id="a23e4c-headers"></span> `headers` | `MAP` | `String` |   | Headers added to each export message |
| <span id="a6edc1-internal-telemetry-version"></span> [`internal-telemetry-version`](../config/io_opentelemetry_sdk_common_InternalTelemetryVersion.md) | `VALUE` | `i.o.s.c.InternalTelemetryVersion` |   | Self-monitoring telemetry OpenTelemetry should collect |
| <span id="a16c7e-memory-mode"></span> [`memory-mode`](../config/io_opentelemetry_sdk_common_export_MemoryMode.md) | `VALUE` | `i.o.s.c.e.MemoryMode` |   | Memory mode |
| <span id="a813ef-protocol"></span> `protocol` | `VALUE` | `i.h.t.o.O.CustomMethods` | `DEFAULT` | Exporter protocol type |
| <span id="a6fde0-retry-policy"></span> `retry-policy` | `VALUE` | `i.h.t.o.O.CustomMethods` |   | Retry policy |
| <span id="ae5a36-timeout"></span> `timeout` | `VALUE` | `Duration` |   | Exporter timeout |

See the [manifest](../config/manifest.md) for all available types.
