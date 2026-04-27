# io.helidon.tracing.providers.jaeger.JaegerTracerBuilder

## Description

Jaeger tracer configuration

## Configuration options

<style>
    table.cm-table code {
        white-space: nowrap !important;
    }

    table.cm-table .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


<table class="cm-table">
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
<a id="propagation"></a>
<a href="io.helidon.tracing.providers.jaeger.JaegerTracerBuilder.PropagationFormat.md">
<code>propagation</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;PropagationFormat&gt;">List&lt;PropagationFormat&gt;</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">JAEGER</code>
</td>
<td>Add propagation format to use</td>
</tr>
<tr>
<td>
<code>sampler-param</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Number</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">1</code>
</td>
<td>The sampler parameter (number)</td>
</tr>
<tr>
<td>
<code>schedule-delay</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">PT5S</code>
</td>
<td>Schedule Delay of exporter requests</td>
</tr>
<tr>
<td>
<a id="span-processor-type"></a>
<a href="io.helidon.tracing.providers.jaeger.JaegerTracerBuilder.SpanProcessorType.md">
<code>span-processor-type</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="SpanProcessorType">SpanProcessorType</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">batch</code>
</td>
<td>Span Processor type used</td>
</tr>
<tr>
<td>
<code>max-export-batch-size</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">512</code>
</td>
<td>Maximum Export Batch Size of exporter requests</td>
</tr>
<tr>
<td>
<a id="trusted-cert-pem"></a>
<a href="io.helidon.common.configurable.Resource.md">
<code>trusted-cert-pem</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Resource</code>
</td>
<td class="cm-default-cell">
</td>
<td>Trusted certificates in PEM format</td>
</tr>
<tr>
<td>
<code>exporter-timeout</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">PT10S</code>
</td>
<td>Timeout of exporter requests</td>
</tr>
<tr>
<td>
<a id="sampler-type"></a>
<a href="io.helidon.tracing.providers.jaeger.JaegerTracerBuilder.SamplerType.md">
<code>sampler-type</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="SamplerType">SamplerType</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">CONSTANT</code>
</td>
<td>Sampler type</td>
</tr>
<tr>
<td>
<a id="private-key-pem"></a>
<a href="io.helidon.common.configurable.Resource.md">
<code>private-key-pem</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Resource</code>
</td>
<td class="cm-default-cell">
</td>
<td>Private key in PEM format</td>
</tr>
<tr>
<td>
<a id="client-cert-pem"></a>
<a href="io.helidon.common.configurable.Resource.md">
<code>client-cert-pem</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Resource</code>
</td>
<td class="cm-default-cell">
</td>
<td>Certificate of client in PEM format</td>
</tr>
<tr>
<td>
<code>max-queue-size</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">2048</code>
</td>
<td>Maximum Queue Size of exporter requests</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
