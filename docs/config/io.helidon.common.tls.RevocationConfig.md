# io.helidon.common.tls.RevocationConfig

## Description

Certificate revocation configuration

## Configuration options

<style>
    code {
        white-space: nowrap !important;
    }

    .cm-truncate-value {
        display: inline-block;
        max-width: 10ch;
        overflow: hidden;
        text-overflow: ellipsis;
        vertical-align: bottom;
    }
</style>


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
<code>soft-fail-enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Allow revocation check to succeed if the revocation status cannot be determined for one of the following reasons: The CRL or OCSP response cannot be obtained because of a      network error</td>
</tr>
<tr>
<td>
<code>ocsp-responder-uri</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">URI</code>
</td>
<td class="cm-default-cell">
</td>
<td>The URI that identifies the location of the OCSP responder</td>
</tr>
<tr>
<td>
<code>check-only-end-entity</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Only check the revocation status of end-entity certificates</td>
</tr>
<tr>
<td>
<code>prefer-crl-over-ocsp</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Prefer CRL over OCSP</td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">false</code>
</td>
<td>Flag indicating whether this revocation config is enabled</td>
</tr>
<tr>
<td>
<code>fallback-enabled</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">Boolean</code>
</td>
<td class="cm-default-cell">
<code class="cm-truncate-value">true</code>
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
