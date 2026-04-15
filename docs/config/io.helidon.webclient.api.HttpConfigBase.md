# io.helidon.webclient.api.HttpConfigBase

## Description

Common configuration for HTTP protocols

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
<td><a id="tls"></a><a href="io.helidon.common.tls.Tls.md"><code>tls</code></a></td>
<td><code>Tls</code></td>
<td></td>
<td>TLS configuration for any TLS request from this client</td>
</tr>
<tr>
<td><code>properties</code></td>
<td><code>Map&lt;String, String&gt;</code></td>
<td></td>
<td>Properties configured for this client</td>
</tr>
</tbody>
</table>


## Dependent Types

- [io.helidon.webclient.api.HttpClientConfig](io.helidon.webclient.api.HttpClientConfig.md)
- [io.helidon.webclient.api.WebClient](io.helidon.webclient.api.WebClient.md)

---

See the [manifest](manifest.md) for all available types.
