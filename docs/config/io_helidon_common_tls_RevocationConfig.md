# io.helidon.common.tls.RevocationConfig

## Description

Certificate revocation configuration.

## Usages

- [`clients.tls.revocation`](../config/io_helidon_common_tls_Tls.md#a7a660-revocation)
- [`security.providers.idcs-role-mapper.oidc-config.webclient.tls.revocation`](../config/io_helidon_common_tls_Tls.md#a7a660-revocation)
- [`security.providers.oidc.webclient.tls.revocation`](../config/io_helidon_common_tls_Tls.md#a7a660-revocation)
- [`server.features.security.security.providers.idcs-role-mapper.oidc-config.webclient.tls.revocation`](../config/io_helidon_common_tls_Tls.md#a7a660-revocation)
- [`server.features.security.security.providers.oidc.webclient.tls.revocation`](../config/io_helidon_common_tls_Tls.md#a7a660-revocation)
- [`server.sockets.tls.revocation`](../config/io_helidon_common_tls_Tls.md#a7a660-revocation)
- [`server.tls.revocation`](../config/io_helidon_common_tls_Tls.md#a7a660-revocation)

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
<td class="tableblock halign-left valign-top"><p><span id="af8d12-check-only-end-entity"></span> <code>check-only-end-entity</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>false</code></p></td>
<td class="tableblock halign-left valign-top"><p>Only check the revocation status of end-entity certificates</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a50413-enabled"></span> <code>enabled</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>false</code></p></td>
<td class="tableblock halign-left valign-top"><p>Flag indicating whether this revocation config is enabled</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a0a3e8-fallback-enabled"></span> <code>fallback-enabled</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>true</code></p></td>
<td class="tableblock halign-left valign-top"><p>Enable fallback to the less preferred checking option</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="a501f0-ocsp-responder-uri"></span> <code>ocsp-responder-uri</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>URI</code></p></td>
<td class="tableblock halign-left valign-top"><p> </p></td>
<td class="tableblock halign-left valign-top"><p>The URI that identifies the location of the OCSP responder</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="aabaa0-prefer-crl-over-ocsp"></span> <code>prefer-crl-over-ocsp</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>false</code></p></td>
<td class="tableblock halign-left valign-top"><p>Prefer CRL over OCSP</p></td>
</tr>
<tr>
<td class="tableblock halign-left valign-top"><p><span id="ab0b51-soft-fail-enabled"></span> <code>soft-fail-enabled</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>VALUE</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>Boolean</code></p></td>
<td class="tableblock halign-left valign-top"><p><code>false</code></p></td>
<td class="tableblock halign-left valign-top"><p>Allow revocation check to succeed if the revocation status cannot be determined for one of the following reasons:</p>
<ul>
<li>The CRL or OCSP response cannot be obtained because of a network error</li>
</ul></td>
</tr>
</tbody>
</table>

------------------------------------------------------------------------

See the [manifest](../config/manifest.md) for all available types.
