# io.helidon.webclient.api.WebClient

## Description

WebClient configuration

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
<td><code>connect-timeout</code></td>
<td><code>Duration</code></td>
<td></td>
<td>Connect timeout</td>
</tr>
<tr>
<td><a id="proxy"></a><a href="io.helidon.webclient.api.Proxy.md"><code>proxy</code></a></td>
<td><code>Proxy</code></td>
<td></td>
<td>Proxy configuration to be used for requests</td>
</tr>
<tr>
<td><code>follow-redirects</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether to follow redirects</td>
</tr>
<tr>
<td><code>keep-alive</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Determines if connection keep alive is enabled (NOT socket keep alive, but HTTP connection keep alive, to re-use the same connection for multiple requests)</td>
</tr>
<tr>
<td><code>read-timeout</code></td>
<td><code>Duration</code></td>
<td></td>
<td>Read timeout</td>
</tr>
<tr>
<td><code>max-redirects</code></td>
<td><code>Integer</code></td>
<td><code>10</code></td>
<td>Max number of followed redirects</td>
</tr>
<tr>
<td><a id="protocol-configs"></a><a href="io.helidon.webclient.spi.ProtocolConfig.md"><code>protocol-configs</code></a></td>
<td><code>List&lt;ProtocolConfig&gt;</code></td>
<td></td>
<td>Configuration of client protocols</td>
</tr>
<tr>
<td><code>protocol-configs-discover-services</code></td>
<td><code>Boolean</code></td>
<td><code>true</code></td>
<td>Whether to enable automatic service discovery for &lt;code&gt;protocol-configs&lt;/code&gt;</td>
</tr>
<tr>
<td><a id="tls"></a><a href="io.helidon.common.tls.Tls.md"><code>tls</code></a></td>
<td><code>Tls</code></td>
<td></td>
<td>TLS configuration for any TLS request from this client</td>
</tr>
<tr>
<td><code>protocol-preference</code></td>
<td><code>List&lt;String&gt;</code></td>
<td></td>
<td>List of HTTP protocol IDs by order of preference</td>
</tr>
<tr>
<td><code>properties</code></td>
<td><code>Map&lt;String, String&gt;</code></td>
<td></td>
<td>Properties configured for this client</td>
</tr>
</tbody>
</table>


## Usages

- [`clients`](config_reference.md#clients)
- [`security.providers.idcs-role-mapper.oidc-config.webclient`](io.helidon.security.providers.oidc.common.OidcConfig.md#webclient)
- [`security.providers.oidc.webclient`](io.helidon.security.providers.oidc.OidcProvider.md#webclient)
- [`server.features.security.security.providers.idcs-role-mapper.oidc-config.webclient`](io.helidon.security.providers.oidc.common.OidcConfig.md#webclient)
- [`server.features.security.security.providers.oidc.webclient`](io.helidon.security.providers.oidc.OidcProvider.md#webclient)

---

See the [manifest](manifest.md) for all available types.
