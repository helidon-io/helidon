# io.helidon.common.pki.Keys

## Description

Configuration of keys

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
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<a id="pem"></a>
<a href="io.helidon.common.pki.PemKeys.md">
<code>pem</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">PemKeys</code>
</td>
<td>Configure keys from pem file(s)</td>
</tr>
<tr>
<td>
<a id="keystore"></a>
<a href="io.helidon.common.pki.KeystoreKeys.md">
<code>keystore</code>
</a>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="KeystoreKeys">KeystoreKeys</code>
</td>
<td>Configure keys from a keystore</td>
</tr>
</tbody>
</table>



## Usages

- [`clients.tls.private-key`](io.helidon.common.tls.Tls.md#private-key)
- [`clients.tls.trust`](io.helidon.common.tls.Tls.md#trust)
- [`security.providers.idcs-role-mapper.oidc-config.webclient.tls.private-key`](io.helidon.common.tls.Tls.md#private-key)
- [`security.providers.idcs-role-mapper.oidc-config.webclient.tls.trust`](io.helidon.common.tls.Tls.md#trust)
- [`security.providers.oidc.webclient.tls.private-key`](io.helidon.common.tls.Tls.md#private-key)
- [`security.providers.oidc.webclient.tls.trust`](io.helidon.common.tls.Tls.md#trust)
- [`server.features.security.security.providers.idcs-role-mapper.oidc-config.webclient.tls.private-key`](io.helidon.common.tls.Tls.md#private-key)
- [`server.features.security.security.providers.idcs-role-mapper.oidc-config.webclient.tls.trust`](io.helidon.common.tls.Tls.md#trust)
- [`server.features.security.security.providers.oidc.webclient.tls.private-key`](io.helidon.common.tls.Tls.md#private-key)
- [`server.features.security.security.providers.oidc.webclient.tls.trust`](io.helidon.common.tls.Tls.md#trust)
- [`server.sockets.tls.private-key`](io.helidon.common.tls.Tls.md#private-key)
- [`server.sockets.tls.trust`](io.helidon.common.tls.Tls.md#trust)
- [`server.tls.private-key`](io.helidon.common.tls.Tls.md#private-key)
- [`server.tls.trust`](io.helidon.common.tls.Tls.md#trust)

---

See the [manifest](manifest.md) for all available types.
