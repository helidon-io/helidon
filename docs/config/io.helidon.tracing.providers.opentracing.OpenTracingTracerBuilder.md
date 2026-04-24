# io.helidon.tracing.providers.opentracing.OpenTracingTracerBuilder

## Description

OpenTracing tracer configuration

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
<code>path</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Path on the collector host to use when sending data to tracing collector</td>
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
<td>Protocol to use (such as <code>http</code> or <code>https</code>) to connect to tracing collector</td>
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
<td>Tracer level tags that get added to all reported spans</td>
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
<td>Port to use to connect to tracing collector</td>
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
<td>Host to use to connect to tracing collector</td>
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
<td>When enabled, the created instance is also registered as a global tracer</td>
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
<td>Tracer level tags that get added to all reported spans</td>
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
<td>When enabled, tracing will be sent</td>
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
<td>Tracer level tags that get added to all reported spans</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
