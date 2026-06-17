# io.<wbr>helidon.<wbr>webclient.<wbr>api.<wbr>Http<wbr>Config<wbr>Base

## Description

Common configuration for HTTP protocols

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



## Dependent Types

- [io.<wbr>helidon.<wbr>webclient.<wbr>api.<wbr>Http<wbr>Client<wbr>Config](io.helidon.webclient.api.HttpClientConfig.md)
- [io.<wbr>helidon.<wbr>webclient.<wbr>api.<wbr>WebClient](io.helidon.webclient.api.WebClient.md)

---

See the [manifest](manifest.md) for all available types.
