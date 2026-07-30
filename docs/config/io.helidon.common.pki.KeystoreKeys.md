# io.<wbr>helidon.<wbr>common.<wbr>pki.<wbr>Keystore<wbr>Keys

## Description

Resources from a java keystore (PKCS12, JKS etc.)

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
<a id="cert"></a>
<a href="io.helidon.clients.tls.privateKey.keystore.CertConfig.md">
<code>cert</code>
</a>
</td>
<td>
</td>
<td>
</td>
<td>Configuration for cert</td>
</tr>
<tr>
<td>
<a id="cert-chain"></a>
<a href="io.helidon.clients.tls.privateKey.keystore.CertChainConfig.md">
<code>cert-<wbr>chain</code>
</a>
</td>
<td>
</td>
<td>
</td>
<td>Configuration for cert-chain</td>
</tr>
<tr>
<td>
<a id="key"></a>
<a href="io.helidon.clients.tls.privateKey.keystore.KeyConfig.md">
<code>key</code>
</a>
</td>
<td>
</td>
<td>
</td>
<td>Configuration for key</td>
</tr>
<tr>
<td>
<code>passphrase</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Pass-phrase of the keystore (supported with JKS and PKCS12 keystores)</td>
</tr>
<tr>
<td>
<a id="resource"></a>
<a href="io.helidon.common.configurable.Resource.md">
<code>resource</code>
</a>
</td>
<td>
<code>Resource</code>
</td>
<td>
</td>
<td>Keystore resource definition</td>
</tr>
<tr>
<td>
<code>trust-<wbr>store</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>If you want to build a trust store, call this method to add all certificates present in the keystore to certificate list</td>
</tr>
<tr>
<td>
<code>type</code>
</td>
<td>
<code>String</code>
</td>
<td>
<code>PKCS12</code>
</td>
<td>Set type of keystore</td>
</tr>
</tbody>
</table>



## Usages

- <a href="io.helidon.common.pki.Keys.md#keystore"><code>clients.<wbr>tls.<wbr>private-<wbr>key.<wbr>keystore</code></a>
- <a href="io.helidon.common.pki.Keys.md#keystore"><code>clients.<wbr>tls.<wbr>trust.<wbr>keystore</code></a>
- <a href="io.helidon.common.pki.Keys.md#keystore"><code>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient.<wbr>tls.<wbr>private-<wbr>key.<wbr>keystore</code></a>
- <a href="io.helidon.common.pki.Keys.md#keystore"><code>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient.<wbr>tls.<wbr>trust.<wbr>keystore</code></a>
- <a href="io.helidon.common.pki.Keys.md#keystore"><code>security.<wbr>providers.<wbr>oidc.<wbr>webclient.<wbr>tls.<wbr>private-<wbr>key.<wbr>keystore</code></a>
- <a href="io.helidon.common.pki.Keys.md#keystore"><code>security.<wbr>providers.<wbr>oidc.<wbr>webclient.<wbr>tls.<wbr>trust.<wbr>keystore</code></a>
- <a href="io.helidon.common.pki.Keys.md#keystore"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient.<wbr>tls.<wbr>private-<wbr>key.<wbr>keystore</code></a>
- <a href="io.helidon.common.pki.Keys.md#keystore"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>idcs-<wbr>role-<wbr>mapper.<wbr>oidc-<wbr>config.<wbr>webclient.<wbr>tls.<wbr>trust.<wbr>keystore</code></a>
- <a href="io.helidon.common.pki.Keys.md#keystore"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>oidc.<wbr>webclient.<wbr>tls.<wbr>private-<wbr>key.<wbr>keystore</code></a>
- <a href="io.helidon.common.pki.Keys.md#keystore"><code>server.<wbr>features.<wbr>security.<wbr>security.<wbr>providers.<wbr>oidc.<wbr>webclient.<wbr>tls.<wbr>trust.<wbr>keystore</code></a>
- <a href="io.helidon.common.pki.Keys.md#keystore"><code>server.<wbr>sockets.<wbr>tls.<wbr>private-<wbr>key.<wbr>keystore</code></a>
- <a href="io.helidon.common.pki.Keys.md#keystore"><code>server.<wbr>sockets.<wbr>tls.<wbr>trust.<wbr>keystore</code></a>
- <a href="io.helidon.common.pki.Keys.md#keystore"><code>server.<wbr>tls.<wbr>private-<wbr>key.<wbr>keystore</code></a>
- <a href="io.helidon.common.pki.Keys.md#keystore"><code>server.<wbr>tls.<wbr>trust.<wbr>keystore</code></a>

---

See the [manifest](manifest.md) for all available types.
