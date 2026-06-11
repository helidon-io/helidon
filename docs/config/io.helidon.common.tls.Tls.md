# io.helidon.common.tls.Tls

## Description

TLS configuration - common for server and client

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
<a id="trust"></a>
<a href="io.helidon.common.pki.Keys.md">
<code>trust</code>
</a>
</td>
<td>
<code>List&lt;<wbr>Keys&gt;</code>
</td>
<td>
</td>
<td>List of certificates that form the trust manager</td>
</tr>
<tr>
<td>
<code>session-<wbr>timeout</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
<code>PT24H</code>
</td>
<td>SSL session timeout</td>
</tr>
<tr>
<td>
<code>internal-<wbr>keystore-<wbr>provider</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Provider of the key stores used internally to create a key and trust manager factories</td>
</tr>
<tr>
<td>
<a id="manager"></a>
<a href="io.helidon.common.tls.TlsManager.md">
<code>manager</code>
</a>
</td>
<td>
<code>Tls<wbr>Manager</code>
</td>
<td>
</td>
<td>The Tls manager</td>
</tr>
<tr>
<td>
<code>endpoint-<wbr>identification-<wbr>algorithm</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>HTTPS</code>
</td>
<td>Identification algorithm for SSL endpoints</td>
</tr>
<tr>
<td>
<a id="private-key"></a>
<a href="io.helidon.common.pki.Keys.md">
<code>private-<wbr>key</code>
</a>
</td>
<td>
<code>Keys</code>
</td>
<td>
</td>
<td>Private key to use</td>
</tr>
<tr>
<td>
<code>key-<wbr>manager-<wbr>factory-<wbr>algorithm</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Algorithm of the key manager factory used when private key is defined</td>
</tr>
<tr>
<td>
<code>manager-<wbr>discover-<wbr>services</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Whether to enable automatic service discovery for <code>manager</code></td>
</tr>
<tr>
<td>
<code>secure-<wbr>random-<wbr>provider</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Provider to use when creating a new secure random</td>
</tr>
<tr>
<td>
<code>session-<wbr>cache-<wbr>size</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
<code>20480</code>
</td>
<td>SSL session cache size</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Flag indicating whether Tls is enabled</td>
</tr>
<tr>
<td>
<a id="revocation"></a>
<a href="io.helidon.common.tls.RevocationConfig.md">
<code>revocation</code>
</a>
</td>
<td>
<code>Revocation<wbr>Config</code>
</td>
<td>
</td>
<td>Certificate revocation check configuration</td>
</tr>
<tr>
<td>
<code>protocol</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>TLS</code>
</td>
<td>Configure the protocol used to obtain an instance of <code>javax.<wbr>net.<wbr>ssl.<wbr>SSLContext</code></td>
</tr>
<tr>
<td>
<code>provider</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Use explicit provider to obtain an instance of <code>javax.<wbr>net.<wbr>ssl.<wbr>SSLContext</code></td>
</tr>
<tr>
<td>
<a id="client-auth"></a>
<a href="io.helidon.common.tls.TlsClientAuth.md">
<code>client-<wbr>auth</code>
</a>
</td>
<td>
<code>Tls<wbr>Client<wbr>Auth</code>
</td>
<td>
<code>NONE</code>
</td>
<td>Configure requirement for mutual TLS</td>
</tr>
<tr>
<td>
<code>cipher-<wbr>suite</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
</td>
<td>Enabled cipher suites for TLS communication</td>
</tr>
<tr>
<td>
<code>internal-<wbr>keystore-<wbr>type</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Type of the key stores used internally to create a key and trust manager factories</td>
</tr>
<tr>
<td>
<code>trust-<wbr>manager-<wbr>factory-<wbr>algorithm</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Trust manager factory algorithm</td>
</tr>
<tr>
<td>
<code>trust-<wbr>all</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Trust any certificate provided by the other side of communication</td>
</tr>
<tr>
<td>
<code>protocols</code>
</td>
<td>
<code>List&lt;<wbr>String&gt;</code>
</td>
<td>
</td>
<td>Enabled protocols for TLS communication</td>
</tr>
<tr>
<td>
<code>secure-<wbr>random-<wbr>algorithm</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Algorithm to use when creating a new secure random</td>
</tr>
</tbody>
</table>



## Usages

- [`clients.tls`](io.helidon.webclient.api.WebClient.md#tls)
- [`security.providers.idcs-role-mapper.oidc-config.webclient.tls`](io.helidon.webclient.api.WebClient.md#tls)
- [`security.providers.oidc.webclient.tls`](io.helidon.webclient.api.WebClient.md#tls)
- [`server.features.security.security.providers.idcs-role-mapper.oidc-config.webclient.tls`](io.helidon.webclient.api.WebClient.md#tls)
- [`server.features.security.security.providers.oidc.webclient.tls`](io.helidon.webclient.api.WebClient.md#tls)
- [`server.sockets.tls`](io.helidon.webserver.ListenerConfig.md#tls)
- [`server.tls`](io.helidon.webserver.WebServer.md#tls)

---

See the [manifest](manifest.md) for all available types.
