# io.<wbr>helidon.<wbr>webclient.<wbr>api.<wbr>WebClient

## Description

WebClient configuration

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
<code>connect-<wbr>timeout</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
</td>
<td>Connect timeout</td>
</tr>
<tr>
<td>
<a id="proxy"></a>
<a href="io.helidon.webclient.api.Proxy.md">
<code>proxy</code>
</a>
</td>
<td>
<code>Proxy</code>
</td>
<td>
</td>
<td>Proxy configuration to be used for requests</td>
</tr>
<tr>
<td>
<code>follow-<wbr>redirects</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to follow redirects</td>
</tr>
<tr>
<td>
<code>keep-<wbr>alive</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Determines if connection keep alive is enabled (NOT socket keep alive, but HTTP connection keep alive, to re-use the same connection for multiple requests)</td>
</tr>
<tr>
<td>
<code>read-<wbr>timeout</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
</td>
<td>Read timeout</td>
</tr>
<tr>
<td>
<code>max-<wbr>redirects</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>10</code>
</td>
<td>Max number of followed redirects</td>
</tr>
<tr>
<td>
<a id="protocol-configs"></a>
<a href="io.helidon.webclient.spi.ProtocolConfig.md">
<code>protocol-<wbr>configs</code>
</a>
</td>
<td>
<code>List&lt;<wbr>Protocol<wbr>Config&gt;</code>
</td>
<td>
</td>
<td>Configuration of client protocols</td>
</tr>
<tr>
<td>
<code>protocol-<wbr>configs-<wbr>discover-<wbr>services</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to enable automatic service discovery for <code>protocol-<wbr>configs</code></td>
</tr>
<tr>
<td>
<a id="tls"></a>
<a href="io.helidon.common.tls.Tls.md">
<code>tls</code>
</a>
</td>
<td>
<code>Tls</code>
</td>
<td>
</td>
<td>TLS configuration for any TLS request from this client</td>
</tr>
<tr>
<td>
<code>protocol-<wbr>preference</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
</td>
<td>List of HTTP protocol IDs by order of preference</td>
</tr>
<tr>
<td>
<code>properties</code>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> String&gt;</code>
</td>
<td>
</td>
<td>Properties configured for this client</td>
</tr>
</tbody>
</table>



## Usages

- <a href="config_reference.md#clients"><code>clients</code></a>
- <a href="io.helidon.security.providers.oidc.common.OidcConfig.md#webclient"><code>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient</code></a>
- <a href="io.helidon.security.providers.oidc.OidcProvider.md#webclient"><code>security.<wbr>providers.<wbr>oidc.<wbr>webclient</code></a>
- <a href="io.helidon.security.providers.oidc.common.OidcConfig.md#webclient"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient</code></a>
- <a href="io.helidon.security.providers.oidc.OidcProvider.md#webclient"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>oidc.<wbr>webclient</code></a>

---

See the [manifest](manifest.md) for all available types.
