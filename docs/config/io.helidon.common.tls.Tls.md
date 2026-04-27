# io.helidon.common.tls.Tls

## Description

TLS configuration - common for server and client

## Configuration options

<style>
    table.cm-table code {
        white-space: nowrap !important;
    }

    table.cm-table .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


<table class="cm-table">
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
<td class="cm-type-cell">
<code class="cm-truncate-value">List&lt;Keys&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>List of certificates that form the trust manager</td>
</tr>
<tr>
<td>
<code>session-timeout</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Duration</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">PT24H</code>
</td>
<td>SSL session timeout</td>
</tr>
<tr>
<td>
<code>internal-keystore-provider</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
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
<td class="cm-type-cell">
<code class="cm-truncate-value">TlsManager</code>
</td>
<td class="cm-default-cell">
</td>
<td>The Tls manager</td>
</tr>
<tr>
<td>
<code>endpoint-identification-algorithm</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">HTTPS</code>
</td>
<td>Identification algorithm for SSL endpoints</td>
</tr>
<tr>
<td>
<a id="private-key"></a>
<a href="io.helidon.common.pki.Keys.md">
<code>private-key</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Keys</code>
</td>
<td class="cm-default-cell">
</td>
<td>Private key to use</td>
</tr>
<tr>
<td>
<code>key-manager-factory-algorithm</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Algorithm of the key manager factory used when private key is defined</td>
</tr>
<tr>
<td>
<code>manager-discover-services</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Whether to enable automatic service discovery for <code>manager</code></td>
</tr>
<tr>
<td>
<code>secure-random-provider</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Provider to use when creating a new secure random</td>
</tr>
<tr>
<td>
<code>session-cache-size</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Integer</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">20480</code>
</td>
<td>SSL session cache size</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
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
<td class="cm-type-cell">
<code class="cm-truncate-value" title="RevocationConfig">RevocationConfig</code>
</td>
<td class="cm-default-cell">
</td>
<td>Certificate revocation check configuration</td>
</tr>
<tr>
<td>
<code>protocol</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">TLS</code>
</td>
<td>Configure the protocol used to obtain an instance of <code>javax.net.ssl.SSLContext</code></td>
</tr>
<tr>
<td>
<code>provider</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Use explicit provider to obtain an instance of <code>javax.net.ssl.SSLContext</code></td>
</tr>
<tr>
<td>
<a id="client-auth"></a>
<a href="io.helidon.common.tls.TlsClientAuth.md">
<code>client-auth</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="TlsClientAuth">TlsClientAuth</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">NONE</code>
</td>
<td>Configure requirement for mutual TLS</td>
</tr>
<tr>
<td>
<code>cipher-suite</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Enabled cipher suites for TLS communication</td>
</tr>
<tr>
<td>
<code>internal-keystore-type</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Type of the key stores used internally to create a key and trust manager factories</td>
</tr>
<tr>
<td>
<code>trust-manager-factory-algorithm</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
</td>
<td>Trust manager factory algorithm</td>
</tr>
<tr>
<td>
<code>trust-all</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Trust any certificate provided by the other side of communication</td>
</tr>
<tr>
<td>
<code>protocols</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td class="cm-default-cell">
</td>
<td>Enabled protocols for TLS communication</td>
</tr>
<tr>
<td>
<code>secure-random-algorithm</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td class="cm-default-cell">
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
