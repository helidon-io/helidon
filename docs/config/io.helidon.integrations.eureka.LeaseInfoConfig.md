# io.helidon.integrations.eureka.LeaseInfoConfig

## Description

A &lt;code&gt;Prototype.Api prototype&lt;/code&gt; describing initial Eureka Server service instance registration lease details

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
<td><code>duration</code></td>
<td><code>Integer</code></td>
<td><code>90</code></td>
<td>The lease duration in seconds; the default value is strongly recommended</td>
</tr>
<tr>
<td><code>renewalInterval</code></td>
<td><code>Integer</code></td>
<td><code>30</code></td>
<td>The lease renewal interval in seconds; the default value is strongly recommended</td>
</tr>
</tbody>
</table>


## Usages

- [`server.features.eureka.instance.lease`](io.helidon.integrations.eureka.InstanceInfoConfig.md#lease)

---

See the [manifest](manifest.md) for all available types.
