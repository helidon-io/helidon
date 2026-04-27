# io.helidon.security.providers.oidc.webclient.tls.trust.keystore.KeyConfig

## Description

Configuration for security.providers.oidc.webclient.tls.trust.keystore.key

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
<code>alias</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>Alias of the private key in the keystore</td>
</tr>
<tr>
<td>
<code>passphrase</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value">String</code>
</td>
<td>Pass-phrase of the key in the keystore (used for private keys)</td>
</tr>
</tbody>
</table>



## Usages

- [`security.providers.oidc.webclient.tls.trust.keystore.key`](io.helidon.common.pki.KeystoreKeys.md#key)

---

See the [manifest](manifest.md) for all available types.
