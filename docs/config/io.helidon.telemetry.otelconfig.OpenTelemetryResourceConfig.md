# io.<wbr>helidon.<wbr>telemetry.<wbr>otelconfig.<wbr>Open<wbr>Telemetry<wbr>Resource<wbr>Config

## Description

OpenTelemetry resource settings shared by all configured signals

## Configuration options


<table>
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>container-<wbr>id</code>
</td>
<td>
<code>String</code>
</td>
<td>Identifier of the container in which the service instance is running</td>
</tr>
<tr>
<td>
<code>service-<wbr>namespace</code>
</td>
<td>
<code>String</code>
</td>
<td>Namespace for the service</td>
</tr>
<tr>
<td>
<code>container-<wbr>image-<wbr>repo-<wbr>digests</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>Repository digests of the container image as reported by the container runtime</td>
</tr>
<tr>
<td>
<code>container-<wbr>image-<wbr>name</code>
</td>
<td>
<code>String</code>
</td>
<td>Name of the container image on which the container was built</td>
</tr>
<tr>
<td>
<code>service-<wbr>instance-<wbr>id</code>
</td>
<td>
<code>String</code>
</td>
<td>Identifier for this service instance</td>
</tr>
<tr>
<td>
<code>container-<wbr>image-<wbr>tags</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>Tags of the container image on which the container was built</td>
</tr>
<tr>
<td>
<code>attributes</code>
</td>
<td>
<code>Custom<wbr>Methods</code>
</td>
<td>Additional resource attributes shared by all configured signals</td>
</tr>
<tr>
<td>
<code>deployment-<wbr>environment-<wbr>name</code>
</td>
<td>
<code>String</code>
</td>
<td>Name of the deployment environment</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.telemetry.otelconfig.HelidonOpenTelemetry.md#resource"><code>telemetry.<wbr>resource</code></a>

---

See the [manifest](manifest.md) for all available types.
