# io.<wbr>helidon.<wbr>tracing.<wbr>Tracer

## Description

Tracer configuration

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
<code>path</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Path on the collector host to use when sending data to tracing collector</td>
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
<td>Protocol to use (such as <code>http</code> or <code>https</code>) to connect to tracing collector</td>
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
<td>Tracer level tags that get added to all reported spans</td>
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
<td>Port to use to connect to tracing collector</td>
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
<td>Host to use to connect to tracing collector</td>
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
<td>When enabled, the created instance is also registered as a global tracer</td>
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
<td>Tracer level tags that get added to all reported spans</td>
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
<td>When enabled, tracing will be sent</td>
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
<td>Tracer level tags that get added to all reported spans</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
