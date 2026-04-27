# io.helidon.security.providers.config.vault.ConfigVaultProvider

## Description

Secrets and Encryption provider using just configuration

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
<code>master-password</code>
</td>
<td class="cm-type-cell">
<code class="cm-truncate-value" title="List&lt;String&gt;">List&lt;String&gt;</code>
</td>
<td>Configure master password used for encryption/decryption</td>
</tr>
</tbody>
</table>



## Usages

- [`security.providers.config-vault`](io.helidon.security.spi.SecurityProvider.md#config-vault)
- [`server.features.security.security.providers.config-vault`](io.helidon.security.spi.SecurityProvider.md#config-vault)

---

See the [manifest](manifest.md) for all available types.
