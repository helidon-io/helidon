# io.helidon.tracing.providers.opentelemetry.OpenTelemetryTracer

## Description

Settings for OpenTelemetry tracer configuration under the

OpenTelemetryTracerConfig#TRACING_CONFIG_KEY

config key.

## Usages

- [`tracing`](../config/config_reference.md#ace1ff-tracing)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="aec0bf-exporter-type"></span> [`exporter-type`](../config/io_helidon_tracing_providers_opentelemetry_OtlpExporterProtocolType.md) | `VALUE` | `i.h.t.p.o.OtlpExporterProtocolType` | `GRPC` | Type of OTLP exporter to use for pushing span data |
| <span id="a0fe59-propagators"></span> `propagators` | `LIST` | `i.h.t.p.o.O.CustomMethods` |   | Context propagators |

See the [manifest](../config/manifest.md) for all available types.
