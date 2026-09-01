# io.<wbr>helidon.<wbr>common.<wbr>tls.<wbr>TlsMaterial

## Description

TLS key and trust material used to set up or reload manager state

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
<code>trust-<wbr>manager-<wbr>factory-<wbr>provider</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Trust manager factory provider to use</td>
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
<code>key-<wbr>manager-<wbr>factory-<wbr>provider</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Key manager factory provider</td>
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



## Dependent Types

- [io.<wbr>helidon.<wbr>common.<wbr>tls.<wbr>Tls](io.helidon.common.tls.Tls.md)

---

See the [manifest](manifest.md) for all available types.
