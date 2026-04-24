# io.helidon.webclient.api.Proxy

## Description

A definition of a proxy server to use for outgoing requests

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
<code>password</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Sets a new password for the proxy</td>
</tr>
<tr>
<td>
<code>port</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
</td>
<td>Sets a port value</td>
</tr>
<tr>
<td>
<code>host</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Sets a new host value</td>
</tr>
<tr>
<td>
<code>force-http-connect</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
</td>
<td>Forces HTTP CONNECT with the proxy server</td>
</tr>
<tr>
<td>
<code>no-proxy</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Configure a host pattern that is not going through a proxy</td>
</tr>
<tr>
<td>
<a id="type"></a>
<a href="io.helidon.webclient.api.Proxy.ProxyType.md">
<code>type</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">ProxyType</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">HTTP</code>
</td>
<td>Sets a new proxy type</td>
</tr>
<tr>
<td>
<code>username</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Sets a new username for the proxy</td>
</tr>
</tbody>
</table>



## Usages

- [`clients.proxy`](io.helidon.webclient.api.WebClient.md#proxy)
- [`security.providers.idcs-role-mapper.oidc-config.webclient.proxy`](io.helidon.webclient.api.WebClient.md#proxy)
- [`security.providers.oidc.webclient.proxy`](io.helidon.webclient.api.WebClient.md#proxy)
- [`server.features.security.security.providers.idcs-role-mapper.oidc-config.webclient.proxy`](io.helidon.webclient.api.WebClient.md#proxy)
- [`server.features.security.security.providers.oidc.webclient.proxy`](io.helidon.webclient.api.WebClient.md#proxy)

---

See the [manifest](manifest.md) for all available types.
