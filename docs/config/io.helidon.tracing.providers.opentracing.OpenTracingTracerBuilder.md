# io.helidon.tracing.providers.opentracing.OpenTracingTracerBuilder

## Description

OpenTracing tracer configuration

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
<td><code>path</code></td>
<td><code>String</code></td>
<td></td>
<td>Path on the collector host to use when sending data to tracing collector</td>
</tr>
<tr>
<td><code>protocol</code></td>
<td><code>String</code></td>
<td></td>
<td>Protocol to use (such as &lt;code&gt;http&lt;/code&gt; or &lt;code&gt;https&lt;/code&gt;) to connect to tracing collector</td>
</tr>
<tr>
<td><code>boolean-tags</code></td>
<td><code>Map&lt;String, Boolean&gt;</code></td>
<td></td>
<td>Tracer level tags that get added to all reported spans</td>
</tr>
<tr>
<td><code>port</code></td>
<td><code>Integer</code></td>
<td></td>
<td>Port to use to connect to tracing collector</td>
</tr>
<tr>
<td><code>service</code></td>
<td><code>String</code></td>
<td></td>
<td>Service name of the traced service</td>
</tr>
<tr>
<td><code>host</code></td>
<td><code>String</code></td>
<td></td>
<td>Host to use to connect to tracing collector</td>
</tr>
<tr>
<td><code>global</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>When enabled, the created instance is also registered as a global tracer</td>
</tr>
<tr>
<td><code>int-tags</code></td>
<td><code>Map&lt;String, Integer&gt;</code></td>
<td></td>
<td>Tracer level tags that get added to all reported spans</td>
</tr>
<tr>
<td><code>enabled</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>When enabled, tracing will be sent</td>
</tr>
<tr>
<td><code>tags</code></td>
<td><code>Map&lt;String, String&gt;</code></td>
<td></td>
<td>Tracer level tags that get added to all reported spans</td>
</tr>
</tbody>
</table>


---

See the [manifest](manifest.md) for all available types.
