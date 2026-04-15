# io.helidon.common.pki.KeystoreKeys

## Description

Resources from a java keystore (PKCS12, JKS etc.)

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
<td><a id="cert"></a><a href="io.helidon.clients.tls.privateKey.keystore.CertConfig.md"><code>cert</code></a></td>
<td></td>
<td></td>
<td>Configuration for cert</td>
</tr>
<tr>
<td><a id="cert-chain"></a><a href="io.helidon.clients.tls.privateKey.keystore.CertChainConfig.md"><code>cert-chain</code></a></td>
<td></td>
<td></td>
<td>Configuration for cert-chain</td>
</tr>
<tr>
<td><a id="key"></a><a href="io.helidon.clients.tls.privateKey.keystore.KeyConfig.md"><code>key</code></a></td>
<td></td>
<td></td>
<td>Configuration for key</td>
</tr>
<tr>
<td><code>passphrase</code></td>
<td><code>String</code></td>
<td></td>
<td>Pass-phrase of the keystore (supported with JKS and PKCS12 keystores)</td>
</tr>
<tr>
<td><a id="resource"></a><a href="io.helidon.common.configurable.Resource.md"><code>resource</code></a></td>
<td><code>Resource</code></td>
<td></td>
<td>Keystore resource definition</td>
</tr>
<tr>
<td><code>trust-store</code></td>
<td><code>Boolean</code></td>
<td><code>false</code></td>
<td>If you want to build a trust store, call this method to add all certificates present in the keystore to certificate list</td>
</tr>
<tr>
<td><code>type</code></td>
<td><code>String</code></td>
<td><code>PKCS12</code></td>
<td>Set type of keystore</td>
</tr>
</tbody>
</table>


## Usages

- [`clients.tls.private-key.keystore`](io.helidon.common.pki.Keys.md#keystore)
- [`clients.tls.trust.keystore`](io.helidon.common.pki.Keys.md#keystore)
- [`security.providers.idcs-role-mapper.oidc-config.webclient.tls.private-key.keystore`](io.helidon.common.pki.Keys.md#keystore)
- [`security.providers.idcs-role-mapper.oidc-config.webclient.tls.trust.keystore`](io.helidon.common.pki.Keys.md#keystore)
- [`security.providers.oidc.webclient.tls.private-key.keystore`](io.helidon.common.pki.Keys.md#keystore)
- [`security.providers.oidc.webclient.tls.trust.keystore`](io.helidon.common.pki.Keys.md#keystore)
- [`server.features.security.security.providers.idcs-role-mapper.oidc-config.webclient.tls.private-key.keystore`](io.helidon.common.pki.Keys.md#keystore)
- [`server.features.security.security.providers.idcs-role-mapper.oidc-config.webclient.tls.trust.keystore`](io.helidon.common.pki.Keys.md#keystore)
- [`server.features.security.security.providers.oidc.webclient.tls.private-key.keystore`](io.helidon.common.pki.Keys.md#keystore)
- [`server.features.security.security.providers.oidc.webclient.tls.trust.keystore`](io.helidon.common.pki.Keys.md#keystore)
- [`server.sockets.tls.private-key.keystore`](io.helidon.common.pki.Keys.md#keystore)
- [`server.sockets.tls.trust.keystore`](io.helidon.common.pki.Keys.md#keystore)
- [`server.tls.private-key.keystore`](io.helidon.common.pki.Keys.md#keystore)
- [`server.tls.trust.keystore`](io.helidon.common.pki.Keys.md#keystore)

---

See the [manifest](manifest.md) for all available types.
