# io.helidon.telemetry.otelconfig.OtlpExporterConfig

## Description

Settings for OpenTelemetry OTLP exporters.

## Usages

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a13db1-certificate"></span> [`certificate`](../config/io_helidon_common_configurable_Resource.md) | `VALUE` | `i.h.c.c.Resource` |   | Trusted certificates |
| <span id="a5bbef-client-certificate"></span> [`client.certificate`](../config/io_helidon_common_configurable_Resource.md) | `VALUE` | `i.h.c.c.Resource` |   | TLS certificate |
| <span id="a75a00-client-key"></span> [`client.key`](../config/io_helidon_common_configurable_Resource.md) | `VALUE` | `i.h.c.c.Resource` |   | TLS client key |
| <span id="aeddd9-compression"></span> [`compression`](../config/io_helidon_telemetry_otelconfig_CompressionType.md) | `VALUE` | `i.h.t.o.CompressionType` |   | Compression the exporter uses |
| <span id="ade7dd-connect-timeout"></span> `connect-timeout` | `VALUE` | `Duration` |   | Connection timeout |
| <span id="ac7f6f-endpoint"></span> `endpoint` | `VALUE` | `URI` |   | Endpoint of the collector to which the exporter should transmit |
| <span id="ab438b-headers"></span> `headers` | `MAP` | `String` |   | Headers added to each export message |
| <span id="a13506-internal-telemetry-version"></span> [`internal-telemetry-version`](../config/io_opentelemetry_sdk_common_InternalTelemetryVersion.md) | `VALUE` | `i.o.s.c.InternalTelemetryVersion` |   | Self-monitoring telemetry OpenTelemetry should collect |
| <span id="a2502b-memory-mode"></span> [`memory-mode`](../config/io_opentelemetry_sdk_common_export_MemoryMode.md) | `VALUE` | `i.o.s.c.e.MemoryMode` |   | Memory mode |
| <span id="a83cb7-protocol"></span> `protocol` | `VALUE` | `i.h.t.o.O.CustomMethods` | `DEFAULT` | Exporter protocol type |
| <span id="a8e89c-retry-policy"></span> `retry-policy` | `VALUE` | `i.h.t.o.O.CustomMethods` |   | Retry policy |
| <span id="ab1755-timeout"></span> `timeout` | `VALUE` | `Duration` |   | Exporter timeout |

See the [manifest](../config/manifest.md) for all available types.
