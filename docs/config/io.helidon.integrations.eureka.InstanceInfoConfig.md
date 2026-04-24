# io.helidon.integrations.eureka.InstanceInfoConfig

## Description

A <code>Prototype.Api prototype</code> describing initial Eureka Server service instance registration details

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
<code>appGroup</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">unknown</code>
</td>
<td>The app group name</td>
</tr>
<tr>
<td>
<code>asgName</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>The ASG name</td>
</tr>
<tr>
<td>
<code>healthCheckUrl</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">URI</code>
</td>
<td class="cm-default-cell">
</td>
<td>The health check URL</td>
</tr>
<tr>
<td>
<code>healthCheckUrlPath</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>The health check URL path (used if any health check URL is not explicitly set)</td>
</tr>
<tr>
<td>
<code>homePageUrl</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">URI</code>
</td>
<td class="cm-default-cell">
</td>
<td>The home page URL</td>
</tr>
<tr>
<td>
<code>homePageUrlPath</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">/</code>
</td>
<td>The home page URL path (used if the homepage URL is not explicitly set)</td>
</tr>
<tr>
<td>
<code>hostName</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>The hostname</td>
</tr>
<tr>
<td>
<code>instanceId</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>The instance id</td>
</tr>
<tr>
<td>
<code>ipAddr</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>The IP address</td>
</tr>
<tr>
<td>
<a id="lease"></a>
<a href="io.helidon.integrations.eureka.LeaseInfoConfig.md">
<code>lease</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="LeaseInfoConfig">LeaseInfoConfig</code>
</td>
<td class="cm-default-cell">
</td>
<td>The <code>LeaseInfoConfig</code></td>
</tr>
<tr>
<td>
<code>metadata</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="Map&lt;String, String&gt;">Map&lt;String, String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Metadata</td>
</tr>
<tr>
<td>
<code>name</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">unknown</code>
</td>
<td>The app name</td>
</tr>
<tr>
<td>
<a id="port"></a>
<a href="io.helidon.integrations.eureka.PortInfoConfig.md">
<code>port</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="PortInfoConfig">PortInfoConfig</code>
</td>
<td class="cm-default-cell">
</td>
<td>(Non-secure) port information</td>
</tr>
<tr>
<td>
<code>secureHealthCheckUrl</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">URI</code>
</td>
<td class="cm-default-cell">
</td>
<td>The secure health check URL</td>
</tr>
<tr>
<td>
<a id="securePort"></a>
<a href="io.helidon.integrations.eureka.PortInfoConfig.md">
<code>securePort</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="PortInfoConfig">PortInfoConfig</code>
</td>
<td class="cm-default-cell">
</td>
<td>Secure port information</td>
</tr>
<tr>
<td>
<code>secureVipAddress</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>The secure VIP address</td>
</tr>
<tr>
<td>
<code>statusPageUrl</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">URI</code>
</td>
<td class="cm-default-cell">
</td>
<td>The status page URL</td>
</tr>
<tr>
<td>
<code>statusPageUrlPath</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">/Status</code>
</td>
<td>The status page URL path (used if status page URL is not explicitly set)</td>
</tr>
<tr>
<td>
<a id="traffic"></a>
<a href="io.helidon.server.features.eureka.instance.TrafficConfig.md">
<code>traffic</code>
</a>
</td>
<td class="cm-type-cell">
</td>
<td class="cm-default-cell">
</td>
<td>Configuration for traffic</td>
</tr>
<tr>
<td>
<code>vipAddress</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>The VIP address</td>
</tr>
</tbody>
</table>



## Usages

- [`server.features.eureka.instance`](io.helidon.integrations.eureka.EurekaRegistrationServerFeature.md#instance)

---

See the [manifest](manifest.md) for all available types.
