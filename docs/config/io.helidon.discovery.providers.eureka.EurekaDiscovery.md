# io.helidon.discovery.providers.eureka.EurekaDiscovery

## Description

Prototypical state for <code>EurekaDiscovery</code> instances

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
<td class="cm-type-cell">
<code class="cm-truncate-value" title="CacheConfig">CacheConfig</code>
</td>
<td>The <code>CacheConfig</code> to use controlling how a local cache of Eureka server information is used</td>
</tr>
<tr>
<td>
<a id="client"></a>
<a href="io.helidon.webclient.http1.Http1Client.md">
<code>client</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="Http1Client">Http1Client</code>
</td>
<td>The <code>Http1Client</code> to use to communicate with the Eureka server</td>
</tr>
<tr>
<td>
<code>prefer-ip-address</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td>Whether the <dfn>host</dfn> component of any <code>java.net.URI URI</code> should be set to the IP address stored by Eureka, or the hostname; <code>false</code> by default</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
