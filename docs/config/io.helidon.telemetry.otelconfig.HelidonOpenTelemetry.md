# io.<wbr>helidon.<wbr>telemetry.<wbr>otelconfig.<wbr>Helidon<wbr>Open<wbr>Telemetry

## Description

OpenTelemetry settings

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
<code>enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether the OpenTelemetry support is enabled</td>
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
<td>Whether the <code>io.<wbr>opentelemetry.<wbr>api.<wbr>Open<wbr>Telemetry</code> instance created from this configuration should be made the global one</td>
</tr>
<tr>
<td>
<code>propagators</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
</td>
<td>OpenTelemetry <code>io.<wbr>opentelemetry.<wbr>context.<wbr>propagation.<wbr>Text<wbr>MapPropagator</code> instances added explicitly by the app</td>
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
<td>Service name used in sending telemetry data to the collector</td>
</tr>
<tr>
<td>
<a id="signals"></a>
<a href="io.helidon.telemetry.SignalsConfig.md">
<code>signals</code>
</a>
</td>
<td>
</td>
<td>
</td>
<td>Configuration for signals</td>
</tr>
</tbody>
</table>



## Usages

- <a href="config_reference.md#telemetry"><code>telemetry</code></a>

---

See the [manifest](manifest.md) for all available types.
