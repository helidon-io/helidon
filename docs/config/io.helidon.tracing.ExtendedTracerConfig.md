# io.helidon.tracing.ExtendedTracerConfig

## Description

Common settings for tracers including settings for span processors and secure client connections

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }

    .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


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
<code>export-timeout</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">PT10S</code>
</td>
<td>Maximum time a transmission can be in progress before being cancelled</td>
</tr>
<tr>
<td>
<code>sampler-param</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Double</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">1.0</code>
</td>
<td>Parameter value used by the selected sampler; interpretation depends on the sampler type</td>
</tr>
<tr>
<td>
<code>boolean-tags</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="Map&lt;String, Boolean&gt;">Map&lt;String, Boolean&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Tracer-level tags with boolean values added to all reported spans</td>
</tr>
<tr>
<td>
<a id="span-processor-type"></a>
<a href="io.helidon.tracing.SpanProcessorType.md">
<code>span-processor-type</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="SpanProcessorType">SpanProcessorType</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">BATCH</code>
</td>
<td>Type of span processor for accumulating spans before transmission to the tracing collector</td>
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
<td>Trusted certificates for connecting to the tracing collector</td>
</tr>
<tr>
<td>
<a id="sampler-type"></a>
<a href="io.helidon.tracing.SamplerType.md">
<code>sampler-type</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="SamplerType">SamplerType</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">CONSTANT</code>
</td>
<td>Type of sampler for collecting spans</td>
</tr>
<tr>
<td>
<code>global</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to create and register a tracer as the global tracer</td>
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
<td>Client certificate for connecting securely to the tracing collector</td>
</tr>
<tr>
<td>
<code>uri</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">URI</code>
</td>
<td class="cm-default-cell">
</td>
<td>URI for the collector to which to send tracing data</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
</td>
<td>Whether to enable tracing</td>
</tr>
<tr>
<td>
<code>tags</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="Map&lt;String, String&gt;">Map&lt;String, String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Tracer-level tags with <code>String</code> values added to all reported spans</td>
</tr>
<tr>
<td>
<code>path</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Path at the collector host and port used when sending trace data to the collector</td>
</tr>
<tr>
<td>
<code>protocol</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Protocol (such as <code>http</code> or <code>https</code>) used in connecting to the tracing collector</td>
</tr>
<tr>
<td>
<code>port</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
</td>
<td>Port used in connecting to the tracing collector</td>
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
<td>Delay between consecutive transmissions to the tracing collector (batch processing)</td>
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
<td>Maximum number of spans grouped for transmission together; typically does not exceed <code>#maxQueueSize()</code> (batch processing)</td>
</tr>
<tr>
<td>
<code>service</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Service name of the traced service</td>
</tr>
<tr>
<td>
<code>host</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Host used in connecting to the tracing collector</td>
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
<td>Private key for connecting securely to the tracing collector</td>
</tr>
<tr>
<td>
<code>int-tags</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="Map&lt;String, Integer&gt;">Map&lt;String, Integer&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Tracer level tags with integer values added to all reported spans</td>
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
<td>Maximum number of spans retained before discarding any not sent to the tracing collector (batch processing)</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
