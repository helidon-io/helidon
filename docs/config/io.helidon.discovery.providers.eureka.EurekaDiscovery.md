# io.helidon.discovery.providers.eureka.EurekaDiscovery

## Description

Prototypical state for &lt;code&gt;EurekaDiscovery&lt;/code&gt; instances

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }
</style>

<table>
<thead>
<tr>
<th>Key</th><th>Type</th><th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td><a id="cache"></a><a href="io.helidon.discovery.providers.eureka.CacheConfig.md"><code>cache</code></a></td>
<td><code>CacheConfig</code></td>
<td>The &lt;code&gt;CacheConfig&lt;/code&gt; to use controlling how a local cache of Eureka server information is used</td>
</tr>
<tr>
<td><a id="client"></a><a href="io.helidon.webclient.http1.Http1Client.md"><code>client</code></a></td>
<td><code>Http1Client</code></td>
<td>The &lt;code&gt;Http1Client&lt;/code&gt; to use to communicate with the Eureka server</td>
</tr>
<tr>
<td><code>prefer-ip-address</code></td>
<td><code>Boolean</code></td>
<td>Whether the &lt;dfn&gt;host&lt;/dfn&gt; component of any &lt;code&gt;java.net.URI URI&lt;/code&gt; should be set to the IP address stored by Eureka, or the hostname; &lt;code&gt;false&lt;/code&gt; by default</td>
</tr>
</tbody>
</table>


---

See the [manifest](manifest.md) for all available types.
