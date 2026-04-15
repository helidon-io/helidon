# io.helidon.TracingConfig

## Description

Merged configuration for tracing

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }
</style>

<table>
<thead>
<tr>
<th>Key</th><th>Type</th><th>Default Value</th><th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><a id="api-version"></a><a href="io.helidon.tracing.providers.zipkin.ZipkinTracerBuilder.Version.md"><code>api-version</code></a></td>
<td><code>Version</code></td>
<td><code>V2</code></td>
<td>Version of Zipkin API to use</td>
</tr>
<tr>
<td><a id="client-cert-pem"></a><a href="io.helidon.common.configurable.Resource.md"><code>client-cert-pem</code></a></td>
<td><code>Resource</code></td>
<td></td>
<td>Certificate of client in PEM format</td>
</tr>
<tr>
<td><code>exporter-timeout</code></td>
<td><code>Duration</code></td>
<td><code>PT10S</code></td>
<td>Timeout of exporter requests</td>
</tr>
<tr>
<td><a id="exporter-type"></a><a href="io.helidon.tracing.providers.opentelemetry.OtlpExporterProtocolType.md"><code>exporter-type</code></a></td>
<td><code>OtlpExporterProtocolType</code></td>
<td><code>GRPC</code></td>
<td>Type of OTLP exporter to use for pushing span data</td>
</tr>
<tr>
<td><code>max-export-batch-size</code></td>
<td><code>Integer</code></td>
<td><code>512</code></td>
<td>Maximum Export Batch Size of exporter requests</td>
</tr>
<tr>
<td><code>max-queue-size</code></td>
<td><code>Integer</code></td>
<td><code>2048</code></td>
<td>Maximum Queue Size of exporter requests</td>
</tr>
<tr>
<td><a id="private-key-pem"></a><a href="io.helidon.common.configurable.Resource.md"><code>private-key-pem</code></a></td>
<td><code>Resource</code></td>
<td></td>
<td>Private key in PEM format</td>
</tr>
<tr>
<td><a id="propagation"></a><a href="io.helidon.tracing.providers.jaeger.JaegerTracerBuilder.PropagationFormat.md"><code>propagation</code></a></td>
<td><code>List&lt;PropagationFormat&gt;</code></td>
<td><code>JAEGER</code></td>
<td>Add propagation format to use</td>
</tr>
<tr>
<td><code>propagators</code></td>
<td><code>List&lt;CustomMethods&gt;</code></td>
<td></td>
<td>Context propagators</td>
</tr>
<tr>
<td><code>sampler-param</code></td>
<td><code>Number</code></td>
<td><code>1</code></td>
<td>The sampler parameter (number)</td>
</tr>
<tr>
<td><a id="sampler-type"></a><a href="io.helidon.tracing.providers.jaeger.JaegerTracerBuilder.SamplerType.md"><code>sampler-type</code></a></td>
<td><code>SamplerType</code></td>
<td><code>CONSTANT</code></td>
<td>Sampler type</td>
</tr>
<tr>
<td><code>schedule-delay</code></td>
<td><code>Duration</code></td>
<td><code>PT5S</code></td>
<td>Schedule Delay of exporter requests</td>
</tr>
<tr>
<td><a id="span-processor-type"></a><a href="io.helidon.tracing.providers.jaeger.JaegerTracerBuilder.SpanProcessorType.md"><code>span-processor-type</code></a></td>
<td><code>SpanProcessorType</code></td>
<td><code>batch</code></td>
<td>Span Processor type used</td>
</tr>
<tr>
<td><a id="trusted-cert-pem"></a><a href="io.helidon.common.configurable.Resource.md"><code>trusted-cert-pem</code></a></td>
<td><code>Resource</code></td>
<td></td>
<td>Trusted certificates in PEM format</td>
</tr>
</tbody>
</table>


## Merged Types

- [io.helidon.tracing.providers.jaeger.JaegerTracerBuilder](io.helidon.tracing.providers.jaeger.JaegerTracerBuilder.md)
- [io.helidon.tracing.providers.opentelemetry.OpenTelemetryTracer](io.helidon.tracing.providers.opentelemetry.OpenTelemetryTracer.md)
- [io.helidon.tracing.providers.zipkin.ZipkinTracerBuilder](io.helidon.tracing.providers.zipkin.ZipkinTracerBuilder.md)

## Usages

- [`tracing`](config_reference.md#tracing)

---

See the [manifest](manifest.md) for all available types.
