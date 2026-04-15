# io.helidon.integrations.eureka.EurekaRegistrationServerFeature

## Description

A &lt;code&gt;Prototype.Api prototype&lt;/code&gt; for &lt;code&gt;EurekaRegistrationServerFeature&lt;/code&gt; &lt;code&gt;io.helidon.builder.api.RuntimeType.Api runtime type&lt;/code&gt; instances

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
<td><a id="instance"></a><a href="io.helidon.integrations.eureka.InstanceInfoConfig.md"><code>instance</code></a></td>
<td><code>InstanceInfoConfig</code></td>
<td></td>
<td>An &lt;code&gt;InstanceInfoConfig&lt;/code&gt; describing the service instance to be registered</td>
</tr>
<tr>
<td><code>weight</code></td>
<td><code>Double</code></td>
<td><code>100.0</code></td>
<td>The (zero or positive) &lt;code&gt;io.helidon.common.Weighted weight&lt;/code&gt; of this instance</td>
</tr>
<tr>
<td><code>enabled</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether this feature will be enabled</td>
</tr>
</tbody>
</table>


## Usages

- [`server.features.eureka`](io.helidon.webserver.spi.ServerFeature.md#eureka)

---

See the [manifest](manifest.md) for all available types.
