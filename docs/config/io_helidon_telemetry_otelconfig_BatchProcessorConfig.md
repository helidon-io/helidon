# io.helidon.telemetry.otelconfig.BatchProcessorConfig

## Description

Configuration for a batch processor.

## Usages

## Configuration options

| Key | Kind | Type | Description |
|----|----|----|----|
| <span id="a3fd48-exporters"></span> `exporters` | `LIST` | `String` | Name(s) of the exporter(s) this processor should use; specifying no names uses all configured exporters (or if no exporters are configured, the default OpenTelemetry exporter(s)) |
| <span id="a945cc-max-export-batch-size"></span> `max-export-batch-size` | `VALUE` | `Integer` | Maximum number of items batched for export together |
| <span id="abb28b-max-queue-size"></span> `max-queue-size` | `VALUE` | `Integer` | Maximum number of items retained before discarding excess unexported ones |
| <span id="a9794f-schedule-delay"></span> `schedule-delay` | `VALUE` | `Duration` | Delay between consecutive exports |
| <span id="a3709d-timeout"></span> `timeout` | `VALUE` | `Duration` | Maximum time an export can run before being cancelled |
| <span id="a0ebee-type"></span> [`type`](../config/io_helidon_telemetry_otelconfig_ProcessorType.md) | `VALUE` | `i.h.t.o.ProcessorType` | Processor type |

See the [manifest](../config/manifest.md) for all available types.
