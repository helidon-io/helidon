# io.helidon.security.providers.httpsign.InboundClientDefinition

## Description

Configuration of inbound client.

## Usages

## Configuration options

<table class="tableblock frame-all grid-all stretch">
<colgroup>
<col style="width: 20%" />
<col style="width: 20%" />
<col style="width: 20%" />
<col style="width: 20%" />
<col style="width: 20%" />
</colgroup>
<thead>
<tr>
<th class="tableblock halign-left valign-top">Key</th>
<th class="tableblock halign-left valign-top">Kind</th>
<th class="tableblock halign-left valign-top">Type</th>
<th class="tableblock halign-left valign-top">Default Value</th>
<th class="tableblock halign-left valign-top">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a708a4-algorithm"></span> <code>algorithm</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>String</code></p></td>
<td class="tableblock halign-left valign-top"><p> </p></td>
<td class="tableblock halign-left valign-top"><p>Algorithm of signature used by this client</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a3011b-hmac-secret"></span> <code>hmac.secret</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>String</code></p></td>
<td class="tableblock halign-left valign-top"><p> </p></td>
<td class="tableblock halign-left valign-top">Helper method to configure a password-like secret (instead of byte based
#hmacSecret(byte[)&lt;/code&gt;]</td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a7c8b3-key-id"></span> <code>key-id</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>String</code></p></td>
<td class="tableblock halign-left valign-top"><p> </p></td>
<td class="tableblock halign-left valign-top"><p>The key id of this client to map to this signature validation configuration</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a42b54-principal-name"></span> <code>principal-name</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>String</code></p></td>
<td class="tableblock halign-left valign-top"><p> </p></td>
<td class="tableblock halign-left valign-top"><p>The principal name of the client, defaults to keyId if not configured</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="ae69b8-principal-type"></span> <a href="../config/../config/io_helidon_security_SubjectType.html"><code>principal-type</code></a></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>i.h.s.SubjectType</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>SERVICE</code></p></td>
<td class="tableblock halign-left valign-top"><p>The type of principal we have authenticated (either user or service, defaults to service)</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="ad4680-public-key"></span> <a href="../config/../config/io_helidon_common_pki_Keys.html"><code>public-key</code></a></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>i.h.c.p.Keys</code></p></td>
<td class="tableblock halign-left valign-top"><p> </p></td>
<td class="tableblock halign-left valign-top"><p>For algorithms based on public/private key (such as rsa-sha256), this provides access to the public key of the client</p></td>
</tr>
</tbody>
</table>

------------------------------------------------------------------------

See the [manifest](../config/manifest.md) for all available types.
