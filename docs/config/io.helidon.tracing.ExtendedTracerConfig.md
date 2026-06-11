# io.helidon.tracing.ExtendedTracerConfig

## Description

Common settings for tracers including settings for span processors and secure client connections

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
<code>sampler-<wbr>param</code>
</td>
<td>
<code>Double</code>
</td>
<td>
<code>1.<wbr>0</code>
</td>
<td>Parameter value used by the selected sampler; interpretation depends on the sampler type</td>
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
<a id="span-processor-type"></a>
<a href="io.helidon.tracing.SpanProcessorType.md">
<code>span-<wbr>processor-<wbr>type</code>
</a>
</td>
<td>
<code>Span<wbr>Processor<wbr>Type</code>
</td>
<td>
<code>BATCH</code>
</td>
<td>Type of span processor for accumulating spans before transmission to the tracing collector</td>
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
<td>Trusted certificates for connecting to the tracing collector</td>
</tr>
<tr>
<td>
<a id="sampler-type"></a>
<a href="io.helidon.tracing.SamplerType.md">
<code>sampler-<wbr>type</code>
</a>
</td>
<td>
<code>Sampler<wbr>Type</code>
</td>
<td>
<code>CONSTANT</code>
</td>
<td>Type of sampler for collecting spans</td>
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
<td>Client certificate for connecting securely to the tracing collector</td>
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
<code>schedule-<wbr>delay</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT5S</code>
</td>
<td>Delay between consecutive transmissions to the tracing collector (batch processing)</td>
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
<td>Maximum number of spans grouped for transmission together; typically does not exceed <code>#max<wbr>Queue<wbr>Size(<wbr>)</code> (batch processing)</td>
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
<td>Private key for connecting securely to the tracing collector</td>
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
<code>max-<wbr>queue-<wbr>size</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>2048</code>
</td>
<td>Maximum number of spans retained before discarding any not sent to the tracing collector (batch processing)</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
