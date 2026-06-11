# io.helidon.discovery.providers.eureka.EurekaDiscovery

## Description

Prototypical state for <code>Eureka<wbr>Discovery</code> instances

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
<a id="cache"></a>
<a href="io.helidon.discovery.providers.eureka.CacheConfig.md">
<code>cache</code>
</a>
</td>
<td>
<code>Cache<wbr>Config</code>
</td>
<td>The <code>Cache<wbr>Config</code> to use controlling how a local cache of Eureka server information is used</td>
</tr>
<tr>
<td>
<a id="client"></a>
<a href="io.helidon.webclient.http1.Http1Client.md">
<code>client</code>
</a>
</td>
<td>
<code>Http1Client</code>
</td>
<td>The <code>Http1Client</code> to use to communicate with the Eureka server</td>
</tr>
<tr>
<td>
<code>prefer-<wbr>ip-address</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>Whether the <dfn>host</dfn> component of any <code>java.<wbr>net.<wbr>URI URI</code> should be set to the IP address stored by Eureka, or the hostname; <code>false</code> by default</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
