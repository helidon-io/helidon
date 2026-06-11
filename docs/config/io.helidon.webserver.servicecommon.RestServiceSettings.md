# io.helidon.webserver.servicecommon.RestServiceSettings

## Description

Common settings across REST services

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
<code>routing</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Sets the routing name to use for setting up the service's endpoint</td>
</tr>
<tr>
<td>
<code>web-<wbr>context</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Sets the web context to use for the service's endpoint</td>
</tr>
<tr>
<td>
<a id="cors"></a>
<a href="io.helidon.cors.CrossOriginConfig.md">
<code>cors</code>
</a>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> Cross<wbr>Origin<wbr>Config&gt;</code>
</td>
<td>
</td>
<td>Sets the cross-origin config builder for use in establishing CORS support for the service endpoints</td>
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
<td>Is this service enabled or not</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
