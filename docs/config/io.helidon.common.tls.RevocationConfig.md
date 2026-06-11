# io.helidon.common.tls.RevocationConfig

## Description

Certificate revocation configuration

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
<code>soft-<wbr>fail-<wbr>enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Allow revocation check to succeed if the revocation status cannot be determined for one of the following reasons: <ul>  <li>The CRL or OCSP response cannot be obtained because of a      network error.</li></ul></td>
</tr>
<tr>
<td>
<code>ocsp-<wbr>responder-<wbr>uri</code>
</td>
<td>
<code>URI</code>
</td>
<td>
</td>
<td>The URI that identifies the location of the OCSP responder</td>
</tr>
<tr>
<td>
<code>check-<wbr>only-<wbr>end-<wbr>entity</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Only check the revocation status of end-entity certificates</td>
</tr>
<tr>
<td>
<code>prefer-<wbr>crl-<wbr>over-<wbr>ocsp</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Prefer CRL over OCSP</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>false</code>
</td>
<td>Flag indicating whether this revocation config is enabled</td>
</tr>
<tr>
<td>
<code>fallback-<wbr>enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Enable fallback to the less preferred checking option</td>
</tr>
</tbody>
</table>



## Usages

- [`clients.tls.revocation`](io.helidon.common.tls.Tls.md#revocation)
- [`security.providers.idcs-role-mapper.oidc-config.webclient.tls.revocation`](io.helidon.common.tls.Tls.md#revocation)
- [`security.providers.oidc.webclient.tls.revocation`](io.helidon.common.tls.Tls.md#revocation)
- [`server.features.security.security.providers.idcs-role-mapper.oidc-config.webclient.tls.revocation`](io.helidon.common.tls.Tls.md#revocation)
- [`server.features.security.security.providers.oidc.webclient.tls.revocation`](io.helidon.common.tls.Tls.md#revocation)
- [`server.sockets.tls.revocation`](io.helidon.common.tls.Tls.md#revocation)
- [`server.tls.revocation`](io.helidon.common.tls.Tls.md#revocation)

---

See the [manifest](manifest.md) for all available types.
