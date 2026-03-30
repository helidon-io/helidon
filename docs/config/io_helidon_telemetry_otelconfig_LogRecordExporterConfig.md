# io.helidon.telemetry.otelconfig.LogRecordExporterConfig

## Description

Settings for a log record exporter.

## Usages

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="afac53-certificate"></span> [`certificate`](../config/io_helidon_common_configurable_Resource.md) | `VALUE` | `i.h.c.c.Resource` |   | Trusted certificates |
| <span id="ae2352-client-certificate"></span> [`client.certificate`](../config/io_helidon_common_configurable_Resource.md) | `VALUE` | `i.h.c.c.Resource` |   | TLS certificate |
| <span id="a68883-client-key"></span> [`client.key`](../config/io_helidon_common_configurable_Resource.md) | `VALUE` | `i.h.c.c.Resource` |   | TLS client key |
| <span id="a8c6f0-compression"></span> [`compression`](../config/io_helidon_telemetry_otelconfig_CompressionType.md) | `VALUE` | `i.h.t.o.CompressionType` |   | Compression the exporter uses |
| <span id="a8e962-connect-timeout"></span> `connect-timeout` | `VALUE` | `Duration` |   | Connection timeout |
| <span id="a7a8fb-endpoint"></span> `endpoint` | `VALUE` | `URI` |   | Endpoint of the collector to which the exporter should transmit |
| <span id="ac53ca-headers"></span> `headers` | `MAP` | `String` |   | Headers added to each export message |
| <span id="a85d7e-internal-telemetry-version"></span> [`internal-telemetry-version`](../config/io_opentelemetry_sdk_common_InternalTelemetryVersion.md) | `VALUE` | `i.o.s.c.InternalTelemetryVersion` |   | Self-monitoring telemetry OpenTelemetry should collect |
| <span id="ab8373-memory-mode"></span> [`memory-mode`](../config/io_opentelemetry_sdk_common_export_MemoryMode.md) | `VALUE` | `i.o.s.c.e.MemoryMode` |   | Memory mode |
| <span id="a0b17c-protocol"></span> `protocol` | `VALUE` | `i.h.t.o.O.CustomMethods` | `DEFAULT` | Exporter protocol type |
| <span id="a8ef83-retry-policy"></span> `retry-policy` | `VALUE` | `i.h.t.o.O.CustomMethods` |   | Retry policy |
| <span id="ac1713-timeout"></span> `timeout` | `VALUE` | `Duration` |   | Exporter timeout |
| <span id="a2a158-type"></span> [`type`](../config/io_helidon_telemetry_otelconfig_LogExporterType.md) | `VALUE` | `i.h.t.o.LogExporterType` | `DEFAULT` | Logger exporter type |

See the [manifest](../config/manifest.md) for all available types.
