# io.helidon.security.providers.httpsign.InboundClientDefinition

## Description

Configuration of inbound client

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
<code>key-<wbr>id</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>The key id of this client to map to this signature validation configuration</td>
</tr>
<tr>
<td>
<a id="public-key"></a>
<a href="io.helidon.common.pki.Keys.md">
<code>public-<wbr>key</code>
</a>
</td>
<td>
<code>Keys</code>
</td>
<td>
</td>
<td>For algorithms based on public/private key (such as rsa-sha256), this provides access to the public key of the client</td>
</tr>
<tr>
<td>
<code>hmac.<wbr>secret</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Helper method to configure a password-like secret (instead of byte based <code>#hmac<wbr>Secret(<wbr>byte[])</code></td>
</tr>
<tr>
<td>
<code>principal-<wbr>name</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>The principal name of the client, defaults to keyId if not configured</td>
</tr>
<tr>
<td>
<a id="principal-type"></a>
<a href="io.helidon.security.SubjectType.md">
<code>principal-<wbr>type</code>
</a>
</td>
<td>
<code>Subject<wbr>Type</code>
</td>
<td>
<code>SERVICE</code>
</td>
<td>The type of principal we have authenticated (either user or service, defaults to service)</td>
</tr>
<tr>
<td>
<code>algorithm</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Algorithm of signature used by this client</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
