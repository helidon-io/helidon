# io.helidon.integrations.eureka.EurekaRegistrationServerFeature

## Description

A <code>Prototype.<wbr>Api prototype</code> for <code>Eureka<wbr>Registration<wbr>Server<wbr>Feature</code> <code>io.<wbr>helidon.<wbr>builder.<wbr>api.<wbr>Runtime<wbr>Type.<wbr>Api runtime type</code> instances

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
<a id="instance"></a>
<a href="io.helidon.integrations.eureka.InstanceInfoConfig.md">
<code>instance</code>
</a>
</td>
<td>
<code>Instance<wbr>Info<wbr>Config</code>
</td>
<td>
</td>
<td>An <code>Instance<wbr>Info<wbr>Config</code> describing the service instance to be registered</td>
</tr>
<tr>
<td>
<code>weight</code>
</td>
<td>
<code>Double</code>
</td>
<td>
<code>100.<wbr>0</code>
</td>
<td>The (zero or positive) <code>io.<wbr>helidon.<wbr>common.<wbr>Weighted weight</code> of this instance</td>
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
<td>Whether this feature will be enabled</td>
</tr>
</tbody>
</table>



## Usages

- [`server.features.eureka`](io.helidon.webserver.spi.ServerFeature.md#eureka)

---

See the [manifest](manifest.md) for all available types.
