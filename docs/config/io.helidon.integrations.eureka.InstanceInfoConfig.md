# io.helidon.integrations.eureka.InstanceInfoConfig

## Description

A &lt;code&gt;Prototype.Api prototype&lt;/code&gt; describing initial Eureka Server service instance registration details

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
<td><code>appGroup</code></td>
<td><code>String</code></td>
<td><code>unknown</code></td>
<td>The app group name</td>
</tr>
<tr>
<td><code>asgName</code></td>
<td><code>String</code></td>
<td></td>
<td>The ASG name</td>
</tr>
<tr>
<td><code>healthCheckUrl</code></td>
<td><code>URI</code></td>
<td></td>
<td>The health check URL</td>
</tr>
<tr>
<td><code>healthCheckUrlPath</code></td>
<td><code>String</code></td>
<td></td>
<td>The health check URL path (used if any health check URL is not explicitly set)</td>
</tr>
<tr>
<td><code>homePageUrl</code></td>
<td><code>URI</code></td>
<td></td>
<td>The home page URL</td>
</tr>
<tr>
<td><code>homePageUrlPath</code></td>
<td><code>String</code></td>
<td><code>/</code></td>
<td>The home page URL path (used if the homepage URL is not explicitly set)</td>
</tr>
<tr>
<td><code>hostName</code></td>
<td><code>String</code></td>
<td></td>
<td>The hostname</td>
</tr>
<tr>
<td><code>instanceId</code></td>
<td><code>String</code></td>
<td></td>
<td>The instance id</td>
</tr>
<tr>
<td><code>ipAddr</code></td>
<td><code>String</code></td>
<td></td>
<td>The IP address</td>
</tr>
<tr>
<td><a id="lease"></a><a href="io.helidon.integrations.eureka.LeaseInfoConfig.md"><code>lease</code></a></td>
<td><code>LeaseInfoConfig</code></td>
<td></td>
<td>The &lt;code&gt;LeaseInfoConfig&lt;/code&gt;</td>
</tr>
<tr>
<td><code>metadata</code></td>
<td><code>Map&lt;String, String&gt;</code></td>
<td></td>
<td>Metadata</td>
</tr>
<tr>
<td><code>name</code></td>
<td><code>String</code></td>
<td><code>unknown</code></td>
<td>The app name</td>
</tr>
<tr>
<td><a id="port"></a><a href="io.helidon.integrations.eureka.PortInfoConfig.md"><code>port</code></a></td>
<td><code>PortInfoConfig</code></td>
<td></td>
<td>(Non-secure) port information</td>
</tr>
<tr>
<td><code>secureHealthCheckUrl</code></td>
<td><code>URI</code></td>
<td></td>
<td>The secure health check URL</td>
</tr>
<tr>
<td><a id="securePort"></a><a href="io.helidon.integrations.eureka.PortInfoConfig.md"><code>securePort</code></a></td>
<td><code>PortInfoConfig</code></td>
<td></td>
<td>Secure port information</td>
</tr>
<tr>
<td><code>secureVipAddress</code></td>
<td><code>String</code></td>
<td></td>
<td>The secure VIP address</td>
</tr>
<tr>
<td><code>statusPageUrl</code></td>
<td><code>URI</code></td>
<td></td>
<td>The status page URL</td>
</tr>
<tr>
<td><code>statusPageUrlPath</code></td>
<td><code>String</code></td>
<td><code>/Status</code></td>
<td>The status page URL path (used if status page URL is not explicitly set)</td>
</tr>
<tr>
<td><a id="traffic"></a><a href="io.helidon.server.features.eureka.instance.TrafficConfig.md"><code>traffic</code></a></td>
<td></td>
<td></td>
<td>Configuration for traffic</td>
</tr>
<tr>
<td><code>vipAddress</code></td>
<td><code>String</code></td>
<td></td>
<td>The VIP address</td>
</tr>
</tbody>
</table>


## Usages

- [`server.features.eureka.instance`](io.helidon.integrations.eureka.EurekaRegistrationServerFeature.md#instance)

---

See the [manifest](manifest.md) for all available types.
