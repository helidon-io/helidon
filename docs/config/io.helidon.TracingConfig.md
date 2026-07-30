# io.<wbr>helidon.<wbr>Tracing<wbr>Config

## Description

Merged configuration for tracing

## Configuration options


<table>
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Default</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<a id="api-version"></a>
<a href="io.helidon.tracing.providers.zipkin.ZipkinTracerBuilder.Version.md">
<code>api-<wbr>version</code>
</a>
</td>
<td>
<code>Version</code>
</td>
<td>
<code>V2</code>
</td>
<td>Version of Zipkin API to use</td>
</tr>
<tr>
<td>
<code>boolean-<wbr>tags</code>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> Boolean&gt;</code>
</td>
<td>
</td>
<td>Tracer-level tags with boolean values added to all reported spans</td>
</tr>
<tr>
<td>
<a id="client-cert-pem"></a>
<a href="io.helidon.common.configurable.Resource.md">
<code>client-<wbr>cert-<wbr>pem</code>
</a>
</td>
<td>
<code>Resource</code>
</td>
<td>
</td>
<td>Certificate of client in PEM format</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to enable tracing</td>
</tr>
<tr>
<td>
<code>export-<wbr>timeout</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT10S</code>
</td>
<td>Maximum time a transmission can be in progress before being cancelled</td>
</tr>
<tr>
<td>
<code>exporter-<wbr>timeout</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT10S</code>
</td>
<td>Timeout of exporter requests</td>
</tr>
<tr>
<td>
<a id="exporter-type"></a>
<a href="io.helidon.tracing.providers.opentelemetry.OtlpExporterProtocolType.md">
<code>exporter-<wbr>type</code>
</a>
</td>
<td>
<code>Otlp<wbr>Exporter<wbr>Protocol<wbr>Type</code>
</td>
<td>
<code>GRPC</code>
</td>
<td>Type of OTLP exporter to use for pushing span data</td>
</tr>
<tr>
<td>
<code>global</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to create and register a tracer as the global tracer</td>
</tr>
<tr>
<td>
<code>host</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Host used in connecting to the tracing collector</td>
</tr>
<tr>
<td>
<code>int-<wbr>tags</code>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> Integer&gt;</code>
</td>
<td>
</td>
<td>Tracer level tags with integer values added to all reported spans</td>
</tr>
<tr>
<td>
<code>max-<wbr>export-<wbr>batch-<wbr>size</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>512</code>
</td>
<td>Maximum Export Batch Size of exporter requests</td>
</tr>
<tr>
<td>
<code>max-<wbr>queue-<wbr>size</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>2048</code>
</td>
<td>Maximum Queue Size of exporter requests</td>
</tr>
<tr>
<td>
<code>path</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Path at the collector host and port used when sending trace data to the collector</td>
</tr>
<tr>
<td>
<code>port</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
</td>
<td>Port used in connecting to the tracing collector</td>
</tr>
<tr>
<td>
<a id="private-key-pem"></a>
<a href="io.helidon.common.configurable.Resource.md">
<code>private-<wbr>key-<wbr>pem</code>
</a>
</td>
<td>
<code>Resource</code>
</td>
<td>
</td>
<td>Private key in PEM format</td>
</tr>
<tr>
<td>
<a id="propagation"></a>
<a href="io.helidon.tracing.providers.jaeger.JaegerTracerBuilder.PropagationFormat.md">
<code>propagation</code>
</a>
</td>
<td>
<code>List&lt;<wbr>Propagation<wbr>Format&gt;</code>
</td>
<td>
<code>JAEGER</code>
</td>
<td>Add propagation format to use</td>
</tr>
<tr>
<td>
<code>propagators</code>
</td>
<td>
<code>List&lt;<wbr>Custom<wbr>Methods&gt;</code>
</td>
<td>
</td>
<td>Context propagators</td>
</tr>
<tr>
<td>
<code>protocol</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Protocol (such as <code>http</code> or <code>https</code>) used in connecting to the tracing collector</td>
</tr>
<tr>
<td>
<code>sampler-<wbr>param</code>
</td>
<td>
<code>Number</code>
</td>
<td>
<code>1</code>
</td>
<td>The sampler parameter (number)</td>
</tr>
<tr>
<td>
<a id="sampler-type"></a>
<a href="io.helidon.tracing.providers.jaeger.JaegerTracerBuilder.SamplerType.md">
<code>sampler-<wbr>type</code>
</a>
</td>
<td>
<code>Sampler<wbr>Type</code>
</td>
<td>
<code>CONSTANT</code>
</td>
<td>Sampler type</td>
</tr>
<tr>
<td>
<code>schedule-<wbr>delay</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT5S</code>
</td>
<td>Schedule Delay of exporter requests</td>
</tr>
<tr>
<td>
<code>service</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Service name of the traced service</td>
</tr>
<tr>
<td>
<a id="span-processor-type"></a>
<a href="io.helidon.tracing.providers.jaeger.JaegerTracerBuilder.SpanProcessorType.md">
<code>span-<wbr>processor-<wbr>type</code>
</a>
</td>
<td>
<code>Span<wbr>Processor<wbr>Type</code>
</td>
<td>
<code>batch</code>
</td>
<td>Span Processor type used</td>
</tr>
<tr>
<td>
<code>tags</code>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> String&gt;</code>
</td>
<td>
</td>
<td>Tracer-level tags with <code>String</code> values added to all reported spans</td>
</tr>
<tr>
<td>
<a id="trusted-cert-pem"></a>
<a href="io.helidon.common.configurable.Resource.md">
<code>trusted-<wbr>cert-<wbr>pem</code>
</a>
</td>
<td>
<code>Resource</code>
</td>
<td>
</td>
<td>Trusted certificates in PEM format</td>
</tr>
<tr>
<td>
<code>uri</code>
</td>
<td>
<code>URI</code>
</td>
<td>
</td>
<td>URI for the collector to which to send tracing data</td>
</tr>
</tbody>
</table>



## Merged Types

- [io.<wbr>helidon.<wbr>tracing.<wbr>providers.<wbr>jaeger.<wbr>Jaeger<wbr>Tracer<wbr>Builder](io.helidon.tracing.providers.jaeger.JaegerTracerBuilder.md)
- [io.<wbr>helidon.<wbr>tracing.<wbr>providers.<wbr>opentelemetry.<wbr>Open<wbr>Telemetry<wbr>Tracer](io.helidon.tracing.providers.opentelemetry.OpenTelemetryTracer.md)
- [io.<wbr>helidon.<wbr>tracing.<wbr>providers.<wbr>zipkin.<wbr>Zipkin<wbr>Tracer<wbr>Builder](io.helidon.tracing.providers.zipkin.ZipkinTracerBuilder.md)

## Usages

- <a href="config_reference.md#tracing"><code>tracing</code></a>

---

See the [manifest](manifest.md) for all available types.
