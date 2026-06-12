# io.helidon.integrations.eureka.InstanceInfoConfig

## Description

A <code>Prototype.<wbr>Api prototype</code> describing initial Eureka Server service instance registration details

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
<code>app<wbr>Group</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>unknown</code>
</td>
<td>The app group name</td>
</tr>
<tr>
<td>
<code>asg<wbr>Name</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>The ASG name</td>
</tr>
<tr>
<td>
<code>health<wbr>Check<wbr>Url</code>
</td>
<td>
<code>URI</code>
</td>
<td>
</td>
<td>The health check URL</td>
</tr>
<tr>
<td>
<code>health<wbr>Check<wbr>UrlPath</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>The health check URL path (used if any health check URL is not explicitly set)</td>
</tr>
<tr>
<td>
<code>home<wbr>Page<wbr>Url</code>
</td>
<td>
<code>URI</code>
</td>
<td>
</td>
<td>The home page URL</td>
</tr>
<tr>
<td>
<code>home<wbr>Page<wbr>UrlPath</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>/</code>
</td>
<td>The home page URL path (used if the homepage URL is not explicitly set)</td>
</tr>
<tr>
<td>
<code>host<wbr>Name</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>The hostname</td>
</tr>
<tr>
<td>
<code>instance<wbr>Id</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>The instance id</td>
</tr>
<tr>
<td>
<code>ip<wbr>Addr</code>
</td>
<td>
<code>String</code>
</td>
<td>
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
<td>
<code>Lease<wbr>Info<wbr>Config</code>
</td>
<td>
</td>
<td>The <code>Lease<wbr>Info<wbr>Config</code></td>
</tr>
<tr>
<td>
<code>metadata</code>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> String&gt;</code>
</td>
<td>
</td>
<td>Metadata</td>
</tr>
<tr>
<td>
<code>name</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>unknown</code>
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
<td>
<code>Port<wbr>Info<wbr>Config</code>
</td>
<td>
</td>
<td>(Non-secure) port information</td>
</tr>
<tr>
<td>
<code>secure<wbr>Health<wbr>Check<wbr>Url</code>
</td>
<td>
<code>URI</code>
</td>
<td>
</td>
<td>The secure health check URL</td>
</tr>
<tr>
<td>
<a id="securePort"></a>
<a href="io.helidon.integrations.eureka.PortInfoConfig.md">
<code>secure<wbr>Port</code>
</a>
</td>
<td>
<code>Port<wbr>Info<wbr>Config</code>
</td>
<td>
</td>
<td>Secure port information</td>
</tr>
<tr>
<td>
<code>secure<wbr>VipAddress</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>The secure VIP address</td>
</tr>
<tr>
<td>
<code>status<wbr>Page<wbr>Url</code>
</td>
<td>
<code>URI</code>
</td>
<td>
</td>
<td>The status page URL</td>
</tr>
<tr>
<td>
<code>status<wbr>Page<wbr>UrlPath</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>&#8288;/&#8288;Status</code>
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
<td>
</td>
<td>
</td>
<td>Configuration for traffic</td>
</tr>
<tr>
<td>
<code>vip<wbr>Address</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>The VIP address</td>
</tr>
</tbody>
</table>



## Usages

- [`server.features.eureka.instance`](io.helidon.integrations.eureka.EurekaRegistrationServerFeature.md#instance)

---

See the [manifest](manifest.md) for all available types.
