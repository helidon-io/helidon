# io.helidon.tracing.ExtendedTracerConfig

## Description

Common settings for tracers including settings for span processors and secure client connections.

## Usages

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="af4eea-boolean-tags"></span> `boolean-tags` | `MAP` | `Boolean` |   | Tracer-level tags with boolean values added to all reported spans |
| <span id="a06ff0-client-cert-pem"></span> [`client-cert-pem`](../config/io_helidon_common_configurable_Resource.md) | `VALUE` | `i.h.c.c.Resource` |   | Client certificate for connecting securely to the tracing collector |
| <span id="a3b608-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Whether to enable tracing |
| <span id="a9f351-export-timeout"></span> `export-timeout` | `VALUE` | `Duration` | `PT10S` | Maximum time a transmission can be in progress before being cancelled |
| <span id="ac87f1-global"></span> `global` | `VALUE` | `Boolean` | `true` | Whether to create and register a tracer as the global tracer |
| <span id="a5be91-host"></span> `host` | `VALUE` | `String` |   | Host used in connecting to the tracing collector |
| <span id="a4a16b-int-tags"></span> `int-tags` | `MAP` | `Integer` |   | Tracer level tags with integer values added to all reported spans |
| <span id="a90220-max-export-batch-size"></span> `max-export-batch-size` | `VALUE` | `Integer` | `512` | Maximum number of spans grouped for transmission together; typically does not exceed `#maxQueueSize()` (batch processing) |
| <span id="a964fb-max-queue-size"></span> `max-queue-size` | `VALUE` | `Integer` | `2048` | Maximum number of spans retained before discarding any not sent to the tracing collector (batch processing) |
| <span id="a68b2a-path"></span> `path` | `VALUE` | `String` |   | Path at the collector host and port used when sending trace data to the collector |
| <span id="aedcc8-port"></span> `port` | `VALUE` | `Integer` |   | Port used in connecting to the tracing collector |
| <span id="a9228e-private-key-pem"></span> [`private-key-pem`](../config/io_helidon_common_configurable_Resource.md) | `VALUE` | `i.h.c.c.Resource` |   | Private key for connecting securely to the tracing collector |
| <span id="a2b780-protocol"></span> `protocol` | `VALUE` | `String` |   | Protocol (such as `http` or `https`) used in connecting to the tracing collector |
| <span id="af2924-sampler-param"></span> `sampler-param` | `VALUE` | `Double` | `1.0` | Parameter value used by the selected sampler; interpretation depends on the sampler type |
| <span id="a23803-sampler-type"></span> [`sampler-type`](../config/io_helidon_tracing_SamplerType.md) | `VALUE` | `i.h.t.SamplerType` | `CONSTANT` | Type of sampler for collecting spans |
| <span id="a4383c-schedule-delay"></span> `schedule-delay` | `VALUE` | `Duration` | `PT5S` | Delay between consecutive transmissions to the tracing collector (batch processing) |
| <span id="a5b04a-service"></span> `service` | `VALUE` | `String` |   | Service name of the traced service |
| <span id="a501dc-span-processor-type"></span> [`span-processor-type`](../config/io_helidon_tracing_SpanProcessorType.md) | `VALUE` | `i.h.t.SpanProcessorType` | `BATCH` | Type of span processor for accumulating spans before transmission to the tracing collector |
| <span id="a892f2-tags"></span> `tags` | `MAP` | `String` |   | Tracer-level tags with `String` values added to all reported spans |
| <span id="a38973-trusted-cert-pem"></span> [`trusted-cert-pem`](../config/io_helidon_common_configurable_Resource.md) | `VALUE` | `i.h.c.c.Resource` |   | Trusted certificates for connecting to the tracing collector |
| <span id="a90d64-uri"></span> `uri` | `VALUE` | `URI` |   | URI for the collector to which to send tracing data |

See the [manifest](../config/manifest.md) for all available types.
