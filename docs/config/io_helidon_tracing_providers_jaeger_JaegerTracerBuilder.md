# io.helidon.tracing.providers.jaeger.JaegerTracerBuilder

## Description

Jaeger tracer configuration.

## Usages

- [`tracing`](../config/config_reference.md#a56f94-tracing)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="aed342-client-cert-pem"></span> [`client-cert-pem`](../config/io_helidon_common_configurable_Resource.md) | `VALUE` | `i.h.c.c.Resource` |   | Certificate of client in PEM format |
| <span id="a097b8-exporter-timeout"></span> `exporter-timeout` | `VALUE` | `Duration` | `PT10S` | Timeout of exporter requests |
| <span id="a24a43-max-export-batch-size"></span> `max-export-batch-size` | `VALUE` | `Integer` | `512` | Maximum Export Batch Size of exporter requests |
| <span id="ade69e-max-queue-size"></span> `max-queue-size` | `VALUE` | `Integer` | `2048` | Maximum Queue Size of exporter requests |
| <span id="a5d885-private-key-pem"></span> [`private-key-pem`](../config/io_helidon_common_configurable_Resource.md) | `VALUE` | `i.h.c.c.Resource` |   | Private key in PEM format |
| <span id="a65431-propagation"></span> [`propagation`](../config/io_helidon_tracing_providers_jaeger_JaegerTracerBuilder_PropagationFormat.md) | `LIST` | `i.h.t.p.j.J.PropagationFormat` | `JAEGER` | Add propagation format to use |
| <span id="aa4177-sampler-param"></span> `sampler-param` | `VALUE` | `Number` | `1` | The sampler parameter (number) |
| <span id="a28b35-sampler-type"></span> [`sampler-type`](../config/io_helidon_tracing_providers_jaeger_JaegerTracerBuilder_SamplerType.md) | `VALUE` | `i.h.t.p.j.J.SamplerType` | `CONSTANT` | Sampler type |
| <span id="a8ee29-schedule-delay"></span> `schedule-delay` | `VALUE` | `Duration` | `PT5S` | Schedule Delay of exporter requests |
| <span id="a1bdae-span-processor-type"></span> [`span-processor-type`](../config/io_helidon_tracing_providers_jaeger_JaegerTracerBuilder_SpanProcessorType.md) | `VALUE` | `i.h.t.p.j.J.SpanProcessorType` | `batch` | Span Processor type used |
| <span id="a667f5-trusted-cert-pem"></span> [`trusted-cert-pem`](../config/io_helidon_common_configurable_Resource.md) | `VALUE` | `i.h.c.c.Resource` |   | Trusted certificates in PEM format |

See the [manifest](../config/manifest.md) for all available types.
