# io.helidon.security.providers.httpsign.InboundClientDefinition

## Description

Configuration of inbound client

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
<td><code>key-id</code></td>
<td><code>String</code></td>
<td></td>
<td>The key id of this client to map to this signature validation configuration</td>
</tr>
<tr>
<td><a id="public-key"></a><a href="io.helidon.common.pki.Keys.md"><code>public-key</code></a></td>
<td><code>Keys</code></td>
<td></td>
<td>For algorithms based on public/private key (such as rsa-sha256), this provides access to the public key of the client</td>
</tr>
<tr>
<td><code>hmac.secret</code></td>
<td><code>String</code></td>
<td></td>
<td>Helper method to configure a password-like secret (instead of byte based &lt;code&gt;#hmacSecret(byte[])&lt;/code&gt;</td>
</tr>
<tr>
<td><code>principal-name</code></td>
<td><code>String</code></td>
<td></td>
<td>The principal name of the client, defaults to keyId if not configured</td>
</tr>
<tr>
<td><a id="principal-type"></a><a href="io.helidon.security.SubjectType.md"><code>principal-type</code></a></td>
<td><code>SubjectType</code></td>
<td><code>SERVICE</code></td>
<td>The type of principal we have authenticated (either user or service, defaults to service)</td>
</tr>
<tr>
<td><code>algorithm</code></td>
<td><code>String</code></td>
<td></td>
<td>Algorithm of signature used by this client</td>
</tr>
</tbody>
</table>


---

See the [manifest](manifest.md) for all available types.
